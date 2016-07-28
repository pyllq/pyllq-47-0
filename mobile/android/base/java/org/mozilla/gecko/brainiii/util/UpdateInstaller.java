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

import com.brainiii.util.Updater;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import org.json.*;

public class UpdateInstaller
	implements Updater.OnUrlRequestCallback {

    public interface OnUpdateInstallerCallback {
        // the application should quit
        public void quitApp();
    }

    //private final long hourMillis = 1000*60*60;
    private final long hourMillis = 100;

	private static final String LOGTAG = "UpdateInstaller";

    private final String PREFS_UPDATER = "updater";
    private final String PREFS_JSON = "json";
    private final String PREFS_LAST_DECLINED_VERSION = "declineVersion";
    private final String PREFS_LATEST_VERSION = "latestVersion";

    private final String CHECK_HTTP_INTERVAL_KEY = "lastCheck";
    private final long CHECK_HTTP_INTERVAL_MILLIS = 24*hourMillis;
    private final String CHECK_TRIGGER_UPDATE_INTERVAL_KEY = "triggerUpdate";
    private final long CHECK_TRIGGER_UPDATE_INTERVAL_MILLIS = 24*hourMillis;
    private final String CHECK_UPDATECALL_INTERVAL_KEY = "lastUpdateCall";
    private final long CHECK_UPDATECALL_INTERVAL_MILLIS = 3*hourMillis;

	private Context mContext;
	private Updater mHttpHandler;

    private OnUpdateInstallerCallback   mCallback;

	public UpdateInstaller(Context context, OnUpdateInstallerCallback callback) {
		mContext = context;
        mHttpHandler = new Updater(3000,null);
        mCallback = callback;
	}

    // I. an update has been obtained (from the last check), 
    //    trigger it.
    // II. else checks for update once in a day
    //     if the version codes have increased, it means there is an update
	public void checkForUpdate(String url, int currentVersion) {

        Log.e(LOGTAG, "checkForUpdate: url="+url);

        if (!hasTimeElapsed(CHECK_UPDATECALL_INTERVAL_KEY, CHECK_UPDATECALL_INTERVAL_MILLIS)) {
            Log.e(LOGTAG, "checkForUpdate: function call not time yet");
            return;
        }

        // policy checks
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_UPDATER, 0);
        int lastDeclinedVersion = prefs.getInt(PREFS_LAST_DECLINED_VERSION,-1);
        int latestVersion = prefs.getInt(PREFS_LATEST_VERSION,-1);
        String strJson = prefs.getString(PREFS_JSON,"");

        Log.e(LOGTAG, "checkForUpdate: lastDeclinedVersion="+lastDeclinedVersion);
        Log.e(LOGTAG, "checkForUpdate: latestVersion="+latestVersion);
        Log.e(LOGTAG, "checkForUpdate: strJson="+strJson);

        JSONObject json = null;
        try {
            if (strJson.length() > 0) {
                Log.e(LOGTAG, "checkForUpdate: has json in prefs");
                json = new JSONObject(strJson);
            }
        } catch (JSONException e) {}

        boolean mandatoryUpdate = false;
        if (json != null) {
            try {
                int forcedYes = json.getInt("update-quit-yes");
                int forcedNo = json.getInt("update-quit-no");
                mandatoryUpdate = (forcedYes>0 || forcedNo>0)?true:false;
            }catch (JSONException e) { }
            Log.e(LOGTAG, "checkForUpdate: mandatoryUpdate="+mandatoryUpdate);
        }

        if (json != null && currentVersion < latestVersion &&
            (mandatoryUpdate || lastDeclinedVersion == -1 || lastDeclinedVersion != latestVersion)) {

            Log.e(LOGTAG, "checkForUpdate: entered update");

            if (mandatoryUpdate ||
                hasTimeElapsed(CHECK_TRIGGER_UPDATE_INTERVAL_KEY, CHECK_TRIGGER_UPDATE_INTERVAL_MILLIS)) {
                updateDialog(json);
            } else {
                Log.e(LOGTAG, "checkForUpdate: not update time yet");
            }
        }

        if (hasTimeElapsed(CHECK_HTTP_INTERVAL_KEY, CHECK_HTTP_INTERVAL_MILLIS)) {
            Log.e(LOGTAG, "checkForUpdate: http for update json");
            // calls handler to fetch the update json
            mHttpHandler.requestQuery(url,getCallbackHandler());
        } else {
            Log.e(LOGTAG, "checkForUpdate: not http pull time yet");
        }
	}

    private void setDeclineVersion(int version) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_UPDATER, 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREFS_LAST_DECLINED_VERSION, version);
        editor.commit();        
    }

    // called by mHttpHandler
    @Override
    public void setUpdateJson(String strJson) {

        try {
            JSONObject json = new JSONObject(strJson);
            int version = json.getInt("version");

            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_UPDATER, 0);
            int latestVersion = prefs.getInt(PREFS_LATEST_VERSION,-1);
            if (version > latestVersion) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(PREFS_LATEST_VERSION, version);
                editor.putString(PREFS_JSON,strJson);
                editor.commit();
                Log.e(LOGTAG, "checkForUpdate: json committed");
            }
        } catch (JSONException e) {
            Log.e(LOGTAG, "checkForUpdate: json parsing error: ", e);
        }

        //Toast.makeText(mContext,"setUpdateJson:"+strJson, Toast.LENGTH_LONG).show();
    }

    // called by mHttpHandler
    @Override
    public void onError(String url, String error) {
        Toast.makeText(mContext,"onError:"+error, Toast.LENGTH_LONG).show();
    }


    public void updateDialog(final JSONObject json) {

        try {
            new AlertDialog.Builder(mContext)
            .setTitle(json.getString("dialog-title"))
            .setMessage(json.getString("dialog-message"))
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { 
                    try {
                        // continue with update
                        String packageName = json.getString("package-name");
                        startInstaller(packageName);

                        int forced = json.getInt("update-quit-yes");
                        if (forced > 0 && mCallback != null)
                            mCallback.quitApp();                        
                    } catch (JSONException e) {
                       Log.e(LOGTAG, "json parsing error: ", e);
                    }
                }
             })
            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { 
                    try {
                        int forced = json.getInt("update-quit-no");
                        if (forced == 0)
                            setDeclineVersion(json.getInt("version"));
                        if (forced > 0 && mCallback != null)
                            mCallback.quitApp();
                    } catch (JSONException e) {
                        Log.e(LOGTAG, "json parsing error: ", e);
                    }                        
                }
             })
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
        } catch (JSONException e) {
            Log.e(LOGTAG, "json parsing error: ", e);
        }
    }



    private Updater.OnUrlRequestCallback getCallbackHandler() {
    	return this;
    }

    // returns true if the required interval has lapsed
    private boolean hasTimeElapsed(String timeKey, long minElapseInterval) {
    	long nowTime = System.currentTimeMillis();

        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_UPDATER, 0);
        
        long lastTime = prefs.getLong(timeKey,0);
        if (lastTime != 0 && (nowTime-lastTime < minElapseInterval)) {
        	// still not time yet
        	return false;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(timeKey, nowTime);
        editor.commit();

        return lastTime==0?false:true;
    }

    // package name example: org.mozilla.firefox
    public void startInstaller(String packageName) {
		String url = "";

		try {
		    //Check whether Google Play store is installed or not:
		    mContext.getPackageManager().getPackageInfo("com.android.vending", 0);

		    url = "market://details?id=" + packageName;
		} catch ( final Exception e ) {
		    url = "https://play.google.com/store/apps/details?id=" + packageName;
		}

		//Open the app page in Google Play store:
		final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		mContext.startActivity(intent);
	}

}
