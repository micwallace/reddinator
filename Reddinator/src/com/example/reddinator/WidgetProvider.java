package com.example.reddinator;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViews.RemoteView;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class WidgetProvider extends AppWidgetProvider {
	public static String ITEM_URL = "ITEM_URL";
	public static String ITEM_CLICK = "ITEM_CLICK";
	private static final String ACTION_CLICK = "ACTION_CLICK_WIDGET";
	public static String ACTION_WIDGET_CLICK_PREFS = "Action_prefs";
	public static String APPWIDGET_UPDATE = "Action_update";
	public static String UPDATE_COMPLETE = "Action_update";
	public WidgetProvider() {
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
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
            // Subreddit BUTTON
            Intent srintent = new Intent(context, SubredditSelect.class);
            srintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);  // Identifies the particular widget...
            srintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            srintent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent srpendIntent = PendingIntent.getActivity(context, 0, srintent, PendingIntent.FLAG_UPDATE_CURRENT);
            // REMOTE DATA
            Intent intent2 = new Intent(context, Rservice.class);
            // Add the app widget ID to the intent extras.
            intent2.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            intent2.setData(Uri.parse(intent2.toUri(Intent.URI_INTENT_SCHEME)));
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widgetmain);
            // REFRESH BUTTON
            Intent irefresh = new Intent(context, WidgetProvider.class);
            irefresh.setAction(APPWIDGET_UPDATE);
            irefresh.putExtra("id", appWidgetId);
            PendingIntent rpIntent = PendingIntent.getBroadcast(context, 0, irefresh, 0);
            // ITEM CLICK
            Intent clickintent = new Intent(context, WidgetProvider.class);
            clickintent.setAction(ITEM_CLICK);
            clickintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            clickintent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent clickPI = PendingIntent.getBroadcast(context, 0, clickintent, PendingIntent.FLAG_UPDATE_CURRENT);
            // ADD ALL TO REMOTE VIEWS
            views.setPendingIntentTemplate(R.id.listview, clickPI);
            views.setOnClickPendingIntent(R.id.subreddittxt, srpendIntent);
            views.setOnClickPendingIntent(R.id.refreshbutton, rpIntent);
            views.setOnClickPendingIntent(R.id.prefsbutton, pendIntent);
            // This is how you populate the data.
            views.setRemoteAdapter(appWidgetIds[i], R.id.listview, intent2);
            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId , views);
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.listview);
            System.out.println("onUpdate() fires!");
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
	}


	@Override
	public void onAppWidgetOptionsChanged(Context context,
			AppWidgetManager appWidgetManager, int appWidgetId,
			Bundle newOptions) {
		// TODO Auto-generated method stub
		super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId,
				newOptions);
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		// TODO Auto-generated method stub
		super.onDeleted(context, appWidgetIds);
	}

	@Override
	public void onDisabled(Context context) {
		// TODO Auto-generated method stub
		super.onDisabled(context);
	}

	@Override
	public void onEnabled(Context context) {
        super.onEnabled(context);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		
		if (intent.getAction().equals(APPWIDGET_UPDATE)) {
			int id = intent.getExtras().getInt("id");
			AppWidgetManager mgr = AppWidgetManager.getInstance(context);
			mgr.notifyAppWidgetViewDataChanged(id, R.id.listview);
		}
		if (intent.getAction().equals(ITEM_CLICK)) {
			String url = intent.getStringExtra(ITEM_URL);
			Intent clickintent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			clickintent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(clickintent);
		}
		System.out.println("broadcast received: "+intent.getAction().toString());
		/*if (intent.getAction().equals(UPDATE_COMPLETE)) {
			System.out.println("wiget update complete");
		}*/
         super.onReceive(context, intent);
	}
}
