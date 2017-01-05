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
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.widget.RemoteViews;

import com.joanzapata.android.iconify.Iconify;

import java.util.HashMap;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.activity.MainActivity;
import au.com.wallaceit.reddinator.activity.SubredditSelectActivity;
import au.com.wallaceit.reddinator.activity.WidgetMenuDialogActivity;
import au.com.wallaceit.reddinator.core.Utilities;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class StackWidgetProvider extends WidgetProviderBase {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateAppWidgets(context, appWidgetManager, appWidgetIds);
        // System.out.println("onUpdate();");
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    public static void updateAppWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Reddinator global = (Reddinator) context.getApplicationContext();
        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int appWidgetId : appWidgetIds) {
            // CONFIG BUTTON
            Intent intent = new Intent(context, WidgetMenuDialogActivity.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);  // Identifies the particular widget...
            intent.putExtra("firsttimeconfig", 0); // not first time config
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
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
            Intent refreshIntent = new Intent(context, StackWidgetProvider.class);
            refreshIntent.setAction(WidgetCommon.ACTION_UPDATE_FEED);
            refreshIntent.setPackage(context.getPackageName());
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            refreshIntent.setData(Uri.parse(refreshIntent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            // ITEM CLICK
            Intent clickIntent = new Intent(context, StackWidgetProvider.class);
            clickIntent.setAction(WidgetCommon.ACTION_ITEM_CLICK);
            clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            clickIntent.setData(Uri.parse(clickIntent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent clickPendingIntent = PendingIntent.getBroadcast(context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            // ADD ALL TO REMOTE VIEWS
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stack);
            views.setPendingIntentTemplate(R.id.adapterview, clickPendingIntent);
            views.setOnClickPendingIntent(R.id.sub_container, subredditPendingIntent);
            views.setOnClickPendingIntent(R.id.refreshbutton, refreshPendingIntent);
            views.setOnClickPendingIntent(R.id.prefsbutton, pendIntent);
            views.setEmptyView(R.id.adapterview, R.id.empty_list_view);

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
            views.setInt(R.id.adapterview, "setBackgroundColor", themeColors.get("widget_background_color"));

            int iconColor = themeColors.get("default_icon");
            int[] shadow = new int[]{3, 3, 3, themeColors.get("icon_shadow")};
            views.setImageViewBitmap(R.id.prefsbutton, Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_bars.character()), iconColor, 28, shadow));
            views.setImageViewBitmap(R.id.refreshbutton, Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_refresh.character()), iconColor, 28, shadow));
            views.setImageViewBitmap(R.id.srcaret, Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_caret_down.character()), iconColor, 16, shadow));
            views.setImageViewBitmap(R.id.erroricon, Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_exclamation_triangle.character()), Color.parseColor("#E06B6C"), 28, shadow));

            // set current feed title
            String curFeed = global.getSubredditManager().getCurrentFeedName(appWidgetId);
            views.setTextViewText(R.id.subreddittxt, curFeed);
            views.setTextColor(R.id.subreddittxt, themeColors.get("header_text"));

            // Set remote adapter for widget.
            if (Build.VERSION.SDK_INT >= 14) {
                views.setRemoteAdapter(R.id.adapterview, serviceIntent); // API 14 and above
            } else {
                //noinspection deprecation
                views.setRemoteAdapter(appWidgetId, R.id.adapterview, serviceIntent); // older version compatibility
            }
            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {
            AppWidgetManager mgr2 = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = mgr2.getAppWidgetIds(new ComponentName(context, StackWidgetProvider.class));
            // perform full widget update
            onUpdate(context, mgr2, appWidgetIds);
        }
        //System.out.println("broadcast received: " + action);
        super.onReceive(context, intent);
    }

}