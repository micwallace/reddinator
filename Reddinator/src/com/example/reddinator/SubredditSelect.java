package com.example.reddinator;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.ListActivity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.RemoteViews;
import android.widget.TextView;

public class SubredditSelect extends ListActivity {
	private ArrayList<String> sreddits;
	private int mAppWidgetId;
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.selectsubreddit);
		// add predefined subreddits to arraylist (will load saved/personal subreddits later)
		sreddits = new ArrayList<String>(Arrays.asList("all","arduino","askreddit","technology","video","worldnews"));
		setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sreddits));
		ListView listView = getListView();
		listView.setTextFilterEnabled(true);
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null) {
		    mAppWidgetId = extras.getInt(
		            AppWidgetManager.EXTRA_APPWIDGET_ID, 
		            AppWidgetManager.INVALID_APPWIDGET_ID);
		}
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				String sreddit = ((TextView) view).getText().toString();
				// save preference
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SubredditSelect.this);
				Editor editor = prefs.edit();
				editor.putString("currentfeed", sreddit);
				editor.commit();
				// refresh widget and close activity
				AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelect.this);
				RemoteViews views = new RemoteViews(getPackageName(), R.layout.widgetmain);
				views.setTextViewText(R.id.subreddittxt, sreddit);
				appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, views);
				appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
				finish();
				System.out.println(sreddit+" selected");
				
			}
		});
	}
	
}
