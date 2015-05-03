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
import android.app.ActionBar;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;

public class PrefsActivity extends PreferenceActivity {
    public int mAppWidgetId;
    private SharedPreferences mSharedPreferences;
    private String mRefreshrate = "";
    private String mTitleFontSize = "";
    private String mTitleFontColor = "";
    private String mWidgetTheme = "";
    int mFirstTimeSetup = 0;
    String mMailRefresh = "";
    boolean isfromappview = false;
    GlobalObjects global;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        getListView().setBackgroundColor(Color.WHITE);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(PrefsActivity.this);
        global = ((GlobalObjects) PrefsActivity.this.getApplicationContext());
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(PrefsActivity.this);

        if (!mSharedPreferences.getString("oauthtoken", "").equals("")){
            // Load the account preferences when logged in
            addPreferencesFromResource(R.xml.account_preferences);
            Preference logoutbtn = findPreference("logout");
            final PreferenceCategory accountSettings = (PreferenceCategory) findPreference("account");
            logoutbtn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // clear oauth token and save prefs
                    global.mRedditData.purgeAccountData();
                    // remove mail check alarm
                    MailCheckReceiver.setAlarm(PrefsActivity.this);
                    // remove account prefs
                    getPreferenceScreen().removePreference(accountSettings);
                    Toast.makeText(PrefsActivity.this, "Account Disconnected", Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }

        Preference themeManagerButton = findPreference("theme_manager_button");
        themeManagerButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(PrefsActivity.this, ThemesActivity.class);
                startActivity(intent);
                return true;
            }
        });
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

        mMailRefresh = mSharedPreferences.getString(getString(R.string.background_mail_pref), "43200000");

        // set themes list
        HashMap<String, String> themeList = global.mThemeManager.getThemeList(ThemeManager.LISTMODE_ALL);
        ListPreference themePref = (ListPreference) findPreference("appthemepref");
        themePref.setEntries(themeList.values().toArray(new CharSequence[themeList.values().size()]));
        themePref.setEntryValues(themeList.keySet().toArray(new CharSequence[themeList.keySet().size()]));

        Toast.makeText(this, "Press the back button to save settings", Toast.LENGTH_SHORT).show();
    }

    public void onBackPressed() {
        saveSettingsAndFinish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                saveSettingsAndFinish();
                return true;
        }
        return false;
    }

    private void saveSettingsAndFinish() {
        // check if refresh rate has changed and update if needed
        if (!mRefreshrate.equals(mSharedPreferences.getString(getString(R.string.refresh_rate_pref), "43200000"))) {
            //System.out.println("Refresh preference changed, updating alarm");
            WidgetProvider.setUpdateSchedule(PrefsActivity.this, false);
        }
        // check if background mail check interval has changed
        if (!mMailRefresh.equals(mSharedPreferences.getString(getString(R.string.background_mail_pref), "43200000"))) {
            //System.out.println("Refresh preference changed, updating alarm");
            MailCheckReceiver.setAlarm(PrefsActivity.this);
        }
        int result = 0;
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
            }
            // if we are returning to app view,set the result to 3, indicating a theme update is needed
            result = 3;
        }

        if (isfromappview) {
            setResult(result); // no update needed
            finish();
            return;
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
        if (global != null) {
            global.setRefreshView();
        }
        mgr.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.listview);
    }

    private void setUseThemeColor() {
        mSharedPreferences.edit().putString(getString(R.string.title_color_pref), "0").apply();
    }

}
