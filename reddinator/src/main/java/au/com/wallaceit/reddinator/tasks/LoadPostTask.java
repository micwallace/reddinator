package au.com.wallaceit.reddinator.tasks;
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
 * Created by michael on 27/03/16.
 */

import android.os.AsyncTask;

import org.json.JSONArray;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;

public class LoadPostTask extends AsyncTask<String, Integer, JSONArray> {
    private Reddinator global;
    private RedditData.RedditApiException exception = null;
    private Callback voteCallback = null;

    public interface Callback {
        void onPostLoaded(JSONArray result, RedditData.RedditApiException exception);
    }

    public LoadPostTask(Reddinator global, Callback voteCallback) {
        this.global = global;
        this.voteCallback = voteCallback;
    }

    @Override
    protected JSONArray doInBackground(String... strings) {
        // Do the vote
        try {
            return global.mRedditData.getCommentsFeed(strings[0], strings[1], 25);
        } catch (RedditData.RedditApiException e) {
            e.printStackTrace();
            exception = e;
            return null;
        }
    }

    @Override
    protected void onPostExecute(JSONArray data) {
        if (voteCallback!=null)
            voteCallback.onPostLoaded(data, exception);
    }
}