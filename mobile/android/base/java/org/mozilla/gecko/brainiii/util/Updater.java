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

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;


/**
 * perform a network request in the background
 */
public class Updater {
    public interface OnUrlRequestCallback {
        public void setUpdateJson(String text);
        public void onError(String url, String error);
    }

    private OnUrlRequestCallback mUrlRequestCallback;

    private static final String LOGTAG = "Updater";
    private static final String USER_AGENT = "Brainiii/5.0 (X11; Linux x86_64; rv:23.0) Gecko/20150630 chinesebrowser/40.0.2";

    //private final Context mContext;
    private final int mTimeout;
    private final String mUserAgent;

    public Updater(int timeout, String userAgent) {
        //mContext = context;
        mTimeout = timeout;
        mUserAgent = userAgent!=null?userAgent:USER_AGENT;
    }

    // call this function to fetch the string from the given url
    public void requestQuery(String url, OnUrlRequestCallback callback) {
        mUrlRequestCallback = callback;

        DownloadWebPageTask task = new DownloadWebPageTask();
        task.execute(new String[] { url });
    }

    private class DownloadWebPageTask extends AsyncTask<String, Void, String> {

        private String doRequest(String requestUri) {
            Log.i(LOGTAG, "doRequest: "+requestUri);

            HttpURLConnection urlConnection = null;
            InputStream in = null;
            String json = null;
            try {

                URL url = new URL(requestUri);
                try {
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setConnectTimeout(mTimeout);
                    urlConnection.setRequestProperty("User-Agent", USER_AGENT);
                    in = new BufferedInputStream(urlConnection.getInputStream());
                    json = convertStreamToString(in);
                } finally {
                    if (urlConnection != null)
                        urlConnection.disconnect();
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            Log.e(LOGTAG, "error", e);
                        }
                    }
                }
                return json;

            } catch (java.net.SocketTimeoutException e) {
                Log.e(LOGTAG, "Timeout error", e);
                if (urlConnection != null)
                    urlConnection.disconnect();
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException eIO) {
                        Log.e(LOGTAG, "error", eIO);
                    }
                }            
            } catch (Exception e) {
                Log.e(LOGTAG, "Error", e);
            }

            return "";
        }

        private String convertStreamToString(java.io.InputStream is) {
            try {
                return new java.util.Scanner(is).useDelimiter("\\A").next();
            } catch (java.util.NoSuchElementException e) {
                return "";
            }
        }

        @Override
        protected String doInBackground(String... urls) {
            return doRequest(urls[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            // this runs on the main UI thread
            mUrlRequestCallback.setUpdateJson(result);
        }
    }
}


