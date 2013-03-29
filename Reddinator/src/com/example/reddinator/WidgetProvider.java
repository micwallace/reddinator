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
import android.widget.RemoteViews;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class WidgetProvider extends AppWidgetProvider {
	public static String EXTRA_WORD = "com.example.reddinator.WORD"; // i have no idea what the fuck this is for but we need it for a working demo
	private static final String ACTION_CLICK = "ACTION_CLICK_WIDGET";
	public static String ACTION_WIDGET_CLICK_PREFS = "Action_prefs";
	public static String ACTION_WIDGET_CLICK_REFRESH = "Action_refresh";
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
            // setup the setting button click intent
            Intent intent = new Intent(context, PrefsActivity.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);  // Identifies the particular widget...
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Make the pending intent unique...
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent pendIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            // setup list view adaptor
            Intent intent2 = new Intent(context, Rservice.class);
            // Add the app widget ID to the intent extras.
            intent2.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            intent2.setData(Uri.parse(intent2.toUri(Intent.URI_INTENT_SCHEME)));
            
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widgetmain);
            
            views.setOnClickPendingIntent(R.id.prefsbutton, pendIntent);
            // This is how you populate the data.
            views.setRemoteAdapter(appWidgetIds[i], R.id.listview, intent2);
            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId , views);
            //appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds[i], R.id.listview);
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
		AppWidgetManager mgr = AppWidgetManager.getInstance(context); 
        //retrieve a ref to the manager so we can pass a view update
        Intent i = new Intent(); 
        i.setClassName("com.example.reddinator", "com.example.reddinator.PrefsActivity"); 
        PendingIntent myPI = PendingIntent.getService(context, 0, i, 0); 
        // Get the layout for the App Widget 
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widgetmain); 
        //attach the click listener for the service start command intent 
        views.setOnClickPendingIntent(R.id.prefsbutton, myPI); 
        //define the componenet for self 
        ComponentName comp = new ComponentName(context.getPackageName(), PrefsActivity.class.getName()); 
        //tell the manager to update all instances of the widget with the click listener 
        mgr.updateAppWidget(comp, views);
        
        super.onEnabled(context);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stubs
            super.onReceive(context, intent);
	}
}
