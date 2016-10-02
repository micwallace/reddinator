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
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;

public class SubscriptionEditTask extends AsyncTask<Object, Long, Boolean> {
    public static final int ACTION_MULTI_COPY = 0;
    public static final int ACTION_MULTI_CREATE = 1;
    public static final int ACTION_MULTI_EDIT = 2;
    public static final int ACTION_MULTI_SUB_ADD = 3;
    public static final int ACTION_MULTI_SUB_REMOVE = 4;
    public static final int ACTION_MULTI_DELETE = 5;
    public static final int ACTION_MULTI_RENAME = 6;
    public static final int ACTION_SUBSCRIBE = 7;
    public static final int ACTION_UNSUBSCRIBE = 8;
    private Reddinator global;
    private Context context;
    private Callback callback;
    private JSONObject data;
    private RedditData.RedditApiException exception;
    private int action;
    private Object[] params;
    private ProgressDialog progressDialog;
    private String loadingMessage = "";

    public interface Callback {
        void onSubscriptionEditComplete(boolean result, RedditData.RedditApiException exception, int action, Object[] params, JSONObject data);
    }

    public SubscriptionEditTask(Reddinator global, Context context, Callback callback, int action){
        switch (action) {
            case ACTION_SUBSCRIBE:
                loadingMessage = global.getResources().getString(R.string.subscribing);
                break;
            case ACTION_UNSUBSCRIBE:
                loadingMessage = global.getResources().getString(R.string.unsubscribing);
                break;
            case ACTION_MULTI_COPY:
                loadingMessage = global.getResources().getString(R.string.copying_multi);
                break;
            case ACTION_MULTI_CREATE:
                loadingMessage = global.getResources().getString(R.string.creating_multi);
                break;
            case ACTION_MULTI_EDIT:
            case ACTION_MULTI_RENAME:
            case ACTION_MULTI_SUB_ADD:
            case ACTION_MULTI_SUB_REMOVE:
                loadingMessage = global.getResources().getString(R.string.updating_multi);
                break;
            case ACTION_MULTI_DELETE:
                loadingMessage = global.getResources().getString(R.string.deleting_multi);
                break;
        }
        this.global = global;
        this.context = context;
        this.callback = callback;
        this.action = action;
    }

    protected void onPreExecute(){
        progressDialog = ProgressDialog.show(context, loadingMessage, loadingMessage, true);
    }

    @Override
    protected Boolean doInBackground(Object... strParams) {
        this.params = strParams;
        String id;
        try {
            switch (action){
                case ACTION_SUBSCRIBE:
                    id = ((JSONObject) strParams[0]).getString("name");
                    data = global.mRedditData.subscribe(id, true);
                    break;

                case ACTION_UNSUBSCRIBE:
                    id = global.getSubredditManager().getSubredditData(strParams[0].toString()).getString("name");
                    data = global.mRedditData.subscribe(id, false);
                    break;

                case ACTION_MULTI_COPY:
                    data = global.mRedditData.copyMulti(strParams[0].toString(), strParams[1].toString());
                    break;

                case ACTION_MULTI_CREATE:
                    try {
                        JSONObject multiObj = new JSONObject();
                        multiObj.put("display_name", strParams[0].toString());
                        multiObj.put("decription_md", "");
                        multiObj.put("icon_name", "");
                        multiObj.put("key_color", "#CEE3F8");
                        multiObj.put("subreddits", new JSONArray());
                        multiObj.put("visibility", "private");
                        multiObj.put("weighting_scheme", "classic");
                        data = global.mRedditData.createMulti(strParams[0].toString(), multiObj);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return false;
                    }
                    break;

                case ACTION_MULTI_EDIT:
                    data = global.mRedditData.editMulti(strParams[0].toString(), (JSONObject) strParams[1]);
                    break;

                case ACTION_MULTI_SUB_ADD:
                    data = global.mRedditData.addMultiSubreddit(strParams[0].toString(), strParams[1].toString());
                    break;

                case ACTION_MULTI_SUB_REMOVE:
                    global.mRedditData.removeMultiSubreddit(strParams[0].toString(), strParams[1].toString());
                    break;

                case ACTION_MULTI_DELETE:
                    global.mRedditData.deleteMulti(strParams[0].toString());
                    break;

                case ACTION_MULTI_RENAME:
                    data = global.mRedditData.renameMulti(strParams[0].toString(), strParams[1].toString());
                    break;
            }
            return true;
        } catch (RedditData.RedditApiException e) {
            e.printStackTrace();
            exception = e;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        progressDialog.dismiss();

        if (callback!=null)
            callback.onSubscriptionEditComplete(result, exception, action, params, this.data);
    }
}
