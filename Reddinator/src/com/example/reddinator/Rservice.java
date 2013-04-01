package com.example.reddinator;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

public class Rservice extends RemoteViewsService {
	@Override
	public RemoteViewsFactory onGetViewFactory(Intent intent) {
		return new ListRemoteViewsFactory(this.getApplicationContext(), intent);
	}
}

class ListRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
	private Context ctxt = null;
	
	private int appWidgetId;
	
	public ListRemoteViewsFactory(Context ctxt, Intent intent) {
		this.ctxt = ctxt;
		appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
				AppWidgetManager.INVALID_APPWIDGET_ID);
	}
	
	private RedditData rdata;
	private JSONArray data;
	
	@Override
	public void onCreate() {
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);
		rdata = new RedditData();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		String curfeed = prefs.getString("currentfeed", "technology");
		data = rdata.getRedditFeed(curfeed, "hot");
		//System.out.println("Service started");
	}

	@Override
	public void onDestroy() {
		// no-op
	}

	@Override
	public int getCount() {
		return (data.length());
	}

	@Override
	public RemoteViews getViewAt(int position) {
		String name = "";
		String url = "";
		String domain = "";
		String id = "";
		try {
			JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
			name = tempobj.getString("title");
			domain = tempobj.getString("domain");
			id = tempobj.getString("id");
			url =  tempobj.getString("url");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		RemoteViews row = new RemoteViews(ctxt.getPackageName(), R.layout.listrow);
		row.setTextViewText(R.id.text1, name);
		row.setTextViewText(R.id.sourcetxt, domain);
		Intent i = new Intent();
		Bundle extras = new Bundle();
		extras.putString(WidgetProvider.ITEM_URL, url);
		i.putExtras(extras);
		row.setOnClickFillInIntent(R.id.listrow, i);
		Intent i2 = new Intent(ctxt, ViewActivity.class);
		Bundle extras2 = new Bundle();
		extras2.putString(WidgetProvider.ITEM_URL, "http://www.google.com.au");
		i2.putExtras(extras);
		row.setOnClickFillInIntent(R.id.votebox, i2);
		//System.out.println("getViewAt() firing!");
		return (row);
	}
	@Override
	public RemoteViews getLoadingView() {
		return (null);
	}

	@Override
	public int getViewTypeCount() {
		return (1);
	}

	@Override
	public long getItemId(int position) {
		return (position);
	}

	@Override
	public boolean hasStableIds() {
		return (true);
	}
	private DLTask dltask;
	@Override
	public void onDataSetChanged() {
		// startUpdateIfNoneAlready(); // aync task method (trying to find bug preventing feed to download twice)
		// refresh data
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		String curfeed = prefs.getString("currentfeed", "technology");
		data = rdata.getRedditFeed(curfeed, "hot");
		// hide loader
		AppWidgetManager mgr = AppWidgetManager.getInstance(ctxt);
		RemoteViews views = new RemoteViews(ctxt.getPackageName(), R.layout.widgetmain);
		views.setViewVisibility(R.id.srloader, View.INVISIBLE);
		views.setViewVisibility(R.id.refreshbutton, View.VISIBLE);
		mgr.partiallyUpdateAppWidget(appWidgetId, views);
		
	}
	private void startUpdateIfNoneAlready(){
		if (dltask != null){
			if (dltask.getStatus().equals(AsyncTask.Status.FINISHED)){
				dltask = new DLTask();
				dltask.execute("");
			}
		} else {
			dltask = new DLTask();
			dltask.execute("");
		}
	}
	private class DLTask extends AsyncTask<String, Integer, Long> {
		@Override
		protected Long doInBackground(String... _global) {
			// refresh data
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
			String curfeed = prefs.getString("currentfeed", "technology");
			data = rdata.getRedditFeed(curfeed, "hot");
			return Long.valueOf("1");
		}
		protected void onPostExecute(Long result) {
			AppWidgetManager mgr = AppWidgetManager.getInstance(ctxt);
			RemoteViews views = new RemoteViews(ctxt.getPackageName(), R.layout.widgetmain);
			views.setViewVisibility(R.id.srloader, View.INVISIBLE);
			views.setViewVisibility(R.id.refreshbutton, View.VISIBLE);
			mgr.partiallyUpdateAppWidget(appWidgetId, views);
	    }
	}
}
