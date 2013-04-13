package au.com.wallaceit.reddinator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.app.AlertDialog;
import android.app.ListActivity;
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
			personallist = new ArrayList<String>(Arrays.asList("all","arduino","AskReddit","technology","video","worldnews"));
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
				RemoteViews views = new RemoteViews(getPackageName(), R.layout.widgetmain);
				views.setTextViewText(R.id.subreddittxt, sreddit);
				views.setViewVisibility(R.id.srloader, View.VISIBLE);
				// bypass cache if service not loaded
				global.setBypassCache(true);
				appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, views);
				appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
				finish();
				//System.out.println(sreddit+" selected");
				
			}
		});
		Button btn = (Button) findViewById(R.id.addsrbutton);
		btn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(SubredditSelect.this, ViewAllSubreddits.class);
				startActivityForResult(intent, 0);
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
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
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
		if (!cursort.equals(prefs.getString("sort-"+mAppWidgetId, "hot"))){
			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelect.this);
			RemoteViews views = new RemoteViews(getPackageName(), R.layout.widgetmain);
			views.setViewVisibility(R.id.srloader, View.VISIBLE);
			// bypass cache if service not loaded
			global.setBypassCache(true);
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
}
