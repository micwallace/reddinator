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
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.IconTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.ThemeHelper;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.tasks.LoadSubredditInfoTask;
import au.com.wallaceit.reddinator.ui.HtmlDialog;
import au.com.wallaceit.reddinator.ui.SubredditFeedAdapter;

public class MainActivity extends Activity implements LoadSubredditInfoTask.Callback, SubredditFeedAdapter.ActivityInterface, ThemeHelper.ThemeInstallInterface {
    public static final String EXTRA_FEED_PATH = "feedPath";
    public static final String EXTRA_FEED_NAME = "feedName";

    private Reddinator global;
    private SubredditFeedAdapter listAdapter;
    private AbsListView listView;
    private View appView;
    private ActionBar actionBar;
    private MenuItem messageIcon;
    private MenuItem sortItem;
    private MenuItem sidebarIcon;
    private ProgressBar loader;
    private TextView srtext;
    private IconTextView errorIcon;
    private IconTextView refreshbutton;
    private ThemeManager.Theme theme;
    private int feedId = 0; // 0 for normal feed, -1 for temp feed. Used to keep feed cache storage and settings separate from the main feed.
    private String subredditName;
    private String subredditPath;
    private String subredditSort;
    private boolean hasMultipleSubs = false;
    private boolean viewThemes = false;

