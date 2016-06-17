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
 * Created by michael on 17/06/16.
 */
import android.os.AsyncTask;
import org.json.JSONObject;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;

public class SubmitTask extends AsyncTask<String, Integer, Boolean> {
    private Reddinator global;
    private Callback submitCallback;
    private JSONObject jsonResult;
    private RedditData.RedditApiException exception;
    private boolean isLink;
    private String subreddit;
    private String title;
    private String data;

    public interface Callback {
        void onSubmitted(JSONObject result, RedditData.RedditApiException exception, boolean isLink);
    }

    public SubmitTask(Reddinator global, String subreddit, String title, String data, boolean isLink, Callback callback){
        this.global = global;
        this.submitCallback = callback;
        this.isLink = isLink;
        this.title = title;
        this.data = data;
        this.subreddit = subreddit;
    }

    @Override
    protected Boolean doInBackground(String... strings) {
        try {
            jsonResult = global.mRedditData.submit(subreddit, isLink, title, data);
            return true;
        } catch (RedditData.RedditApiException e) {
            e.printStackTrace();
            exception = e;
        }
        return false;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (submitCallback!=null)
            submitCallback.onSubmitted(jsonResult, exception, isLink);
    }
}