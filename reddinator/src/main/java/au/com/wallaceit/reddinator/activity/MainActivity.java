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
import android.appwidget.AppWidgetManager;
import android.content.Context;
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

        View.OnClickListener srclick = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent srintent = new Intent(context, SubredditSelectActivity.class);
                startActivityForResult(srintent, 0);
            }
        };

        srtext.setOnClickListener(srclick);
        findViewById(R.id.app_logo).setOnClickListener(srclick);
        findViewById(R.id.appcaret).setOnClickListener(srclick);
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
        } else {
            subredditPath = global.getSubredditManager().getCurrentFeedPath(0);
            subredditName = global.getSubredditManager().getCurrentFeedName(0);
            subredditSort = prefs.getString("sort-app", "hot");
        }
        // Setup list adapter
        listView = (ListView) findViewById(R.id.applistview);
        listAdapter = new ReddinatorListAdapter(global, prefs);
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // open in the reddinator view
                Bundle extras = getItemExtras(position);
                if (extras == null) {
                    Toast.makeText(MainActivity.this, R.string.data_error, Toast.LENGTH_LONG).show();
                    return;
                }
                Intent clickIntent1 = new Intent(context, ViewRedditActivity.class);
                clickIntent1.putExtras(extras);
                context.startActivity(clickIntent1);
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
        srtext.setText(subredditName);

        // always load a temp feed (from intent action_view) and when there's no cached data or when the preference is set to always reload when opened
        if (feedId==-1 || (prefs.getBoolean("appreloadpref", false) || listAdapter.getCount() < 2))
            listAdapter.reloadReddits();
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
        (menu.findItem(R.id.menu_sidebar)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_book).color(iconColor).actionBarSize());
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

            case R.id.menu_sidebar:
                new LoadSubredditInfoTask(global, this).execute(subredditName);
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
                if (!global.getSubredditManager().isFeedMulti(0))
                    searchIntent.putExtra("feed_path", global.getSubredditManager().getCurrentFeedPath(0));
                startActivity(searchIntent);
                break;

            case R.id.menu_submit:
                Intent submitIntent = new Intent(this, SubmitActivity.class);
                startActivity(submitIntent);
                break;

            case R.id.menu_feedprefs:
                //showFeedPrefsDialog();
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
                srtext.setText(subredditName);
                listAdapter.loadFeedPrefs();
                listAdapter.reloadReddits();
                break;
            // initiate vote
            case 3:
            case 4:
                listAdapter.showAppLoader();
                int position = data.getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1);
                View view =  listView.getAdapter().getView(position, null, listView);
                if (view!=null) {
                    ListVoteTask listvote = new ListVoteTask((resultcode==3?1:-1), view, position);
                    listvote.execute();
                }
                break;
            // reload feed data from cache
            case 5:
                listAdapter.reloadFeedData();
                listView.invalidateViews();
                break;
        }
        if (data!=null && data.getBooleanExtra("themeupdate", true)){
            setThemeColors();
            listAdapter.loadTheme();
            listView.invalidateViews();
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
            extras.putString(WidgetProvider.ITEM_URL, item.getString("url"));
            extras.putString(WidgetProvider.ITEM_PERMALINK, item.getString("permalink"));
            extras.putString(WidgetProvider.ITEM_DOMAIN, item.getString("domain"));
            extras.putString(WidgetProvider.ITEM_SUBREDDIT, item.getString("subreddit"));
            extras.putString(WidgetProvider.ITEM_USERLIKES, item.getString("likes"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return extras;
    }

    @Override
    public void onSubredditInfoLoaded(JSONObject result, RedditData.RedditApiException exception) {
        if (result!=null){
            try {
                String html = result.getString("description_html");
                HtmlDialog.init(this, subredditPath, Html.fromHtml(html).toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public class ReddinatorListAdapter extends BaseAdapter {

        private JSONArray data;
        private Reddinator global;
        private SharedPreferences mSharedPreferences;
        private String titleFontSize = "16";
        private HashMap<String, Integer> themeColors;
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
                    Reddinator.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_expand.character()), themeColors.get("comments_text"), 12, shadow)
            };

            // get font size preference
            titleFontSize = mSharedPreferences.getString("titlefontpref", "16");
        }

        private void loadFeedPrefs() {
            // get thumbnail load preference for the widget
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
                    // create remote view from specified layout
                    if (bigThumbs) {
                        row = getLayoutInflater().inflate(R.layout.applistrowbigthumb, parent, false);
                    } else {
                        row = getLayoutInflater().inflate(R.layout.applistrow, parent, false);
                    }
                    ((ImageView) row.findViewById(R.id.votesicon)).setImageBitmap(images[0]);
                    ((ImageView) row.findViewById(R.id.commentsicon)).setImageBitmap(images[1]);
                    viewHolder.listheading = (TextView) row.findViewById(R.id.listheading);
                    viewHolder.sourcetxt = (TextView) row.findViewById(R.id.sourcetxt);
                    viewHolder.votestxt = (TextView) row.findViewById(R.id.votestxt);
                    viewHolder.commentstxt = (TextView) row.findViewById(R.id.commentstxt);
                    viewHolder.thumbview = (ImageView) row.findViewById(R.id.thumbnail);
                    viewHolder.thumbview_expand = (ImageView) row.findViewById(R.id.thumbnail_expand);
                    viewHolder.infview = row.findViewById(R.id.infbox);
                    viewHolder.upvotebtn = (ImageButton) row.findViewById(R.id.app_upvote);
                    viewHolder.downvotebtn = (ImageButton) row.findViewById(R.id.app_downvote);
                    viewHolder.nsfw = (TextView) row.findViewById(R.id.nsfwflag);
                } else {
                    viewHolder = (ViewHolder) row.getTag();
                }
                // collect data
                String name, thumbnail, domain, id, url, userLikes, subreddit;
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
                viewHolder.commentstxt.setTextColor(themeColors.get("comments_text"));
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

                // load thumbnail if they are enabled for this widget
                if (loadThumbnails) {
                    // check for thumbnail; detect default thumbnails
                    if (!thumbnail.equals("")) {
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
                            viewHolder.thumbview.setImageResource(resource);
                            viewHolder.thumbview.setVisibility(View.VISIBLE);
                            //System.out.println("Loading default image: "+thumbnail);
                        } else {
                            // check if the image is in cache
                            String fileurl = getCacheDir() + Reddinator.THUMB_CACHE_DIR + id + ".png";
                            if (new File(fileurl).exists()) {
                                Bitmap bitmap = BitmapFactory.decodeFile(fileurl);
                                if (bitmap == null) {
                                    viewHolder.thumbview.setVisibility(View.GONE);
                                } else {
                                    viewHolder.thumbview.setImageBitmap(bitmap);
                                    viewHolder.thumbview.setVisibility(View.VISIBLE);
                                }
                            } else {
                                // start the image load
                                loadImage(position, thumbnail, id);
                                viewHolder.thumbview.setVisibility(View.VISIBLE);
                                // set image source as default to prevent an image from a previous view being used
                                viewHolder.thumbview.setImageResource(android.R.drawable.screen_background_dark_transparent);
                            }
                        }
                        // check if url is image, if so, add ViewImageDialog intent and show indicator
                        if (Reddinator.isImageUrl(url)){
                            viewHolder.thumbview.setClickable(true);
                            viewHolder.thumbview.setOnClickListener(new View.OnClickListener() {
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
                            viewHolder.thumbview.setClickable(false);
                            viewHolder.thumbview_expand.setVisibility(View.GONE);
                        }
                    } else {
                        viewHolder.thumbview.setVisibility(View.GONE);
                        viewHolder.thumbview_expand.setVisibility(View.GONE);
                    }
                } else {
                    viewHolder.thumbview.setVisibility(View.GONE);
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

        private void loadImage(final int itempos, final String urlstr, final String redditid) {
            new ImageLoader(itempos, urlstr, redditid).execute();
        }

        class ViewHolder {
            TextView listheading;
            TextView sourcetxt;
            TextView votestxt;
            TextView commentstxt;
            ImageView thumbview;
            ImageView thumbview_expand;
            ImageButton upvotebtn;
            ImageButton downvotebtn;
            View infview;
            TextView nsfw;
        }

        class ImageLoader extends AsyncTask<Void, Integer, Bitmap> {
            int itempos;
            String urlstr;
            String redditid;

            ImageLoader(int position, String url, String id) {
                itempos = position;
                urlstr = url;
                redditid = id;
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
                View v = listView.getChildAt(itempos - listView.getFirstVisiblePosition());
                if (v == null) {
                    return;
                }
                // save bitmap to cache, the item name will be the reddit id
                global.saveThumbnailToCache(result, redditid);
                // update view if it's being shown
                ImageView img = ((ImageView) v.findViewById(R.id.thumbnail));
                if (img != null) {
                    if (result != null) {
                        img.setImageBitmap(result);
                    } else {
                        img.setVisibility(View.GONE);
                    }
                }
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

        class FeedLoader extends AsyncTask<Void, Integer, Long> {

            private Boolean loadMore;
            private RedditData.RedditApiException exception;

            public FeedLoader(Boolean loadmore) {
                loadMore = loadmore;
            }

            @Override
            protected Long doInBackground(Void... none) {
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
                        return (long) 0;
                    }
                    if (tempArray.length() == 0) {
                        endOfFeed = true;
                    } else {
                        tempArray = global.getSubredditManager().filterFeed(0, tempArray, data, isAll, !global.mRedditData.isLoggedIn());

                        int i = 0;
                        while (i < tempArray.length()) {
                            try {
                                data.put(tempArray.get(i));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            i++;
                        }
                    }
                } else {
                    // reloading
                    int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));
                    try {
                        tempArray = global.mRedditData.getRedditFeed(curFeed, sort, limit, "0");
                    } catch (RedditData.RedditApiException e) {
                        e.printStackTrace();
                        exception = e;
                        return (long) 0;
                    }
                    // check if end of feed, if not process & set feed data
                    if (tempArray.length() == 0) {
                        endOfFeed = true;
                    } else {
                        tempArray = global.getSubredditManager().filterFeed(0, tempArray, null, isAll, !global.mRedditData.isLoggedIn());
                    }
                    data = tempArray;
                }
                // save feed
                global.setFeed(prefs, feedId, data);
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
                return (long) 1;
            }

            @Override
            protected void onPostExecute(Long result) {
                if (result > 0) {
                    // hide loader
                    if (loadMore) {
                        hideAppLoader(false, false); // don't go to top of list
                    } else {
                        hideAppLoader(true, false); // go to top
                    }
                    listAdapter.notifyDataSetChanged();
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


