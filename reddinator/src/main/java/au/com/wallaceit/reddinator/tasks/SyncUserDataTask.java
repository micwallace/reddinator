/*
 * Copyright 2013 Michael Boyde Wallace (http://wallaceit.com.au)
 * This file is part of Reddinator.
 *
 * Reddinator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Reddinator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Reddinator (COPYING). If not, see <http://www.gnu.org/licenses/>.
 *
 * Created by michael on 1/02/16.
 */
package au.com.wallaceit.reddinator.tasks;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.Utilities;

public class SyncUserDataTask extends AsyncTask<String, String, Boolean> {
    public static int MODE_SUBREDDITS = 1;
    public static int MODE_MULTIS = 2;

    private Reddinator global;
    private RedditData.RedditApiException exception;
    private Context context;
    private Runnable callback;
    private boolean showUI;
    private ProgressDialog progressDialog;
    private int mode;

    public SyncUserDataTask(Context context, Runnable callback, boolean showUI, int mode){
        this.context = context;
        this.callback = callback;
        this.showUI = showUI;
        this.mode = mode;
        global = (Reddinator) context.getApplicationContext();
    }

    @Override
    protected void onPreExecute() {
        if (showUI)
            progressDialog = ProgressDialog.show(context, context.getString(R.string.loading), "", true);
    }

    @Override
    protected Boolean doInBackground(String... params) {
        // load subreddits & multis
        try {
            if (mode != MODE_MULTIS) {
                publishProgress(context.getString(R.string.loading_subreddits));
                global.loadAccountSubreddits();
                if (mode == MODE_SUBREDDITS) return true;
            }

            publishProgress(context.getString(R.string.loading_multis));
            global.loadAccountMultis();
            if (mode == MODE_MULTIS) return true;

            publishProgress(context.getString(R.string.loading_filters));
            global.syncAllFilters();
        } catch (RedditData.RedditApiException e) {
            e.printStackTrace();
            exception = e;
            return false;
        }
        return true;
    }

    @Override
    protected void onProgressUpdate(String... statusText){
        if (!showUI) return;
        progressDialog.setMessage(statusText[0]);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (showUI) {
            progressDialog.dismiss();
            if (!result){
                // check login required
                if (exception.isAuthError()) global.mRedditData.initiateLogin(context, false);
                // show error
                Utilities.showApiErrorToastOrDialog(context, exception);
            }
        }
        if (result)
            global.mSharedPreferences.edit().putLong("last_sync_time", System.currentTimeMillis()).apply();

        if (callback!=null)
            callback.run();
    }
}
