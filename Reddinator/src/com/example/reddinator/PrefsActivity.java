package com.example.reddinator;

import java.util.List;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Button;
import android.widget.RemoteViews;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class PrefsActivity extends PreferenceActivity {
	public int mAppWidgetId;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Add a button to the header list.
		if (hasHeaders()) {
			Button button = new Button(this);
			button.setText("Some action");
			setListFooter(button);
		}
		
	}
	@Override
	protected void onResume(){
		super.onResume();
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null) {
		    mAppWidgetId = extras.getInt(
		            AppWidgetManager.EXTRA_APPWIDGET_ID, 
		            AppWidgetManager.INVALID_APPWIDGET_ID);
		}
	}
	public void onBackPressed(){
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(PrefsActivity.this);
		RemoteViews views = new RemoteViews(PrefsActivity.this.getPackageName(), R.layout.widgetmain);
		appWidgetManager.updateAppWidget(mAppWidgetId, views);
		appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
		//WidgetProvider.updateAppWidget(this, appWidgetManager, mAppWidgetId);
		Intent resultValue = new Intent();
		resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
		setResult(RESULT_OK, resultValue);
		finish();
	}
	
	/**
	 * Populate the activity with the top-level headers.
	 */
	@Override
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.preference_headers, target);
	}

	public static class Prefs1Fragment extends PreferenceFragment { 
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// Make sure default values are applied. In a real app, you would
			// want this in a shared function that is used to retrieve the
			// SharedPreferences wherever they are needed.
			PreferenceManager.setDefaultValues(getActivity(),
					R.xml.advanced_preferences, false);

			// Load the preferences from an XML resource 
			addPreferencesFromResource(R.xml.fragmented_preferences);
		}
	}

	/**
	 * This fragment contains a second-level set of preference that you can get
	 * to by tapping an item in the first preferences fragment.
	 */
	public static class Prefs1FragmentInner extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// Can retrieve arguments from preference XML.
			Log.i("args", "Arguments: " + getArguments());

			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.fragmented_preferences_inner);
		}
	}

	/**
	 * This fragment shows the preferences for the second header.
	 */
	public static class Prefs2Fragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// Can retrieve arguments from headers XML.
			Log.i("args", "Arguments: " + getArguments());

			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.preference_dependencies);
		}
	}

}
