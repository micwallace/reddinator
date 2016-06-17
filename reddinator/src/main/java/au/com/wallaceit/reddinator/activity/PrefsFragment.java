package au.com.wallaceit.reddinator.activity;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.LinkedHashMap;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.service.MailCheckReceiver;
import au.com.wallaceit.reddinator.service.WidgetProvider;

public class PrefsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    public int mAppWidgetId;
    private SharedPreferences mSharedPreferences;
    private String mRefreshrate = "";
    private String mTitleFontSize = "";
    private String mAppTheme = "";
    private String mMailRefresh = "";
    boolean isfromappview = false;
    private Reddinator global;
    private boolean themeChanged = false;
    private PreferenceCategory appearanceCat;
    private ListPreference themePref;
    private Preference themeEditorButton;
    @Override
    public void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        ((PrefsActivity) getActivity()).getListView().setBackgroundColor(Color.WHITE);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        global = ((Reddinator) getActivity().getApplicationContext());
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String token = mSharedPreferences.getString("oauthtoken", "");
        if (!token.equals("")){
            // Load the account preferences when logged in
            addPreferencesFromResource(R.xml.account_preferences);
            Preference logoutbtn = findPreference("logout");
            final PreferenceCategory accountSettings = (PreferenceCategory) findPreference("account");
            logoutbtn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // clear oauth token and userdata and load default subreddits
                    global.mRedditData.purgeAccountData();
                    global.getSubredditManager().clearMultis();
                    global.getSubredditManager().loadDefaultSubreddits();
                    // remove mail check alarm
                    MailCheckReceiver.setAlarm(getActivity());
                    // remove account prefs
                    getPreferenceScreen().removePreference(accountSettings);
                    Toast.makeText(getActivity(), getResources().getString(R.string.account_disconnected), Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }

        appearanceCat = (PreferenceCategory) findPreference("appearance");

        Preference themeManagerButton = findPreference("theme_manager_button");
        themeManagerButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), ThemesActivity.class);
                startActivity(intent);
                return true;
            }
        });

        themeEditorButton = findPreference("theme_editor_button");
        themeEditorButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), ThemeEditorActivity.class);
                intent.putExtra("themeId", mAppTheme);
                startActivity(intent);
                return true;
            }
        });

        Preference clearFilterButton = findPreference("clear_post_filter");
        if (!global.mRedditData.isLoggedIn()) {
            clearFilterButton.setSummary(getString(R.string.clear_post_filter_summary, global.getSubredditManager().getPostFilterCount()));
            clearFilterButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    global.getSubredditManager().clearPostFilters();
                    Toast.makeText(getActivity(), getString(R.string.clear_post_filter_message), Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        } else {
            clearFilterButton.setSummary(getString(R.string.clear_post_filter_summary_disabled));
            clearFilterButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(getActivity(), AccountActivity.class);
                    intent.setAction(AccountActivity.ACTION_HIDDEN);
                    startActivity(intent);
                    getActivity().finish();
                    return true;
                }
            });
        }

        themePref = (ListPreference) findPreference("appthemepref");

        mSharedPreferences.registerOnSharedPreferenceChangeListener(PrefsFragment.this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch(key){
            case "appthemepref":
                setupThemePrefs();
            case "userThemes":
            case "logoopenpref":
                themeChanged = true;
                break;
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(PrefsFragment.this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent intent = getActivity().getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            isfromappview = !intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID);
            if (!isfromappview) {
                mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            }
        }
        mRefreshrate = mSharedPreferences.getString(getString(R.string.refresh_rate_pref), "43200000");
        mTitleFontSize = mSharedPreferences.getString(getString(R.string.title_font_pref), "16");
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
            WidgetProvider.setUpdateSchedule(getActivity(), false);
        }
        // check if background mail check interval has changed
        if (!mMailRefresh.equals(mSharedPreferences.getString(getString(R.string.background_mail_pref), "43200000"))) {
            //System.out.println("Refresh preference changed, updating alarm");
            MailCheckReceiver.setAlarm(getActivity());
        }
        // check if theme or style has changed and update if needed
        if (themeChanged || !mTitleFontSize.equals(mSharedPreferences.getString(getString(R.string.title_font_pref), "16"))) {
            // if we are returning to app view,set the result intent, indicating a theme update is needed
            if (isfromappview) {
                Intent intent = new Intent();
                intent.putExtra("themeupdate", true);
                getActivity().setResult(3, intent);
            } else {
                updateWidget();
            }
        }

        getActivity().finish();
    }

    private void updateWidget() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(getActivity());
        int[] appWidgetIds = mgr.getAppWidgetIds(new ComponentName(getActivity(), WidgetProvider.class));
        WidgetProvider.updateAppWidgets(getActivity(), mgr, appWidgetIds);
        Reddinator global = ((Reddinator) getActivity().getApplicationContext());
        if (global != null) {
            global.setRefreshView();
        }
        mgr.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.listview);
    }
}