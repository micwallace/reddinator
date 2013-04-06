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
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

public class Rservice extends RemoteViewsService {
	@Override
	public RemoteViewsFactory onGetViewFactory(Intent intent) {
		return new ListRemoteViewsFactory(this.getApplicationContext(), intent);
	}
	public class LocalBinder extends Binder {
        Rservice getService() {
            // Return this instance of LocalService so clients can call public methods
            return Rservice.this;
        }
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
	//private GlobalObjects global;
	private String itemfontsize = "16";
	@Override
	public void onCreate() {
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
				.permitAll().build();
		StrictMode.setThreadPolicy(policy);
		rdata = new RedditData();
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(ctxt);
		String curfeed = prefs.getString("currentfeed", "technology");
		String limit = prefs.getString("numitemloadpref", "25");
		data = rdata.getRedditFeed(curfeed, "hot", limit);
		// System.out.println("Service started");
		//global = ((GlobalObjects) ctxt.getApplicationContext());
	}

	@Override
	public void onDestroy() {
		// no-op
	}

	@Override
	public int getCount() {
		return (data.length() + 1); // plus 1 advertises the "load more" item to the listview without having to add it to the data source
	}

	@Override
	public RemoteViews getViewAt(int position) {
		RemoteViews row;
		// check if its the last view and return loading view instead of normal row
		if (position == data.length()) {
			System.out.println("load more firing"); 
			RemoteViews loadmorerow = new RemoteViews(ctxt.getPackageName(), R.layout.listrowloadmore);
			Intent i = new Intent();
			Bundle extras = new Bundle();
			extras.putString(WidgetProvider.ITEM_ID, "0"); // zero will be an indicator in the onreceive function of widget provider
			i.putExtras(extras);
			loadmorerow.setOnClickFillInIntent(R.id.listrowloadmore, i);
			return loadmorerow;
		} else {
			String name = "";
			String url = "";
			String permalink = "";
			String domain = "";
			String id = "";
			int score = 0;
			int numcomments = 0;
			try {
				JSONObject tempobj = data.getJSONObject(position)
						.getJSONObject("data");
				name = tempobj.getString("title");
				domain = tempobj.getString("domain");
				id = tempobj.getString("id");
				url = tempobj.getString("url");
				permalink = tempobj.getString("permalink");
				score = tempobj.getInt("score");
				numcomments = tempobj.getInt("num_comments");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			row = new RemoteViews(ctxt.getPackageName(), R.layout.listrow);
			row.setTextViewText(R.id.listheading, name);
			row.setTextViewTextSize(R.id.listheading, TypedValue.COMPLEX_UNIT_SP, Integer.valueOf(itemfontsize));
			row.setTextViewText(R.id.sourcetxt, domain);
			row.setTextViewText(R.id.votestxt, String.valueOf(score));
			row.setTextViewText(R.id.commentstxt, String.valueOf(numcomments));
			Intent i = new Intent();
			Bundle extras = new Bundle();
			extras.putString(WidgetProvider.ITEM_ID, id);
			extras.putString(WidgetProvider.ITEM_URL, url);
			extras.putString(WidgetProvider.ITEM_PERMALINK, permalink);
			extras.putString(WidgetProvider.ITEM_TXT, name);
			extras.putString(WidgetProvider.ITEM_DOMAIN, domain);
			extras.putInt(WidgetProvider.ITEM_VOTES, score);
			i.putExtras(extras);
			row.setOnClickFillInIntent(R.id.listrow, i);
			
		}
		//System.out.println("getViewAt("+position+") firing!");
		return row;
	}

	@Override
	public RemoteViews getLoadingView() {
		RemoteViews rowload = new RemoteViews(ctxt.getPackageName(),
				R.layout.listrowload);
		return rowload;
	}

	@Override
	public int getViewTypeCount() {
		return (2);
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
		// startUpdateIfNoneAlready(); // aync task method (trying to find bug making feed to download twice)
		// refresh data
		/*if (global.getLoadType() == GlobalObjects.LOADTYPE_LOADMORE){
			global.SetLoad();
			loadMoreReddits();
		} else {*/
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		String curfeed = prefs.getString("currentfeed", "technology");
		String sort = prefs.getString("sort", "hot");
		String limit = prefs.getString("numitemloadpref", "25");
		itemfontsize = prefs.getString("widgetfontpref", "16");
		data = rdata.getRedditFeed(curfeed, sort, limit);
		// hide loader
		AppWidgetManager mgr = AppWidgetManager.getInstance(ctxt);
		RemoteViews views = new RemoteViews(ctxt.getPackageName(),
				R.layout.widgetmain);
		views.setViewVisibility(R.id.srloader, View.INVISIBLE);
		views.setViewVisibility(R.id.refreshbutton, View.VISIBLE);
		mgr.partiallyUpdateAppWidget(appWidgetId, views);
		//}
	}

	public void loadMoreReddits() {
		System.out.println("loadMoreReddits(); fired");
	}

	private void startUpdateIfNoneAlready() {
		if (dltask != null) {
			if (dltask.getStatus().equals(AsyncTask.Status.FINISHED)) {
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
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(ctxt);
			String curfeed = prefs.getString("currentfeed", "technology");
			String limit = prefs.getString("numitemloadpref", "25");
			data = rdata.getRedditFeed(curfeed, "hot", limit);
			return Long.valueOf("1");
		}

		protected void onPostExecute(Long result) {
			AppWidgetManager mgr = AppWidgetManager.getInstance(ctxt);
			RemoteViews views = new RemoteViews(ctxt.getPackageName(),
					R.layout.widgetmain);
			views.setViewVisibility(R.id.srloader, View.INVISIBLE);
			views.setViewVisibility(R.id.refreshbutton, View.VISIBLE);
			mgr.partiallyUpdateAppWidget(appWidgetId, views);
		}
	}
}
