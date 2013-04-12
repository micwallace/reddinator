package com.example.reddinator;

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

	@SuppressWarnings("deprecation")
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		final int N = appWidgetIds.length;
        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i=0; i<N; i++) {
            int appWidgetId = appWidgetIds[i];
            // CONFIG BUTTON
            Intent intent = new Intent(context, PrefsActivity.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);  // Identifies the particular widget...
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
            servintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]); // Add the app widget ID to the intent extras.
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
            clickintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            clickintent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent clickPI = PendingIntent.getBroadcast(context, 0, clickintent, PendingIntent.FLAG_UPDATE_CURRENT);
            // ADD ALL TO REMOTE VIEWS
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widgetmain);
            views.setPendingIntentTemplate(R.id.listview, clickPI);
            views.setOnClickPendingIntent(R.id.subreddittxt, srpendIntent);
            views.setOnClickPendingIntent(R.id.refreshbutton, rpIntent);
            views.setOnClickPendingIntent(R.id.prefsbutton, pendIntent);
            views.setEmptyView(R.id.listview, R.id.empty_list_view);
            // set current feed title
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    		String curfeed = prefs.getString("currentfeed-"+appWidgetId, "technology");
    		views.setTextViewText(R.id.subreddittxt, curfeed);
    		// This is how you populate the data.
    		views.setRemoteAdapter(R.id.listview, servintent);
    		// Tell the AppWidgetManager to perform an update on the current app widget
    		appWidgetManager.updateAppWidget(appWidgetId , views);
        }
        //appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.listview); // this causes the feed to update twice :(
        System.out.println("onUpdate() fires!");
        super.onUpdate(context, appWidgetManager, appWidgetIds);
	}
	
	@Override
	public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
		System.out.println("onAppWidgetOptionsChanged fired");
		this.onUpdate(context, appWidgetManager, new int[]{appWidgetId}); // fix for the widget not loading the second time round (adding to the homescreen)
		super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		System.out.println("onDeleted fired");
		super.onDeleted(context, appWidgetIds);
	}

	@Override
	public void onDisabled(Context context) {
		Intent intent =  new Intent(context.getApplicationContext(), WidgetProvider.class);
        intent.setAction(APPWIDGET_AUTO_UPDATE);
        intent.setPackage(context.getPackageName());
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        updateintent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, intent, 0);
		final AlarmManager m = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		m.cancel(updateintent);
		System.out.println("onDisabled fired");
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
			m.cancel(updateintent); // just incase theres a rougue alarm
		}
		System.out.println("onEnabled fired");
        super.onEnabled(context);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (action.equals(APPWIDGET_UPDATE_FEED)) {
			int widgetid = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
			AppWidgetManager mgr = AppWidgetManager.getInstance(context);
			// set cache bypass
			GlobalObjects global = ((GlobalObjects) context.getApplicationContext());
			global.setBypassCache(true);
			// show loader
			RemoteViews views = new RemoteViews(intent.getPackage(), R.layout.widgetmain);
			views.setViewVisibility(R.id.srloader, View.VISIBLE);
			mgr.partiallyUpdateAppWidget(widgetid, views);
			mgr.notifyAppWidgetViewDataChanged(widgetid, R.id.listview);
		}
		if (action.equals(ITEM_CLICK)) {
			int widgetid = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
			// check if its the load more button being clicked
			String redditid = intent.getExtras().getString(WidgetProvider.ITEM_ID);
			if (redditid.equals("0")){
				System.out.println("loading more feed items");
				// set loadmore indicator so the notifydatasetchanged function knows what to do
				GlobalObjects global = ((GlobalObjects) context.getApplicationContext());
				global.setBypassCache(true);
				global.setLoadMore();
				AppWidgetManager mgr2 = AppWidgetManager.getInstance(context);
				// show loader
				RemoteViews views2 = new RemoteViews(intent.getPackage(), R.layout.widgetmain);
				views2.setViewVisibility(R.id.srloader, View.VISIBLE);
				mgr2.updateAppWidget(widgetid, views2);
				mgr2.notifyAppWidgetViewDataChanged(widgetid, R.id.listview);
			} else {
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
		if (action.equals(APPWIDGET_AUTO_UPDATE)) {
			AppWidgetManager mgr3 = AppWidgetManager.getInstance(context);
			int[] appWidgetIds = mgr3.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
			// set cache bypass
			GlobalObjects global = ((GlobalObjects) context.getApplicationContext());
			global.setBypassCache(true);
			// show loader
			RemoteViews views = new RemoteViews(intent.getPackage(), R.layout.widgetmain);
			views.setViewVisibility(R.id.srloader, View.VISIBLE);
			mgr3.updateAppWidget(appWidgetIds, views);
			mgr3.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.listview);
		}
		if (action.equals("android.intent.action.PACKAGE_RESTARTED")){
			AppWidgetManager mgr4 = AppWidgetManager.getInstance(context);
			int[] appWidgetIds = mgr4.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
			// perform full widget update
			onUpdate(context, mgr4, appWidgetIds);
		}
		System.out.println("broadcast received: "+intent.getAction().toString());
        super.onReceive(context, intent);
	}

}
