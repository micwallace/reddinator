package com.example.reddinator;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.View;
import android.widget.Button;
import android.widget.RemoteViews;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class PrefsActivity extends PreferenceActivity {
	public int mAppWidgetId;
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Add a button to the header list.
		if (hasHeaders()) {
			Button button = new Button(this);
			button.setText("Some action");
			setListFooter(button);
		}
		addPreferencesFromResource(R.xml.preferences);
		getListView().setBackgroundColor(Color.WHITE);
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
		views.setViewVisibility(R.id.srloader, View.VISIBLE);
		appWidgetManager.updateAppWidget(mAppWidgetId, views);
		appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
		//WidgetProvider.updateAppWidget(this, appWidgetManager, mAppWidgetId);
		Intent resultValue = new Intent();
		resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
		setResult(RESULT_OK, resultValue);
		finish();
	}

}
