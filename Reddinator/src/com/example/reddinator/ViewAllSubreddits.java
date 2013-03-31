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

public class ViewAllSubreddits extends ListActivity {
	private ArrayList<String> sreddits;
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.viewallsubreddit);
		// add predefined subreddits to arraylist (will load saved/personal subreddits later)
		sreddits = new ArrayList<String>(Arrays.asList("all","arduino","askreddit","technology","video","worldnews"));
		setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sreddits));
		ListView listView = getListView();
		listView.setTextFilterEnabled(true);
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				String sreddit = ((TextView) view).getText().toString();
				finish();
				System.out.println(sreddit+" selected");
				
			}
		});
	}
	
}
