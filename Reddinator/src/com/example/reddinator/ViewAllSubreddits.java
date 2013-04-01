package com.example.reddinator;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class ViewAllSubreddits extends ListActivity {
	private GlobalObjects global;
	private ArrayList<String> sreddits;
	private RedditData rdata;
	private JSONArray srjson;
	private ArrayAdapter<String> listadapter;
	private EditText searchbox;
	private ListView listview;
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		global = ((GlobalObjects) getApplicationContext());
		setContentView(R.layout.viewallsubreddit);
		// setup list view
		listview = getListView();
		listview.setTextFilterEnabled(true);
		listview.setOnItemClickListener(new OnItemClickListener() {
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
		// get list data
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
		// setup search buttons
		searchbox = (EditText) this.findViewById(R.id.searchbox);
		searchbox.setOnEditorActionListener(new OnEditorActionListener(){
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP){
					search(v.getText().toString());
				}
				return true;
			}
			
		});
		ImageView searchbtn = (ImageView) this.findViewById(R.id.searchbutton);
		searchbtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String query = searchbox.getText().toString();
				search(query);
			}
		});
	}
	public void onBackPressed(){
		System.out.println("onBackPressed()");
		if (searchbox.getText().toString().equals("")){
			this.finish();
		} else {
			sreddits.clear();
			sreddits.addAll(global.getSrList());
			updateAdapter();
			searchbox.setText("");
		}
	}
	protected void onResume(){
		System.out.println("onResume()");
		super.onResume();
	}
	private void search(final String query){
		System.out.println("Searching: "+query);
		// use a thread for searching
		final ProgressDialog sdialog = ProgressDialog.show(ViewAllSubreddits.this, "", ("Searching..."), true);
		Thread t = new Thread() {
				public void run() {
					// get all popular subreddits
					rdata = new RedditData();
					srjson = rdata.getSubredditSearch(query);
					// put into arraylist
					sreddits.clear();
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
					
					System.out.println("search complete");
					runOnUiThread(new Runnable() {
							public void run() {
								updateAdapter();
								sdialog.dismiss();
							}
					});
					
				}
		};
		t.start();
	}
	private void setListAdaptor(){
		listadapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sreddits);
		listview.setAdapter(listadapter);
	}
	private void updateAdapter(){
		listadapter.notifyDataSetChanged();
	}
}
