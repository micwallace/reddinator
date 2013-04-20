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

import au.com.wallaceit.reddinator.R;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.RemoteViews;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class WidgetProvider extends AppWidgetProvider {
	public static String ITEM_URL = "ITEM_URL";
	public static String ITEM_PERMALINK = "ITEM_PERMALINK";
	public static String ITEM_TXT = "ITEM_TXT";
	public static String ITEM_ID = "ITEM_ID";
	public static String ITEM_VOTES = "ITEM_VOTES";
	public static String ITEM_DOMAIN = "ITEM_DOMAIN";
	public static String ITEM_CLICK = "ITEM_CLICK";
	public static String ACTION_WIDGET_CLICK_PREFS = "Action_prefs";
	public static String APPWIDGET_UPDATE = "android.appwidget.action.APPWIDGET_UPDATE";
	public static String APPWIDGET_UPDATE_FEED = "APPWIDGET_UPDATE_FEED";
	public static String APPWIDGET_AUTO_UPDATE = "APPWIDGET_AUTO_UPDATE_FEED";
	private PendingIntent updateintent = null;
	public WidgetProvider() {
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		updateAppWidgets(context, appWidgetManager, appWidgetIds, true);
        //System.out.println("onUpdate();");
        super.onUpdate(context, appWidgetManager, appWidgetIds);
	}
	
	@SuppressWarnings("deprecation")
	public static void updateAppWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds, boolean scrolltotop){
		final int N = appWidgetIds.length;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i=0; i<N; i++) {
        	int appWidgetId = appWidgetIds[i];
        	// CONFIG BUTTON
            Intent intent = new Intent(context, PrefsActivity.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);  // Identifies the particular widget...
            intent.putExtra("firsttimeconfig", 0); // not first time config
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent pendIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            // PICK Subreddit BUTTON
            Intent srintent = new Intent(context, SubredditSelect.class);
            srintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);  // Identifies the particular widget...
            srintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            srintent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent srpendIntent = PendingIntent.getActivity(context, 0, srintent, PendingIntent.FLAG_UPDATE_CURRENT);
            // REMOTE DATA
            Intent servintent = new Intent(context, Rservice.class);
            servintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId); // Add the app widget ID to the intent extras.
            servintent.setData(Uri.parse(servintent.toUri(Intent.URI_INTENT_SCHEME)));
            // REFRESH BUTTON
            Intent irefresh = new Intent(context, WidgetProvider.class);
            irefresh.setAction(APPWIDGET_UPDATE_FEED);
            irefresh.setPackage(context.getPackageName());
            irefresh.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            irefresh.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent rpIntent = PendingIntent.getBroadcast(context, 0, irefresh, PendingIntent.FLAG_UPDATE_CURRENT);
            // ITEM CLICK
            Intent clickintent = new Intent(context, WidgetProvider.class);
            clickintent.setAction(ITEM_CLICK);
            clickintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            clickintent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent clickPI = PendingIntent.getBroadcast(context, 0, clickintent, PendingIntent.FLAG_UPDATE_CURRENT);
            // get theme layout id
         	int layout = R.layout.widgetmain;
         	switch(Integer.valueOf(prefs.getString("widgetthemepref", "1"))){
         		case 1: layout = R.layout.widgetmain; break;
         		case 2: layout = R.layout.widgetdark; break;
         		case 3: layout = R.layout.widgetholo; break;
         		case 4: layout = R.layout.widgetdarkholo; break;
         	}
            // ADD ALL TO REMOTE VIEWS
            RemoteViews views = new RemoteViews(context.getPackageName(), layout);
            views.setPendingIntentTemplate(R.id.listview, clickPI);
            views.setOnClickPendingIntent(R.id.subreddittxt, srpendIntent);
            views.setOnClickPendingIntent(R.id.widget_logo, srpendIntent);
            views.setOnClickPendingIntent(R.id.refreshbutton, rpIntent);
            views.setOnClickPendingIntent(R.id.prefsbutton, pendIntent);
            views.setEmptyView(R.id.listview, R.id.empty_list_view);
            // views.setViewVisibility(R.id.srloader, View.VISIBLE); // loader is hidden by default (to stop is displaying on screen rotation) so we need to show it when updating.
            // set current feed title
    		String curfeed = prefs.getString("currentfeed-"+appWidgetId, "technology");
    		views.setTextViewText(R.id.subreddittxt, curfeed);
    		// Set remote adapter for widget.
    		if (android.os.Build.VERSION.SDK_INT >= 14){
    			views.setRemoteAdapter(R.id.listview, servintent); // API 14 and above
    		} else {
    			views.setRemoteAdapter(appWidgetId, R.id.listview, servintent); // older version compatibility
    		}
    		if (scrolltotop){
    			views.setScrollPosition(R.id.listview, 0); // in-case an auto update
    		}
    		// Tell the AppWidgetManager to perform an update on the current app widget
    		appWidgetManager.updateAppWidget(appWidgetId , views);
        }
	}
	
	@Override
	public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
		//System.out.println("onAppWidgetOptionsChanged();");
		this.onUpdate(context, appWidgetManager, new int[]{appWidgetId}); // fix for the widget not loading the second time round (adding to the homescreen)
		super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		//System.out.println("onDeleted();");
		super.onDeleted(context, appWidgetIds);
	}

	@Override
	public void onDisabled(Context context) {
		// cancel the alarm for automatic updates
		Intent intent =  new Intent(context.getApplicationContext(), WidgetProvider.class);
        intent.setAction(APPWIDGET_AUTO_UPDATE);
        intent.setPackage(context.getPackageName());
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        updateintent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, intent, 0);
		final AlarmManager m = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		m.cancel(updateintent);
		//System.out.println("onDisabled();");
		super.onDisabled(context);
	}
	

	@Override
	public void onEnabled(Context context) {
		// set the pending intent for automatic update
        Intent intent =  new Intent(context.getApplicationContext(), WidgetProvider.class);
        intent.setAction(APPWIDGET_AUTO_UPDATE);
        intent.setPackage(context.getPackageName());
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        updateintent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, intent, 0);
        final AlarmManager m = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE); 
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		int refreshrate = Integer.valueOf(prefs.getString("refreshrate", "43200000"));
		if (refreshrate!=0){
        	m.setRepeating(AlarmManager.RTC, System.currentTimeMillis()+refreshrate, refreshrate, updateintent);
		} else {
			m.cancel(updateintent); // auto update disabled
		}
		// System.out.println("onEnabled();");
        super.onEnabled(context);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (action.equals(ITEM_CLICK)) {
			// check if its the load more button being clicked
			String redditid = intent.getExtras().getString(WidgetProvider.ITEM_ID);
			if (redditid.equals("0")){
				// LOAD MORE FEED ITEM CLICKED
				//System.out.println("loading more feed items...");
				int widgetid = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
				// set loadmore indicator so the notifydatasetchanged function knows what to do
				setLoadMore(context);
				// show loader
				showLoaderAndUpdate(context, intent, new int[]{widgetid});
			} else {
				// NORMAL FEED ITEM CLICK
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
				String clickprefst = prefs.getString("onclickpref", "1");
				int clickpref = Integer.valueOf(clickprefst);
				switch (clickpref){
					case 1:
						// open in the reddinator view
						Intent clickintent1 = new Intent(context, ViewReddit.class);
						clickintent1.putExtras(intent.getExtras());
						clickintent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						context.startActivity(clickintent1);
						break;
					case 2:
						// open link in browser
						String url = intent.getStringExtra(ITEM_URL);
						Intent clickintent2 = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
						clickintent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						context.startActivity(clickintent2);
						break;
					case 3:
						// open reddit comments page in browser
						String plink = intent.getStringExtra(ITEM_PERMALINK);
						Intent clickintent3 = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.reddit.com"+plink));
						clickintent3.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						context.startActivity(clickintent3);
						break;
				}
			}
		}
		if (action.equals(APPWIDGET_UPDATE_FEED)) {
			// get widget id
			int widgetid = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
			// set cache bypass incase widget needs new view factory
			setNoCache(context);
			// show loader and update data
			showLoaderAndUpdate(context, intent, new int[]{widgetid});
		}
		if (action.equals(APPWIDGET_AUTO_UPDATE)) {
			AppWidgetManager mgr = AppWidgetManager.getInstance(context);
			int[] appWidgetIds = mgr.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
			// set cache bypass
			setNoCache(context);
			// perform full update
			onUpdate(context, mgr, appWidgetIds);
			// request update from service
			mgr.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.listview); // this might not be needed
		}
		if (action.equals("android.intent.action.PACKAGE_RESTARTED") || action.equals("android.intent.action.PACKAGE_REPLACED")){
			AppWidgetManager mgr2 = AppWidgetManager.getInstance(context);
			int[] appWidgetIds = mgr2.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
			// perform full widget update
			onUpdate(context, mgr2, appWidgetIds);
		}
		//System.out.println("broadcast received: "+intent.getAction().toString());
        super.onReceive(context, intent);
	}
	
	private void showLoaderAndUpdate(Context context, Intent intent, int[] widgetid){
		AppWidgetManager mgr = AppWidgetManager.getInstance(context);
		// show loader
		RemoteViews views = new RemoteViews(intent.getPackage(), getThemeLayoutId(context));
		views.setViewVisibility(R.id.srloader, View.VISIBLE);
		views.setViewVisibility(R.id.erroricon, View.INVISIBLE); // make sure we hide the error icon
		// update view
		mgr.partiallyUpdateAppWidget(widgetid, views);
		// request update of listview data
		mgr.notifyAppWidgetViewDataChanged(widgetid, R.id.listview);
	}
	
	private void setLoadMore(Context context){
		// set the loadmore indicator in global object, we also set bypass cache in case a new remoteviewsfactory is created
		GlobalObjects global = ((GlobalObjects) context.getApplicationContext());
		global.setBypassCache(true);
		global.setLoadMore();
	}
	
	private void setNoCache(Context context){
		// the bypass cache indicator is used when the last remoteviewfactory has been terminated. A new one is created so we need to tell it not to load the cached data
		GlobalObjects global = ((GlobalObjects) context.getApplicationContext());
		global.setBypassCache(true);
	}
	
	private int getThemeLayoutId(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		// get theme layout id
     	int layoutid = 1;
     	switch(Integer.valueOf(prefs.getString("widgetthemepref", "1"))){
     		case 1: layoutid = R.layout.widgetmain; break;
     		case 2: layoutid = R.layout.widgetdark; break;
     		case 3: layoutid = R.layout.widgetholo; break;
     		case 4: layoutid = R.layout.widgetdarkholo; break;
     	}
     	return layoutid;
	}
}
