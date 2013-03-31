package com.example.reddinator;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ViewAllSubreddits extends ListActivity {
	private GlobalObjects global;
	private ArrayList<String> sreddits;
	private RedditData rdata;
	private JSONArray srjson;
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		global = ((GlobalObjects) getApplicationContext());
		setContentView(R.layout.viewallsubreddit);
		if (global.isSrlistCached()){
			sreddits = global.getSrList();
			setListAdaptor();
		} else {
			final ProgressDialog dialog = ProgressDialog.show(ViewAllSubreddits.this, "", ("Loading data..."), true);
			Thread t = new Thread() {
					public void run() {
						// get all popular subreddits
						rdata = new RedditData();
						srjson = rdata.getSubreddits();
						// put into arraylist
						sreddits = new ArrayList<String>();
						int i = 0;
						while (i<srjson.length()){
							try {
								sreddits.add(srjson.getJSONObject(i).getJSONObject("data").getString("display_name"));
							} catch (JSONException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							i++;
						}
						global.putSrList(sreddits);
						runOnUiThread(new Runnable() {
								public void run() {
									setListAdaptor();
									
									dialog.dismiss();
								}
						});
					}
			};
			t.start();	
		}
		ListView listView = getListView();
		listView.setTextFilterEnabled(true);
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				String sreddit = ((TextView) view).getText().toString();
				Intent intent = new Intent();
				intent.putExtra("subreddit", sreddit);
				setResult(1, intent);
				finish();
				System.out.println(sreddit+" selected");
			}
		});
	}
	private void setListAdaptor(){
		setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sreddits));
	}	
}