    private String lastItemId = "0";
    private boolean endOfFeed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        global = ((Reddinator) getApplicationContext());
        global.mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.activity_main);
        // Setup actionbar
        appView = findViewById(R.id.appview);
        actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setCustomView(R.layout.appheader);
        }
        // get actionBar Views
        loader = (ProgressBar) findViewById(R.id.appsrloader);
        errorIcon = (IconTextView) findViewById(R.id.apperroricon);
        refreshbutton = (IconTextView) findViewById(R.id.apprefreshbutton);
        LinearLayout srbutton = (LinearLayout) findViewById(R.id.sub_container);
        srtext = (TextView) findViewById(R.id.appsubreddittxt);

        // set theme colors
        setThemeColors();

        // setup button onclicks
        refreshbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                reloadReddits();
            }
        });
        View.OnClickListener srclick;
        // check intent and set needed feed params accordingly
        if (getIntent().getAction()!=null && getIntent().getAction().equals(Intent.ACTION_VIEW)){
            // open reddit feed via url, extract path and name. Feed can be a subreddit, domain or multi
            Pattern pattern = Pattern.compile(".*reddit.com((/(r|domain|user/.*/m)/([^/]*))?(/([^/,a-z]*))?)?");
            Matcher matcher = pattern.matcher(getIntent().getDataString());
            if (matcher.find()){
                if (matcher.group(2)!=null) {
                    subredditPath = matcher.group(2);
                    subredditName = matcher.group(4);
                    hasMultipleSubs = (matcher.group(3).contains("user/") || matcher.group(3).contains("domain/"));
                } else {
                    subredditPath = "";
                    subredditName = "Front Page";
                    hasMultipleSubs = true;
                }
                subredditSort = matcher.group(6)!=null ? matcher.group(6) : "hot";
            } else {
                Toast.makeText(this, "Could not decode post URL", Toast.LENGTH_LONG).show();
                this.finish();
                return;
            }
            feedId = -1;
            srclick = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Toast.makeText(MainActivity.this, "This is a temporary feed, change sort and preferences from the menu", Toast.LENGTH_LONG).show();
                }
            };
            findViewById(R.id.appcaret).setVisibility(View.GONE);
            // put into theme viewing mode if view_themes extra is provided
            if (getIntent().getBooleanExtra("view_themes", false)){
                viewThemes = true;
            }
        } else {
            subredditPath = global.getSubredditManager().getCurrentFeedPath(0);
            subredditName = global.getSubredditManager().getCurrentFeedName(0);
            subredditSort = global.mSharedPreferences.getString("sort-app", "hot");
            hasMultipleSubs = global.getSubredditManager().isFeedMulti(0);
            srclick = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent srintent = new Intent(MainActivity.this, SubredditSelectActivity.class);
                    startActivityForResult(srintent, 0);
                }
            };
            findViewById(R.id.appcaret).setOnClickListener(srclick);
        }
        // don't add subreddit select click for temp feeds, feed prefs and sort are changable from the menu only in temp feeds
        srbutton.setOnClickListener(srclick);
        // Load current data if available & setup list adapter'
        if (feedId==0) {
            data = global.getFeed(0);
            //System.out.println("cached Data length: "+data.length());
            if (data.length() != 0) {
                try {
                    lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name");
                } catch (JSONException e) {
                    lastItemId = "0"; // Could not get last item ID; perform a reload next time and show error view :(
                    endOfFeed = true;
                    e.printStackTrace();
                }
            }
        } else {
            data = new JSONArray();
        }
        listView = (ListView) findViewById(R.id.applistview);
        listAdapter = new SubredditFeedAdapter(this, this, global, theme, feedId, data, !endOfFeed, hasMultipleSubs);
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                final Bundle extras = listAdapter.getItemExtras(position);
                if (extras == null) {
                    Toast.makeText(MainActivity.this, R.string.data_error, Toast.LENGTH_LONG).show();
                    return;
                }
                String subreddit = extras.getString(Reddinator.ITEM_SUBREDDIT);
                if (Reddinator.SUBREDDIT_MULTIHUB.equals(subreddit)){
                    // Extract name from multi path
                    String url = extras.getString(Reddinator.ITEM_URL);
                    if (url!=null) {
                        Pattern pattern = Pattern.compile(".*reddit.com(/user/.*/m/([^/]*))");
                        Matcher matcher = pattern.matcher(url);
                        if (matcher.find()) {
                            // open in a new temporary activity
                            global.openSubredditFeed(MainActivity.this, Reddinator.REDDIT_BASE_URL + url);
                            return;
                        }
                    }
                } else if (Reddinator.SUBREDDIT_REDDINATOR.equals(subreddit)){
                    try {
                        JSONObject postData = listAdapter.getItem(position);
                        if (viewThemes || postData.getString("title").indexOf("[Theme]")==0) {
                            ThemeHelper.handleThemeInstall(MainActivity.this, global, MainActivity.this, postData, new Runnable() {
                                @Override
                                public void run() {
                                    openPostView(extras, true);
                                }
                            });
                            return;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                // open in the reddinator view
                openPostView(extras, false);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
                Bundle extras = listAdapter.getItemExtras(position);
                if (extras == null) {
                    Toast.makeText(MainActivity.this, R.string.data_error, Toast.LENGTH_LONG).show();
                    return true;
                }
                Intent ointent = new Intent(MainActivity.this, FeedItemDialogActivity.class);
                // if this is a temp feed, pass the feed path to the dialog so it knows whether to show, subreddit and domain buttons
                if (feedId<0) {
                    extras.putString(FeedItemDialogActivity.EXTRA_CURRENT_FEED_PATH, subredditPath);
                }
                try {
                    String subreddit = extras.getString(Reddinator.ITEM_SUBREDDIT);
                    JSONObject postData = listAdapter.getItem(position);
                    if ((Reddinator.SUBREDDIT_REDDINATOR.equals(subreddit) && (viewThemes || postData.getString("title").indexOf("[Theme]")==0))) {
                        extras.putBoolean(FeedItemDialogActivity.EXTRA_IS_THEME, true);
                        extras.putString(FeedItemDialogActivity.EXTRA_POST_DATA, postData.toString());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                ointent.putExtras(extras);
                MainActivity.this.startActivityForResult(ointent, 1);
                return true;
            }
        });

        // set the current subreddit name
        String heading = subredditName + (viewThemes?" themes":"");
        srtext.setText(heading);

        // always load a temp feed (from intent action_view) and when there's no cached data or when the preference is set to always reload when opened
        if (feedId==-1 || (global.mSharedPreferences.getBoolean("appreloadpref", false) || listAdapter.getCount() < 2))
            reloadReddits();
    }

    private void openPostView(Bundle extras, boolean viewComments){
        Intent intent = new Intent(MainActivity.this, ViewRedditActivity.class);
        intent.putExtras(extras);
        if (viewComments)
            intent.putExtra("view_comments", true);
        startActivityForResult(intent, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Bundle update = global.getItemUpdate();
        if (update != null) {
            listAdapter.updateUiVote(update.getInt("position", 0), update.getString("id"), update.getString("val"), update.getInt("netvote"));
        }
        if (messageIcon!=null){
            int inboxColor = global.mRedditData.getInboxCount()>0?Color.parseColor("#E06B6C"): Utilities.getActionbarIconColor();
            messageIcon.setIcon(new IconDrawable(this, Iconify.IconValue.fa_envelope).color(inboxColor).actionBarSize());
        }
        if (sidebarIcon!=null)
            if (feedId==-1 || !global.getSubredditManager().isFeedMulti(0)) {
                sidebarIcon.setVisible(true);
            } else {
                sidebarIcon.setVisible(false);
            }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.feed_menu, menu);

        // set options menu view
        int iconColor = Utilities.getActionbarIconColor();
        int inboxColor = global.mRedditData.getInboxCount()>0?Color.parseColor("#E06B6C"): iconColor;
        messageIcon = (menu.findItem(R.id.menu_inbox));
        messageIcon.setIcon(new IconDrawable(this, Iconify.IconValue.fa_envelope).color(inboxColor).actionBarSize());
        sortItem = (menu.findItem(R.id.menu_sort));
        sortItem.setIcon(new IconDrawable(this, Iconify.IconValue.fa_sort).color(iconColor).actionBarSize());
        sortItem.setTitle(getString(R.string.sort_label) + " " + subredditSort);
        sidebarIcon = menu.findItem(R.id.menu_sidebar);
        sidebarIcon.setIcon(new IconDrawable(this, Iconify.IconValue.fa_book).color(iconColor).actionBarSize());
        if (feedId==-1 || !global.getSubredditManager().isFeedMulti(0)) {
            sidebarIcon.setVisible(true);
        }
        (menu.findItem(R.id.menu_submit)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_pencil).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_feedprefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_list_alt).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_thememanager)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_cogs).color(iconColor).actionBarSize());
        MenuItem accountItem = (menu.findItem(R.id.menu_account));
        if (global.mRedditData.isLoggedIn())
            accountItem.setTitle(global.mRedditData.getUsername());
        accountItem.setIcon(new IconDrawable(this, Iconify.IconValue.fa_reddit_square).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_search)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_search).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_prefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_wrench).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_about)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_info_circle).color(iconColor).actionBarSize());

        return true;
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

            case R.id.menu_sort:
                showSortDialog();
                break;

            case R.id.menu_sidebar:
                openSidebar();
                break;

            case R.id.menu_inbox:
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(this, false);
                    Toast.makeText(this, "Reddit login required", Toast.LENGTH_LONG).show();
                } else {
                    Intent inboxIntent = new Intent(this, MessagesActivity.class);
                    if (global.mRedditData.getInboxCount() > 0) {
                        inboxIntent.setAction(MessagesActivity.ACTION_UNREAD);
                    }
                    startActivity(inboxIntent);
                }
                break;

            case R.id.menu_account:
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(this, false);
                    Toast.makeText(this, "Reddit login required", Toast.LENGTH_LONG).show();
                } else {
                    Intent accnIntent = new Intent(this, AccountActivity.class);
                    startActivity(accnIntent);
                }
                break;

            case R.id.menu_search:
                Intent searchIntent = new Intent(this, SearchActivity.class);
                if (feedId==-1 || !global.getSubredditManager().isFeedMulti(0))
                    searchIntent.putExtra("feed_path", subredditPath);
                startActivity(searchIntent);
                break;

            case R.id.menu_submit:
                Intent submitIntent = new Intent(this, SubmitActivity.class);
                startActivity(submitIntent);
                break;

            case R.id.menu_feedprefs:
                showFeedPrefsDialog();
                break;

            case R.id.menu_thememanager:
                Intent intent = new Intent(this, ThemesActivity.class);
                startActivityForResult(intent, ThemesActivity.REQUEST_CODE_UPDATE_WIDGETS);
                break;

            case R.id.menu_prefs:
                Intent intent2 = new Intent(this, PrefsActivity.class);
                startActivityForResult(intent2, ThemesActivity.REQUEST_CODE_UPDATE_WIDGETS);
                break;

            case R.id.menu_about:
                AboutDialog.show(this, true);
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private boolean needsFeedViewUpdate = false;
    private void showFeedPrefsDialog(){
        final CharSequence[] names = {getString(R.string.image_previews), getString(R.string.thumbnails), getString(R.string.thumbnails_on_top), getString(R.string.hide_post_info)};
        final boolean[] initvalue = {global.mSharedPreferences.getBoolean("imagepreviews-app", true), global.mSharedPreferences.getBoolean("thumbnails-app", true), global.mSharedPreferences.getBoolean("bigthumbs-app", false), global.mSharedPreferences.getBoolean("hideinf-app", false)};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_feed_prefs));
        builder.setMultiChoiceItems(names, initvalue, new DialogInterface.OnMultiChoiceClickListener() {
            public void onClick(DialogInterface dialogInterface, int item, boolean state) {
                SharedPreferences.Editor prefsedit = global.mSharedPreferences.edit();
                switch (item) {
                    case 0:
                        prefsedit.putBoolean("imagepreviews-app", state);
                        break;
                    case 1:
                        prefsedit.putBoolean("thumbnails-app", state);
                        break;
                    case 2:
                        prefsedit.putBoolean("bigthumbs-app", state);
                        break;
                    case 3:
                        prefsedit.putBoolean("hideinf-app", state);
                        break;
                }
                prefsedit.apply();
                needsFeedViewUpdate = true;
            }
        });
        builder.setPositiveButton(getString(R.string.close), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (needsFeedViewUpdate) {
                    listAdapter.loadFeedPrefs();
                    listView.invalidateViews();
                    needsFeedViewUpdate = false;
                }
            }
        });
        builder.show().setCanceledOnTouchOutside(true);
    }

    // show sort select dialog
    private void showSortDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.select_sort));
        builder.setItems(R.array.reddit_sorts, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor prefsedit = global.mSharedPreferences.edit();
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
                if (feedId==0) { // don't update persitent setting if it's a temp feed.
                    prefsedit.putString("sort-app", sort);
                    prefsedit.apply();
                }
                subredditSort = sort;
                // set new text in button
                sortItem.setTitle(getString(R.string.sort_label) + " " + subredditSort);
                dialog.dismiss();
                reloadReddits();
            }
        });
        builder.show().setCanceledOnTouchOutside(true);
    }

    private void setThemeColors() {

        theme = global.mThemeManager.getActiveTheme("appthemepref");

        appView.setBackgroundColor(Color.parseColor(theme.getValue("background_color")));
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor(theme.getValue("header_color"))));
        srtext.setTextColor(Color.parseColor(theme.getValue("header_text")));

        final int iconColor = Color.parseColor(theme.getValue("default_icon"));
        int[] shadow = new int[]{3, 4, 4, Color.parseColor(theme.getValue("icon_shadow"))};

        refreshbutton.setTextColor(iconColor);
        refreshbutton.setShadowLayer(shadow[0], shadow[1], shadow[2], shadow[3]);
        ((IconTextView) findViewById(R.id.appcaret)).setTextColor(iconColor);

        // Set actionbar overflow icon drawable
        Utilities.updateActionbarOverflowIcon(this, iconColor);
    }

    private void refreshTheme(){
        setThemeColors();
        listAdapter.setTheme(theme);
        //listView.invalidateViews();
    }

    @Override
    protected void onActivityResult(int reqcode, int resultcode, Intent data) {
        switch (resultcode) {
            // reload feed prefs
            case 1:
                listAdapter.loadFeedPrefs();
                listView.invalidateViews();
                break;
            // reload feed prefs and update feed, subreddit has changed
            case 2:
                if (feedId < 0){
                    subredditPath = data.getStringExtra(EXTRA_FEED_PATH);
                    subredditName = data.getStringExtra(EXTRA_FEED_NAME);
                    subredditSort = "hot";
                    hasMultipleSubs = (Utilities.isFeedPathDomain(subredditPath) || Utilities.isFeedPathMulti(subredditPath));
                } else {
                    subredditName = global.getSubredditManager().getCurrentFeedName(0);
                    subredditPath = global.getSubredditManager().getCurrentFeedPath(0);
                    subredditSort = global.mSharedPreferences.getString("sort-app", "hot");
                    hasMultipleSubs = global.getSubredditManager().isFeedMulti(0);
                }
                updateFeed();
                break;
            // initiate vote
            case 3:
            case 4:
                if (data!=null) {
                    int position = data.getIntExtra(Reddinator.ITEM_FEED_POSITION, -1);
                    listAdapter.initialiseVote(position, (resultcode == 3 ? 1 : -1));
                }
                break;
            // reload feed data from cache
            case 5:
                reloadFeedData();
                //listView.invalidateViews();
                break;
        }
        if (resultcode==6 || (data!=null && data.getBooleanExtra("themeupdate", false))){
            refreshTheme();
        }
    }

    private void updateFeed(){
        sortItem.setTitle(getString(R.string.sort_label)+" "+subredditSort);
        srtext.setText(subredditName);
        listAdapter.loadFeedPrefs();
        reloadReddits();
    }

    private ProgressDialog sidebarProg;
    private void openSidebar(){
        sidebarProg = new ProgressDialog(this);
        sidebarProg.setIndeterminate(true);
        sidebarProg.setTitle(R.string.loading);
        sidebarProg.setMessage(getString(R.string.loading_sidebar));
        sidebarProg.show();
        new LoadSubredditInfoTask(global, this).execute(subredditName);
    }
    @Override
    public void onSubredditInfoLoaded(JSONObject result, RedditData.RedditApiException exception) {
        if (result!=null){
            try {
                String html = "&lt;p&gt;"+result.getString("subscribers")+" readers&lt;br/&gt;"+result.getString("accounts_active")+" users here now&lt;/p&gt;";
                html += result.getString("description_html");
                HtmlDialog.init(this, subredditPath, Utilities.fromHtml(html).toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Error loading sidebar: "+exception.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (sidebarProg!=null)
            sidebarProg.dismiss();
    }

    private JSONArray data;

    public void loadMore() {
        loadReddits(!endOfFeed);
    }

    void reloadFeedData() {
        data = global.getFeed(feedId);
        listAdapter.setFeed(data, !endOfFeed, (feedId==0 && global.getSubredditManager().isFeedMulti(feedId)));
        //listAdapter.notifyDataSetChanged();
    }

    void reloadReddits() {
        global.triggerThunbnailCacheClean();
        loadReddits(false);
    }

    private void loadReddits(boolean loadMore) {
        showLoader();
        new FeedLoader(loadMore).execute();
    }

    // hide loader
    private void hideAppLoader(boolean goToTopOfList, boolean showError) {
        hideLoader();
        // go to the top of the list view
        if (goToTopOfList) {
            listView.smoothScrollToPosition(0);
        }
        if (showError) {
            errorIcon.setVisibility(View.VISIBLE);
        }
    }

    public void hideLoader(){
        // get theme layout id
        loader.setVisibility(View.GONE);
    }

    public void showLoader() {
        errorIcon.setVisibility(View.GONE);
        loader.setVisibility(View.VISIBLE);
    }

    @Override
    public void onThemeResult(boolean updateTheme) {
        if (updateTheme) refreshTheme();
    }

    private class FeedLoader extends AsyncTask<Void, Integer, JSONArray> {

        private Boolean loadMore;
        private RedditData.RedditApiException exception;

        FeedLoader(Boolean loadmore) {
            loadMore = loadmore;
        }

        @Override
        protected JSONArray doInBackground(Void... none) {
            String curFeed = subredditPath;
            boolean isAll = subredditName.equals("all");
            String sort = subredditSort;
            JSONArray tempArray;
            endOfFeed = false;
            if (loadMore) {
                // fetch 25 more after current last item and append to the list
                try {
                    tempArray = global.mRedditData.getRedditFeed(curFeed, sort, 25, lastItemId);
                } catch (RedditData.RedditApiException e) {
                    e.printStackTrace();
                    exception = e;
                    return null;
                }
            } else {
                // reloading
                int limit = Integer.valueOf(global.mSharedPreferences.getString("numitemloadpref", "25"));
                try {
                    tempArray = global.mRedditData.getRedditFeed(curFeed, sort, limit, "0");
                } catch (RedditData.RedditApiException e) {
                    e.printStackTrace();
                    exception = e;
                    return null;
                }
            }
            // check if end of feed, if not, run filters and return data
            if (tempArray.length() == 0) {
                endOfFeed = true;
            } else {
                // exclude all non theme posts if viewThemes mode is true (used to browse themes on /r/reddinator)
                if (viewThemes) {
                    tempArray = filterThemes(tempArray);
                } else {
                    tempArray = global.getSubredditManager().filterFeed(0, tempArray, loadMore?data:null, isAll, !global.mRedditData.isLoggedIn());
                }
                if (tempArray.length() == 0)
                    endOfFeed = true;
            }
            // set last item id
            if (endOfFeed){
                lastItemId = "0";
            } else {
                try {
                    lastItemId = tempArray.getJSONObject(tempArray.length() - 1).getJSONObject("data").getString("name"); // name is actually the unique id we want
                } catch (JSONException e) {
                    lastItemId = "0"; // Could not get last item ID; perform a reload next time
                    e.printStackTrace();
                }
            }

            return tempArray;
        }

        private JSONArray filterThemes(JSONArray feed){
            JSONArray filtered = new JSONArray();
            for (int i=0; i<feed.length(); i++){
                try {
                    if (feed.getJSONObject(i).getJSONObject("data").getString("title").contains("[Theme]"))
                        filtered.put(feed.getJSONObject(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return filtered;
        }

        @Override
        protected void onPostExecute(JSONArray result) {
            if (result !=null) {
                // hide loader
                if (loadMore) {
                    hideAppLoader(false, false); // don't go to top of list
                    int i = 0;
                    while (i < result.length()) {
                        try {
                            data.put(result.get(i));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        i++;
                    }
                } else {
                    hideAppLoader(true, false); // go to top
                    data = result;
                }
                listAdapter.setFeed(data, !endOfFeed, hasMultipleSubs);
                //listView.invalidateViews();
                // save feed
                if (feedId>-1)
                    global.setFeed(feedId, data);
            } else {
                Toast.makeText(MainActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
                hideAppLoader(false, true); // don't go to top of list and show error icon
            }
        }
    }

}


