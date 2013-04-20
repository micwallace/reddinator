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

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Button;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class PrefsActivity extends PreferenceActivity {
	public int mAppWidgetId;
	private SharedPreferences prefs;
	private String refreshrate = "";
	private String titlefontsize = "";
	private String titlefontcolor = "";
	private String widgettheme = ""; 
	int firsttimesetup = 1;
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
		    firsttimesetup = extras.getInt("firsttimeconfig", 1);
		}
		prefs = PreferenceManager.getDefaultSharedPreferences(PrefsActivity.this);
		refreshrate = prefs.getString("refreshrate", "43200000");
		widgettheme = prefs.getString("widgetthemepref", "1");
		titlefontsize = prefs.getString("titlefontpref", "16");
		titlefontcolor = prefs.getString("titlecolorpref", "0");
	}
	public void onBackPressed(){
		// check if refresh rate has changed and update if needed
		if (!refreshrate.equals(prefs.getString("refreshrate", "43200000"))){
			//System.out.println("Refresh preference changed, updating alarm");
			Intent intent =  new Intent(getApplicationContext(), WidgetProvider.class);
	        intent.setAction(WidgetProvider.APPWIDGET_AUTO_UPDATE);
	        intent.setPackage(getApplicationContext().getPackageName());
	        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
	        PendingIntent updateintent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, 0);
	        final AlarmManager m = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
			int refreshrate = Integer.valueOf(prefs.getString("refreshrate", "43200000"));
			if (refreshrate!=0){
	        	m.setRepeating(AlarmManager.RTC, System.currentTimeMillis()+refreshrate, refreshrate, updateintent);
			} else {
				m.cancel(updateintent); // just incase theres a rougue alarm
			}
		}
		
		// check if theme or style has changed and update if needed
		if (!widgettheme.equals(prefs.getString("widgetthemepref", "1")) || !titlefontcolor.equals(prefs.getString("titlefontpref", "0")) || !titlefontsize.equals(prefs.getString("titlefontpref", "16"))){
			// set theme selected title color if theme has changed but font hasn't.
			if (titlefontcolor.equals(prefs.getString("titlecolorpref", "0")) && !widgettheme.equals(prefs.getString("widgetthemepref", "1"))){
				setUseThemeColor();
			}
			if (firsttimesetup == 0){ // if its the first time setup (ie new widget added), reload the feed items as there will be no cached items for new widget
				updateWidget();
			}
		}
		// for first time setup, widget provider receives this intent in onWidgetOptionsChanged();
		Intent resultValue = new Intent();
		resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
		setResult(RESULT_OK, resultValue);
		finish();
	}
	
	private void updateWidget(){
		AppWidgetManager mgr = AppWidgetManager.getInstance(PrefsActivity.this);
		int[] appWidgetIds = mgr.getAppWidgetIds(new ComponentName(PrefsActivity.this, WidgetProvider.class));
		WidgetProvider.updateAppWidgets(PrefsActivity.this, mgr, appWidgetIds, false);
		GlobalObjects global = ((GlobalObjects) this.getApplicationContext());
		global.setRefreshView();
		mgr.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.listview);
	}
	
	private void setUseThemeColor(){
		prefs.edit().putString("titlecolorpref", "0").commit();
	}

}
