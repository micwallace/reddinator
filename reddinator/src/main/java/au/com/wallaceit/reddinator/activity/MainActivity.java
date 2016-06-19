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
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.IconTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.service.WidgetProvider;
import au.com.wallaceit.reddinator.tasks.LoadSubredditInfoTask;
import au.com.wallaceit.reddinator.ui.HtmlDialog;

public class MainActivity extends Activity implements LoadSubredditInfoTask.Callback {

    private Context context;
    private SharedPreferences prefs;
    private Reddinator global;
    private ReddinatorListAdapter listAdapter;
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
    private Bitmap[] images;
    private int feedId = 0; // 0 for normal feed, -1 for temp feed. Used to keep feed cache storage and settings separate from the main feed.
    private String subredditName;
    private String subredditPath;
    private String subredditSort;
    private boolean viewThemes = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = MainActivity.this;
        global = ((Reddinator) context.getApplicationContext());
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
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

        srtext = (TextView) findViewById(R.id.appsubreddittxt);

        // set theme colors
        setThemeColors();

        // setup button onclicks
        refreshbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listAdapter.reloadReddits();
            }
        });
        View.OnClickListener srclick;
        // check intent and set needed feed params accordingly
        if (getIntent().getAction()!=null && getIntent().getAction().equals(Intent.ACTION_VIEW)){
            // open reddit feed via url, extract path
            Pattern pattern = Pattern.compile(".*reddit.com(/r/([^/]*))");
            Matcher matcher = pattern.matcher(getIntent().getDataString());
            if (matcher.find()){
                //System.out.println(matcher.group(2)+" "+matcher.group(1));
                subredditPath = matcher.group(1);
                subredditName = matcher.group(2);
                subredditSort = "hot";
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
            subredditSort = prefs.getString("sort-app", "hot");
            srclick = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent srintent = new Intent(context, SubredditSelectActivity.class);
                    startActivityForResult(srintent, 0);
                }
            };
            findViewById(R.id.appcaret).setOnClickListener(srclick);
        }
        // don't add subreddit select click for temp feeds, feed prefs and sort are changable from the menu only in temp feeds
        srtext.setOnClickListener(srclick);
        findViewById(R.id.app_logo).setOnClickListener(srclick);
        // Setup list adapter
        listView = (ListView) findViewById(R.id.applistview);
        listAdapter = new ReddinatorListAdapter(global, prefs);
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                final Bundle extras = getItemExtras(position);
                if (extras == null) {
                    Toast.makeText(MainActivity.this, R.string.data_error, Toast.LENGTH_LONG).show();
                    return;
                }
                if ("reddinator".equals(extras.getString(WidgetProvider.ITEM_SUBREDDIT))){
                    try {
                        JSONObject postData = listAdapter.getItem(position);
                        if (viewThemes || postData.getString("title").indexOf("[Theme]")==0) {
                            // extract and parse json from theme
                            String postText = postData.getString("selftext");
                            Pattern pattern = Pattern.compile("reddinator_theme=(.*\\}\\})");
                            Matcher matcher = pattern.matcher(postText);
                            if (matcher.find()){
                                final JSONObject themeJson = new JSONObject(matcher.group(1));

                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                builder.setTitle(R.string.install_theme_title)
                                    .setMessage(R.string.install_theme_message)
                                    .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (global.mThemeManager.importTheme(themeJson)){
                                                Toast.makeText(MainActivity.this, R.string.theme_install_success, Toast.LENGTH_LONG).show();
                                            } else {
                                                Toast.makeText(MainActivity.this, R.string.theme_load_error, Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    })
                                    .setNeutralButton(R.string.preview, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (global.mThemeManager.setPreviewTheme(themeJson)){
                                                refreshTheme();
                                                new AlertDialog.Builder(MainActivity.this)
                                                        .setTitle(R.string.theme_preview)
                                                        .setMessage(R.string.theme_preview_applied_message)
                                                        .show().setCanceledOnTouchOutside(true);
                                            } else {
                                                Toast.makeText(MainActivity.this, R.string.theme_load_error, Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    })
                                    .setNegativeButton(R.string.view_comments_noicon, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            openPostView(extras, true);
                                        }
                                    });
                                builder.show();
                                return;
                            }
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
                Bundle extras = getItemExtras(position);
                if (extras == null) {
                    Toast.makeText(MainActivity.this, R.string.data_error, Toast.LENGTH_LONG).show();
                    return true;
                }
                Intent ointent = new Intent(MainActivity.this, FeedItemDialogActivity.class);
                ointent.putExtras(extras);
                MainActivity.this.startActivityForResult(ointent, 1);
                return true;
            }
        });

        // set the current subreddit name
        String heading = subredditName + (viewThemes?" themes":"");
        srtext.setText(heading);

        // always load a temp feed (from intent action_view) and when there's no cached data or when the preference is set to always reload when opened
        if (feedId==-1 || (prefs.getBoolean("appreloadpref", false) || listAdapter.getCount() < 2))
            listAdapter.reloadReddits();
    }

    public void openPostView(Bundle extras, boolean viewComments){
        Intent intent = new Intent(context, ViewRedditActivity.class);
        intent.putExtras(extras);
        if (viewComments)
            intent.putExtra("view_comments", true);
        context.startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Bundle update = global.getItemUpdate();
        if (update != null) {
            listAdapter.updateUiVote(update.getInt("position", 0), update.getString("id"), update.getString("val"));
        }
        if (messageIcon!=null){
            int inboxColor = global.mRedditData.getInboxCount()>0?Color.parseColor("#E06B6C"): Reddinator.getActionbarIconColor();
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
        int iconColor = Reddinator.getActionbarIconColor();
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
                startActivityForResult(intent, 2);
                break;

            case R.id.menu_prefs:
                Intent intent2 = new Intent(this, PrefsActivity.class);
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

    private boolean needsFeedViewUpdate = false;
    private void showFeedPrefsDialog(){
        final CharSequence[] names = {getString(R.string.image_previews), getString(R.string.thumbnails), getString(R.string.thumbnails_on_top), getString(R.string.hide_post_info)};
        final boolean[] initvalue = {prefs.getBoolean("imagepreviews-app", true), prefs.getBoolean("thumbnails-app", true), prefs.getBoolean("bigthumbs-app", false), prefs.getBoolean("hideinf-app", false)};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_feed_prefs));
        builder.setMultiChoiceItems(names, initvalue, new DialogInterface.OnMultiChoiceClickListener() {
            public void onClick(DialogInterface dialogInterface, int item, boolean state) {
                SharedPreferences.Editor prefsedit = prefs.edit();
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
                SharedPreferences.Editor prefsedit = prefs.edit();
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
                listAdapter.reloadReddits();
            }
        });
        builder.show().setCanceledOnTouchOutside(true);
    }

    private void setThemeColors() {

        theme = global.mThemeManager.getActiveTheme("appthemepref");

        appView.setBackgroundColor(Color.parseColor(theme.getValue("background_color")));
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor(theme.getValue("header_color"))));
        srtext.setTextColor(Color.parseColor(theme.getValue("header_text")));

        int iconColor = Color.parseColor(theme.getValue("default_icon"));
        int[] shadow = new int[]{3, 4, 4, Color.parseColor(theme.getValue("icon_shadow"))};

        refreshbutton.setTextColor(iconColor);
        refreshbutton.setShadowLayer(shadow[0], shadow[1], shadow[2], shadow[3]);
        ((IconTextView) findViewById(R.id.appcaret)).setTextColor(iconColor);
        ((IconTextView) findViewById(R.id.appcaret)).setTextColor(iconColor);
    }

    private void refreshTheme(){
        setThemeColors();
        listAdapter.loadTheme();
        listView.invalidateViews();
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
                feedId = 0;
                subredditName = global.getSubredditManager().getCurrentFeedName(0);
                subredditPath = global.getSubredditManager().getCurrentFeedPath(0);
                subredditSort = prefs.getString("sort-app", "hot");
                sortItem.setTitle(getString(R.string.sort_label)+" "+subredditSort);
                srtext.setText(subredditName);
                listAdapter.loadFeedPrefs();
                listAdapter.reloadReddits();
                break;
            // initiate vote
            case 3:
            case 4:
                if (data!=null) {
                    listAdapter.showAppLoader();
                    int position = data.getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1);
                    View view = listView.getAdapter().getView(position, null, listView);
                    if (view != null) {
                        ListVoteTask listvote = new ListVoteTask((resultcode == 3 ? 1 : -1), view, position);
                        listvote.execute();
                    }
                }
                break;
            // reload feed data from cache
            case 5:
                listAdapter.reloadFeedData();
                listView.invalidateViews();
                break;
        }
        if (resultcode==6 || (data!=null && data.getBooleanExtra("themeupdate", true))){
            refreshTheme();
        }
    }

    private Bundle getItemExtras(int position){
        JSONObject item = listAdapter.getItem(position);
        Bundle extras = new Bundle();
        if (item==null){
            return null;
        }
        try {
            extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, feedId);
            extras.putString(WidgetProvider.ITEM_ID, item.getString("name"));
            extras.putInt(WidgetProvider.ITEM_FEED_POSITION, position);
            extras.putString(WidgetProvider.ITEM_URL, StringEscapeUtils.unescapeHtml(item.getString("url")));
            extras.putString(WidgetProvider.ITEM_PERMALINK, item.getString("permalink"));
            extras.putString(WidgetProvider.ITEM_DOMAIN, item.getString("domain"));
            extras.putString(WidgetProvider.ITEM_SUBREDDIT, item.getString("subreddit"));
            extras.putString(WidgetProvider.ITEM_USERLIKES, item.getString("likes"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return extras;
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
                HtmlDialog.init(this, subredditPath, Html.fromHtml(html).toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Error loading sidebar: "+exception.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (sidebarProg!=null)
            sidebarProg.dismiss();
    }

    public class ReddinatorListAdapter extends BaseAdapter {

        private JSONArray data;
        private Reddinator global;
        private SharedPreferences mSharedPreferences;
        private String titleFontSize = "16";
        private HashMap<String, Integer> themeColors;
        private boolean loadPreviews = false;
        private boolean loadThumbnails = false;
        private boolean bigThumbs = false;
        private boolean hideInf = false;
        private boolean showItemSubreddit = false;

        protected ReddinatorListAdapter(Reddinator gobjects, SharedPreferences prefs) {

            global = gobjects;
            mSharedPreferences = prefs;
            // load the caches items
            if (feedId==0) {
                data = global.getFeed(mSharedPreferences, 0);
                //System.out.println("cached Data length: "+data.length());
                if (data.length() != 0) {
                    try {
                        lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name");
                    } catch (JSONException e) {
                        lastItemId = "0"; // Could not get last item ID; perform a reload next time and show error view :(
                        e.printStackTrace();
                    }
                }
            } else {
                data = new JSONArray();
            }
            // load preferences
            loadTheme();
            loadFeedPrefs();
        }

        private void loadTheme() {
            themeColors = theme.getIntColors();

            int[] shadow = new int[]{3, 3, 3, themeColors.get("icon_shadow")};
            // load images
            images = new Bitmap[]{
                    Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_star.character()), themeColors.get("votes_icon"), 12, shadow),
                    Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_comment.character()), themeColors.get("comments_icon"), 12, shadow),
                    Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_arrow_up.character()), Color.parseColor(Reddinator.COLOR_VOTE), 28, shadow),
                    Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_arrow_up.character()), Color.parseColor(Reddinator.COLOR_UPVOTE_ACTIVE), 28, shadow),
                    Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_arrow_down.character()), Color.parseColor(Reddinator.COLOR_VOTE), 28, shadow),
                    Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_arrow_down.character()), Color.parseColor(Reddinator.COLOR_DOWNVOTE_ACTIVE), 28, shadow),
                    Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_expand.character()), themeColors.get("comments_count"), 12, shadow)
            };

            // get font size preference
            titleFontSize = mSharedPreferences.getString("titlefontpref", "16");
        }

        private void loadFeedPrefs() {
            // get thumbnail load preference for the widget
            loadPreviews = mSharedPreferences.getBoolean("imagepreviews-app", true);
            loadThumbnails = mSharedPreferences.getBoolean("thumbnails-app", true);
            bigThumbs = mSharedPreferences.getBoolean("bigthumbs-app", false);
            hideInf = mSharedPreferences.getBoolean("hideinf-app", false);
            showItemSubreddit = feedId==0 && global.getSubredditManager().isFeedMulti(0);
        }

        @Override
        public int getCount() {
            return data.length()>0 ? (data.length() + 1) : 0; // plus 1 advertises the "load more" item to the listview without having to add it to the data source
        }

        @Override
        public View getView(final int position, View row, ViewGroup parent) {
            if (position > data.length()) {
                return null; //  prevent errornous views
            }
            // check if its the last view and return loading view instead of normal row
            if (position == data.length()) {
                // build load more item
                View loadmorerow = getLayoutInflater().inflate(R.layout.listrowloadmore, parent, false);
                TextView loadtxtview = (TextView) loadmorerow.findViewById(R.id.loadmoretxt);
                if (endOfFeed) {
                    loadtxtview.setText(R.string.nothing_more_here);
                } else {
                    loadtxtview.setText(R.string.load_more);
                }
                loadtxtview.setTextColor(themeColors.get("load_text"));
                loadmorerow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ((TextView) view.findViewById(R.id.loadmoretxt)).setText(R.string.loading);
                        loadMoreReddits();
                    }
                });
                return loadmorerow;
            } else {
                // inflate new view or load view holder if existing
                ViewHolder viewHolder = new ViewHolder();
                if (row == null || row.getTag() == null) {
                    // put views into viewholder
                    row = getLayoutInflater().inflate(R.layout.applistrow, parent, false);
                    ((ImageView) row.findViewById(R.id.votesicon)).setImageBitmap(images[0]);
                    ((ImageView) row.findViewById(R.id.commentsicon)).setImageBitmap(images[1]);
                    viewHolder.listheading = (TextView) row.findViewById(R.id.listheading);
                    viewHolder.sourcetxt = (TextView) row.findViewById(R.id.sourcetxt);
                    viewHolder.votestxt = (TextView) row.findViewById(R.id.votestxt);
                    viewHolder.commentstxt = (TextView) row.findViewById(R.id.commentstxt);
                    viewHolder.thumbview_top = (ImageView) row.findViewById(R.id.thumbnail_top);
                    viewHolder.thumbview = (ImageView) row.findViewById(R.id.thumbnail);
                    viewHolder.thumbview_expand = (ImageView) row.findViewById(R.id.thumbnail_expand);
                    viewHolder.preview = (ImageView) row.findViewById(R.id.preview);
                    viewHolder.infview = row.findViewById(R.id.infbox);
                    viewHolder.upvotebtn = (ImageButton) row.findViewById(R.id.app_upvote);
                    viewHolder.downvotebtn = (ImageButton) row.findViewById(R.id.app_downvote);
                    viewHolder.nsfw = (TextView) row.findViewById(R.id.nsfwflag);
                } else {
                    viewHolder = (ViewHolder) row.getTag();
                }
                // collect data
                String name, thumbnail, domain, id, url, userLikes, subreddit, previewUrl = null;
                int score;
                int numcomments;
                boolean nsfw;
                try {
                    JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
                    name = tempobj.getString("title");
                    id = tempobj.getString("name");
                    url = tempobj.getString("url");
                    domain = tempobj.getString("domain");
                    thumbnail = (String) tempobj.get("thumbnail"); // we have to call get and cast cause its not in quotes
                    score = tempobj.getInt("score");
                    numcomments = tempobj.getInt("num_comments");
                    userLikes = tempobj.getString("likes");
                    nsfw = tempobj.getBoolean("over_18");
                    subreddit = tempobj.getString("subreddit");
                    // check and select preview url
                    if (tempobj.has("preview")) {
                        JSONObject prevObj = tempobj.getJSONObject("preview");
                        if (prevObj.has("images")) {
                            JSONArray arr = prevObj.getJSONArray("images");
                            if (arr.length()>0) {
                                prevObj = arr.getJSONObject(0);
                                arr = prevObj.getJSONArray("resolutions");
                                // get third resolution (320px wide)
                                if (arr.length() > 0){
                                    prevObj = arr.length() < 3 ? arr.getJSONObject(arr.length() - 1) : arr.getJSONObject(2);
                                    previewUrl = Html.fromHtml(prevObj.getString("url")).toString();
                                } else {
                                    // or default to source
                                    previewUrl = Html.fromHtml(prevObj.getJSONObject("source").getString("url")).toString();
                                }
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    return row; // The view is invalid;
                }
                // Update view
                viewHolder.listheading.setText(Html.fromHtml(name).toString());
                viewHolder.listheading.setTextSize(Integer.valueOf(titleFontSize)); // use for compatibility setTextViewTextSize only introduced in API 16
                viewHolder.listheading.setTextColor(themeColors.get("headline_text"));
                String sourceText = (showItemSubreddit?subreddit+" - ":"")+domain;
                viewHolder.sourcetxt.setText(sourceText);
                viewHolder.sourcetxt.setTextColor(themeColors.get("source_text"));
                viewHolder.votestxt.setText(String.valueOf(score));
                viewHolder.votestxt.setTextColor(themeColors.get("votes_text"));
                viewHolder.commentstxt.setText(String.valueOf(numcomments));
                viewHolder.commentstxt.setTextColor(themeColors.get("comments_count"));
                viewHolder.nsfw.setVisibility((nsfw ? TextView.VISIBLE : TextView.GONE));
                row.findViewById(R.id.listdivider).setBackgroundColor(themeColors.get("divider"));
                // set vote button
                if (!userLikes.equals("null")) {
                    if (userLikes.equals("true")) {
                        viewHolder.upvotebtn.setImageBitmap(images[3]);
                        viewHolder.downvotebtn.setImageBitmap(images[4]);
                    } else {
                        viewHolder.upvotebtn.setImageBitmap(images[2]);
                        viewHolder.downvotebtn.setImageBitmap(images[5]);
                    }
                } else {
                    viewHolder.upvotebtn.setImageBitmap(images[2]);
                    viewHolder.downvotebtn.setImageBitmap(images[4]);
                }
                // Set vote onclick listeners
                viewHolder.upvotebtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listAdapter.showAppLoader();
                        view = (View) view.getParent().getParent();
                        ListVoteTask listvote = new ListVoteTask(1, view, position);
                        listvote.execute();
                    }
                });
                viewHolder.downvotebtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listAdapter.showAppLoader();
                        view = (View) view.getParent().getParent();
                        ListVoteTask listvote = new ListVoteTask(-1, view, position);
                        listvote.execute();
                    }
                });
                // Get thumbnail view & hide the other
                ImageView thumbView;
                if (bigThumbs){
                    thumbView = viewHolder.thumbview_top;
                    viewHolder.thumbview.setVisibility(View.GONE);
                } else {
                    thumbView = viewHolder.thumbview;
                    viewHolder.thumbview_top.setVisibility(View.GONE);
                }
                // check for preview images & thumbnails
                String imageUrl = null;
                int imageLoadFlag = 0; // 1 for thumbnail, 2 for preview, 3 for default thumbnail
                if (loadPreviews  && !nsfw && previewUrl!=null){
                    imageUrl = previewUrl;
                    imageLoadFlag = 2;
                    thumbView.setVisibility(View.GONE);
                    viewHolder.thumbview_expand.setVisibility(View.GONE);
                } else if (loadThumbnails && !thumbnail.equals("")) {
                    // hide preview view
                    viewHolder.preview.setVisibility(View.GONE);
                    // check for default thumbnails
                    if (thumbnail.equals("nsfw") || thumbnail.equals("self") || thumbnail.equals("default")) {
                        int resource = 0;
                        switch (thumbnail) {
                            case "nsfw":
                                resource = R.drawable.nsfw;
                                break;
                            case "default":
                            case "self":
                                resource = R.drawable.self_default;
                                break;
                        }
                        thumbView.setImageResource(resource);
                        thumbView.setVisibility(View.VISIBLE);
                        imageLoadFlag = 3;
                        //System.out.println("Loading default image: "+thumbnail);
                    } else {
                        imageUrl = thumbnail;
                        imageLoadFlag = 1;
                    }
                } else {
                    // hide preview and thumbnails
                    thumbView.setVisibility(View.GONE);
                    viewHolder.thumbview_expand.setVisibility(View.GONE);
                    viewHolder.preview.setVisibility(View.GONE);
                }
                // load external images into view
                if (imageLoadFlag>0){
                    ImageView imageView = imageLoadFlag == 1 ? thumbView : viewHolder.preview;
                    // skip if default thumbnail, just check for image
                    if (imageLoadFlag!=3) {
                        // check if the image is in cache
                        String fileurl = getCacheDir() + Reddinator.THUMB_CACHE_DIR + id + (imageLoadFlag == 2 ? "-preview" : "") + ".png";
                        if (new File(fileurl).exists()) {
                            Bitmap bitmap = BitmapFactory.decodeFile(fileurl);
                            if (bitmap == null) {
                                imageView.setVisibility(View.GONE);
                            } else {
                                imageView.setImageBitmap(bitmap);
                                imageView.setVisibility(View.VISIBLE);
                            }
                        } else {
                            // start the image load
                            loadImage(imageView, imageUrl, id + (imageLoadFlag == 2 ? "-preview" : ""));
                            imageView.setVisibility(View.VISIBLE);
                            // set image source as default to prevent an image from a previous view being used
                            imageView.setImageResource(android.R.drawable.screen_background_dark_transparent);
                        }
                    }
                    // check if url is image, if so, add ViewImageDialog intent and show indicator
                    if (Reddinator.isImageUrl(url)){
                        imageView.setClickable(true);
                        imageView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent intent = new Intent(MainActivity.this, ViewImageDialogActivity.class);
                                intent.putExtras(getItemExtras(position));
                                MainActivity.this.startActivity(intent);
                            }
                        });
                        viewHolder.thumbview_expand.setImageBitmap(images[6]);
                        viewHolder.thumbview_expand.setVisibility(View.VISIBLE);
                    } else {
                        imageView.setClickable(false);
                        viewHolder.thumbview_expand.setVisibility(View.GONE);
                    }
                }
                // hide info bar if options set
                if (hideInf) {
                    viewHolder.infview.setVisibility(View.GONE);
                } else {
                    viewHolder.infview.setVisibility(View.VISIBLE);
                }

                row.setTag(viewHolder);
            }
            //System.out.println("getViewAt("+position+");");
            return row;
        }

        @Override
        public JSONObject getItem(int position) {
            try {
                return data.getJSONObject(position).getJSONObject("data");
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }

        public void updateUiVote(int position, String id, String val) {
            try {
                // Incase the feed updated after opening reddinator view, check that the id's match to update the correct view.
                boolean recordexists = data.getJSONObject(position).getJSONObject("data").getString("name").equals(id);
                if (recordexists) {
                    // update in current data (already updated in saved feed)
                    data.getJSONObject(position).getJSONObject("data").put("likes", val);
                    // refresh view; unfortunately we have to refresh them all :( invalidateViewAtPosition(); please android?
                    listView.invalidateViews();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void loadImage(final ImageView view, final String urlstr, final String redditid) {
            new ImageLoader(view, urlstr, redditid).execute();
        }

        class ViewHolder {
            TextView listheading;
            TextView sourcetxt;
            TextView votestxt;
            TextView commentstxt;
            ImageView thumbview;
            ImageView thumbview_top;
            ImageView thumbview_expand;
            ImageView preview;
            ImageButton upvotebtn;
            ImageButton downvotebtn;
            View infview;
            TextView nsfw;
        }

        class ImageLoader extends AsyncTask<Void, Integer, Bitmap> {
            //int itempos;
            String urlstr;
            String redditid;
            ImageView imageView;

            ImageLoader(ImageView view, String url, String id) {
                //itempos = position;
                urlstr = url;
                redditid = id;
                imageView = view;
            }

            @Override
            protected Bitmap doInBackground(Void... voids) {
                URL url;
                try {
                    url = new URL(urlstr);
                    URLConnection con = url.openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(8000);
                    return BitmapFactory.decodeStream(con.getInputStream());
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    return null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap result) {
                /*View v = listView.getChildAt(itempos - listView.getFirstVisiblePosition());
                if (v == null) {
                    return;
                }*/
                // save bitmap to cache, the item name will be the reddit id
                global.saveThumbnailToCache(result, redditid);
                // update view if it's being shown
                if (imageView!=null){
                    if (result!=null){
                        imageView.setImageBitmap(result);
                    } else {
                        imageView.setVisibility(View.GONE);
                    }
                }

                /*ImageView img = ((ImageView) v.findViewById(R.id.thumbnail));
                if (img != null) {
                    if (result != null) {
                        img.setImageBitmap(result);
                    } else {
                        img.setVisibility(View.GONE);
                    }
                }*/
            }
        }

        @Override
        public int getViewTypeCount() {
            return (3);
        }

        @Override
        public long getItemId(int position) {
            return (position);
        }

        @Override
        public boolean hasStableIds() {
            return (false);
        }

        private String lastItemId = "0";
        private boolean endOfFeed = false;

        public void loadMoreReddits() {
            loadReddits(true);
        }

        public void reloadFeedData() {
            data = global.getFeed(mSharedPreferences, feedId);
        }

        public void reloadReddits() {
            global.triggerThunbnailCacheClean();
            loadReddits(false);
        }

        private void loadReddits(boolean loadMore) {
            showAppLoader();
            new FeedLoader(loadMore).execute();
        }

        class FeedLoader extends AsyncTask<Void, Integer, JSONArray> {

            private Boolean loadMore;
            private RedditData.RedditApiException exception;

            public FeedLoader(Boolean loadmore) {
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
                    int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));
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
                    listAdapter.notifyDataSetChanged();
                    // save feed
                    if (feedId>-1)
                        global.setFeed(prefs, feedId, data);
                    // set last item id
                    if (endOfFeed){
                        lastItemId = "0";
                    } else {
                        try {
                            lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name"); // name is actually the unique id we want
                        } catch (JSONException e) {
                            lastItemId = "0"; // Could not get last item ID; perform a reload next time
                            endOfFeed = true;
                            e.printStackTrace();
                        }
                    }
                } else {
                    Toast.makeText(context, exception.getMessage(), Toast.LENGTH_LONG).show();
                    hideAppLoader(false, true); // don't go to top of list and show error icon
                }
            }
        }

        // hide loader
        private void hideAppLoader(boolean goToTopOfList, boolean showError) {
            // get theme layout id
            loader.setVisibility(View.GONE);
            // go to the top of the list view
            if (goToTopOfList) {
                listView.smoothScrollToPosition(0);
            }
            if (showError) {
                errorIcon.setVisibility(View.VISIBLE);
            }
        }

        private void showAppLoader() {
            errorIcon.setVisibility(View.GONE);
            loader.setVisibility(View.VISIBLE);
        }
    }

    class ListVoteTask extends AsyncTask<String, Integer, Boolean> {
        JSONObject item;
        private String redditid;
        private int direction;
        private String curVote;
        private int listposition;
        private ImageButton upvotebtn;
        private ImageButton downvotebtn;
        private RedditData.RedditApiException exception;

        public ListVoteTask(int dir, View view, int position) {
            direction = dir;
            upvotebtn = (ImageButton) view.findViewById(R.id.app_upvote);
            downvotebtn = (ImageButton) view.findViewById(R.id.app_downvote);
            // Get data by position in list
            listposition = position;
            item = listAdapter.getItem(listposition);
            try {
                redditid = item.getString("name");
                curVote = item.getString("likes");
            } catch (JSONException e) {
                redditid = "null";
                curVote = "null";
            }
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            // enumerate current vote and clicked direction
            if (direction == 1) {
                if (curVote.equals("true")) { // if already upvoted, neutralize.
                    direction = 0;
                }
            } else { // downvote
                if (curVote.equals("false")) {
                    direction = 0;
                }
            }
            // Do the vote
            try {
                return global.mRedditData.vote(redditid, direction);
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                exception = e;
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                // set icon + current "likes" in the data array, this way ViewRedditActivity will get the new version without updating the hole feed.
                String value = "null";
                switch (direction) {
                    case -1:
                        upvotebtn.setImageBitmap(images[3]);
                        downvotebtn.setImageBitmap(images[5]);
                        value = "false";
                        break;

                    case 0:
                        upvotebtn.setImageBitmap(images[2]);
                        downvotebtn.setImageBitmap(images[4]);
                        value = "null";
                        break;

                    case 1:
                        upvotebtn.setImageBitmap(images[3]);
                        downvotebtn.setImageBitmap(images[4]);
                        value = "true";
                        break;
                }
                listAdapter.updateUiVote(listposition, redditid, value);
                global.setItemVote(prefs, 0, listposition, redditid, value);
            } else {
                // check login required
                if (exception.isAuthError()) global.mRedditData.initiateLogin(MainActivity.this, false);
                // show error
                Toast.makeText(MainActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
            }
            listAdapter.hideAppLoader(false, false);
        }
    }

}


