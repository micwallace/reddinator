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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.TextView;

public class SubredditSelect extends ListActivity {
	ArrayAdapter<String> listadaptor;
	private ArrayList<String> personallist;
	SharedPreferences prefs;
	GlobalObjects global;
	String cursort;
	Button sortbtn;
	boolean curthumbpref;
	boolean curbigthumbpref;
	boolean curhideinfpref;
	TextView widgetprefbtn;
	private int mAppWidgetId;
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.subredditselect);
		// load personal list from saved prefereces, if null use default and save
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		global = ((GlobalObjects) SubredditSelect.this.getApplicationContext());
		Set<String> feeds = prefs.getStringSet("personalsr", new HashSet<String>());
		if (feeds.isEmpty()){
			// first time setup
			personallist = new ArrayList<String>(Arrays.asList("Front Page","all","arduino","AskReddit","pics","technology","science","videos","worldnews"));
			savePersonalList();
		} else {
			personallist = new ArrayList<String>(feeds);
		}
		listadaptor = new MyRedditsAdapter(this, personallist);
		setListAdapter(listadaptor);
		ListView listView = getListView();
		listView.setTextFilterEnabled(true);
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null) {
			mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		}
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				String sreddit = ((TextView) view.findViewById(R.id.srname)).getText().toString();
				// save list
				savePersonalList();
				// save preference
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SubredditSelect.this);
				Editor editor = prefs.edit();
				editor.putString("currentfeed-"+mAppWidgetId, sreddit);
				editor.commit();
				// refresh widget and close activity (NOTE: put in function)
				AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelect.this);
				RemoteViews views = new RemoteViews(getPackageName(), getThemeLayoutId());
				views.setTextViewText(R.id.subreddittxt, sreddit);
				views.setViewVisibility(R.id.srloader, View.VISIBLE);
				views.setViewVisibility(R.id.erroricon, View.INVISIBLE);
				// bypass cache if service not loaded
				global.setBypassCache(true);
				appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, views);
				appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
				finish();
				//System.out.println(sreddit+" selected");
				
			}
		});
		Button addbtn = (Button) findViewById(R.id.addsrbutton);
		addbtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(SubredditSelect.this, ViewAllSubreddits.class);
				startActivityForResult(intent, 0);
			}
		});
		Button importbtn = (Button) findViewById(R.id.importsrbutton);
		importbtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				showImportDialog();
			}
		});
		// sort button
		sortbtn = (Button) findViewById(R.id.sortselect);
		cursort = prefs.getString("sort-"+mAppWidgetId, "hot");
		String sorttxt = "Sort:  "+cursort;
		sortbtn.setText(sorttxt);
		sortbtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				showSortDialog();
			}
		});
		// set options dialog click listener
		widgetprefbtn = (TextView) findViewById(R.id.widgetprefbtn);
		widgetprefbtn.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				final CharSequence[] names = {"Thumbnails", "Thumbs On Top", "Hide Post Info"};
			    final boolean[] initvalue = {prefs.getBoolean("thumbnails-"+mAppWidgetId, true), prefs.getBoolean("bigthumbs-"+mAppWidgetId, false),  prefs.getBoolean("hideinf-"+mAppWidgetId, false)};
			    AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelect.this);
			    builder.setTitle("Widget Options");
			    builder.setMultiChoiceItems(names, initvalue, new DialogInterface.OnMultiChoiceClickListener(){
			        public void onClick(DialogInterface dialogInterface, int item, boolean state) {
			        	Editor prefsedit = prefs.edit();
			        	switch(item){
			        		case 0:
			        			prefsedit.putBoolean("thumbnails-"+mAppWidgetId, state);
			        			break;
			        		case 1:
			        			prefsedit.putBoolean("bigthumbs-"+mAppWidgetId, state);
			        			break;
			        		case 2:
			        			prefsedit.putBoolean("hideinf-"+mAppWidgetId, state);
			        			break;
			        	}
			        	prefsedit.commit();
			        }
			    });
			    builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int id) {
			            dialog.cancel();
			        }
			    });
			    builder.create().show();
			}
		});
		// load initial values for comparison
		curthumbpref = prefs.getBoolean("thumbnails-"+mAppWidgetId, true);
		curbigthumbpref = prefs.getBoolean("bigthumbs-"+mAppWidgetId, false);
		curhideinfpref = prefs.getBoolean("hideinf-"+mAppWidgetId, false);
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == 1){
			String subreddit = data.getStringExtra("subreddit");
			personallist.add(subreddit);
			listadaptor.notifyDataSetChanged();
		}
	}
	// save changes on back press
	public void onBackPressed(){
		savePersonalList();
		// check if sort has changed
		if (!cursort.equals(prefs.getString("sort-"+mAppWidgetId, "hot")) || curthumbpref!=prefs.getBoolean("thumbnails-"+mAppWidgetId, true) || curbigthumbpref!=prefs.getBoolean("bigthumbs-"+mAppWidgetId, false) || curhideinfpref!=prefs.getBoolean("hideinf-"+mAppWidgetId, false)){
			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelect.this);
			RemoteViews views = new RemoteViews(getPackageName(), getThemeLayoutId());
			views.setViewVisibility(R.id.srloader, View.VISIBLE);
			views.setViewVisibility(R.id.erroricon, View.INVISIBLE);
			// bypass the cached entrys only if the sorting preference has changed
			if (!cursort.equals(prefs.getString("sort-"+mAppWidgetId, "hot"))){
				global.setBypassCache(true);
			} else {
				global.setRefreshView();
			}
			appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, views);
			appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
		}
		finish();
	}
	// save/restore personal list
	private void savePersonalList(){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Editor editor = prefs.edit();
        Set<String> set = new HashSet<String>();
        set.addAll(personallist);
        editor.putStringSet("personalsr", set);
        editor.commit();
	}
	// show sort select dialog
	private void showSortDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelect.this);
	    builder.setTitle("Pick a sort, any sort");
	    builder.setItems(R.array.reddit_sorts, new DialogInterface.OnClickListener() {
	               public void onClick(DialogInterface dialog, int which) {
	            	   Editor prefsedit = prefs.edit();
	            	   String sort = "hot"; // default if fails
	            	   // find index
	            	   switch (which){
	            	   		case 0 : sort = "hot"; break;
	            	   		case 1 : sort = "new"; break;
	            	   		case 2 : sort = "rising"; break;
	            	   		case 3 : sort = "controversial"; break;
	            	   		case 4 : sort = "top"; break;
	            	   }
	            	   prefsedit.putString("sort-"+mAppWidgetId, sort);
	            	   prefsedit.commit();
	            	   // set new text in button
	            	   String sorttxt = "Sort:  "+sort;
	           		   sortbtn.setText(sorttxt);
	            	   //System.out.println("Sort set: "+sort);
	            	   dialog.dismiss();
	               }
	    });
	    builder.show();
	}
	// show the import/login dialog
	private void showImportDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelect.this);
	    // Get the layout inflater
	    LayoutInflater inflater = getLayoutInflater();
	    final View v = inflater.inflate(R.layout.importdialog, null);
	    builder.setView(v)
	    // Add action buttons
	    .setPositiveButton("Import", new DialogInterface.OnClickListener() {
	    	@Override
	        public void onClick(DialogInterface dialog, int id) {
	    		String username =  ((EditText) v.findViewById(R.id.username)).getText().toString();
	            String password =  ((EditText) v.findViewById(R.id.password)).getText().toString();
	            boolean clearlist =  ((CheckBox) v.findViewById(R.id.clearlist)).isChecked();
	    		dialog.cancel();
	        	// import
	    		importSubreddits(username, password, clearlist);
	        }
	    })
	    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int id) {
	            dialog.cancel();
	        }
	    })
	    .setTitle("Login to Reddit");    
		builder.create().show();
	}
	// import personal subreddits
	private void importSubreddits(final String username, final String password, final boolean clearlist){
		// use a thread for searching
		final ProgressDialog sdialog = ProgressDialog.show(SubredditSelect.this, "", ("Importing..."), true);
		Thread t = new Thread() {
				public void run() {
					
					final ArrayList<String> list = global.mRedditData.getMySubreddits(username, password);
					// copy into current personal list if not empty or error
					if (!list.isEmpty() && !list.get(0).contains("Error:")){
						if (clearlist){
							personallist.clear();
						}
						personallist.addAll(list);
					}
					runOnUiThread(new Runnable() {
							public void run() {
								sdialog.dismiss();
								if (list.isEmpty() || list.get(0).contains("Error:")){
									new AlertDialog.Builder(SubredditSelect.this).setMessage(list.isEmpty()?"No subreddits to import!":list.get(0))
									.setPositiveButton("OK", new DialogInterface.OnClickListener() {
								           public void onClick(DialogInterface dialog, int id) {
								                dialog.cancel();
								           }
								       }).show();
								} else {
									listadaptor.notifyDataSetChanged();
								}
							}
					});	
				}
		};
		t.start();
	}
	// list adapter
	class MyRedditsAdapter extends ArrayAdapter<String> {
		private LayoutInflater inflater;
		
		public MyRedditsAdapter(Context context, List<String> objects) {
			super(context, R.layout.myredditlistitem, R.id.srname, objects);
			inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			convertView = inflater.inflate(R.layout.myredditlistitem, parent,
					false);
			super.getView(position, convertView, parent);
			// setup the row
			((TextView) convertView.findViewById(R.id.srname)).setText(personallist.get(position).toString());
			convertView.findViewById(R.id.srdeletebtn).setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					String sreddit = ((TextView) ((View) v.getParent()).findViewById(R.id.srname)).getText().toString();
					personallist.remove(sreddit);
					listadaptor.notifyDataSetChanged();
				}
			});
			return convertView;
		}
	}
	
	private int getThemeLayoutId(){
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
