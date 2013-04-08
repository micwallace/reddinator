package com.example.reddinator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
	/*public class LocalBinder extends Binder {
        Rservice getService() {
            // Return this instance of LocalService so clients can call public methods
            return Rservice.this;
        }
    }*/
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
	private GlobalObjects global;
	private String itemfontsize = "16";
	@Override
	public void onCreate() {
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);
		rdata = new RedditData();
		global = ((GlobalObjects) ctxt.getApplicationContext());
		/*SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(ctxt);
		String curfeed = prefs.getString("currentfeed", "technology");
		int limit = Integer.valueOf(prefs.getString("numitemloadpref", "25"));
		data = rdata.getRedditFeed(curfeed, "hot", limit, "0");
		System.out.println("Service started");*/
		data = new JSONArray();
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
		if (position > data.length()){
			//System.out.println("getViewAt(); index "+ position + "not found"); 
			// this is a fix for listview bug causing blank views when left to automatically update after more than one page has loaded.
			return null; //  By returning null we presumably force a call to getCount() from the listview to get a new listlength thus forcing the view to update properly
		}
		// check if its the last view and return loading view instead of normal row
		if (position == data.length()) {
			//System.out.println("load more getViewAt()"); 
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

	@Override
	public void onDataSetChanged() {
		// refresh data
		if (global.getLoadType() == GlobalObjects.LOADTYPE_LOADMORE){
			global.SetLoad();
			loadMoreReddits();
			//startDownloadIfNoneAlready(true); // use aync task download method; CURRENTLY NOT FUNCTIONING AND MIGHT NOT BE NEEDED
		} else {
			loadReddits(false);
			//startDownloadIfNoneAlready(false); // use aync task download method
		}
	}
	private String lastitemid = "0";
	private boolean endoffeed = false;
	private void loadMoreReddits() {
		System.out.println("loadMoreReddits();");
		loadReddits(true);
	}
	private void loadReddits(boolean loadmore){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		String curfeed = prefs.getString("currentfeed", "technology");
		String sort = prefs.getString("sort", "hot");
		itemfontsize = prefs.getString("widgetfontpref", "16");
		// Load more or initial load/reload?
		if (loadmore){
			// fetch 25 more after current last item and append to the list
			int limit = 25;
			JSONArray tempdata = rdata.getRedditFeed(curfeed, sort, limit, lastitemid);
			if (tempdata.length() == 0){
				endoffeed = true;
			} else {
				endoffeed = false;
				int i = 0;
				while (i<tempdata.length()){
					try {
						data.put(tempdata.get(i));
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					i++;
				}
			}
		} else {
			endoffeed = false;
			// reloading
			int limit = Integer.valueOf(prefs.getString("numitemloadpref", "25"));
			data = rdata.getRedditFeed(curfeed, sort, limit, "0");
		}
		// set last item id for "loadmore use"
		// Damn reddit doesn't allow you to specify a start index for the data, instead you have to reference the last item id from the prev page :(
		try {
			lastitemid = data.getJSONObject(data.length()-1).getJSONObject("data").getString("name"); // name is actually the unique id we want
		} catch (JSONException e) {
			lastitemid = "0"; // last item of 0 will fetch the first page
			e.printStackTrace();
		};
		// hide loader
		AppWidgetManager mgr = AppWidgetManager.getInstance(ctxt);
		RemoteViews views = new RemoteViews(ctxt.getPackageName(), R.layout.widgetmain);
		views.setViewVisibility(R.id.srloader, View.INVISIBLE);
		if (endoffeed){ 
			views.setTextViewText(R.id.loadmoretxt, "There's nothing more here");
		}
		mgr.partiallyUpdateAppWidget(appWidgetId, views);
		
	}
	
	// REDUNDANT ASYNC TASK STUFF
	/*private DLTask dltask;
	private void startDownloadIfNoneAlready(boolean loadmore) {
		String loadstr;
		if (loadmore){
			loadstr ="1";
		} else {
			loadstr ="0";
		}
		if (dltask != null) {
			if (dltask.getStatus().equals(AsyncTask.Status.FINISHED)) {
				dltask = new DLTask();
				dltask.execute(loadstr);
			}
		} else {
			dltask = new DLTask();
			dltask.execute(loadstr);
		}
	}
	private class DLTask extends AsyncTask<String, Integer, Long> {
		@Override
		protected Long doInBackground(String... loadmore) {
			// refresh data
			if (loadmore[0].equals("0")){
				loadReddits(false);
			} else {
				System.out.println("async loading more reddits");
				loadMoreReddits();
			}
			return Long.valueOf("1");
		}

		protected void onPostExecute(Long result) {
			
		}
	}*/
}
