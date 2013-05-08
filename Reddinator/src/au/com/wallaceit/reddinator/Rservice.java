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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.Html;
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
	private JSONArray data;
	private GlobalObjects global;
	private SharedPreferences prefs;
	private Editor prefseditor;
	private String titlefontsize = "16";
	private int[] themecolors;
	private boolean loadcached = false; // tells the ondatasetchanged function that it should not download any further items, cache is loaded
	private boolean loadthumbnails = false;
	private boolean bigthumbs = false;
	private boolean hideinf = false;
	
	public ListRemoteViewsFactory(Context ctxt, Intent intent) {
		this.ctxt = ctxt;
		appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		global = ((GlobalObjects) ctxt.getApplicationContext());
		prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		prefseditor = prefs.edit();
		//System.out.println("New view factory created for widget ID:"+appWidgetId);
		// if this is a user request (apart from 'loadmore') or an auto update, do not attempt to load cache. 
		// when a user clicks load more and a new view factory needs to be created we don't want to bypass cache, we want to load the cached items
		int loadtype = global.getLoadType();
		if (!global.getBypassCache() || loadtype ==  GlobalObjects.LOADTYPE_LOADMORE){
			//System.out.println("This is not a standard user request or auto update, checking for cache");
			try {
				data = new JSONArray(prefs.getString("feeddata-"+appWidgetId, "[]"));
			} catch (JSONException e) {
				data = new JSONArray();
				e.printStackTrace();
			}
			//System.out.println("cached Data length: "+data.length());
			if (data.length() != 0){
				titlefontsize = prefs.getString("widgetfontpref", "16");
				try {
					lastitemid = data.getJSONObject(data.length()-1).getJSONObject("data").getString("name");
				} catch (JSONException e) {
					lastitemid = "0"; // Could not get last item ID; perform a reload next time and show error view :(
					e.printStackTrace();
				}
				if (loadtype == GlobalObjects.LOADTYPE_LOAD){
					loadcached = true; // this isn't a loadmore request, the cache is loaded and we're done
					//System.out.println("Cache loaded, no user request received.");
				}
			}
		} else {
			data = new JSONArray(); // set empty data to prevent any NPE
		}
	}
	
	@Override
	public void onCreate() {
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);
		endoffeed = false;
		// get thumbnail load preference for the widget
		loadthumbnails = prefs.getBoolean("thumbnails-"+appWidgetId, true);
		bigthumbs = prefs.getBoolean("bigthumbs-"+appWidgetId, false);
		hideinf = prefs.getBoolean("hideinf-"+appWidgetId, false);
	}

	@Override
	public void onDestroy() {
		// no-op
		System.out.println("Service detroyed");
	}

	@Override
	public int getCount() {
		return (data.length() + 1); // plus 1 advertises the "load more" item to the listview without having to add it to the data source
	}
	
	@Override
	public RemoteViews getViewAt(int position) {
		RemoteViews row;
		if (position > data.length()){
			return null; //  prevent errornous views
		}
		// check if its the last view and return loading view instead of normal row
		if (position == data.length()) {
			// build load more item
			//System.out.println("load more getViewAt("+position+") firing"); 
			RemoteViews loadmorerow = new RemoteViews(ctxt.getPackageName(), R.layout.listrowloadmore);
			if (endoffeed){ 
				loadmorerow.setTextViewText(R.id.loadmoretxt, "There's nothing more here");
			} else {
				loadmorerow.setTextViewText(R.id.loadmoretxt, "Load more...");
			}
			loadmorerow.setTextColor(R.id.loadmoretxt, themecolors[1]);
			Intent i = new Intent();
			Bundle extras = new Bundle();
			extras.putString(WidgetProvider.ITEM_ID, "0"); // zero will be an indicator in the onreceive function of widget provider if its not present it forces a reload
			i.putExtras(extras);
			loadmorerow.setOnClickFillInIntent(R.id.listrowloadmore, i);
			return loadmorerow;
		} else {
			// build normal item
			String name = "";
			String url = "";
			String permalink = "";
			String thumbnail = "";
			String domain = "";
			String id = "";
			int score = 0;
			int numcomments = 0;
			try {
				JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
				name = tempobj.getString("title");
				domain = tempobj.getString("domain");
				id = tempobj.getString("id");
				url = tempobj.getString("url");
				permalink = tempobj.getString("permalink");
				thumbnail = (String) tempobj.get("thumbnail"); // we have to call get and cast cause its not in quotes
				score = tempobj.getInt("score");
				numcomments = tempobj.getInt("num_comments");
			} catch (JSONException e) {
				e.printStackTrace();
				// return null; // The view is invalid;
			}
			// create remote view from specified layout
			if (bigthumbs){
				row = new RemoteViews(ctxt.getPackageName(), R.layout.listrowbigthumb);
			} else {
				row = new RemoteViews(ctxt.getPackageName(), R.layout.listrow);
			}
			// build view
			row.setTextViewText(R.id.listheading, Html.fromHtml(name).toString());
			row.setFloat(R.id.listheading, "setTextSize", Integer.valueOf(titlefontsize)); // use for compatibility setTextViewTextSize only introduced in API 16
			row.setTextColor(R.id.listheading, themecolors[0]);
			row.setTextViewText(R.id.sourcetxt, domain);
			row.setTextColor(R.id.sourcetxt, themecolors[3]);
			row.setTextColor(R.id.votestxt, themecolors[4]);
			row.setTextColor(R.id.commentstxt, themecolors[4]);
			row.setTextViewText(R.id.votestxt, String.valueOf(score));
			row.setTextViewText(R.id.commentstxt, String.valueOf(numcomments));
			row.setInt(R.id.listdivider, "setBackgroundColor", themecolors[2]);
			// add extras and set click intent
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
			// load thumbnail if they are enabled for this widget
			if (loadthumbnails){
				if (!thumbnail.equals("") && !thumbnail.equals("self")){ // check for thumbnail; self is used to display the thinking logo on the reddit site, we'll just show nothing for now
					Bitmap bitmap = loadImage(thumbnail);
					if (bitmap != null){
						row.setImageViewBitmap(R.id.thumbnail, bitmap);
						row.setViewVisibility(R.id.thumbnail, View.VISIBLE);
					} else {
						// row.setImageViewResource(R.id.thumbnail, android.R.drawable.stat_notify_error); for later
						row.setViewVisibility(R.id.thumbnail, View.GONE);
					}
				} else {
					row.setViewVisibility(R.id.thumbnail, View.GONE);
				}
			} else {
				row.setViewVisibility(R.id.thumbnail, View.GONE);
			}
			// hide info bar if options set
			if (hideinf){
				row.setViewVisibility(R.id.infbox, View.GONE);
			} else {
				row.setViewVisibility(R.id.infbox, View.VISIBLE);
			}
		}
		//System.out.println("getViewAt("+position+");");
		return row;
	}
	private Bitmap loadImage(String urlstr){
		URL url = null;
		Bitmap bmp = null;
		try {
			url = new URL(urlstr);
			URLConnection con = url.openConnection();
			con.setConnectTimeout(8000);
			con.setReadTimeout(8000);
			bmp = BitmapFactory.decodeStream(con.getInputStream());
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	    return bmp;
	}

	@Override
	public RemoteViews getLoadingView() {
		RemoteViews rowload = new RemoteViews(ctxt.getPackageName(), R.layout.listrowload);
		rowload.setTextColor(R.id.listloadtxt, themecolors[0]);
		return rowload;
	}

	@Override
	public int getViewTypeCount() {
		return (3);
	}

	@Override
	public long getItemId(int position) {
		return (position);
	}

	@Override
	public boolean hasStableIds() {
		return (false);
	}

	@Override
	public void onDataSetChanged() {
		// get thumbnail load preference for the widget
		loadthumbnails = prefs.getBoolean("thumbnails-"+appWidgetId, true);
		bigthumbs = prefs.getBoolean("bigthumbs-"+appWidgetId, false);
		hideinf = prefs.getBoolean("hideinf-"+appWidgetId, false);
		titlefontsize = prefs.getString("titlefontpref", "16");
		getThemeColors(); // reset theme colors
		int loadtype = global.getLoadType();
		if (!loadcached){
			loadcached = (loadtype==GlobalObjects.LOADTYPE_REFRESH_VIEW); // see if its just a call to refresh view and set var accordingly but only check it if load cached is not already set true in the above constructor
		}
		//System.out.println("Loading type "+loadtype);
		if (!loadcached){
			// refresh data
			if (loadtype == GlobalObjects.LOADTYPE_LOADMORE && !lastitemid.equals("0")){ // do not attempt a "loadmore" if we don't have a valid item ID; this would append items to the list, instead perform a full reload
				global.SetLoad();
				loadMoreReddits();
			} else {
				//System.out.println("loadReddits();");
				loadReddits(false);
			}
			global.setBypassCache(false); // don't bypass the cache check the next time the service starts
		} else {
			loadcached = false;
			global.SetLoad();
			// hide loader
			hideWidgetLoader(false, false); // don't go to top as the user is probably interacting with the list
		}
		
	}
	private String lastitemid = "0";
	private boolean endoffeed = false;
	private void loadMoreReddits() {
		//System.out.println("loadMoreReddits();");
		loadReddits(true);
	}
	private void loadReddits(boolean loadmore){
		String curfeed = prefs.getString("currentfeed-"+appWidgetId, "technology");
		String sort = prefs.getString("sort-"+appWidgetId, "hot");
		// Load more or initial load/reload?
		if (loadmore){
			// fetch 25 more after current last item and append to the list
			JSONArray tempdata = global.rdata.getRedditFeed(curfeed, sort, 25, lastitemid);
			if (!isError(tempdata)){
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
					prefseditor.putString("feeddata-"+appWidgetId, data.toString());
					prefseditor.commit();
				}
			} else {
				hideWidgetLoader(false, true); // don't go to top of list and show error icon
				return;
			}
		} else {
			endoffeed = false;
			// reloading
			int limit = Integer.valueOf(prefs.getString("numitemloadpref", "25"));
			JSONArray temparray = global.rdata.getRedditFeed(curfeed, sort, limit, "0");
			// check if data is valid; if the getredditfeed function fails to create a connection it returns -1 in the first value of the array
			if (!isError(temparray)){
				data = temparray;
				if (data.length() == 0){
					endoffeed=true;
				}
				prefseditor.putString("feeddata-"+appWidgetId, data.toString());
				prefseditor.commit();
			} else {
				hideWidgetLoader(false, true); // don't go to top of list and show error icon
				return;
			}
		}
		// set last item id for "loadmore use"
		// Damn reddit doesn't allow you to specify a start index for the data, instead you have to reference the last item id from the prev page :(
		try {
			lastitemid = data.getJSONObject(data.length()-1).getJSONObject("data").getString("name"); // name is actually the unique id we want
		} catch (JSONException e) {
			lastitemid = "0"; // Could not get last item ID; perform a reload next time and show error view :(
			e.printStackTrace();
		};
		// hide loader
		if (loadmore){
			hideWidgetLoader(false, false); // don't go to top of list
		} else {
			hideWidgetLoader(true, false); // go to top
		}
	}
	// check if the array is an error array
	private boolean isError(JSONArray temparray){
		boolean error;
		if (temparray == null){
			return true; // null error
		}
		if (temparray.length() > 0){
			try {
				error = temparray.getString(0).equals("-1");
			} catch (JSONException e) {
				error = true;
				e.printStackTrace();
			}
		} else {
			error = false; // empty array means no more feed items
		}
		return error;
	}
	// hide appwidget loader
	private void hideWidgetLoader(boolean gototopoflist, boolean showerror){
		AppWidgetManager mgr = AppWidgetManager.getInstance(ctxt);
		// get theme layout id
     	int layout = 1;
     	switch(Integer.valueOf(prefs.getString("widgetthemepref", "1"))){
     		case 1: layout = R.layout.widgetmain; break;
     		case 2: layout = R.layout.widgetdark; break;
     		case 3: layout = R.layout.widgetholo; break;
     		case 4: layout = R.layout.widgetdarkholo; break;
     	}
		RemoteViews views = new RemoteViews(ctxt.getPackageName(), layout);
		views.setViewVisibility(R.id.srloader, View.INVISIBLE);
		// go to the top of the list view
		if (gototopoflist){
			views.setScrollPosition(R.id.listview, 0);
		}
		if (showerror){
			views.setViewVisibility(R.id.erroricon, View.VISIBLE);
		}
		mgr.partiallyUpdateAppWidget(appWidgetId, views);
	}
	
	private void getThemeColors(){
     	switch(Integer.valueOf(prefs.getString("widgetthemepref", "1"))){
     		// set colors array: healine text, load more text, divider, domain text, vote & comments
     		case 1: themecolors = new int[]{Color.BLACK, Color.BLACK, Color.parseColor("#D7D7D7"), Color.parseColor("#336699"), Color.parseColor("#FF4500")}; break;
     		case 2: themecolors = new int[]{Color.WHITE, Color.WHITE, Color.parseColor("#646464"), Color.parseColor("#5F99CF"), Color.parseColor("#FF8B60")}; break;
     		case 3: 
     		case 4: themecolors = new int[]{Color.WHITE, Color.WHITE, Color.parseColor("#646464"), Color.parseColor("#CEE3F8"), Color.parseColor("#FF8B60")}; break;
     	}
     	// user title color override
     	if (!prefs.getString("titlecolorpref", "0").equals("0")){
     		themecolors[0] = Color.parseColor(prefs.getString("titlecolorpref", "#000"));
     	}
	}
}
