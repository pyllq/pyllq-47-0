# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

from __future__ import absolute_import, print_function, unicode_literals

import argparse
import logging
import mozpack.path as mozpath
import os
import platform
import subprocess
import sys
import which
from distutils.version import LooseVersion

from mozbuild.base import (
    MachCommandBase,
)

from mach.decorators import (
    CommandArgument,
    CommandProvider,
    Command,
)

ESLINT_NOT_FOUND_MESSAGE = '''
Could not find eslint!  We looked at the --binary option, at the ESLINT
environment variable, and then at your path.  Install eslint and needed plugins
with

mach eslint --setup

and try again.
'''.strip()

NODE_NOT_FOUND_MESSAGE = '''
nodejs v4.2.3 is either not installed or is installed to a non-standard path.
Please install nodejs from https://nodejs.org and try again.

Valid installation paths:
'''.strip()

NPM_NOT_FOUND_MESSAGE = '''
Node Package Manager (npm) is either not installed or installed to a
non-standard path. Please install npm from https://nodejs.org (it comes as an
option in the node installation) and try again.

Valid installation paths:
'''.strip()


@CommandProvider
class MachCommands(MachCommandBase):
    @Command('python', category='devenv',
        description='Run Python.')
    @CommandArgument('args', nargs=argparse.REMAINDER)
    def python(self, args):
        # Avoid logging the command
        self.log_manager.terminal_handler.setLevel(logging.CRITICAL)

        self._activate_virtualenv()

        return self.run_process([self.virtualenv_manager.python_path] + args,
            pass_thru=True,  # Allow user to run Python interactively.
            ensure_exit_code=False,  # Don't throw on non-zero exit code.
            # Note: subprocess requires native strings in os.environ on Windows
            append_env={b'PYTHONDONTWRITEBYTECODE': str('1')})

    @Command('python-test', category='testing',
        description='Run Python unit tests.')
    @CommandArgument('--verbose',
        default=False,
        action='store_true',
        help='Verbose output.')
    @CommandArgument('--stop',
        default=False,
        action='store_true',
        help='Stop running tests after the first error or failure.')
    @CommandArgument('tests', nargs='+',
        metavar='TEST',
        help='Tests to run. Each test can be a single file or a directory.')
    def python_test(self, tests, verbose=False, stop=False):
        self._activate_virtualenv()
        import glob

        # Python's unittest, and in particular discover, has problems with
        # clashing namespaces when importing multiple test modules. What follows
        # is a simple way to keep environments separate, at the price of
        # launching Python multiple times. This also runs tests via mozunit,
        # which produces output in the format Mozilla infrastructure expects.
        return_code = 0
        files = []
        # We search for files in both the current directory (for people running
        # from topsrcdir or cd'd into their test directory) and topsrcdir (to
        # support people running mach from the objdir).  The |break|s in the
        # loop below ensure that we don't run tests twice if we're running mach
        # from topsrcdir
        search_dirs = ['.', self.topsrcdir]
        last_search_dir = search_dirs[-1]
        for t in tests:
            for d in search_dirs:
                test = mozpath.join(d, t)
                if test.endswith('.py') and os.path.isfile(test):
                    files.append(test)
                    break
                elif os.path.isfile(test + '.py'):
                    files.append(test + '.py')
                    break
                elif os.path.isdir(test):
                    files += glob.glob(mozpath.join(test, 'test*.py'))
                    files += glob.glob(mozpath.join(test, 'unit*.py'))
                    break
                elif d == last_search_dir:
                    self.log(logging.WARN, 'python-test',
                             {'test': t},
                             'TEST-UNEXPECTED-FAIL | Invalid test: {test}')
                    if stop:
                        return 1

        for f in files:
            file_displayed_test = []  # Used as a boolean.

            def _line_handler(line):
                if not file_displayed_test and line.startswith('TEST-'):
                    file_displayed_test.append(True)

            inner_return_code = self.run_process(
                [self.virtualenv_manager.python_path, f],
                ensure_exit_code=False,  # Don't throw on non-zero exit code.
                log_name='python-test',
                # subprocess requires native strings in os.environ on Windows
                append_env={b'PYTHONDONTWRITEBYTECODE': str('1')},
                line_handler=_line_handler)
            return_code += inner_return_code

            if not file_displayed_test:
                self.log(logging.WARN, 'python-test', {'file': f},
                         'TEST-UNEXPECTED-FAIL | No test output (missing mozunit.main() call?): {file}')

            if verbose:
                if inner_return_code != 0:
                    self.log(logging.INFO, 'python-test', {'file': f},
                             'Test failed: {file}')
                else:
                    self.log(logging.INFO, 'python-test', {'file': f},
                             'Test passed: {file}')
            if stop and return_code > 0:
                return 1

        return 0 if return_code == 0 else 1

    @Command('eslint', category='devenv',
        description='Run eslint or help configure eslint for optimal development.')
    @CommandArgument('-s', '--setup', default=False, action='store_true',
        help='configure eslint for optimal development.')
    @CommandArgument('-e', '--ext', default='[.js,.jsm,.jsx,.xml,.html]',
        help='Filename extensions to lint, default: "[.js,.jsm,.jsx,.xml,.html]".')
    @CommandArgument('-b', '--binary', default=None,
        help='Path to eslint binary.')
    @CommandArgument('args', nargs=argparse.REMAINDER)  # Passed through to eslint.
    def eslint(self, setup, ext=None, binary=None, args=None):
        '''Run eslint.'''

        # eslint requires at least node 4.2.3
        nodePath = self.getNodeOrNpmPath("node", LooseVersion("4.2.3"))
        if not nodePath:
            return 1

        if setup:
            return self.eslint_setup()

        if not binary:
            binary = os.environ.get('ESLINT', None)
            if not binary:
                try:
                    binary = which.which('eslint')
                except which.WhichError:
                    pass

        if not binary:
            print(ESLINT_NOT_FOUND_MESSAGE)
            return 1

        self.log(logging.INFO, 'eslint', {'binary': binary, 'args': args},
            'Running {binary}')

        args = args or ['.']

        cmd_args = [binary,
                    # Enable the HTML plugin.
                    # We can't currently enable this in the global config file
                    # because it has bad interactions with the SublimeText
                    # ESLint plugin (bug 1229874).
                    '--plugin', 'html',
                    '--ext', ext,  # This keeps ext as a single argument.
                    ] + args

        success = self.run_process(cmd_args,
            pass_thru=True,  # Allow user to run eslint interactively.
            ensure_exit_code=False,  # Don't throw on non-zero exit code.
            require_unix_environment=True # eslint is not a valid Win32 binary.
        )

        self.log(logging.INFO, 'eslint', {'msg': ('No errors' if success == 0 else 'Errors')},
            'Finished eslint. {msg} encountered.')
        return success

    def eslint_setup(self, update_only=False):
        """Ensure eslint is optimally configured.

        This command will inspect your eslint configuration and
        guide you through an interactive wizard helping you configure
        eslint for optimal use on Mozilla projects.
        """
        sys.path.append(os.path.dirname(__file__))

        npmPath = self.getNodeOrNpmPath("npm")
        if not npmPath:
            return 1

        # Install eslint 1.10.3.
        # Note that that's the version currently compatible with the mozilla
        # eslint plugin.
        success = self.callProcess("eslint",
                                   [npmPath, "install", "eslint@1.10.3", "-g"])
        if not success:
            return 1

        # Install eslint-plugin-mozilla.
        success = self.callProcess("eslint-plugin-mozilla",
                                   [npmPath, "link"],
                                   "testing/eslint-plugin-mozilla")
        if not success:
            return 1

        # Install eslint-plugin-html.
        success = self.callProcess("eslint-plugin-html",
                                   [npmPath, "install", "eslint-plugin-html", "-g"])
        if not success:
            return 1

        # Install eslint-plugin-react.
        success = self.callProcess("eslint-plugin-react",
                                   [npmPath, "install", "eslint-plugin-react", "-g"])
        if not success:
            return 1

        print("\nESLint and approved plugins installed successfully!")

    def callProcess(self, name, cmd, cwd=None):
        print("\nInstalling %s using \"%s\"..." % (name, " ".join(cmd)))

        try:
            with open(os.devnull, "w") as fnull:
                subprocess.check_call(cmd, cwd=cwd, stdout=fnull)
        except subprocess.CalledProcessError:
            if cwd:
                print("\nError installing %s in the %s folder, aborting." % (name, cwd))
            else:
                print("\nError installing %s, aborting." % name)

            return False

        return True

    def getPossibleNodePathsWin(self):
        """
        Return possible nodejs paths on Windows.
        """
        if platform.system() != "Windows":
            return []

        return list({
            "%s\\nodejs" % os.environ.get("SystemDrive"),
            os.path.join(os.environ.get("ProgramFiles"), "nodejs"),
            os.path.join(os.environ.get("PROGRAMW6432"), "nodejs"),
            os.path.join(os.environ.get("PROGRAMFILES"), "nodejs")
        })

    def getNodeOrNpmPath(self, filename, minversion=None):
        """
        Return the nodejs or npm path.
        """
        if platform.system() == "Windows":
            for ext in [".cmd", ".exe", ""]:
                try:
                    nodeOrNpmPath = which.which(filename + ext,
                                                path=self.getPossibleNodePathsWin())
                    if self.is_valid(nodeOrNpmPath, minversion):
                        return nodeOrNpmPath
                except which.WhichError:
                    pass
        else:
            try:
                nodeOrNpmPath = which.which(filename)
                if self.is_valid(nodeOrNpmPath, minversion):
                    return nodeOrNpmPath
            except which.WhichError:
                pass

        if filename == "node":
            print(NODE_NOT_FOUND_MESSAGE)
        elif filename == "npm":
            print(NPM_NOT_FOUND_MESSAGE)

        if platform.system() == "Windows":
            appPaths = self.getPossibleNodePathsWin()

            for p in appPaths:
                print("  - %s" % p)
        elif platform.system() == "Darwin":
            print("  - /usr/local/bin/node")
        elif platform.system() == "Linux":
            print("  - /usr/bin/nodejs")

        return None

    def is_valid(self, path, minversion = None):
        try:
            version_str = subprocess.check_output([path, "--version"],
                                                  stderr=subprocess.STDOUT)
            if minversion:
                # nodejs prefixes its version strings with "v"
                version = LooseVersion(version_str.lstrip('v'))
                return version >= minversion
            return True
        except (subprocess.CalledProcessError, WindowsError):
            return False