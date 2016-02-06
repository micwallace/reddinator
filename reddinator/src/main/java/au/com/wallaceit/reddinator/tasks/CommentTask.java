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
 * Created by michael on 6/02/16.
 */

import android.os.AsyncTask;
import org.json.JSONObject;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;

public class CommentTask extends AsyncTask<String, Integer, JSONObject> {
    private Reddinator global;
    private String redditId;
    private String messageText;
    private RedditData.RedditApiException exception = null;
    private Callback callback = null;
    private int action = 0; // 0=add, 1=edit, -1=delete
    public static final int ACTION_ADD = 0;
    public static final int ACTION_EDIT = 1;
    public static final int ACTION_DELETE = -1;

    public interface Callback {
        void onCommentComplete(JSONObject result, RedditData.RedditApiException exception, int action, String redditId);
    }

    public CommentTask(Reddinator global, String thingId, String text, int action, Callback callback) {
        this.global = global;
        this.callback = callback;
        messageText = text;
        redditId = thingId;
        this.action = action;
    }

    @Override
    protected JSONObject doInBackground(String... strings) {
        // Do the vote
        JSONObject result = null;
        try {
            switch (action){
                case ACTION_DELETE:
                    result = global.mRedditData.deleteComment(redditId)?new JSONObject():null;
                    break;
                case ACTION_ADD:
                    result = global.mRedditData.postComment(redditId, messageText);
                    break;
                case ACTION_EDIT:
                    result = global.mRedditData.editComment(redditId, messageText);
                    break;
            }

        } catch (RedditData.RedditApiException e) {
            e.printStackTrace();
            exception = e;
            return null;
        }

        return result;
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        if (callback!=null)
            callback.onCommentComplete(result, exception, action, redditId);
    }
}
