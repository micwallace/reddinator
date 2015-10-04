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
package au.com.wallaceit.reddinator.activity;

import android.app.ActionBar;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.LinkedHashMap;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.service.MailCheckReceiver;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.service.WidgetProvider;

public class PrefsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    public int mAppWidgetId;
    private SharedPreferences mSharedPreferences;
    private String mRefreshrate = "";
    private String mTitleFontSize = "";
    private String mAppTheme = "";
    //int mFirstTimeSetup = 0;
    private String mMailRefresh = "";
    boolean isfromappview = false;
    private Reddinator global;
    private boolean themeChanged = false;
    private PreferenceCategory appearanceCat;
    private ListPreference themePref;
    private Preference themeEditorButton;

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
        global = ((Reddinator) PrefsActivity.this.getApplicationContext());
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(PrefsActivity.this);
        String token = mSharedPreferences.getString("oauthtoken", "");
        if (!token.equals("")){
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

        appearanceCat = (PreferenceCategory) findPreference("appearance");

        Preference themeManagerButton = findPreference("theme_manager_button");
        themeManagerButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(PrefsActivity.this, ThemesActivity.class);
                startActivity(intent);
                return true;
            }
        });

        themeEditorButton = findPreference("theme_editor_button");
        themeEditorButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(PrefsActivity.this, ThemeEditorActivity.class);
                intent.putExtra("themeId", mAppTheme);
                startActivity(intent);
                return true;
            }
        });

        themePref = (ListPreference) findPreference("appthemepref");

        mSharedPreferences.registerOnSharedPreferenceChangeListener(PrefsActivity.this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch(key){
            case "appthemepref":
                setupThemePrefs();
            case "userThemes":
                themeChanged = true;
                break;
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(PrefsActivity.this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            isfromappview = !intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID);
            if (!isfromappview) {
                //mFirstTimeSetup = extras.getInt("firsttimeconfig", 1);
                mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            }
        }
        mRefreshrate = mSharedPreferences.getString(getString(R.string.refresh_rate_pref), "43200000");
        mTitleFontSize = mSharedPreferences.getString(getString(R.string.title_font_pref), "16");
        /*mTitleFontColor = mSharedPreferences.getString(getString(R.string.title_color_pref), "0");*/
        setupThemePrefs();

        mMailRefresh = mSharedPreferences.getString(getString(R.string.background_mail_pref), "43200000");

        // set themes list
        LinkedHashMap<String, String> themeList = global.mThemeManager.getThemeList(ThemeManager.LISTMODE_ALL);
        themePref.setEntries(themeList.values().toArray(new CharSequence[themeList.values().size()]));
        themePref.setEntryValues(themeList.keySet().toArray(new CharSequence[themeList.keySet().size()]));

        //Toast.makeText(this, "Press the back button to save settings", Toast.LENGTH_SHORT).show();
    }

    private void setupThemePrefs(){
        mAppTheme = mSharedPreferences.getString(getString(R.string.app_theme_pref), "reddit_classic");
        if (global.mThemeManager.isThemeEditable(mAppTheme)){
            appearanceCat.addPreference(themeEditorButton);
        } else {
            appearanceCat.removePreference(themeEditorButton);
        }
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
        // check if theme or style has changed and update if needed
        if (themeChanged || !mTitleFontSize.equals(mSharedPreferences.getString(getString(R.string.title_font_pref), "16"))) {
            // if we are returning to app view,set the result intent, indicating a theme update is needed
            if (isfromappview) {
                Intent intent = new Intent();
                intent.putExtra("themeupdate", true);
                setResult(3, intent);
            } else {
                updateWidget();
            }
        }

        finish();
    }

    private void updateWidget() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(PrefsActivity.this);
        int[] appWidgetIds = mgr.getAppWidgetIds(new ComponentName(PrefsActivity.this, WidgetProvider.class));
        WidgetProvider.updateAppWidgets(PrefsActivity.this, mgr, appWidgetIds);
        Reddinator global = ((Reddinator) PrefsActivity.this.getApplicationContext());
        if (global != null) {
            global.setRefreshView();
        }
        mgr.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.listview);
    }

}
