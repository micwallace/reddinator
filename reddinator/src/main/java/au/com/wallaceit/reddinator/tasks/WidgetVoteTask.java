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
 * Created by michael on 23/01/16.
 */
package au.com.wallaceit.reddinator.tasks;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.service.WidgetCommon;

public class WidgetVoteTask extends AsyncTask<String, Integer, Boolean> {
    private Context context;
    private Reddinator global;
    private int widgetId;
    private String redditid;
    private int direction;
    private String curVote;
    private int netvote;
    private int listposition;
    private RedditData.RedditApiException exception;
    private boolean archived = false;

    public WidgetVoteTask(Context context, int widgetId, int dir, int position, String redditId) {
        this.context = context;
        global = (Reddinator) context.getApplicationContext();
        direction = dir;
        netvote = dir;
        this.widgetId = widgetId;
        // Get data by position in list
        listposition = position;
        JSONObject item = global.getFeedObject(widgetId, position, redditId);
        try {
            redditid = item.getString("name");
            curVote = item.getString("likes");
            archived = item.getBoolean("archived");
            if (archived)
                return;
        } catch (JSONException e) {
            redditid = "null";
            curVote = "null";
        }
        //AppWidgetManager mgr = AppWidgetManager.getInstance(context).get.getAppWidgetInfo(widgetId).provider;

        WidgetCommon.showLoaderAndRefreshViews(context, widgetId);
    }

    @Override
    protected Boolean doInBackground(String... strings) {
        if (archived)
            return false;
        // enumerate current vote, score change & clicked direction
        if (direction == 1) {
            if (curVote.equals("true")) { // if already upvoted, neutralize.
                direction = 0;
                netvote = -1;
            } else if (curVote.equals("false")){
                netvote = 2;
            }
        } else { // downvote
            if (curVote.equals("false")) {
                direction = 0;
                netvote = 1;
            } else if (curVote.equals("true")){
                netvote = -2;
            }
        }
        // Do the vote
        try {
            return global.mRedditData.vote(redditid, direction);
        } catch (RedditData.RedditApiException e) {
            e.printStackTrace();
            exception = e;
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (result) {
            // set icon + current "likes" in the data array, this way ViewRedditActivity will get the new version without updating the hole feed.
            String value = "null";
            switch (direction) {
                case -1:
                    value = "false";
                    break;
                case 0:
                    value = "null";
                    break;
                case 1:
                    value = "true";
                    break;
            }

            global.setItemVote(widgetId, listposition, redditid, value, netvote);
        } else {
            if (archived) {
                Toast.makeText(context, R.string.archived_post_error, Toast.LENGTH_LONG).show();
                return;
            }
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(context, true);
            // show error
            Toast.makeText(context, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
        WidgetCommon.hideLoaderAndRefreshViews(context, widgetId, (!result && !exception.isAuthError()));
    }
}
