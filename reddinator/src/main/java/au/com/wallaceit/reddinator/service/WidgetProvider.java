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
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.RemoteViews;

import com.joanzapata.android.iconify.Iconify;

import java.util.HashMap;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.activity.MainActivity;
import au.com.wallaceit.reddinator.activity.PrefsActivity;
import au.com.wallaceit.reddinator.activity.SubredditSelectActivity;
import au.com.wallaceit.reddinator.activity.ViewImageDialogActivity;
import au.com.wallaceit.reddinator.activity.ViewRedditActivity;
import au.com.wallaceit.reddinator.activity.FeedItemDialogActivity;
import au.com.wallaceit.reddinator.tasks.WidgetVoteTask;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class WidgetProvider extends AppWidgetProvider {
    public static final String ITEM_URL = "ITEM_URL";
    public static final String ITEM_PERMALINK = "ITEM_PERMALINK";
    public static final String ITEM_ID = "ITEM_ID";
    public static final String ITEM_DOMAIN = "ITEM_DOMAIN";
    public static final String ITEM_SUBREDDIT = "ITEM_SUBREDDIT";
    public static final String ITEM_USERLIKES = "ITEM_USERLIKES";
    public static final String ITEM_CLICK = "ITEM_CLICK";
    public static final String ITEM_CLICK_MODE = "ITEM_CLICK_MODE";
    public static final int ITEM_CLICK_OPEN = 0;
    public static final int ITEM_CLICK_UPVOTE = 1;
    public static final int ITEM_CLICK_DOWNVOTE = 2;
    public static final int ITEM_CLICK_OPTIONS = 3;
    public static final int ITEM_CLICK_IMAGE = 4;
    public static final String ITEM_FEED_POSITION = "ITEM_FEED_POSITION";
    public static final String APPWIDGET_UPDATE_FEED = "APPWIDGET_UPDATE_FEED";
    public static final String APPWIDGET_AUTO_UPDATE = "APPWIDGET_AUTO_UPDATE_FEED";

    public WidgetProvider() {
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateAppWidgets(context, appWidgetManager, appWidgetIds);
        // System.out.println("onUpdate();");
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @SuppressWarnings("deprecation")
    public static void updateAppWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Reddinator global = (Reddinator) context.getApplicationContext();
        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int appWidgetId : appWidgetIds) {
            // CONFIG BUTTON
            Intent intent = new Intent(context, PrefsActivity.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);  // Identifies the particular widget...
            intent.putExtra("firsttimeconfig", 0); // not first time config
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent pendIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            // PICK Subreddit BUTTON
            Intent subredditIntent = new Intent(context, SubredditSelectActivity.class);
            subredditIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);  // Identifies the particular widget...
            subredditIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            subredditIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            subredditIntent.setData(Uri.parse(subredditIntent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent subredditPendingIntent = PendingIntent.getActivity(context, 0, subredditIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            // REMOTE DATA
            Intent serviceIntent = new Intent(context, WidgetService.class);
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId); // Add the app widget ID to the intent extras.
            serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));

            // REFRESH BUTTON
            Intent refreshIntent = new Intent(context, WidgetProvider.class);
            refreshIntent.setAction(APPWIDGET_UPDATE_FEED);
            refreshIntent.setPackage(context.getPackageName());
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            refreshIntent.setData(Uri.parse(refreshIntent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            // ITEM CLICK
            Intent clickIntent = new Intent(context, WidgetProvider.class);
            clickIntent.setAction(ITEM_CLICK);
            clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            clickIntent.setData(Uri.parse(clickIntent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent clickPendingIntent = PendingIntent.getBroadcast(context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            // ADD ALL TO REMOTE VIEWS
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
            views.setPendingIntentTemplate(R.id.listview, clickPendingIntent);
            views.setOnClickPendingIntent(R.id.srcaret, subredditPendingIntent);
            views.setOnClickPendingIntent(R.id.subreddittxt, subredditPendingIntent);
            views.setOnClickPendingIntent(R.id.refreshbutton, refreshPendingIntent);
            views.setOnClickPendingIntent(R.id.prefsbutton, pendIntent);
            views.setEmptyView(R.id.listview, R.id.empty_list_view);

            // setup app open intent
            if (global.mSharedPreferences.getBoolean("logoopenpref", true)){
                Intent appIntent = new Intent(context, MainActivity.class);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                appIntent.setData(Uri.parse(subredditIntent.toUri(Intent.URI_INTENT_SCHEME)));
                PendingIntent appPendingIntent = PendingIntent.getActivity(context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                views.setOnClickPendingIntent(R.id.widget_logo, appPendingIntent);
            } else {
                views.setOnClickPendingIntent(R.id.widget_logo, subredditPendingIntent);
            }

            // setup theme
            HashMap<String, Integer> themeColors = global.mThemeManager.getActiveTheme("widgettheme-"+appWidgetId).getIntColors();
            views.setInt(R.id.widgetheader, "setBackgroundColor", themeColors.get("widget_header_color"));
            views.setInt(R.id.listview, "setBackgroundColor", themeColors.get("widget_background_color"));

            int iconColor = themeColors.get("default_icon");
            int[] shadow = new int[]{3, 3, 3, themeColors.get("icon_shadow")};
            views.setImageViewBitmap(R.id.prefsbutton, Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_wrench.character()), iconColor, 28, shadow));
            views.setImageViewBitmap(R.id.refreshbutton, Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_refresh.character()), iconColor, 28, shadow));
            views.setImageViewBitmap(R.id.srcaret, Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_caret_down.character()), iconColor, 16, shadow));
            views.setImageViewBitmap(R.id.erroricon, Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_exclamation_triangle.character()), Color.parseColor("#E06B6C"), 28, shadow));

            // set current feed title
            String curFeed = global.getSubredditManager().getCurrentFeedName(appWidgetId);
            views.setTextViewText(R.id.subreddittxt, curFeed);
            views.setTextColor(R.id.subreddittxt, themeColors.get("header_text"));

            // Set remote adapter for widget.
            if (Build.VERSION.SDK_INT >= 14) {
                views.setRemoteAdapter(R.id.listview, serviceIntent); // API 14 and above
            } else {
                views.setRemoteAdapter(appWidgetId, R.id.listview, serviceIntent); // older version compatibility
            }
            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
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
            global.clearFeedData(widgetId);
        }
        super.onDeleted(context, appWidgetIds);
    }

    public static void setUpdateSchedule(Context context, boolean widgetsDisabled){
        Intent intent = new Intent(context.getApplicationContext(), WidgetProvider.class);
        intent.setAction(APPWIDGET_AUTO_UPDATE);
        intent.setPackage(context.getPackageName());
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        PendingIntent updateIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, intent, 0);

        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int refreshRate = Integer.valueOf(prefs.getString(context.getString(R.string.refresh_rate_pref), "43200000"));

        if (refreshRate==0 || widgetsDisabled) {
            alarmManager.cancel(updateIntent); // auto update disabled or all widgets removed
        } else {
            alarmManager.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + refreshRate, refreshRate, updateIntent);
        }
    }

    @Override
    public void onDisabled(Context context) {
        // cancel the alarm for automatic updates
        setUpdateSchedule(context, true);
        //System.out.println("onDisabled();");
        super.onDisabled(context);
    }

    @Override
    public void onEnabled(Context context) {
        // set the pending intent for automatic update
        setUpdateSchedule(context, false);
        // System.out.println("onEnabled();");
        super.onEnabled(context);
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        String action = intent.getAction();
        if (action.equals(ITEM_CLICK)) {
            // check if its the load more button being clicked
            String redditId = intent.getExtras().getString(WidgetProvider.ITEM_ID);
            if (redditId!=null && redditId.equals("0")) {
                // LOAD MORE FEED ITEM CLICKED
                //System.out.println("loading more feed items...");
                int widgetid = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
                // show loader
                showLoaderAndUpdate(context, widgetid, true);
            } else {
                int clickMode = intent.getExtras().getInt(WidgetProvider.ITEM_CLICK_MODE);
                switch (clickMode) {
                    // NORMAL FEED ITEM CLICK
                    case WidgetProvider.ITEM_CLICK_OPEN:
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
                                String url = intent.getStringExtra(ITEM_URL);
                                Intent clickIntent2 = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                clickIntent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(clickIntent2);
                                break;
                            case 3:
                                // open reddit comments page in browser
                                String permalink = intent.getStringExtra(ITEM_PERMALINK);
                                Intent clickIntent3 = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com" + permalink));
                                clickIntent3.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(clickIntent3);
                                break;
                        }
                        break;
                    // upvote
                    case WidgetProvider.ITEM_CLICK_UPVOTE:
                        new WidgetVoteTask(
                                context,
                                intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
                                1,
                                intent.getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1),
                                intent.getStringExtra(WidgetProvider.ITEM_ID)
                        ).execute();
                        break;
                    // downvote
                    case WidgetProvider.ITEM_CLICK_DOWNVOTE:
                        new WidgetVoteTask(
                                context,
                                intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
                                -1,
                                intent.getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1),
                                intent.getStringExtra(WidgetProvider.ITEM_ID)
                        ).execute();
                        break;
                    // post options
                    case WidgetProvider.ITEM_CLICK_OPTIONS:
                        Intent ointent = new Intent(context, FeedItemDialogActivity.class);
                        ointent.putExtras(intent.getExtras());
                        ointent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(ointent);
                        break;
                    // open image view
                    case WidgetProvider.ITEM_CLICK_IMAGE:
                        Intent imageintent = new Intent(context, ViewImageDialogActivity.class);
                        imageintent.putExtras(intent.getExtras());
                        imageintent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(imageintent);
                        break;
                }
            }
        }

        if (action.equals(APPWIDGET_UPDATE_FEED)) {
            // get widget id
            int widgetId = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
            // show loader and update data
            showLoaderAndUpdate(context, widgetId, false);
        }

        if (action.equals(APPWIDGET_AUTO_UPDATE)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = mgr.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
            // perform full update, just to refresh views
            onUpdate(context, mgr, appWidgetIds);
            // show loader and update data
            updateAllWidgets(context, appWidgetIds);
        }

        if (action.equals("android.intent.action.PACKAGE_RESTARTED") || action.equals("android.intent.action.PACKAGE_REPLACED") || action.equals("android.intent.action.PACKAGE_CHANGED")) {
            AppWidgetManager mgr2 = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = mgr2.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
            // perform full widget update
            onUpdate(context, mgr2, appWidgetIds);
        }
        //System.out.println("broadcast received: " + action);
        super.onReceive(context, intent);
    }

    public static void updateAllWidgets(Context context, int[] widgetIds) {
        for (int widgetId : widgetIds) {
            showLoaderAndUpdate(context, widgetId, false);
        }
    }

    public static void showLoaderAndUpdate(Context context, int widgetId, boolean loadmore) {
        Reddinator global = ((Reddinator) context.getApplicationContext());
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        // show loader
        RemoteViews views = new RemoteViews((new Intent(context, WidgetProvider.class)).getPackage(), R.layout.widget);
        views.setViewVisibility(R.id.srloader, View.VISIBLE);
        views.setViewVisibility(R.id.erroricon, View.INVISIBLE); // make sure we hide the error icon
        views.setTextViewText(R.id.subreddittxt, global.getSubredditManager().getCurrentFeedName(widgetId));
        // load more text
        if (loadmore) {
            views.setTextViewText(R.id.loadmoretxt, context.getResources().getString(R.string.loading));
            // set loadmore indicator so the notifydatasetchanged function knows what to do
            global.setLoadMore();
        }
        // set cache bypass incase widget needs new view factory
        global.setBypassCache(true);
        // update view
        mgr.partiallyUpdateAppWidget(widgetId, views);
        // request update of listview data
        mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.listview);
    }

    public static void showLoaderAndRefreshViews(Context context, int widgetId){
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        // show loader
        RemoteViews views = new RemoteViews((new Intent(context, WidgetProvider.class)).getPackage(), R.layout.widget);
        views.setViewVisibility(R.id.srloader, View.VISIBLE);
        views.setViewVisibility(R.id.erroricon, View.INVISIBLE); // make sure we hide the error icon
        // update view
        mgr.partiallyUpdateAppWidget(widgetId, views);
    }

    public static void hideLoaderAndRefreshViews(Context context, int widgetId, boolean showerror){
        Reddinator global = ((Reddinator) context.getApplicationContext());
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        // show loader
        RemoteViews views = new RemoteViews((new Intent(context, WidgetProvider.class)).getPackage(), R.layout.widget);
        views.setViewVisibility(R.id.srloader, View.GONE);
        views.setViewVisibility(R.id.erroricon, (showerror ? View.VISIBLE : View.GONE)); // make sure we hide the error icon
        // update view
        global.setRefreshView();
        mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.listview);
        mgr.partiallyUpdateAppWidget(widgetId, views);
    }

}