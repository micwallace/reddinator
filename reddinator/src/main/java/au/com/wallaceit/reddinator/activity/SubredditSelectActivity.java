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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.IconTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.ui.SimpleTabsAdapter;
import au.com.wallaceit.reddinator.ui.SimpleTabsWidget;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.service.WidgetProvider;
import au.com.wallaceit.reddinator.ui.SubAutoCompleteAdapter;

public class SubredditSelectActivity extends Activity {
    private ArrayList<String> subredditList;
    private ArrayAdapter<String> subsAdapter;
    private MyMultisAdapter mMultiAdapter;
    private SharedPreferences mSharedPreferences;
    private Reddinator global;
    private Button sortBtn;
    private boolean widgetFirstTimeSetup = false;
    private boolean needsThemeUpdate = false;
    private boolean needsFeedUpdate = false;
    private boolean needsFeedViewUpdate = false;
    private int mAppWidgetId;
    private SimpleTabsWidget tabs;
    private Button refreshButton;
    private Button addButton;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.subredditselect);

        // load personal list from saved prefereces, if null use default and save
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(SubredditSelectActivity.this);
        global = ((Reddinator) SubredditSelectActivity.this.getApplicationContext());

        // get subreddit list and set adapter
        subredditList = global.getSubredditManager().getSubredditNames();
        subsAdapter = new MySubredditsAdapter(this, subredditList);
        ListView subListView = (ListView) findViewById(R.id.sublist);
        subListView.setAdapter(subsAdapter);
        subListView.setTextFilterEnabled(true);
        subListView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                String subreddit = ((TextView) view.findViewById(R.id.subreddit_name)).getText().toString();
                global.getSubredditManager().setFeedSubreddit(mAppWidgetId, subreddit);
                updateFeedAndFinish();
                //System.out.println(sreddit+" selected");
            }
        });
        subsAdapter.sort(new Comparator<String>() {
            @Override
            public int compare(String s, String t1) {
                return s.compareToIgnoreCase(t1);
            }
        });
        // get multi list and set adapter
        mMultiAdapter = new MyMultisAdapter(this);
        ListView multiListView = (ListView) findViewById(R.id.multilist);
        multiListView.setAdapter(mMultiAdapter);
        multiListView.setTextFilterEnabled(true);
        multiListView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                if (position==mMultiAdapter.getCount()-1){
                    LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_multi_add, parent, false);
                    final EditText name = (EditText) layout.findViewById(R.id.new_multi_name);
                    AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
                    builder.setTitle("Create A Multi").setView(layout)
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (name.getText().toString().equals("")){
                                Toast.makeText(SubredditSelectActivity.this, "Please enter a name for the multi", Toast.LENGTH_LONG).show();
                                return;
                            }
                            new SubscriptionEditTask(SubscriptionEditTask.ACTION_MULTI_CREATE).execute(name.getText().toString());
                            dialogInterface.dismiss();
                        }
                    }).show();
                } else {
                    JSONObject multiObj = mMultiAdapter.getItem(position);
                    try {
                        String name = multiObj.getString("display_name");
                        String path = multiObj.getString("path");
                        global.getSubredditManager().setFeed(mAppWidgetId, name, path, true);
                        updateFeedAndFinish();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(SubredditSelectActivity.this, "Error setting multi.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                mAppWidgetId = 0; // Id of 4 zeros indicates its the app view, not a widget, that is being updated
            } else {
                String action = getIntent().getAction();
                widgetFirstTimeSetup = action!=null && action.equals("android.appwidget.action.APPWIDGET_CONFIGURE");
            }
        } else {
            mAppWidgetId = 0;
        }

        final ViewPager pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(new SimpleTabsAdapter(new String[]{"My Subreddits", "My Multis"}, new int[]{R.id.sublist, R.id.multilist}, SubredditSelectActivity.this, null));

        LinearLayout tabsLayout = (LinearLayout) findViewById(R.id.tab_widget);
        tabs = new SimpleTabsWidget(SubredditSelectActivity.this, tabsLayout);
        tabs.setViewPager(pager);

        addButton = (Button) findViewById(R.id.addsrbutton);
        addButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent intent = new Intent(SubredditSelectActivity.this, ViewAllSubredditsActivity.class);
                startActivityForResult(intent, 1);
            }
        });
        refreshButton = (Button) findViewById(R.id.refreshloginbutton);
        refreshButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (global.mRedditData.isLoggedIn()) {
                    if (pager.getCurrentItem()==1) {
                        refreshMultireddits();
                    } else {
                        refreshSubreddits();
                    }
                } else {
                    global.mRedditData.initiateLogin(SubredditSelectActivity.this);
                }
            }
        });
        // sort button
        sortBtn = (Button) findViewById(R.id.sortselect);
        String sortTxt = "Sort:  " + mSharedPreferences.getString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), "hot");
        sortBtn.setText(sortTxt);
        sortBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                showSortDialog();
            }
        });

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // set theme colors
        setThemeColors();

        Reddinator.doShowWelcomeDialog(SubredditSelectActivity.this);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setThemeColors(){
        ThemeManager.Theme theme = global.mThemeManager.getActiveTheme("appthemepref");
        int headerColor = Color.parseColor(theme.getValue("header_color"));
        findViewById(R.id.srtoolbar).setBackgroundColor(headerColor);
        sortBtn.getBackground().setColorFilter(headerColor, PorterDuff.Mode.ADD);
        addButton.getBackground().setColorFilter(headerColor, PorterDuff.Mode.ADD);
        refreshButton.getBackground().setColorFilter(headerColor, PorterDuff.Mode.ADD);
        tabs.setBackgroundColor(headerColor);
        tabs.setInidicatorColor(Color.parseColor(theme.getValue("tab_indicator")));
        tabs.setTextColor(Color.parseColor(theme.getValue("header_text")));
        int buttonIconColor = Color.parseColor("#666666");
        refreshButton.setTextColor(buttonIconColor);
        addButton.setTextColor(buttonIconColor);
        sortBtn.setCompoundDrawables(new IconDrawable(this, Iconify.IconValue.fa_sort).color(buttonIconColor).sizeDp(24), null, null, null);
        if (global.mRedditData.isLoggedIn()) {
            refreshButton.setCompoundDrawables(new IconDrawable(SubredditSelectActivity.this, Iconify.IconValue.fa_refresh).color(buttonIconColor).sizeDp(24), null, null, null);
            refreshButton.setText(R.string.refresh);
        } else {
            refreshButton.setCompoundDrawables(new IconDrawable(SubredditSelectActivity.this, Iconify.IconValue.fa_key).color(buttonIconColor).sizeDp(24), null, null, null);
            refreshButton.setText(R.string.login);
        }
        refreshButton.setCompoundDrawablePadding(6);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==1 && data!=null) {
            try {
                JSONObject subreddit = new JSONObject(data.getStringExtra("subredditObj"));
                String name = subreddit.getString("display_name");
                switch (resultCode) {
                    case ViewAllSubredditsActivity.RESULT_ADD_SUBREDDIT:
                        if (global.mRedditData.isLoggedIn() && (!name.equals("Front Page") && !name.equals("all"))) {
                            new SubscriptionEditTask(SubscriptionEditTask.ACTION_SUBSCRIBE).execute(subreddit);
                        } else {
                            global.getSubredditManager().addSubreddit(subreddit);
                            subredditList.add(name);
                            refreshSubredditsList();
                        }
                        break;
                    case ViewAllSubredditsActivity.RESULT_SET_SUBREDDIT:
                        global.getSubredditManager().setFeedSubreddit(mAppWidgetId, name);
                        updateFeedAndFinish();
                        break;

                    case ViewAllSubredditsActivity.RESULT_ADD_TO_MULTI:
                        new SubscriptionEditTask(SubscriptionEditTask.ACTION_MULTI_SUB_ADD).execute(data.getStringExtra("multipath"), name);
                        break;
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(SubredditSelectActivity.this, "Error reading subreddit data", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode==2 && resultCode==3){
            needsThemeUpdate = true;
            setThemeColors();
        }
    }

    private void updateFeedAndFinish() {
        if (widgetFirstTimeSetup) {
            finishWidgetSetup();
            return;
        }
        if (mAppWidgetId != 0) {
            // refresh widget and close activity (NOTE: put in function)
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelectActivity.this);
            RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);
            views.setTextViewText(R.id.subreddittxt, global.getSubredditManager().getCurrentFeedName(mAppWidgetId));
            views.setViewVisibility(R.id.srloader, View.VISIBLE);
            views.setViewVisibility(R.id.erroricon, View.INVISIBLE);
            // bypass cache if service not loaded
            global.setBypassCache(true);
            appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, views);
            appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
        } else {
            Intent intent = new Intent();
            intent.putExtra("themeupdate", needsThemeUpdate);
            setResult(2, intent); // update feed prefs + reload feed
        }
        finish();
    }

    private void finishWidgetSetup(){
        // for first time setup, widget provider receives this intent in onWidgetOptionsChanged();
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    // save changes on back press
    public void onBackPressed() {
        if (widgetFirstTimeSetup) {
            finishWidgetSetup();
            return;
        }
        // check if sort has changed
        if (needsFeedUpdate || needsFeedViewUpdate || needsThemeUpdate) {
            if (mAppWidgetId != 0) {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelectActivity.this);
                RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);
                views.setViewVisibility(R.id.srloader, View.VISIBLE);
                views.setViewVisibility(R.id.erroricon, View.INVISIBLE);
                // bypass the cached entrys only if the sorting preference has changed
                if (needsFeedUpdate) {
                    global.setBypassCache(true);
                } else {
                    global.setRefreshView();
                }
                if (needsThemeUpdate){
                    WidgetProvider.updateAppWidgets(SubredditSelectActivity.this, appWidgetManager, new int[]{mAppWidgetId});
                } else {
                    appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, views);
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
            } else {
                Intent intent = new Intent();
                intent.putExtra("themeupdate", needsThemeUpdate);
                if (needsFeedUpdate) {
                    setResult(2, intent); // reload feed and prefs
                } else {
                    setResult(1, intent); // tells main activity to update feed prefs
                }
                if (needsThemeUpdate){
                    global.setRefreshView();
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelectActivity.this);
                    int[] widgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(SubredditSelectActivity.this, WidgetProvider.class));
                    WidgetProvider.updateAppWidgets(SubredditSelectActivity.this, appWidgetManager, widgetIds);
                    appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.listview);
                }
            }
        } else {
            setResult(0);
        }
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.subreddit_select_menu, menu);
        // set options menu view
        int iconColor;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            iconColor = Color.parseColor("#8F8F8F");
        } else {
            iconColor = Color.parseColor("#DBDBDB");
        }
        (menu.findItem(R.id.menu_submit)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_pencil).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_feedprefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_list_alt).color(iconColor).actionBarSize());
        if (mAppWidgetId==0) {
            (menu.findItem(R.id.menu_widgettheme)).setVisible(false);
        }
        (menu.findItem(R.id.menu_widgettheme)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_paint_brush).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_thememanager)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_cogs).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_account)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_reddit_square).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_saved)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_save).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_prefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_wrench).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_about)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_info_circle).color(iconColor).actionBarSize());

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu)
    {
        if(featureId == Window.FEATURE_ACTION_BAR && menu != null){
            if(menu.getClass().getSimpleName().equals("MenuBuilder")){
                try{
                    Method m = menu.getClass().getDeclaredMethod(
                            "setOptionalIconsVisible", Boolean.TYPE);
                    m.setAccessible(true);
                    m.invoke(menu, true);
                }
                catch(NoSuchMethodException e){
                    System.out.println("Could not display action icons in menu");
                }
                catch(Exception e){
                    throw new RuntimeException(e);
                }
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;

            case R.id.menu_feedprefs:
                showFeedPrefsDialog();
                break;

            case R.id.menu_widgettheme:
                showWidgetThemeDialog();
                break;

            case R.id.menu_thememanager:
                Intent intent = new Intent(SubredditSelectActivity.this, ThemesActivity.class);
                startActivityForResult(intent, 2);
                break;

            case R.id.menu_account:
                Intent accnIntent = new Intent(SubredditSelectActivity.this, WebViewActivity.class);
                accnIntent.putExtra("url", global.getDefaultMobileSite()+"/user/"+global.mRedditData.getUsername()+"/");
                startActivity(accnIntent);
                break;

            case R.id.menu_saved:
                Intent savedIntent = new Intent(SubredditSelectActivity.this, WebViewActivity.class);
                savedIntent.putExtra("url", global.getDefaultMobileSite()+"/user/"+global.mRedditData.getUsername()+"/saved");
                startActivity(savedIntent);
                break;

            case R.id.menu_submit:
                Intent submitIntent = new Intent(SubredditSelectActivity.this, SubmitActivity.class);
                startActivity(submitIntent);
                break;

            case R.id.menu_prefs:
                Intent intent2 = new Intent(SubredditSelectActivity.this, PrefsActivity.class);
                startActivityForResult(intent2, 2);
                break;

            case R.id.menu_about:
                Reddinator.showInfoDialog(this, true);
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // show sort select dialog
    private void showSortDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        builder.setTitle("Pick a sort, any sort");
        builder.setItems(R.array.reddit_sorts, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Editor prefsedit = mSharedPreferences.edit();
                String sort = "hot"; // default if fails
                // find index
                switch (which) {
                    case 0:
                        sort = "hot";
                        break;
                    case 1:
                        sort = "new";
                        break;
                    case 2:
                        sort = "rising";
                        break;
                    case 3:
                        sort = "controversial";
                        break;
                    case 4:
                        sort = "top";
                        break;
                }
                prefsedit.putString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), sort);
                prefsedit.apply();
                // set new text in button
                String sorttxt = "Sort:  " + sort;
                sortBtn.setText(sorttxt);
                needsFeedUpdate = true; // mark feed for updating
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void showFeedPrefsDialog(){
        final CharSequence[] names = {"Thumbnails", "Thumbs On Top", "Hide Post Info"};
        final boolean[] initvalue = {mSharedPreferences.getBoolean("thumbnails-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), true), mSharedPreferences.getBoolean("bigthumbs-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false), mSharedPreferences.getBoolean("hideinf-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false)};
        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        builder.setTitle("Feed Options");
        builder.setMultiChoiceItems(names, initvalue, new DialogInterface.OnMultiChoiceClickListener() {
            public void onClick(DialogInterface dialogInterface, int item, boolean state) {
                Editor prefsedit = mSharedPreferences.edit();
                switch (item) {
                    case 0:
                        prefsedit.putBoolean("thumbnails-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), state);
                        break;
                    case 1:
                        prefsedit.putBoolean("bigthumbs-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), state);
                        break;
                    case 2:
                        prefsedit.putBoolean("hideinf-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), state);
                        break;
                }
                prefsedit.apply();
                needsFeedViewUpdate = true;
            }
        });
        builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }

    private void showWidgetThemeDialog(){

        // set themes list
        LinkedHashMap<String, String> themeList = global.mThemeManager.getThemeList(ThemeManager.LISTMODE_ALL);
        themeList.put("app_select", "Use App theme");
        final String[] keys = themeList.keySet().toArray(new String[themeList.keySet().size()]);
        String curTheme = mSharedPreferences.getString("widgettheme-"+mAppWidgetId, "app_select");
        int curIndex = 0;
        for (int i=0; i<keys.length; i++){
            if (keys[i].equals(curTheme)) {
                curIndex = i;
                break;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Widget Theme")
        .setSingleChoiceItems(themeList.values().toArray(new String[themeList.values().size()]), curIndex,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    needsThemeUpdate = true;
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    editor.putString("widgettheme-" + mAppWidgetId, keys[i]);
                    System.out.println(keys[i]);
                    editor.apply();
                    dialogInterface.cancel();
                }
            }
        ).setPositiveButton("Close", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        }).show();
    }

    // import personal subreddits
    private void refreshSubreddits() {

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        final ProgressDialog sdialog = ProgressDialog.show(SubredditSelectActivity.this, "Refreshing Subreddits", "One moment...", true);
        Thread t = new Thread() {
            public void run() {

                final int listLength;
                try {
                    listLength = global.loadAccountSubreddits();
                } catch (final RedditData.RedditApiException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            sdialog.dismiss();
                            // check login required
                            if (e.isAuthError())
                                global.mRedditData.initiateLogin(SubredditSelectActivity.this);
                            // show error
                            Toast.makeText(SubredditSelectActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        sdialog.dismiss();
                        if (listLength==0) {
                            Toast.makeText(SubredditSelectActivity.this, "No subscriptions in your account, \nSuscribe to some subreddits", Toast.LENGTH_LONG).show();
                        }
                        refreshSubredditsList();
                    }
                });
            }
        };
        t.start();
    }

    private void refreshMultireddits() {

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        final ProgressDialog sdialog = ProgressDialog.show(SubredditSelectActivity.this, "Refreshing Multis", "One moment...", true);
        Thread t = new Thread() {
            public void run() {

                final int listLength;
                try {
                    listLength = global.loadAccountMultis();
                } catch (final RedditData.RedditApiException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            sdialog.dismiss();
                            // check login required
                            if (e.isAuthError())
                                global.mRedditData.initiateLogin(SubredditSelectActivity.this);
                            // show error
                            Toast.makeText(SubredditSelectActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        sdialog.dismiss();
                        if (listLength == 0) {
                            Toast.makeText(SubredditSelectActivity.this, "No multis in your account \nClick the add multi button to create some", Toast.LENGTH_LONG).show();
                        }
                        mMultiAdapter.refreshMultis();
                    }
                });
            }
        };
        t.start();
    }

    private void refreshSubredditsList(){
        subredditList = global.getSubredditManager().getSubredditNames();
        subsAdapter.clear();
        subsAdapter.addAll(subredditList);
        subsAdapter.notifyDataSetChanged();
        subsAdapter.sort(new Comparator<String>() {
            @Override
            public int compare(String s, String t1) {
                return s.compareToIgnoreCase(t1);
            }
        });
    }

    // list adapter
    class MySubredditsAdapter extends ArrayAdapter<String> {
        private LayoutInflater inflater;

        public MySubredditsAdapter(Context context, ArrayList<String> objects) {
            super(context, R.layout.myredditlistitem, R.id.subreddit_name, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            super.getView(position, convertView, parent);
            ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                // inflate new view
                convertView = inflater.inflate(R.layout.myredditlistitem, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(R.id.subreddit_name);
                viewHolder.deleteIcon = (IconTextView) convertView.findViewById(R.id.subreddit_delete_btn);
                viewHolder.filterIcon = (IconTextView) convertView.findViewById(R.id.subreddit_filter_btn);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            // setup the row
            viewHolder.name.setText(getItem(position));
            viewHolder.deleteIcon.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String sreddit = ((TextView) ((View) v.getParent()).findViewById(R.id.subreddit_name)).getText().toString();
                    if (global.mRedditData.isLoggedIn() && (!sreddit.equals("Front Page") && !sreddit.equals("all"))) {
                        new AlertDialog.Builder(SubredditSelectActivity.this).setTitle("Unsubscribe")
                                .setMessage("Are you sure you want to unsubscribe from " + sreddit + "?")
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                    }
                                }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                new SubscriptionEditTask(SubscriptionEditTask.ACTION_UNSUBSCRIBE).execute(sreddit);
                            }
                        }).show();
                    } else {
                        global.getSubredditManager().removeSubreddit(sreddit);
                        subredditList.remove(sreddit);
                        refreshSubredditsList();
                    }
                }
            });
            if (getItem(position).equals("all")){
                viewHolder.filterIcon.setVisibility(View.VISIBLE);
                viewHolder.filterIcon.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showFilterEditDialog();
                    }
                });
            } else{
                viewHolder.filterIcon.setVisibility(View.GONE);
            }

            convertView.setTag(viewHolder);

            return convertView;
        }

        class ViewHolder {
            TextView name;
            IconTextView deleteIcon;
            IconTextView filterIcon;
        }
    }

    class MyMultisAdapter extends BaseAdapter {
        private LayoutInflater inflater;
        private ArrayList<JSONObject> multiList;

        public MyMultisAdapter(Context context) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            refreshMultis();
        }

        public void refreshMultis(){
            multiList = global.getSubredditManager().getMultiList();
            Collections.sort(multiList, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject s, JSONObject t1) {
                    try {
                        return s.getString("display_name").compareToIgnoreCase(t1.getString("display_name"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
            });
            this.notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            //super.getView(position, convertView, parent);
            ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                // inflate new view
                viewHolder = new ViewHolder();
                if (position==multiList.size()) {
                    convertView = inflater.inflate(R.layout.mymultilistitem_add, parent, false);
                } else {
                    convertView = inflater.inflate(R.layout.mymultilistitem, parent, false);
                    viewHolder.name = (TextView) convertView.findViewById(R.id.multireddit_name);
                    viewHolder.deleteIcon = (IconTextView) convertView.findViewById(R.id.multi_delete_btn);
                    viewHolder.editIcon = (IconTextView) convertView.findViewById(R.id.multi_edit_btn);
                }
                //viewHolder.subsIcon = (IconTextView) convertView.findViewById(R.id.multi_editsubs_btn);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            if (position<multiList.size()) {
                JSONObject multiObj = getItem(position);
                final String displayName, path;
                final boolean canEdit;
                try {
                    displayName = multiObj.getString("display_name");
                    path = multiObj.getString("path");
                    canEdit = multiObj.getBoolean("can_edit");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return convertView;
                }
                // setup the row
                viewHolder.name.setText(displayName);
                viewHolder.deleteIcon.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (canEdit) {
                            showMultiDeleteDialog(path);
                        } else {
                            global.getSubredditManager().removeMulti(path);
                            subsAdapter.notifyDataSetChanged();
                        }
                    }
                });
                if (canEdit) {
                    viewHolder.editIcon.setVisibility(View.VISIBLE);
                    //viewHolder.subsIcon.setVisibility(View.VISIBLE);
                    viewHolder.editIcon.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            showMultiEditDialog(path);
                        }
                    });
                    // will use for viewing subreddits list when not editable; later!
                    /*viewHolder.subsIcon.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            showMultiEditDialog(path);
                        }
                    });*/
                }
            }

            convertView.setTag(viewHolder);

            return convertView;
        }

        @Override
        public int getCount() {
            return multiList.size()+1;
        }

        @Override
        public int getViewTypeCount(){
            return 2;
        }

        @Override
        public int getItemViewType(int position){
            if (position==multiList.size())
                return 1;
            return 0;
        }

        public JSONObject getItem(int position){
           return multiList.get(position);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        class ViewHolder {
            TextView name;
            IconTextView deleteIcon;
            IconTextView editIcon;
            //IconTextView subsIcon;
        }
    }

    private void showMultiDeleteDialog(final String multiPath){
        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        builder.setTitle("Delete Multi").setMessage("Are you sure you want to delete this multi from your account?");
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                new SubscriptionEditTask(SubscriptionEditTask.ACTION_MULTI_DELETE).execute(multiPath);
            }
        }).show();
    }

    private void showMultiRenameDialog(final String multiPath){
        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        final EditText nameInput = new EditText(SubredditSelectActivity.this);
        nameInput.setHint("new multi name");
        builder.setTitle("Rename Multi").setView(nameInput)
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                new SubscriptionEditTask(SubscriptionEditTask.ACTION_MULTI_RENAME).execute(multiPath, nameInput.getText().toString().replaceAll("\\s+",""));
            }
        }).show();
    }

    private SubsListAdapter multiSubsAdapter;
    private AlertDialog multiDialog;
    private TextView multiName;
    private void showMultiEditDialog(final String multiPath){
        JSONObject multiObj = global.getSubredditManager().getMultiData(multiPath);

        @SuppressLint("InflateParams")
        LinearLayout dialogView =  (LinearLayout)  getLayoutInflater().inflate(R.layout.dialog_multi_edit, null); // passing null okay for dialog
        final Button saveButton = (Button) dialogView.findViewById(R.id.multi_save_button);
        final Button renameButton = (Button) dialogView.findViewById(R.id.multi_rename_button);
        multiName = (TextView) dialogView.findViewById(R.id.multi_pname);
        final EditText displayName = (EditText) dialogView.findViewById(R.id.multi_name);
        final EditText description = (EditText) dialogView.findViewById(R.id.multi_description);
        final EditText color = (EditText) dialogView.findViewById(R.id.multi_color);
        final Spinner icon = (Spinner) dialogView.findViewById(R.id.multi_icon);
        final Spinner visibility = (Spinner) dialogView.findViewById(R.id.multi_visibility);
        final Spinner weighting = (Spinner) dialogView.findViewById(R.id.multi_weighting);

        ArrayAdapter<CharSequence> iconAdapter = ArrayAdapter.createFromResource(SubredditSelectActivity.this, R.array.multi_icons, android.R.layout.simple_spinner_item);
        iconAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        icon.setAdapter(iconAdapter);
        ArrayAdapter<CharSequence> visibilityAdapter = ArrayAdapter.createFromResource(SubredditSelectActivity.this, R.array.multi_visibility, android.R.layout.simple_spinner_item);
        visibilityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        visibility.setAdapter(visibilityAdapter);
        ArrayAdapter<CharSequence> weightsAdapter = ArrayAdapter.createFromResource(SubredditSelectActivity.this, R.array.multi_weights, android.R.layout.simple_spinner_item);
        weightsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        weighting.setAdapter(weightsAdapter);

        try {
            multiName.setText(multiObj.getString("name"));
            displayName.setText(multiObj.getString("display_name"));
            description.setText(multiObj.getString("description_md"));
            color.setText(multiObj.getString("key_color"));
            String iconName = multiObj.getString("icon_name");
            icon.setSelection(iconAdapter.getPosition(iconName.equals("")?"none":iconName));
            visibility.setSelection(iconAdapter.getPosition(multiObj.getString("visibility")));
            weighting.setSelection(iconAdapter.getPosition(multiObj.getString("weighting_scheme")));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        ViewPager pager = (ViewPager) dialogView.findViewById(R.id.multi_pager);
        LinearLayout tabsWidget = (LinearLayout) dialogView.findViewById(R.id.multi_tab_widget);
        pager.setAdapter(new SimpleTabsAdapter(new String[]{"Subreddits", "Settings"}, new int[]{R.id.multi_subreddits, R.id.multi_settings}, SubredditSelectActivity.this, dialogView));
        SimpleTabsWidget simpleTabsWidget = new SimpleTabsWidget(SubredditSelectActivity.this, tabsWidget);
        simpleTabsWidget.setViewPager(pager);
        ThemeManager.Theme theme = global.mThemeManager.getActiveTheme("appthemepref");
        int headerColor = Color.parseColor(theme.getValue("header_color"));
        int headerText = Color.parseColor(theme.getValue("header_text"));
        simpleTabsWidget.setBackgroundColor(headerColor);
        simpleTabsWidget.setTextColor(headerText);
        simpleTabsWidget.setInidicatorColor(Color.parseColor(theme.getValue("tab_indicator")));

        ListView subList = (ListView) dialogView.findViewById(R.id.multi_subredditList);
        multiSubsAdapter = new SubsListAdapter(SubredditSelectActivity.this, multiPath);
        subList.setAdapter(multiSubsAdapter);
        renameButton.getBackground().setColorFilter(headerColor, PorterDuff.Mode.MULTIPLY);
        renameButton.setTextColor(headerText);
        renameButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showMultiRenameDialog(multiPath);
            }
        });

                saveButton.getBackground().setColorFilter(headerColor, PorterDuff.Mode.MULTIPLY);
        saveButton.setTextColor(headerText);
        saveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                System.out.println("Save multi");
                JSONObject multiObj = new JSONObject();
                try {
                    multiObj.put("decription_md", description.getText().toString());
                    multiObj.put("display_name", displayName.getText().toString());
                    multiObj.put("icon_name", icon.getSelectedItem().toString().equals("none")?"":icon.getSelectedItem().toString());
                    multiObj.put("key_color", color.getText().toString());
                    multiObj.put("subreddits", global.getSubredditManager().getMultiData(multiPath).getJSONArray("subreddits"));
                    multiObj.put("visibility", visibility.getSelectedItem().toString());
                    multiObj.put("weighting_scheme", weighting.getSelectedItem().toString());

                    new SubscriptionEditTask(SubscriptionEditTask.ACTION_MULTI_EDIT).execute(multiPath, multiObj);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);

        multiDialog = builder.setView(dialogView).show();
    }

    private void showFilterEditDialog(){

        @SuppressLint("InflateParams")
        LinearLayout dialogView =  (LinearLayout)  getLayoutInflater().inflate(R.layout.dialog_filter, null); // passing null okay for dialog

        ThemeManager.Theme theme = global.mThemeManager.getActiveTheme("appthemepref");
        int headerColor = Color.parseColor(theme.getValue("header_color"));
        int headerText = Color.parseColor(theme.getValue("header_text"));
        dialogView.findViewById(R.id.filter_header).setBackgroundColor(headerColor);
        ((TextView) dialogView.findViewById(R.id.filter_headert1)).setTextColor(headerText);
        ((TextView) dialogView.findViewById(R.id.filter_headert2)).setTextColor(headerText);

        ListView subList = (ListView) dialogView.findViewById(R.id.filter_subredditList);
        final SubsListAdapter filterSubsAdapter = new SubsListAdapter(SubredditSelectActivity.this, null);
        subList.setAdapter(filterSubsAdapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        builder.setView(dialogView)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        global.getSubredditManager().setAllFilter(filterSubsAdapter.getSubsList());
                        needsFeedUpdate = true; // mark feed for updating
                    }
                }).show().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    class SubsListAdapter extends BaseAdapter {
        private final int MODE_MULTI = 1;
        private int mode = 0;
        private LayoutInflater inflater;
        private ArrayList<String> subsList;
        private String multiPath;
        private SubAutoCompleteAdapter autoCompleteAdapter;

        public SubsListAdapter(Context context, String multiPath) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            autoCompleteAdapter = new SubAutoCompleteAdapter(context, R.layout.autocomplete_list_item);
            if (multiPath!=null) {
                this.multiPath = multiPath;
                mode = MODE_MULTI;
            }
            refreshList();
        }

        public void refreshList(){
            if (mode==MODE_MULTI) {
                subsList = global.getSubredditManager().getMultiSubreddits(multiPath);
            } else {
                subsList = global.getSubredditManager().getAllFilter();
            }
            Collections.sort(subsList, new Comparator<String>() {
                @Override
                public int compare(String s, String t1) {
                    return s.compareToIgnoreCase(t1);
                }
            });
            this.notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            //super.getView(position, convertView, parent);
            final ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                // inflate new view
                if (position== subsList.size()) {
                    convertView = inflater.inflate(R.layout.multi_sublist_add_item, parent, false);
                    viewHolder = new ViewHolder();
                    viewHolder.nameInput = (AutoCompleteTextView) convertView.findViewById(R.id.subreddit_name);
                    viewHolder.addIcon = (IconTextView) convertView.findViewById(R.id.multi_sub_add);
                    viewHolder.searchIcon = (IconTextView) convertView.findViewById(R.id.multi_sub_search);
                } else {
                    convertView = inflater.inflate(R.layout.multi_sublist_item, parent, false);
                    viewHolder = new ViewHolder();
                    viewHolder.name = (TextView) convertView.findViewById(R.id.subreddit_name);
                    viewHolder.removeIcon = (IconTextView) convertView.findViewById(R.id.multi_sub_remove);
                }
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            // setup the row
            if (position== subsList.size()) {
                viewHolder.nameInput.setAdapter(autoCompleteAdapter);
                viewHolder.nameInput.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        String subreddit = viewHolder.nameInput.getText().toString();
                        performAdd(subreddit);
                        viewHolder.nameInput.setText("");
                    }
                });
                viewHolder.addIcon.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String subreddit = viewHolder.nameInput.getText().toString();
                        if (subreddit.equals("")){
                            Toast.makeText(SubredditSelectActivity.this, "Please enter a subreddit name", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        performAdd(subreddit);
                        viewHolder.nameInput.setText("");
                    }
                });
                if (mode==MODE_MULTI) {
                    viewHolder.searchIcon.setVisibility(View.VISIBLE);
                    viewHolder.searchIcon.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent intent = new Intent(SubredditSelectActivity.this, ViewAllSubredditsActivity.class);
                            intent.setAction(ViewAllSubredditsActivity.ACTION_ADD_MULTI_SUB);
                            intent.putExtra("multipath", multiPath);
                            startActivityForResult(intent, 1);
                        }
                    });
                } else {
                    // search isn't implemented here yet, you could if you want to but I don't see much point
                    viewHolder.searchIcon.setVisibility(View.GONE);
                }
            } else {
                final String subreddit = getItem(position);
                viewHolder.name.setText(subreddit);
                viewHolder.removeIcon.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        performRemove(subreddit);
                    }
                });
            }
            convertView.setTag(viewHolder);

            return convertView;
        }

        private ArrayList<String> getSubsList(){
            return subsList;
        }

        private void performAdd(String subreddit){
            System.out.println("Adding Sub: " + subreddit);
            if (mode==MODE_MULTI) {
                new SubscriptionEditTask(SubscriptionEditTask.ACTION_MULTI_SUB_ADD).execute(multiPath, subreddit);
            } else {
                subsList.add(subreddit);
                notifyDataSetChanged();
            }
        }

        private void performRemove(String subreddit){
            System.out.println("Removing Sub: "+subreddit);
            if (mode==MODE_MULTI) {
                new SubscriptionEditTask(SubscriptionEditTask.ACTION_MULTI_SUB_REMOVE).execute(multiPath, subreddit);
            } else {
                subsList.remove(subreddit);
                notifyDataSetChanged();
            }
        }

        @Override
        public int getViewTypeCount(){
            return 2;
        }

        @Override
        public int getItemViewType(int position){
            if (position== subsList.size())
                return 1;
            return 0;
        }

        @Override
        public int getCount(){
            return subsList.size()+1;
        }

        @Override
        public String getItem(int i) {
            return subsList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        class ViewHolder {
            TextView name;
            AutoCompleteTextView nameInput;
            IconTextView addIcon;
            IconTextView removeIcon;
            IconTextView searchIcon;
        }

    }

    class SubscriptionEditTask extends AsyncTask<Object, Long, Boolean> {
        public static final int ACTION_MULTI_COPY = 0;
        public static final int ACTION_MULTI_CREATE = 1;
        public static final int ACTION_MULTI_EDIT = 2;
        public static final int ACTION_MULTI_SUB_ADD = 3;
        public static final int ACTION_MULTI_SUB_REMOVE = 4;
        public static final int ACTION_MULTI_DELETE = 5;
        public static final int ACTION_MULTI_RENAME = 6;
        public static final int ACTION_SUBSCRIBE = 7;
        public static final int ACTION_UNSUBSCRIBE = 8;
        private JSONObject result;
        private Exception exception;
        private int action;
        private Object[] params;
        ProgressDialog progressDialog;

        public SubscriptionEditTask(int action){
            String loadingMessage = "";
            switch (action) {
                case ACTION_SUBSCRIBE:
                    loadingMessage = "Subscribing...";
                    break;
                case ACTION_UNSUBSCRIBE:
                    loadingMessage = "Unsubscribing...";
                    break;
                case ACTION_MULTI_COPY:
                    loadingMessage = "Copying Multi...";
                    break;
                case ACTION_MULTI_CREATE:
                    loadingMessage = "Creating Multi...";
                    break;
                case ACTION_MULTI_EDIT:
                case ACTION_MULTI_RENAME:
                case ACTION_MULTI_SUB_ADD:
                case ACTION_MULTI_SUB_REMOVE:
                    loadingMessage = "Updating Multi...";
                    break;
                case ACTION_MULTI_DELETE:
                    loadingMessage = "Deleting Multi...";
                    break;
            }
            progressDialog = ProgressDialog.show(SubredditSelectActivity.this, loadingMessage, loadingMessage, true);
            this.action = action;
        }

        @Override
        protected Boolean doInBackground(Object... strParams) {
            this.params = strParams;
            String id;
            try {
                switch (action){
                    case ACTION_SUBSCRIBE:
                        id = ((JSONObject) strParams[0]).getString("name");
                        result = global.mRedditData.subscribe(id, true);
                        break;

                    case ACTION_UNSUBSCRIBE:
                        id = global.getSubredditManager().getSubredditData(strParams[0].toString()).getString("name");
                        result = global.mRedditData.subscribe(id, false);
                        break;

                    case ACTION_MULTI_COPY:
                        result = global.mRedditData.copyMulti(strParams[0].toString(), strParams[1].toString());
                        break;

                    case ACTION_MULTI_CREATE:
                        try {
                            JSONObject multiObj = new JSONObject();
                            multiObj.put("display_name", strParams[0].toString());
                            multiObj.put("decription_md", "");
                            multiObj.put("icon_name", "");
                            multiObj.put("key_color", "#CEE3F8");
                            multiObj.put("subreddits", new JSONArray());
                            multiObj.put("visibility", "private");
                            multiObj.put("weighting_scheme", "classic");
                            result = global.mRedditData.createMulti(strParams[0].toString(), multiObj);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            return false;
                        }
                        break;

                    case ACTION_MULTI_EDIT:
                        result = global.mRedditData.editMulti(strParams[0].toString(), (JSONObject) strParams[1]);
                        break;

                    case ACTION_MULTI_SUB_ADD:
                        result = global.mRedditData.addMultiSubreddit(strParams[0].toString(), strParams[1].toString());
                        break;

                    case ACTION_MULTI_SUB_REMOVE:
                        global.mRedditData.removeMultiSubreddit(strParams[0].toString(), strParams[1].toString());
                        break;

                    case ACTION_MULTI_DELETE:
                        global.mRedditData.deleteMulti(strParams[0].toString());
                        break;

                    case ACTION_MULTI_RENAME:
                        result = global.mRedditData.renameMulti(strParams[0].toString(), strParams[1].toString());
                        break;
                }
                return true;
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                exception = e;
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            progressDialog.dismiss();
            ArrayList<String> subreddits;
            if (result) {
                //if (this.result!=null)
                    //System.out.println("resultData: "+this.result.toString());
                switch (action) {
                    case ACTION_SUBSCRIBE:
                        global.getSubredditManager().addSubreddit((JSONObject) params[0]);
                        try {
                            subredditList.add(0, ((JSONObject) params[0]).getString("display_name"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        refreshSubredditsList();
                        break;
                    case ACTION_UNSUBSCRIBE:
                        global.getSubredditManager().removeSubreddit(params[0].toString());
                        subredditList.remove(params[0].toString());
                        refreshSubredditsList();
                        break;
                    case ACTION_MULTI_COPY:

                        break;
                    case ACTION_MULTI_CREATE:
                        try {
                            if (this.result==null) return;
                            JSONObject multiObj = this.result.getJSONObject("data");
                            String path = multiObj.getString("path");
                            global.getSubredditManager().setMultiData(path, multiObj);
                            showMultiEditDialog(path);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        mMultiAdapter.refreshMultis();
                        break;
                    case ACTION_MULTI_EDIT:
                        try {
                            if (this.result==null) return;
                            JSONObject multiObj = this.result.getJSONObject("data");
                            global.getSubredditManager().setMultiData(params[0].toString(), multiObj);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        multiDialog.dismiss();
                        mMultiAdapter.refreshMultis();
                        break;
                    case ACTION_MULTI_SUB_ADD:
                        subreddits = global.getSubredditManager().getMultiSubreddits(params[0].toString());
                        subreddits.add(params[1].toString());
                        global.getSubredditManager().setMultiSubs(params[0].toString(), subreddits);
                        multiSubsAdapter.refreshList();
                        break;
                    case ACTION_MULTI_SUB_REMOVE:
                        subreddits = global.getSubredditManager().getMultiSubreddits(params[0].toString());
                        subreddits.remove(params[1].toString());
                        global.getSubredditManager().setMultiSubs(params[0].toString(), subreddits);
                        multiSubsAdapter.refreshList();
                        break;
                    case ACTION_MULTI_DELETE:
                        global.getSubredditManager().removeMulti(params[0].toString());
                        mMultiAdapter.refreshMultis();
                        break;
                    case ACTION_MULTI_RENAME:
                        global.getSubredditManager().removeMulti(params[0].toString());
                        try {
                            JSONObject multiObj = this.result.getJSONObject("data");
                            String path = multiObj.getString("path");
                            global.getSubredditManager().setMultiData(path, multiObj);
                            multiName.setText(multiObj.getString("name"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        mMultiAdapter.refreshMultis();
                        break;
                }
            } else {
                // check login required
                if (exception instanceof RedditData.RedditApiException && ((RedditData.RedditApiException)exception).isAuthError())
                    global.mRedditData.initiateLogin(SubredditSelectActivity.this);
                // show error
                Toast.makeText(SubredditSelectActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}
