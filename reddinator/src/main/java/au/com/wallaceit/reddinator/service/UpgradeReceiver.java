package au.com.wallaceit.reddinator.service;

/*
 * Copyright 2016 Michael Boyde Wallace (http://wallaceit.com.au)
 * This file is part of reddinator.
 *
 * reddinator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reddinator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reddinator (COPYING). If not, see <http://www.gnu.org/licenses/>.
 *
 * Created by michael on 3/10/16.
 */

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;

import au.com.wallaceit.reddinator.Reddinator;

public class UpgradeReceiver extends BroadcastReceiver {
    Reddinator global;

    public UpgradeReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Run upgrades for new version support
        global = (Reddinator) context.getApplicationContext();
        // Clear old thumbnails directory
        File oldCacheDir = new File(context.getCacheDir().getPath() + "/thumbnail_cache/");
        if (oldCacheDir.exists())
            deleteRecursive(oldCacheDir);
        // Migrate feeds to new file storage and remove from preferences
        migrateFeedData(0); // app feed data
        AppWidgetManager mgr2 = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = mgr2.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        for (int appWidgetId : appWidgetIds) {
            migrateFeedData(appWidgetId);
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        //noinspection ResultOfMethodCallIgnored
        fileOrDirectory.delete();
    }

    private void migrateFeedData(int feedId){
        String prefKey = "feeddata-" + (feedId == 0 ? "app" : feedId);
        String feedData = global.mSharedPreferences.getString(prefKey, null);
        if (feedData==null)
            return;
        JSONArray data;
        try {
            data = new JSONArray(feedData);
            global.setFeed(feedId, data);
            global.mSharedPreferences.edit().remove(prefKey).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
