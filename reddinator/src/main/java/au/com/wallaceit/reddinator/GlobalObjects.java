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
 */
package au.com.wallaceit.reddinator;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class GlobalObjects extends Application {
    static final boolean DEBUG_LOGGING = true;
    private ArrayList<String> mSubredditList;
    static int LOADTYPE_LOAD = 0;
    static int LOADTYPE_LOADMORE = 1;
    static int LOADTYPE_REFRESH_VIEW = 3;
    private int loadtype = 0; // tells the service what to do when notifyAppDataChanged is fired
    private boolean bypassCache = false; // tells the factory to bypass the cache when creating a new remoteviewsfacotry
    public RedditData mRedditData;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mSubredditList == null) {
            mSubredditList = new ArrayList<>();
        }
        mRedditData = new RedditData(GlobalObjects.this.getApplicationContext());
    }

    // app feed update from view reddit activity; if the user voted, that data is stored here for the MainActivity to access in on resume
    Bundle itemupdate;

    public Bundle getItemUpdate() {
        if (itemupdate == null) {
            return null;
        }
        Bundle tempdata = itemupdate;
        itemupdate = null; // prevent duplicate updates
        return tempdata;
    }

    public void setItemUpdate(int position, String id, String val) {
        itemupdate = new Bundle();
        itemupdate.putInt("position", position);
        itemupdate.putString("id", id);
        itemupdate.putString("val", val);
    }

    // methods for setting/getting vote statuses, this keeps vote status persistent accross apps and widgets
    public void setItemVote(SharedPreferences prefs, int widgetId, int position, String id, String val) {
        try {
            JSONArray data = new JSONArray(prefs.getString("feeddata-" + (widgetId == 0 ? "app" : widgetId), "[]"));
            JSONObject record = data.getJSONObject(position).getJSONObject("data");
            if (record.getString("name").equals(id)) {
                data.getJSONObject(position).getJSONObject("data").put("likes", val);
                // commit to shared prefs
                SharedPreferences.Editor mEditor = prefs.edit();
                mEditor.putString("feeddata-" + (widgetId == 0 ? "app" : widgetId), data.toString());
                mEditor.apply();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // set get current feeds from cache
    public void setFeed(SharedPreferences prefs, int widgetId, JSONArray feeddata) {
        SharedPreferences.Editor mEditor = prefs.edit();
        mEditor.putString("feeddata-" + (widgetId == 0 ? "app" : widgetId), feeddata.toString());
        mEditor.apply();
    }

    public JSONArray getFeed(SharedPreferences prefs, int widgetId) {
        JSONArray data;
        try {
            data = new JSONArray(prefs.getString("feeddata-" + (widgetId == 0 ? "app" : widgetId), "[]"));
        } catch (JSONException e) {
            data = new JSONArray();
            e.printStackTrace();
        }
        return data;
    }

    public JSONObject getFeedObject(SharedPreferences prefs, int widgetId, int position, String redditId) {
        try {
            JSONArray data = new JSONArray(prefs.getString("feeddata-" + (widgetId == 0 ? "app" : widgetId), "[]"));
            JSONObject item = data.getJSONObject(position).getJSONObject("data");
            if (item.getString("name").equals(redditId)) {
                return item;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    // cached data
    public boolean isSrlistCached() {
        return !mSubredditList.isEmpty();
    }

    public void putSrList(ArrayList<String> list) {
        mSubredditList.clear();
        mSubredditList.addAll(list);
    }

    public ArrayList<String> getSrList() {
        return mSubredditList;
    }

    // widget data loadtype functions; a bypass for androids restrictive widget api
    public int getLoadType() {
        return loadtype;
    }

    public void setLoadMore() {
        loadtype = LOADTYPE_LOADMORE;
    }

    public void setLoad() {
        loadtype = LOADTYPE_LOAD;
    }

    public void setRefreshView() {
        loadtype = LOADTYPE_REFRESH_VIEW;
    }

    // data cache functions
    public boolean getBypassCache() {
        return bypassCache;
    }

    public void setBypassCache(boolean bypassed) {
        bypassCache = bypassed;
    }

}
