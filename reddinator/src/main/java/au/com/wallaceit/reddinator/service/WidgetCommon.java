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
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.RemoteViews;

import org.apache.commons.lang3.ArrayUtils;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;

public class WidgetCommon {

    static final String ACTION_UPDATE_FEED = "wallaceit.redinator.action.APPWIDGET_UPDATE_FEED";
    static final String ACTION_AUTO_UPDATE = "wallaceit.redinator.action.APPWIDGET_AUTO_UPDATE";
    static final String ACTION_ITEM_CLICK = "wallaceit.redinator.action.APPWIDGET_ITEM_CLICK";

    static final String ITEM_CLICK_MODE = "ITEM_CLICK_MODE";
    static final int ITEM_CLICK_OPEN = 0;
    static final int ITEM_CLICK_UPVOTE = 1;
    static final int ITEM_CLICK_DOWNVOTE = 2;
    static final int ITEM_CLICK_OPTIONS = 3;
    static final int ITEM_CLICK_IMAGE = 4;

    static final Class WIDGET_CLASS_LIST = WidgetProvider.class;
    static final Class WIDGET_CLASS_STACK = StackWidgetProvider.class;

    static Class getWidgetProviderClass(Context context, int appWidgetId){
        AppWidgetProviderInfo widgetInfo = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId);
        if (widgetInfo!=null) {
            String className = widgetInfo.provider.getClassName();
            className = className.substring(className.lastIndexOf(".") + 1);
            if (className.equals(WIDGET_CLASS_LIST.getSimpleName())) {
                return WIDGET_CLASS_LIST;
            } else if (className.equals(WIDGET_CLASS_STACK.getSimpleName())) {
                return WIDGET_CLASS_STACK;
            }
        }
        return WidgetCommon.class;
    }

    static int getWidgetLayoutId(Class providerClass){
        if (providerClass.getSimpleName().equals(WIDGET_CLASS_STACK.getSimpleName()))
            return R.layout.widget_stack;

        return R.layout.widget;
    }

    static void updateAllWidgets(Context context) {
        int[] widgetIds = getAllAppWidgetIds(context);
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

    private static int[] getAllAppWidgetIds(Context context){
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int listIds[] = mgr.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        int stackIds[] = mgr.getAppWidgetIds(new ComponentName(context, StackWidgetProvider.class));
        return ArrayUtils.addAll(listIds, stackIds);
    }

    public static void setUpdateSchedule(Context context){

        Intent intent = new Intent(context, WidgetProvider.class);
        intent.setAction(WidgetCommon.ACTION_AUTO_UPDATE);
        intent.setPackage(context.getPackageName());
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        PendingIntent updateIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        int ids[] = getAllAppWidgetIds(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long refreshRate = Long.valueOf(prefs.getString(context.getString(R.string.refresh_rate_pref), "43200000"));

        // If there are no widgets for the provider class, or refresh is disabled, cancel the alarm
        if (ids.length > 0 && refreshRate > 0) {
            long next = prefs.getLong("last_auto_refresh", 0) + refreshRate;
            next = (next < System.currentTimeMillis() ? (System.currentTimeMillis()) : next);
            alarmManager.setRepeating(AlarmManager.RTC, next, refreshRate, updateIntent);
            return;
        }

        alarmManager.cancel(updateIntent); // auto update disabled or all widgets removed
    }

}
