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
 * Created by michael on 9/02/16.
 */

import android.os.AsyncTask;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;

public class ComposeMessageTask extends AsyncTask<String, Integer, Boolean> {
    private Reddinator global;
    private RedditData.RedditApiException exception = null;
    private Callback voteCallback = null;
    private String[] args; // [to, subject, text, subreddit, optional args...]

    public interface Callback {
        void onMessageSent(boolean result, RedditData.RedditApiException exception, String[] args);
    }

    public ComposeMessageTask(Reddinator global, Callback voteCallback, String[] args) {
        this.global = global;
        this.voteCallback = voteCallback;
        this.args = args;
    }

    @Override
    protected Boolean doInBackground(String... strings) {
        // Do the vote
        try {
            global.mRedditData.composeMessage(args[0], args[1], args[2], args.length>3?args[3]:null);
        } catch (RedditData.RedditApiException e) {
            e.printStackTrace();
            exception = e;
            return false;
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (voteCallback!=null)
            voteCallback.onMessageSent(result, exception, args);
    }
}