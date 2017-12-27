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
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewPager;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.service.WidgetCommon;
import au.com.wallaceit.reddinator.tasks.LoadRandomTask;
import au.com.wallaceit.reddinator.tasks.SubscriptionEditTask;
import au.com.wallaceit.reddinator.tasks.SyncUserDataTask;
import au.com.wallaceit.reddinator.ui.ActionbarActivity;
import au.com.wallaceit.reddinator.ui.SimpleTabsAdapter;
import au.com.wallaceit.reddinator.ui.SimpleTabsWidget;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.service.WidgetProvider;
import au.com.wallaceit.reddinator.ui.SubAutoCompleteAdapter;

public class SubredditSelectActivity extends ActionbarActivity implements SubscriptionEditTask.Callback, LoadRandomTask.Callback {
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
    private Resources resources;
    private MenuItem messageIcon;
    private boolean isCreated = false;
    private int headerText;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subreddit_select);

        // load personal list from saved prefereces, if null use default and save
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(SubredditSelectActivity.this);
        global = ((Reddinator) SubredditSelectActivity.this.getApplicationContext());
        resources = getResources();

        // get subreddit list and set adapter
        subredditList = global.getSubredditManager().getSubredditNames();
        subsAdapter = new MySubredditsAdapter(this, subredditList);
        final ListView subListView = (ListView) findViewById(R.id.sublist);
        subListView.setAdapter(subsAdapter);
        subListView.setTextFilterEnabled(true);
        subListView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                String subreddit = ((TextView) view.findViewById(R.id.subreddit_name)).getText().toString();
                try {
                    JSONObject subData = global.getSubredditManager().getSubredditData(subreddit);
                    String url = subData.has("url") ? subData.getString("url") : null;
                    global.getSubredditManager().setFeedSubreddit(mAppWidgetId, subreddit, url);
                    updateFeedAndFinish();
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(SubredditSelectActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        subsAdapter.sort(subComparator);

        startupTasks();

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                mAppWidgetId = 0; // Id of zero indicates its the app view, not a widget, that is being updated
            } else {
                String action = getIntent().getAction();
                widgetFirstTimeSetup = action!=null && action.equals("android.appwidget.action.APPWIDGET_CONFIGURE");
            }
        } else {
            mAppWidgetId = 0;
        }

        final ViewPager pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(new SimpleTabsAdapter(new String[]{resources.getString(R.string.my_subreddits), resources.getString(R.string.my_multis)}, new int[]{R.id.sublist, R.id.multilist}, SubredditSelectActivity.this, null));

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
                        new SyncUserDataTask(SubredditSelectActivity.this, new Runnable() {
                            @Override
                            public void run() {
                                mMultiAdapter.refreshMultis();
                            }
                        }, true, SyncUserDataTask.MODE_MULTIS).execute();
                    } else {
                        new SyncUserDataTask(SubredditSelectActivity.this, new Runnable() {
                            @Override
                            public void run() {
                                refreshSubredditsList();
                            }
                        }, true, SyncUserDataTask.MODE_SUBREDDITS).execute();
                    }
                } else {
                    global.mRedditData.initiateLoginForResult(SubredditSelectActivity.this, false);
                }
            }
        });
        // sort button
        sortBtn = (Button) findViewById(R.id.sortselect);
        String sortTxt = resources.getString(R.string.sort_label) + mSharedPreferences.getString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), "hot");
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

        if (!Reddinator.doShowWelcomeDialog(SubredditSelectActivity.this)){
            if (global.mRedditData.isLoggedIn() && !mSharedPreferences.getBoolean("subscribeDialogShown", false) && !subredditList.contains("reddinator")){
                new AlertDialog.Builder(this)
                    .setTitle(R.string.sub_reddinator)
                    .setMessage(R.string.sub_reddinator_message)
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            JSONObject subObj = new JSONObject();
                            try {
                                subObj.put("name", "t5_37ysa");
                                subObj.put("display_name", "reddinator");
                                subObj.put("public description", "Reddinator provides a hightly customisable Reddit experience on Android, with both an Application and Widget interface.\n\nThis is the official subreddit of Reddinator.\n\nCome here to get support, discuss improvements and request new features.");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            new SubscriptionEditTask(global, SubredditSelectActivity.this, SubredditSelectActivity.this, SubscriptionEditTask.ACTION_SUBSCRIBE).execute(subObj);
                        }
                    }).show();
                mSharedPreferences.edit().putBoolean("subscribeDialogShown", true).apply();
            }
        }
    }

    public void startupTasks(){
        // get multi list and set adapter
        final ListView multiListView = (ListView) findViewById(R.id.multilist);
        multiListView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mMultiAdapter = new MyMultisAdapter(SubredditSelectActivity.this);
                multiListView.setAdapter(mMultiAdapter);
                multiListView.setTextFilterEnabled(true);
                multiListView.setOnItemClickListener(new OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View view,
                                            int position, long id) {
                        if (position < mMultiAdapter.getCount()) {
                            JSONObject multiObj = mMultiAdapter.getItem(position);
                            try {
                                String name = multiObj.getString("display_name");
                                String path = multiObj.getString("path");
                                global.getSubredditManager().setFeed(mAppWidgetId, name, path, true);
                                updateFeedAndFinish();
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(SubredditSelectActivity.this, resources.getString(R.string.multi_open_error), Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                });
                if (global.mRedditData.isLoggedIn()) {
                    if (System.currentTimeMillis() > global.mSharedPreferences.getLong("last_sync_time", 0)+86400000)
                        new SyncUserDataTask(SubredditSelectActivity.this, new Runnable() {
                            @Override
                            public void run() {
                                refreshSubredditsList();
                                if (multiSubsAdapter!=null)
                                    multiSubsAdapter.refreshList();
                            }
                        }, false, 0).execute();
                }
            }
        }, 20);
    }

    public void onResume(){
        super.onResume();
        if (isCreated) {
            if (messageIcon != null) {
                int inboxColor = global.mRedditData.getInboxCount() > 0 ? Color.parseColor("#E06B6C") : Utilities.getActionbarIconColor();
                messageIcon.setIcon(new IconDrawable(this, Iconify.IconValue.fa_envelope).color(inboxColor).actionBarSize());
            }
            if (mMultiAdapter != null)
                mMultiAdapter.refreshMultis();
            if (multiSubsAdapter != null)
                multiSubsAdapter.refreshList();
        } else {
            isCreated = true;
        }
    }

    private void setThemeColors(){
        ThemeManager.Theme theme = global.mThemeManager.getActiveTheme("appthemepref");
        int headerColor = Color.parseColor(theme.getValue("header_color"));
        headerText = Color.parseColor(theme.getValue("header_text"));
        findViewById(R.id.srtoolbar).setBackgroundColor(headerColor);
        ColorMatrixColorFilter filter = Utilities.getColorFilterFromColor(headerColor, 210);
        sortBtn.getBackground().setColorFilter(filter);
        addButton.getBackground().setColorFilter(filter);
        refreshButton.getBackground().setColorFilter(filter);
        // TODO: For material design theme
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sortBtn.getBackground().setTint(headerColor);
            addButton.getBackground().setTint(headerColor);
            refreshButton.getBackground().setTint(headerColor);
        }*/

        addButton.setTextColor(headerText);
        refreshButton.setTextColor(headerText);
        tabs.setBackgroundColor(headerColor);
        tabs.setInidicatorColor(Color.parseColor(theme.getValue("tab_indicator")));
        tabs.setTextColor(headerText);
        sortBtn.setPadding(18, sortBtn.getPaddingTop(), sortBtn.getPaddingRight(), sortBtn.getPaddingBottom());
        sortBtn.setCompoundDrawables(new IconDrawable(this, Iconify.IconValue.fa_sort).color(headerText).sizeDp(24), null, null, null);
        setLoginButton();
        refreshButton.setCompoundDrawablePadding(6);
    }

    private void setLoginButton(){
        if (global.mRedditData.isLoggedIn()) {
            refreshButton.setCompoundDrawables(new IconDrawable(SubredditSelectActivity.this, Iconify.IconValue.fa_refresh).color(headerText).sizeDp(24), null, null, null);
            refreshButton.setText(R.string.refresh);
        } else {
            refreshButton.setCompoundDrawables(new IconDrawable(SubredditSelectActivity.this, Iconify.IconValue.fa_key).color(headerText).sizeDp(24), null, null, null);
            refreshButton.setText(R.string.login);
        }
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
                            new SubscriptionEditTask(global, this, this, SubscriptionEditTask.ACTION_SUBSCRIBE).execute(subreddit);
                        } else {
                            global.getSubredditManager().addSubreddit(subreddit);
                            subredditList.add(name);
                            refreshSubredditsList();
                        }
                        break;
                    case ViewAllSubredditsActivity.RESULT_SET_SUBREDDIT:
                        global.getSubredditManager().setFeedSubreddit(mAppWidgetId, name, subreddit.getString("url"));
                        updateFeedAndFinish();
                        break;

                    case ViewAllSubredditsActivity.RESULT_ADD_TO_MULTI:
                        new SubscriptionEditTask(global, this, this, SubscriptionEditTask.ACTION_MULTI_SUB_ADD).execute(data.getStringExtra("multipath"), name);
                        break;
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(SubredditSelectActivity.this, resources.getString(R.string.sub_data_error), Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode==2 && resultCode==6){
            needsThemeUpdate = true;
            setThemeColors();
        } else if (requestCode==2 && resultCode==7){
            refreshSubredditsList();
            setLoginButton();
        }
    }

    private void updateFeedAndFinish() {
        if (widgetFirstTimeSetup) {
            finishWidgetSetup();
            return;
        }
        if (mAppWidgetId != 0) {
            WidgetCommon.showLoaderAndUpdate(this, mAppWidgetId, false);
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
            // refresh widget and close activity (NOTE: clean this up and use updateFeedAndFinish function or methods in WidgetProvider to handle widget update)
            if (mAppWidgetId != 0) {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelectActivity.this);
                RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);
                views.setViewVisibility(R.id.srloader, View.VISIBLE);
                views.setViewVisibility(R.id.erroricon, View.INVISIBLE);
                views.setRelativeScrollPosition(R.id.adapterview, 0); // Reset scroll offset for API >= 25
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
                appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.adapterview);
            } else {
                Intent intent = new Intent();
                intent.putExtra("themeupdate", needsThemeUpdate);
                if (needsFeedUpdate) {
                    setResult(2, intent); // reload feed and prefs
                } else {
                    setResult(1, intent); // tells main activity to update feed prefs
                }
                if (needsThemeUpdate){
                    WidgetCommon.refreshAllWidgetViews(global);
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
        int iconColor = Utilities.getActionbarIconColor();
        int inboxColor = global.mRedditData.getInboxCount()>0?Color.parseColor("#E06B6C"): iconColor;
        messageIcon = (menu.findItem(R.id.menu_inbox));
        messageIcon.setIcon(new IconDrawable(this, Iconify.IconValue.fa_envelope).color(inboxColor).actionBarSize());
        (menu.findItem(R.id.menu_submit)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_pencil).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_feedprefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_list_alt).color(iconColor).actionBarSize());
        if (mAppWidgetId==0) {
            (menu.findItem(R.id.menu_widgettheme)).setVisible(false);
        } else {
            (menu.findItem(R.id.menu_widgettheme)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_paint_brush).color(iconColor).actionBarSize());
        }
        (menu.findItem(R.id.menu_thememanager)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_cogs).color(iconColor).actionBarSize());
        MenuItem accountItem = (menu.findItem(R.id.menu_account));
        if (global.mRedditData.isLoggedIn())
            accountItem.setTitle(global.mRedditData.getUsername());
        accountItem.setIcon(new IconDrawable(this, Iconify.IconValue.fa_reddit_square).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_saved)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_save).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_search)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_search).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_viewdomain)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_globe).color(iconColor).actionBarSize());
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

            case R.id.menu_inbox:
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(SubredditSelectActivity.this, false);
                    Toast.makeText(SubredditSelectActivity.this, "Reddit login required", Toast.LENGTH_LONG).show();
                } else {
                    Intent inboxIntent = new Intent(SubredditSelectActivity.this, MessagesActivity.class);
                    if (global.mRedditData.getInboxCount() > 0) {
                        inboxIntent.setAction(MessagesActivity.ACTION_UNREAD);
                    }
                    startActivity(inboxIntent);
                }
                break;

            case R.id.menu_account:
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(SubredditSelectActivity.this, false);
                    Toast.makeText(SubredditSelectActivity.this, "Reddit login required", Toast.LENGTH_LONG).show();
                } else {
                    Intent accnIntent = new Intent(SubredditSelectActivity.this, AccountActivity.class);
                    startActivity(accnIntent);
                }
                break;

            case R.id.menu_search:
                Intent searchIntent = new Intent(SubredditSelectActivity.this, SearchActivity.class);
                if (!global.getSubredditManager().isFeedMulti(mAppWidgetId))
                    searchIntent.putExtra("feed_path", global.getSubredditManager().getCurrentFeedPath(mAppWidgetId));
                startActivity(searchIntent);
                break;

            case R.id.menu_saved:
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(SubredditSelectActivity.this, false);
                    Toast.makeText(SubredditSelectActivity.this, "Reddit login required", Toast.LENGTH_LONG).show();
                } else {
                    Intent savedIntent = new Intent(SubredditSelectActivity.this, AccountActivity.class);
                    savedIntent.setAction(AccountActivity.ACTION_SAVED);
                    startActivity(savedIntent);
                }
                break;

            case R.id.menu_submit:
                Intent submitIntent = new Intent(SubredditSelectActivity.this, SubmitActivity.class);
                startActivity(submitIntent);
                break;

            case R.id.menu_viewdomain:
                showViewDomainDialog();
                break;

            case R.id.menu_feedprefs:
                showFeedPrefsDialog();
                break;

            case R.id.menu_widgettheme:
                showWidgetThemeDialog();
                break;

            case R.id.menu_thememanager:
                Intent intent = new Intent(SubredditSelectActivity.this, ThemesActivity.class);
                startActivityForResult(intent, ThemesActivity.REQUEST_CODE_NO_WIDGET_UPDATES);
                break;

            case R.id.menu_prefs:
                Intent intent2 = new Intent(SubredditSelectActivity.this, PrefsActivity.class);
                startActivityForResult(intent2, ThemesActivity.REQUEST_CODE_NO_WIDGET_UPDATES);
                break;

            case R.id.menu_about:
                AboutDialog.show(this, true);
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void showViewDomainDialog(){
        final EditText input = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.view_domain_posts));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String domain = input.getText().toString();
                String pattern = "^(([a-zA-Z])|([a-zA-Z][a-zA-Z])|([a-zA-Z][0-9])|([0-9][a-zA-Z])|([a-zA-Z0-9][a-zA-Z0-9-_]{1,61}[a-zA-Z0-9]))\\.([a-zA-Z]{2,6}|[a-zA-Z0-9-]{2,30}\\.[a-zA-Z]{2,3})$";
                Pattern r = Pattern.compile(pattern);
                Matcher m = r.matcher(domain);
                if (m.find()) {
                    dialog.dismiss();
                    global.getSubredditManager().setFeedDomain(mAppWidgetId, domain);
                    updateFeedAndFinish();
                } else {
                    Toast.makeText(SubredditSelectActivity.this, getString(R.string.enter_valid_domain), Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.show().setCanceledOnTouchOutside(true);
    }

    // show sort select dialog
    private void showSortDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        builder.setTitle(resources.getString(R.string.select_sort));
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
                String sorttxt = resources.getString(R.string.sort_label) + sort;
                sortBtn.setText(sorttxt);
                needsFeedUpdate = true; // mark feed for updating
                dialog.dismiss();
            }
        });
        builder.show().setCanceledOnTouchOutside(true);
    }

    private void showFeedPrefsDialog(){
        final CharSequence[] names = {getString(R.string.image_previews), resources.getString(R.string.thumbnails), resources.getString(R.string.thumbnails_on_top), resources.getString(R.string.hide_post_info)};
        final String widgetId = (mAppWidgetId == 0 ? "app" : String.valueOf(mAppWidgetId));
        // previews disabled by default in widgets due to listview dynamic height issue (causes views to jump around when scrolling up)
        final boolean[] initvalue = {mSharedPreferences.getBoolean("imagepreviews-" + widgetId, mAppWidgetId == 0), mSharedPreferences.getBoolean("thumbnails-" + widgetId, true), mSharedPreferences.getBoolean("bigthumbs-" + widgetId, false), mSharedPreferences.getBoolean("hideinf-" + widgetId, false)};
        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        builder.setTitle( mAppWidgetId==0 ? resources.getString(R.string.app_feed_prefs) : resources.getString(R.string.widget_feed_prefs) );
        builder.setMultiChoiceItems(names, initvalue, new DialogInterface.OnMultiChoiceClickListener() {
            public void onClick(DialogInterface dialogInterface, int item, boolean state) {
                Editor prefsedit = mSharedPreferences.edit();
                switch (item) {
                    case 0:
                        prefsedit.putBoolean("imagepreviews-" + widgetId, state);
                        break;
                    case 1:
                        prefsedit.putBoolean("thumbnails-" + widgetId, state);
                        break;
                    case 2:
                        prefsedit.putBoolean("bigthumbs-" + widgetId, state);
                        break;
                    case 3:
                        prefsedit.putBoolean("hideinf-" + widgetId, state);
                        break;
                }
                prefsedit.apply();
                needsFeedViewUpdate = true;
            }
        });
        builder.setPositiveButton(resources.getString(R.string.close), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.show().setCanceledOnTouchOutside(true);
    }

    private void showWidgetThemeDialog(){

        // set themes list
        LinkedHashMap<String, String> themeList = global.mThemeManager.getThemeList(ThemeManager.LISTMODE_ALL);
        themeList.put("app_select", resources.getString(R.string.use_app_theme));
        final String[] keys = themeList.keySet().toArray(new String[themeList.keySet().size()]);
        String curTheme = mSharedPreferences.getString("widgettheme-" + mAppWidgetId, "app_select");
        int curIndex = 0;
        for (int i=0; i<keys.length; i++){
            if (keys[i].equals(curTheme)) {
                curIndex = i;
                break;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(resources.getString(R.string.select_widget_theme))
        .setSingleChoiceItems(themeList.values().toArray(new String[themeList.values().size()]), curIndex,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    needsThemeUpdate = true;
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    editor.putString("widgettheme-" + mAppWidgetId, keys[i]);
                    editor.apply();
                    dialogInterface.cancel();
                }
            }
        ).setPositiveButton(resources.getString(R.string.close), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        }).show().setCanceledOnTouchOutside(true);
    }

    private void refreshSubredditsList(){
        subredditList = global.getSubredditManager().getSubredditNames();
        subsAdapter.clear();
        subsAdapter.addAll(subredditList);
        subsAdapter.notifyDataSetChanged();
        subsAdapter.sort(subComparator);
    }

    private Comparator<String> subComparator = new Comparator<String>() {
        @Override
        public int compare(String s, String t1) {
            if (s.equals("Front Page") || s.equals("all")){
                return -100;
            } else if (t1.equals("Front Page") || t1.equals("all")){
                return 100;
            }
            return s.compareToIgnoreCase(t1);
        }
    };

    private ProgressDialog randomProg = null;
    @Override
    public void onRandomSubredditLoaded(JSONObject result, RedditData.RedditApiException exception) {
        if (result!=null) {
            try {
                global.getSubredditManager().setFeedSubreddit(mAppWidgetId, result.getString("title"), result.getString("url"));
                updateFeedAndFinish();
                return;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (randomProg!=null) randomProg.dismiss();
    }

    // list adapter
    class MySubredditsAdapter extends ArrayAdapter<String> {
        private LayoutInflater inflater;

        MySubredditsAdapter(Context context, ArrayList<String> objects) {
            super(context, R.layout.myredditlistitem, R.id.subreddit_name, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            super.getView(position, convertView, parent);
            ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                // inflate new view
                convertView = inflater.inflate(R.layout.myredditlistitem, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(R.id.subreddit_name);
                viewHolder.deleteIcon = (IconTextView) convertView.findViewById(R.id.subreddit_delete_btn);
                viewHolder.filterIcon = (IconTextView) convertView.findViewById(R.id.subreddit_filter_btn);
                viewHolder.defaultIcon = (IconTextView) convertView.findViewById(R.id.subreddit_default_btn);
                viewHolder.randomIcon = (IconTextView) convertView.findViewById(R.id.subreddit_random_btn);
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
                        new AlertDialog.Builder(SubredditSelectActivity.this).setTitle(resources.getString(R.string.unsubscribe))
                            .setMessage(resources.getString(R.string.confirm_unsubscribe, sreddit))
                            .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                }
                            }).setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    new SubscriptionEditTask(global, SubredditSelectActivity.this, SubredditSelectActivity.this, SubscriptionEditTask.ACTION_UNSUBSCRIBE).execute(sreddit);
                                }
                        }).show().setCanceledOnTouchOutside(true);
                    } else {
                        global.getSubredditManager().removeSubreddit(sreddit);
                        subredditList.remove(sreddit);
                        refreshSubredditsList();
                    }
                }
            });
            if ("all".equals(getItem(position))){
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
            // Display default front page button when logged in
            if ("Front Page".equals(getItem(position)) && global.mRedditData.isLoggedIn()){
                viewHolder.defaultIcon.setVisibility(View.VISIBLE);
                viewHolder.defaultIcon.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        global.getSubredditManager().setFeed(mAppWidgetId, "Default Front Page", "/default", true);
                        updateFeedAndFinish();
                    }
                });
                viewHolder.randomIcon.setVisibility(View.VISIBLE);
                viewHolder.randomIcon.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        randomProg = new ProgressDialog(SubredditSelectActivity.this);
                        randomProg.setTitle(R.string.loading);
                        randomProg.show();
                        new LoadRandomTask(global, SubredditSelectActivity.this).execute();
                    }
                });
            } else{
                viewHolder.defaultIcon.setVisibility(View.GONE);
                viewHolder.randomIcon.setVisibility(View.GONE);
            }

            convertView.setTag(viewHolder);

            return convertView;
        }

        class ViewHolder {
            TextView name;
            IconTextView deleteIcon;
            IconTextView filterIcon;
            IconTextView defaultIcon;
            IconTextView randomIcon;
        }
    }

    class MyMultisAdapter extends BaseAdapter {
        private LayoutInflater inflater;
        private ArrayList<JSONObject> multiList;

        MyMultisAdapter(Context context) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            refreshMultis();
        }

        void refreshMultis(){
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
                    convertView.findViewById(R.id.multi_browse_btn)
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                global.openSubredditFeed(SubredditSelectActivity.this, Reddinator.REDDIT_BASE_URL+"/r/"+Reddinator.SUBREDDIT_MULTIHUB);
                            }
                        });
                    convertView.findViewById(R.id.multi_add_btn)
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_multi_add, parent, false);
                                final EditText name = (EditText) layout.findViewById(R.id.new_multi_name);
                                name.setSingleLine();
                                name.setImeOptions(EditorInfo.IME_ACTION_GO);
                                name.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                                    @Override
                                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                                        createMulti(name.getText().toString());
                                        return false;
                                    }
                                });
                                AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
                                builder.setTitle(resources.getString(R.string.create_a_multi)).setView(layout)
                                        .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                dialogInterface.dismiss();
                                            }
                                        }).setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        createMulti(name.getText().toString());
                                        dialogInterface.dismiss();
                                    }
                                }).show().setCanceledOnTouchOutside(true);
                            }
                        });
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
                }
            }

            convertView.setTag(viewHolder);

            return convertView;
        }

        private void createMulti(String name){
            if (name.equals("")) {
                Toast.makeText(SubredditSelectActivity.this, resources.getString(R.string.enter_multi_name_error), Toast.LENGTH_LONG).show();
                return;
            }
            new SubscriptionEditTask(global, SubredditSelectActivity.this, SubredditSelectActivity.this, SubscriptionEditTask.ACTION_MULTI_CREATE).execute(name);
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
        builder.setTitle(resources.getString(R.string.delete_multi)).setMessage(resources.getString(R.string.delete_multi_message));
        builder.setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        }).setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                new SubscriptionEditTask(global, SubredditSelectActivity.this, SubredditSelectActivity.this, SubscriptionEditTask.ACTION_MULTI_DELETE).execute(multiPath);
            }
        }).show().setCanceledOnTouchOutside(true);
    }

    private void showMultiRenameDialog(final String multiPath){
        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        final EditText nameInput = new EditText(SubredditSelectActivity.this);
        nameInput.setHint(resources.getString(R.string.multi_name_hint));
        builder.setTitle(resources.getString(R.string.rename_multi)).setView(nameInput)
        .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        }).setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                new SubscriptionEditTask(global, SubredditSelectActivity.this, SubredditSelectActivity.this, SubscriptionEditTask.ACTION_MULTI_RENAME).execute(multiPath, nameInput.getText().toString().replaceAll("\\s+",""));
            }
        }).show().setCanceledOnTouchOutside(true);
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
        pager.setAdapter(new SimpleTabsAdapter(new String[]{resources.getString(R.string.subreddits), resources.getString(R.string.settings)}, new int[]{R.id.multi_subreddits, R.id.multi_settings}, SubredditSelectActivity.this, dialogView));
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
                JSONObject multiObj = new JSONObject();
                try {
                    multiObj.put("decription_md", description.getText().toString());
                    multiObj.put("display_name", displayName.getText().toString());
                    multiObj.put("icon_name", icon.getSelectedItem().toString().equals("none")?"":icon.getSelectedItem().toString());
                    multiObj.put("key_color", color.getText().toString());
                    multiObj.put("subreddits", global.getSubredditManager().getMultiData(multiPath).getJSONArray("subreddits"));
                    multiObj.put("visibility", visibility.getSelectedItem().toString());
                    multiObj.put("weighting_scheme", weighting.getSelectedItem().toString());

                    new SubscriptionEditTask(global, SubredditSelectActivity.this, SubredditSelectActivity.this, SubscriptionEditTask.ACTION_MULTI_EDIT).execute(multiPath, multiObj);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);

        multiDialog = builder.setView(dialogView).show();
        multiDialog.setCanceledOnTouchOutside(true);
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
        AlertDialog dialog = builder.setView(dialogView)
                .setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        needsFeedUpdate = true; // mark feed for updating
                    }
                }).show();
        dialog.setCanceledOnTouchOutside(true);
        if (dialog.getWindow()!=null)
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    class SubsListAdapter extends BaseAdapter {
        private final int MODE_MULTI = 1;
        private int mode = 0;
        private LayoutInflater inflater;
        private ArrayList<String> subsList;
        private String multiPath;
        private SubAutoCompleteAdapter autoCompleteAdapter;

        SubsListAdapter(Context context, String multiPath) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            autoCompleteAdapter = new SubAutoCompleteAdapter(context, R.layout.autocomplete_list_item);
            if (multiPath!=null) {
                this.multiPath = multiPath;
                mode = MODE_MULTI;
            }
            refreshList();
        }

        void refreshList(){
            if (mode==MODE_MULTI) {
                subsList = global.getSubredditManager().getMultiSubreddits(multiPath);
            } else {
                subsList = global.getSubredditManager().getAllFilter();
            }
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
                        performAdd(viewHolder.nameInput.getText().toString());
                        viewHolder.nameInput.setText("");
                    }
                });
                viewHolder.nameInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        if (actionId == EditorInfo.IME_ACTION_GO){
                            performAdd(viewHolder.nameInput.getText().toString());
                            viewHolder.nameInput.setText("");
                        }
                        return false;
                    }
                });
                viewHolder.addIcon.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        performAdd(viewHolder.nameInput.getText().toString());
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

        private void performAdd(String subreddit){
            if (subreddit.equals("")){
                Toast.makeText(SubredditSelectActivity.this, resources.getString(R.string.sub_name_error), Toast.LENGTH_SHORT).show();
                return;
            }
            if (mode==MODE_MULTI) {
                new SubscriptionEditTask(global, SubredditSelectActivity.this, SubredditSelectActivity.this, SubscriptionEditTask.ACTION_MULTI_SUB_ADD).execute(multiPath, subreddit);
            } else {
                if (global.mRedditData.isLoggedIn())
                    new SubscriptionEditTask(global, SubredditSelectActivity.this, null, SubscriptionEditTask.ACTION_FILTER_SUB_ADD).execute("all", subreddit);
                subsList.add(subreddit);
                global.getSubredditManager().setAllFilter(subsList);
                System.out.println(global.getSubredditManager().getCurrentFeedName(mAppWidgetId));
                if ("all".equals(global.getSubredditManager().getCurrentFeedName(mAppWidgetId)))
                    needsFeedUpdate = true;
                notifyDataSetChanged();
            }
        }

        private void performRemove(String subreddit){
            if (mode==MODE_MULTI) {
                new SubscriptionEditTask(global, SubredditSelectActivity.this, SubredditSelectActivity.this, SubscriptionEditTask.ACTION_MULTI_SUB_REMOVE).execute(multiPath, subreddit);
            } else {
                if (global.mRedditData.isLoggedIn())
                    new SubscriptionEditTask(global, SubredditSelectActivity.this, null, SubscriptionEditTask.ACTION_FILTER_SUB_REMOVE).execute("all", subreddit);
                subsList.remove(subreddit);
                global.getSubredditManager().setAllFilter(subsList);
                if ("all".equals(global.getSubredditManager().getCurrentFeedName(mAppWidgetId)))
                    needsFeedUpdate = true;
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

    @Override
    public void onSubscriptionEditComplete(boolean result, RedditData.RedditApiException exception, int action, Object[] params, JSONObject data) {
        ArrayList<String> subreddits;
        if (result || (action==SubscriptionEditTask.ACTION_UNSUBSCRIBE)) {
            //if (this.data!=null)
            //System.out.println("resultData: "+this.data.toString());
            switch (action) {
                case SubscriptionEditTask.ACTION_SUBSCRIBE:
                    global.getSubredditManager().addSubreddit((JSONObject) params[0]);
                    try {
                        subredditList.add(0, ((JSONObject) params[0]).getString("display_name"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    refreshSubredditsList();
                    break;
                case SubscriptionEditTask.ACTION_UNSUBSCRIBE:
                    global.getSubredditManager().removeSubreddit(params[0].toString());
                    subredditList.remove(params[0].toString());
                    refreshSubredditsList();
                    break;
                case SubscriptionEditTask.ACTION_MULTI_CREATE:
                    try {
                        if (data ==null) return;
                        JSONObject multiObj = data.getJSONObject("data");
                        String path = multiObj.getString("path");
                        global.getSubredditManager().setMultiData(path, multiObj);
                        showMultiEditDialog(path);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    mMultiAdapter.refreshMultis();
                    break;
                case SubscriptionEditTask.ACTION_MULTI_EDIT:
                    try {
                        if (data ==null) return;
                        JSONObject multiObj = data.getJSONObject("data");
                        global.getSubredditManager().setMultiData(params[0].toString(), multiObj);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    multiDialog.dismiss();
                    mMultiAdapter.refreshMultis();
                    break;
                case SubscriptionEditTask.ACTION_MULTI_SUB_ADD:
                    subreddits = global.getSubredditManager().getMultiSubreddits(params[0].toString());
                    subreddits.add(params[1].toString());
                    global.getSubredditManager().setMultiSubs(params[0].toString(), subreddits);
                    multiSubsAdapter.refreshList();
                    break;
                case SubscriptionEditTask.ACTION_MULTI_SUB_REMOVE:
                    subreddits = global.getSubredditManager().getMultiSubreddits(params[0].toString());
                    subreddits.remove(params[1].toString());
                    global.getSubredditManager().setMultiSubs(params[0].toString(), subreddits);
                    multiSubsAdapter.refreshList();
                    break;
                case SubscriptionEditTask.ACTION_MULTI_DELETE:
                    global.getSubredditManager().removeMulti(params[0].toString());
                    mMultiAdapter.refreshMultis();
                    break;
                case SubscriptionEditTask.ACTION_MULTI_RENAME:
                    global.getSubredditManager().removeMulti(params[0].toString());
                    try {
                        JSONObject multiObj = data.getJSONObject("data");
                        String path = multiObj.getString("path");
                        global.getSubredditManager().setMultiData(path, multiObj);
                        multiName.setText(multiObj.getString("name"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    mMultiAdapter.refreshMultis();
                    break;
            }
        }
        if (!result){
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(SubredditSelectActivity.this, false);
            // show error
            Utilities.showApiErrorToastOrDialog(SubredditSelectActivity.this, exception);
        }
    }
}
