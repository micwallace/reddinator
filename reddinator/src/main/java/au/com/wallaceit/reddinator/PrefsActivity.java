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
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class PrefsActivity extends PreferenceActivity {
    public int mAppWidgetId;
    private SharedPreferences mSharedPreferences;
    private String mRefreshrate = "";
    private String mTitleFontSize = "";
    private String mTitleFontColor = "";
    private String mWidgetTheme = "";
    int mFirstTimeSetup = 0;
    boolean isfromappview = false;

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
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(PrefsActivity.this);
        final Preference logoutbtn = getPreferenceManager().findPreference("logout");
        if (logoutbtn == null) {
            return;
        }
        if ((mSharedPreferences.getString("uname", "").equals("") && mSharedPreferences.getString("pword", "").equals("")) && (mSharedPreferences.getString("cook", "").equals("") && mSharedPreferences.getString("modhash", "").equals(""))) {
            logoutbtn.setEnabled(false);
        } else {
            logoutbtn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    editor.putString("uname", "");
                    editor.putString("pword", "");
                    editor.putString("cook", "");
                    editor.putString("modhash", "");
                    editor.commit();
                    // clear current purge from active objects
                    ((GlobalObjects) PrefsActivity.this.getApplicationContext()).mRedditData.purgeAccountData();
                    // disable button and notify user
                    logoutbtn.setEnabled(false);
                    Toast.makeText(PrefsActivity.this, "Saved account and session cleared", Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (!(isfromappview = extras.getBoolean("fromapp", false))) {
                mFirstTimeSetup = extras.getInt("firsttimeconfig", 1);
                mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            }
        }
        mRefreshrate = mSharedPreferences.getString(getString(R.string.refresh_rate_pref), "43200000");
        mWidgetTheme = mSharedPreferences.getString(getString(R.string.widget_theme_pref), "1");
        mTitleFontSize = mSharedPreferences.getString(getString(R.string.title_font_pref), "16");
        mTitleFontColor = mSharedPreferences.getString(getString(R.string.title_color_pref), "0");

        Toast.makeText(this, "Press the back button to save settings", Toast.LENGTH_SHORT).show();
    }

    public void onBackPressed() {
        // check if refresh rate has changed and update if needed
        if (!mRefreshrate.equals(mSharedPreferences.getString(getString(R.string.refresh_rate_pref), "43200000"))) {
            //System.out.println("Refresh preference changed, updating alarm");
            Intent intent = new Intent(getApplicationContext(), WidgetProvider.class);
            intent.setAction(WidgetProvider.APPWIDGET_AUTO_UPDATE);
            intent.setPackage(PrefsActivity.this.getPackageName());
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent updateIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, 0);
            final AlarmManager alarmManager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            int refreshRate = Integer.valueOf(mSharedPreferences.getString(getString(R.string.refresh_rate_pref), "43200000"));
            if (refreshRate != 0) {
                alarmManager.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + refreshRate, refreshRate, updateIntent);
            } else {
                alarmManager.cancel(updateIntent); // just incase theres a rougue alarm
            }
        }

        // check if theme or style has changed and update if needed
        if (!mWidgetTheme.equals(mSharedPreferences.getString(getString(R.string.widget_theme_pref), "1"))
                || !mTitleFontColor.equals(mSharedPreferences.getString(getString(R.string.title_color_pref), "0"))
                || !mTitleFontSize.equals(mSharedPreferences.getString(getString(R.string.title_font_pref), "16"))) {

            // set theme selected title color if theme has changed but font hasn't.
            if (mTitleFontColor.equals(mSharedPreferences.getString(getString(R.string.title_color_pref), "0"))
                    && !mWidgetTheme.equals(mSharedPreferences.getString(getString(R.string.widget_theme_pref), "1"))) {
                setUseThemeColor();
            }

            if (mFirstTimeSetup == 0) { // if its the first time setup (ie new widget added), reload the feed items as there will be no cached items for new widget
                updateWidget(); // Reloads widget without reloading feed items.
                // if we are returning to app view,set the result to 2, indicating a theme update is needed
                if (isfromappview) {
                    setResult(3);
                    finish();
                }
            }

        }
        if (isfromappview) {
            setResult(0); // no update needed
            finish();
        }
        // for first time setup, widget provider receives this intent in onWidgetOptionsChanged();
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    private void updateWidget() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(PrefsActivity.this);
        int[] appWidgetIds = mgr.getAppWidgetIds(new ComponentName(PrefsActivity.this, WidgetProvider.class));
        WidgetProvider.updateAppWidgets(PrefsActivity.this, mgr, appWidgetIds, false);
        GlobalObjects global = ((GlobalObjects) PrefsActivity.this.getApplicationContext());
        global.setRefreshView();
        mgr.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.listview);
    }

    private void setUseThemeColor() {
        mSharedPreferences.edit().putString(getString(R.string.title_color_pref), "0").commit();
    }

}
