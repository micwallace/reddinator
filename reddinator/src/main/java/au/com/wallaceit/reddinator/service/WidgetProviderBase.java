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
package au.com.wallaceit.reddinator.service;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.activity.FeedItemDialogActivity;
import au.com.wallaceit.reddinator.activity.ViewImageDialogActivity;
import au.com.wallaceit.reddinator.activity.ViewRedditActivity;
import au.com.wallaceit.reddinator.tasks.WidgetVoteTask;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class WidgetProviderBase extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        this.onUpdate(context, appWidgetManager, new int[]{appWidgetId}); // fix for the widget not loading the second time round (adding to the homescreen)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // Cleaup widget data
        Reddinator global = (Reddinator) context.getApplicationContext();
        for (int widgetId : appWidgetIds){
            global.clearFeedDataAndPreferences(widgetId);
        }
        super.onDeleted(context, appWidgetIds);
    }

    @Override
    public void onDisabled(Context context) {
        // cancel the alarm for automatic updates
        WidgetCommon.setUpdateSchedule(context, WidgetProviderBase.class, true);
        //System.out.println("onDisabled();");
        super.onDisabled(context);
    }

    @Override
    public void onEnabled(Context context) {
        // set the pending intent for automatic update
        WidgetCommon.setUpdateSchedule(context, WidgetProviderBase.class, false);
        // System.out.println("onEnabled();");
        super.onEnabled(context);
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        String action = intent.getAction();

        if (action.equals(WidgetCommon.ITEM_CLICK)) {

            int widgetid = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
            // check if its the load more button being clicked
            String redditId = intent.getExtras().getString(Reddinator.ITEM_ID);
            if (redditId!=null && redditId.equals("0")) {
                // LOAD MORE FEED ITEM CLICKED
                //System.out.println("loading more feed items...");
                WidgetCommon.showLoaderAndUpdate(context, widgetid, true);
            } else {
                int clickMode = intent.getExtras().getInt(WidgetCommon.ITEM_CLICK_MODE);
                switch (clickMode) {
                    // NORMAL FEED ITEM CLICK
                    case WidgetCommon.ITEM_CLICK_OPEN:
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        String clickPrefString = prefs.getString(context.getString(R.string.on_click_pref), "1");
                        int clickPref = Integer.valueOf(clickPrefString);
                        switch (clickPref) {
                            case 1:
                                // open in the reddinator view
                                Intent clickIntent1 = new Intent(context, ViewRedditActivity.class);
                                clickIntent1.putExtras(intent.getExtras());
                                clickIntent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                clickIntent1.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                context.startActivity(clickIntent1);
                                break;
                            case 2:
                                // open link in browser
                                String url = intent.getStringExtra(Reddinator.ITEM_URL);
                                Intent clickIntent2 = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                clickIntent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(clickIntent2);
                                break;
                            case 3:
                                // open reddit comments page in browser
                                String permalink = intent.getStringExtra(Reddinator.ITEM_PERMALINK);
                                Intent clickIntent3 = new Intent(Intent.ACTION_VIEW, Uri.parse(Reddinator.REDDIT_BASE_URL + permalink));
                                clickIntent3.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(clickIntent3);
                                break;
                        }
                        break;
                    // upvote
                    case WidgetCommon.ITEM_CLICK_UPVOTE:
                        new WidgetVoteTask(
                                context,
                                widgetid,
                                1,
                                intent.getIntExtra(Reddinator.ITEM_FEED_POSITION, -1),
                                intent.getStringExtra(Reddinator.ITEM_ID)
                        ).execute();
                        break;
                    // downvote
                    case WidgetCommon.ITEM_CLICK_DOWNVOTE:
                        new WidgetVoteTask(
                                context,
                                widgetid,
                                -1,
                                intent.getIntExtra(Reddinator.ITEM_FEED_POSITION, -1),
                                intent.getStringExtra(Reddinator.ITEM_ID)
                        ).execute();
                        break;
                    // post options
                    case WidgetCommon.ITEM_CLICK_OPTIONS:
                        Intent ointent = new Intent(context, FeedItemDialogActivity.class);
                        ointent.putExtras(intent.getExtras());
                        ointent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(ointent);
                        break;
                    // open image view
                    case WidgetCommon.ITEM_CLICK_IMAGE:
                        Intent imageintent = new Intent(context, ViewImageDialogActivity.class);
                        imageintent.putExtras(intent.getExtras());
                        imageintent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(imageintent);
                        break;
                }
            }
        }

        //System.out.println("broadcast received: " + action);
        super.onReceive(context, intent);
    }

}