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
 * Created by michael on 9/10/16.
 */

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.RemoteViews;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;

public class WidgetCommon {

    static final String ITEM_CLICK = "ITEM_CLICK";
    static final String ITEM_CLICK_MODE = "ITEM_CLICK_MODE";
    static final int ITEM_CLICK_OPEN = 0;
    static final int ITEM_CLICK_UPVOTE = 1;
    static final int ITEM_CLICK_DOWNVOTE = 2;
    static final int ITEM_CLICK_OPTIONS = 3;
    static final int ITEM_CLICK_IMAGE = 4;
    static final String APPWIDGET_UPDATE_FEED = "APPWIDGET_UPDATE_FEED";
    static final String APPWIDGET_AUTO_UPDATE = "APPWIDGET_AUTO_UPDATE_FEED";

    static final Class WIDGET_CLASS_LIST = WidgetProvider.class;
    static final Class WIDGET_CLASS_STACK = StackWidgetProvider.class;

    static Class getWidgetProviderClass(Context context, int appWidgetId){
        String className = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId).provider.getShortClassName();
        className = className.substring(className.lastIndexOf(".")+1);
        if (className.equals(WIDGET_CLASS_LIST.getSimpleName())) {
            return WIDGET_CLASS_LIST;
        } else if (className.equals(WIDGET_CLASS_STACK.getSimpleName())) {
            return WIDGET_CLASS_STACK;
        }
        return null;
    }

    static int getWidgetLayoutId(Class providerClass){
        if (providerClass.getSimpleName().equals(WIDGET_CLASS_STACK.getSimpleName()))
            return R.layout.widget_stack;

        return R.layout.widget;
    }

    static void updateAllWidgets(Context context, int[] widgetIds) {
        for (int widgetId : widgetIds) {
            WidgetCommon.showLoaderAndUpdate(context, widgetId, false);
        }
    }

    public static void showLoaderAndUpdate(Context context, int widgetId, boolean loadmore) {
        Reddinator global = ((Reddinator) context.getApplicationContext());
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        Class providerClass = getWidgetProviderClass(context, widgetId);
        // show loader
        RemoteViews views = new RemoteViews(context.getPackageName(), getWidgetLayoutId(providerClass));
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
        mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.adapterview);
    }

    public static void refreshAllWidgetViews(Reddinator global){
        global.setRefreshView();
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(global);
        // update stack widgets
        int[] stackWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(global.getApplicationContext(), StackWidgetProvider.class));
        StackWidgetProvider.updateAppWidgets(global, appWidgetManager, stackWidgetIds);
        appWidgetManager.notifyAppWidgetViewDataChanged(stackWidgetIds, R.id.adapterview);

        int[] widgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(global, WidgetProvider.class));
        WidgetProvider.updateAppWidgets(global, appWidgetManager, widgetIds);
        appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.adapterview);
    }

    public static void showLoaderAndRefreshViews(Context context, int widgetId){
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        Class providerClass = getWidgetProviderClass(context, widgetId);
        // show loader
        RemoteViews views = new RemoteViews(context.getPackageName(), getWidgetLayoutId(providerClass));
        views.setViewVisibility(R.id.srloader, View.VISIBLE);
        views.setViewVisibility(R.id.erroricon, View.INVISIBLE); // make sure we hide the error icon
        // update view
        mgr.partiallyUpdateAppWidget(widgetId, views);
    }

    public static void hideLoaderAndRefreshViews(Context context, int widgetId, boolean showerror){
        Reddinator global = ((Reddinator) context.getApplicationContext());
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        Class providerClass = getWidgetProviderClass(context, widgetId);
        // show loader
        RemoteViews views = new RemoteViews(context.getPackageName(), getWidgetLayoutId(providerClass));
        views.setViewVisibility(R.id.srloader, View.GONE);
        views.setViewVisibility(R.id.erroricon, (showerror ? View.VISIBLE : View.GONE)); // make sure we hide the error icon
        // update view
        global.setRefreshView();
        mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.adapterview);
        mgr.partiallyUpdateAppWidget(widgetId, views);
    }

    public static void setUpdateSchedule(Context context, boolean widgetsDisabled){
        WidgetCommon.setUpdateSchedule(context, WidgetProvider.class, widgetsDisabled);
        WidgetCommon.setUpdateSchedule(context, StackWidgetProvider.class, widgetsDisabled);
    }

    static void setUpdateSchedule(Context context, Class providerClass, boolean widgetsDisabled){

        Intent intent = new Intent(context.getApplicationContext(), providerClass);
        intent.setAction(WidgetCommon.APPWIDGET_AUTO_UPDATE);
        intent.setPackage(context.getPackageName());
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        PendingIntent updateIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, intent, 0);
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // If there are no widgets for the provider class, disable the alarm
        int ids[] = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, providerClass));
        if (ids.length==0) {

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            int refreshRate = Integer.valueOf(prefs.getString(context.getString(R.string.refresh_rate_pref), "43200000"));
            if (!widgetsDisabled && refreshRate > 0) {
                alarmManager.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + refreshRate, refreshRate, updateIntent);
                return;
            }
        }
        alarmManager.cancel(updateIntent); // auto update disabled or all widgets removed
    }

}
