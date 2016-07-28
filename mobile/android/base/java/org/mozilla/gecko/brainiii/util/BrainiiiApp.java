/*
Copyright (C) 2015 Brainiii (Singapore)

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

    Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

    Redistributions in binary form must reproduce the above 
    copyright notice, this list of conditions and the following
    disclaimer in the documentation and/or other materials provided
    with the distribution.

    Neither the name of Brainiii, pīnyīnbrowser, chinesebrowser,
    nor the names of their contributors may be used to endorse
    or promote products derived from this software without specific
    prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/

package com.brainiii.util;


import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.RelativeLayout;


import com.brainiii.AppDefs;
import com.brainiii.util.UpdateInstaller;



public class BrainiiiApp
    implements UpdateInstaller.OnUpdateInstallerCallback {

    public static boolean USE_SMART_BANNER = true;

    private Activity        mParent;

    private UpdateInstaller mUpdateInstaller;

    @Override
    public void quitApp() {
        mParent.finish();
        // android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void checkForUpdate() {
        mUpdateInstaller.checkForUpdate(AppDefs.UPDATE_URL, AppDefs.APP_VERSION);
    }

    public void checkForUpdate(String url, int currentVersion) {
        mUpdateInstaller.checkForUpdate(url, currentVersion);
    }

    public static void triggerPinyinCidianAppInstall(final Activity context) {

        new AlertDialog.Builder(context)
                .setTitle("Install pinyincidian")
                .setMessage("pinyincidian is the English-Chinese dictionary companion app for chinesebrowser. Do you want to proceed?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String url = "";
                        String packageName = AppDefs.PINYINCIDIAN_PACKAGENAME;

                        try {
                            //Check whether Google Play store is installed or not:
                            context.getPackageManager().getPackageInfo("com.android.vending", 0);

                            url = "market://details?id=" + packageName;
                        } catch (final Exception e) {
                            url = "https://play.google.com/store/apps/details?id=" + packageName;
                        }

                        //Open the app page in Google Play store:
                        final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                        context.startActivity(i);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
    }

    public BrainiiiApp(Activity parent) {
        mParent = parent;

        mUpdateInstaller = new UpdateInstaller(mParent,this);
        checkForUpdate();
    }
}


