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

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.IconTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;
import com.kobakei.ratethisapp.RateThisApp;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.service.MailCheckService;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.tasks.LoadPostTask;
import au.com.wallaceit.reddinator.tasks.SavePostTask;
import au.com.wallaceit.reddinator.tasks.VoteTask;
import au.com.wallaceit.reddinator.ui.ActionbarFragmentActivity;
import au.com.wallaceit.reddinator.ui.HtmlDialog;
import au.com.wallaceit.reddinator.ui.RedditViewPager;
import au.com.wallaceit.reddinator.ui.SimpleTabsWidget;
import au.com.wallaceit.reddinator.ui.TabCommentsFragment;
import au.com.wallaceit.reddinator.ui.TabWebFragment;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.service.WidgetProvider;

public class ViewRedditActivity extends ActionbarFragmentActivity implements LoadPostTask.Callback, VoteTask.Callback {

    private Reddinator global;
    private SharedPreferences prefs;
    private MenuItem upvote;
    private MenuItem downvote;
    private MenuItem messageIcon;
    private AsyncTask loadPostTask;
    private JSONObject postInfo;
    private String userLikes = "null"; // string version of curvote, parsed when options menu generated.
    private String redditItemId;
    private String postUrl;
    private String postPermalink;
    private int curvote = 0;
    private int feedposition = -1;
    private int widgetId = -1;
    private ActionBar actionBar;
    private BroadcastReceiver inboxReceiver;
    private RedditPageAdapter pageAdapter;
    private SimpleTabsWidget tabsIndicator;
    private Resources resources;
    private int actionbarIconColor = Utilities.getActionbarIconColor();
    // info panel views
    private SlidingUpPanelLayout infoPanel;
    private TextView sourceText;
    private TextView titleText;
    private TextView votesText;
    private IconTextView votesIcon;
    private TextView commentsText;
    private IconTextView commentsIcon;
    private TextView infoText;
    private IconTextView lockButton;
    private IconTextView refreshButton;
    private IconTextView selfTextButton;
    private boolean viewsLocked = false;

    /**
     * (non-Javadoc)
     *
     * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    protected void onCreate(Bundle savedInstanceState) {
        // set window flags
        getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        // request loading bar first
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        super.onCreate(savedInstanceState);

        global = ((Reddinator) ViewRedditActivity.this.getApplicationContext());
        prefs = PreferenceManager.getDefaultSharedPreferences(ViewRedditActivity.this);
        resources = getResources();
        // get actionbar and set home button, pad the icon
        actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ImageView view = (ImageView) findViewById(android.R.id.home);
        if (view != null) {
            view.setPadding(5, 0, 5, 0);
        }
        // setup needed members
        if (getIntent().getAction()!=null && getIntent().getAction().equals(Intent.ACTION_VIEW)){
            // open post via url, extract permalink and postId
            Pattern pattern = Pattern.compile(".*reddit.com(/r/[^/]*/comments/([^/]*)/[^/]*/)");
            Matcher matcher = pattern.matcher(getIntent().getDataString());
            if (matcher.find()){
                //System.out.println(matcher.group(2)+" "+matcher.group(1));
                postPermalink = matcher.group(1);
                redditItemId = "t3_"+matcher.group(2);
            } else {
                Toast.makeText(this, "Could not decode post URL", Toast.LENGTH_LONG).show();
                this.finish();
                return;
            }
        } else {
            // from widget, app or account feed post click
            widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            feedposition = getIntent().getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1);

            redditItemId = getIntent().getStringExtra(WidgetProvider.ITEM_ID);
            postUrl = getIntent().getStringExtra(WidgetProvider.ITEM_URL);
            postPermalink = getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK);
            userLikes = getIntent().getStringExtra(WidgetProvider.ITEM_USERLIKES);
            // Get selected item from feed and user vote preference
            if (getIntent().getBooleanExtra("submitted", false)){
                userLikes = "true";
            }
        }
        // set content view
        setContentView(R.layout.activity_viewreddit);
        // Setup View Pager and widget
        final RedditViewPager viewPager = (RedditViewPager) findViewById(R.id.tab_content);
        pageAdapter = new RedditPageAdapter(getSupportFragmentManager());
        viewPager.setAdapter(pageAdapter);
        LinearLayout tabLayout = (LinearLayout) findViewById(R.id.tab_widget);
        tabsIndicator = new SimpleTabsWidget(ViewRedditActivity.this, tabLayout);
        tabsIndicator.setViewPager(viewPager);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
            @Override
            public void onPageSelected(int position) {
                Fragment fragment = pageAdapter.getRegisteredFragment(position);
                if (postUrl!=null && fragment!=null && fragment instanceof TabWebFragment) {
                    ((TabWebFragment) fragment).load();
                }
            }
            @Override
            public void onPageScrollStateChanged(int state) {}
        });
        if (getIntent().getBooleanExtra("view_comments", false) || prefs.getBoolean("commentsfirstpref", false)) {
            viewPager.setCurrentItem(1);
        } else {
            viewPager.setCurrentItem(0);
        }
        // setup info panel views
        infoPanel = (SlidingUpPanelLayout) findViewById(R.id.sliding_layout);
        infoPanel.setFadeOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                infoPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            }
        });
        sourceText = (TextView) findViewById(R.id.source_txt);
        votesText = (TextView) findViewById(R.id.votes_txt);
        votesIcon = (IconTextView) findViewById(R.id.votes_icon);
        commentsText = (TextView) findViewById(R.id.comments_txt);
        commentsIcon = (IconTextView) findViewById(R.id.comments_icon);
        titleText = (TextView) findViewById(R.id.post_title);
        infoText = (TextView) findViewById(R.id.info_txt);
        lockButton = (IconTextView) findViewById(R.id.lockbutton);
        lockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewsLocked = !viewsLocked;
                lockButton.setTextColor(viewsLocked ? Color.parseColor("#E06B6C") : Color.parseColor("#DBDBDB"));
                viewPager.setPagingEnabled(!viewsLocked);
            }
        });
        refreshButton = (IconTextView) findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTitle(R.string.loading);
                loadPostTask = new LoadPostTask(global, ViewRedditActivity.this).execute(postPermalink, "best");
            }
        });
        selfTextButton = (IconTextView) findViewById(R.id.selftext_button);
        // theme
        updateTheme();
        // load post data; once loaded comment data is passed to the comment fragment
        // The content view is also loaded if postUrl is not provided via extras
        loadPostTask = new LoadPostTask(global, this).execute(postPermalink, "best");

        // Init rate dialog
        RateThisApp.Config config = new RateThisApp.Config();
        config.setTitle(R.string.rate_reddinator);
        RateThisApp.init(config);
    }

    public void onStart(){
        super.onStart();
        // Monitor launch times and interval from installation
        RateThisApp.onStart(this);
        RateThisApp.showRateDialogIfNeeded(this);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == 6) {
            updateTheme();
            if (pageAdapter.getRegisteredFragment(1)!=null && pageAdapter.getRegisteredFragment(1) instanceof TabCommentsFragment)
                ((TabCommentsFragment) pageAdapter.getRegisteredFragment(1)).updateTheme();
            setResult(6);
        }
    }

    private void updateTheme(){
        ThemeManager.Theme theme = getCurrentTheme();
        int headerBg = Color.parseColor(theme.getValue("header_color"));
        int headerText = Color.parseColor(theme.getValue("header_text"));
        int defaultIcon = Color.parseColor(theme.getValue("default_icon"));
        tabsIndicator.setBackgroundColor(headerBg);
        tabsIndicator.setInidicatorColor(Color.parseColor(theme.getValue("tab_indicator")));
        tabsIndicator.setTextColor(headerText);
        // info panel
        findViewById(R.id.info_panel).setBackgroundColor(headerBg);
        lockButton.setTextColor(defaultIcon);
        refreshButton.setTextColor(defaultIcon);
        selfTextButton.setTextColor(defaultIcon);
        sourceText.setTextColor(headerText);
        titleText.setTextColor(headerText);
        infoText.setTextColor(headerText);
        infoText.setLinkTextColor(headerText);
        votesText.setTextColor(headerText);
        commentsText.setTextColor(headerText);
        votesIcon.setTextColor(Color.parseColor(theme.getValue("votes_icon")));
        commentsIcon.setTextColor(Color.parseColor(theme.getValue("comments_icon")));
    }

    public ThemeManager.Theme getCurrentTheme(){
        return global.mThemeManager.getActiveTheme("appthemepref");
    }

    public void onResume(){
        super.onResume();
        // Register receiver & check for new messages if logged in, enabled and due
        int checkPref = Integer.parseInt(prefs.getString("mail_check_pref", "300000"));
        if (global.mRedditData.isLoggedIn() || checkPref!=0)
        if ((global.mRedditData.getLastUserUpdateTime()+checkPref)<(new Date()).getTime()) {
            inboxReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // update inbox indicator
                    setInboxIcon();
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(MailCheckService.MAIL_CHECK_COMPLETE);
            registerReceiver(inboxReceiver, filter);

            MailCheckService.checkMail(ViewRedditActivity.this, MailCheckService.ACTIVITY_CHECK_ACTION);
        }

        setInboxIcon();
    }

    public void onPause(){
        super.onPause();
        if (inboxReceiver!=null) {
            unregisterReceiver(inboxReceiver);
            inboxReceiver = null;
        }
    }

    @Override
    public void finish() {
        // update widget voting icons if a vote has been placed
        if (widgetId>0){
            if (global.getItemUpdate()!=null){
                WidgetProvider.hideLoaderAndRefreshViews(this, widgetId, false);
            }
        }
        if (loadPostTask!=null)
            loadPostTask.cancel(false);
        ViewGroup view = (ViewGroup) getWindow().getDecorView();
        view.removeAllViews();
        super.finish();
    }

    public void onBackPressed() {
        TabWebFragment webFragment = (TabWebFragment) pageAdapter.getRegisteredFragment(0);
        if (webFragment != null)
        if (webFragment.mFullSView != null) {
            webFragment.mChromeClient.onHideCustomView();
        } else if (infoPanel.getPanelState()==SlidingUpPanelLayout.PanelState.EXPANDED) {
            infoPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        } else if (webFragment.mWebView.canGoBack()) {
            webFragment.mWebView.goBack();
        } else {
            webFragment.mWebView.stopLoading();
            webFragment.mWebView.loadData("", "text/html", "utf-8");
            this.finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.view_menu, menu);
        // set options menu view
        MenuItem accountItem = (menu.findItem(R.id.menu_account));
        if (global.mRedditData.isLoggedIn())
            accountItem.setTitle(global.mRedditData.getUsername());
        accountItem.setIcon(new IconDrawable(this, Iconify.IconValue.fa_reddit_square).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_share)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_share_alt).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_open)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_globe).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_save)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_save).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_submit)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_pencil).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_prefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_wrench).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_about)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_info_circle).color(actionbarIconColor).actionBarSize());
        // determine vote drawables
        upvote = menu.findItem(R.id.menu_upvote);
        downvote = menu.findItem(R.id.menu_downvote);
        setVoteIcons();
        // set inbox icon color based on inbox count
        messageIcon = (menu.findItem(R.id.menu_inbox));
        setInboxIcon();

        return super.onCreateOptionsMenu(menu);
    }

    private void setVoteIcons(){
        if (upvote!=null)
            if (userLikes!=null && !userLikes.equals("null")){
                if (userLikes.equals("true")) {
                    upvote.setIcon(new IconDrawable(this, Iconify.IconValue.fa_arrow_up).color(Color.parseColor(Reddinator.COLOR_UPVOTE_ACTIVE)).actionBarSize());
                    downvote.setIcon(new IconDrawable(this, Iconify.IconValue.fa_arrow_down).color(actionbarIconColor).actionBarSize());
                    curvote = 1;
                } else if (userLikes.equals("false")) {
                    upvote.setIcon(new IconDrawable(this, Iconify.IconValue.fa_arrow_up).color(actionbarIconColor).actionBarSize());
                    downvote.setIcon(new IconDrawable(this, Iconify.IconValue.fa_arrow_down).color(Color.parseColor(Reddinator.COLOR_DOWNVOTE_ACTIVE)).actionBarSize());
                    curvote = -1;
                }
            } else {
                upvote.setIcon(new IconDrawable(this, Iconify.IconValue.fa_arrow_up).color(actionbarIconColor).actionBarSize());
                downvote.setIcon(new IconDrawable(this, Iconify.IconValue.fa_arrow_down).color(actionbarIconColor).actionBarSize());
                curvote = 0;
            }
    }

    private void setInboxIcon(){
        if (messageIcon!=null){
            int inboxColor = global.mRedditData.getInboxCount()>0?Color.parseColor("#E06B6C"): actionbarIconColor;
            messageIcon.setIcon(new IconDrawable(this, Iconify.IconValue.fa_envelope).color(inboxColor).actionBarSize());
        }
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
                if (prefs.getBoolean("backbuttonpref", false)) {
                    onBackPressed();
                } else {
                    TabWebFragment webFragment = (TabWebFragment) pageAdapter.getRegisteredFragment(0);
                    webFragment.mWebView.stopLoading();
                    webFragment.mWebView.loadData("", "text/html", "utf-8");
                    this.finish();
                }
                break;

            case R.id.menu_upvote:
                upVote();
                break;

            case R.id.menu_downvote:
                downVote();
                break;

            case R.id.menu_account:
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(ViewRedditActivity.this, false);
                    Toast.makeText(ViewRedditActivity.this, "Reddit login required", Toast.LENGTH_LONG).show();
                } else {
                    Intent accnIntent = new Intent(ViewRedditActivity.this, AccountActivity.class);
                    startActivity(accnIntent);
                }
                break;

            case R.id.menu_inbox:
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(ViewRedditActivity.this, false);
                    Toast.makeText(ViewRedditActivity.this, "Reddit login required", Toast.LENGTH_LONG).show();
                } else {
                    Intent inboxIntent = new Intent(ViewRedditActivity.this, MessagesActivity.class);
                    if (global.mRedditData.getInboxCount() > 0) {
                        inboxIntent.setAction(MessagesActivity.ACTION_UNREAD);
                    }
                    startActivity(inboxIntent);
                }
                break;

            case R.id.menu_open:
                showOpenDialog();
                break;

            case R.id.menu_share:
                showShareDialog();
                break;

            case R.id.menu_save:
                ViewRedditActivity.this.setTitleText(resources.getString(R.string.saving)); // reset title
                (new SavePostTask(ViewRedditActivity.this, false, new Runnable() {
                    @Override
                    public void run() {
                        ViewRedditActivity.this.setTitleText(resources.getString(R.string.app_name)); // reset title
                    }
                })).execute("link", redditItemId);
                break;

            case R.id.menu_submit:
                Intent submitIntent = new Intent(ViewRedditActivity.this, SubmitActivity.class);
                startActivity(submitIntent);
                break;

            case R.id.menu_prefs:
                Intent intent = new Intent(ViewRedditActivity.this, PrefsActivity.class);
                intent.putExtra("fromapp", true);
                startActivityForResult(intent, 0);
                break;

            case R.id.menu_about:
                AboutDialog.show(this, true);
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // Open stuff
    public void openUrlExternally(String url) {
        Intent openintent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(openintent);
    }

    public void shareText(String txt) {
        Intent sendintent = new Intent(Intent.ACTION_SEND);
        sendintent.setAction(Intent.ACTION_SEND);
        sendintent.putExtra(Intent.EXTRA_TEXT, txt);
        sendintent.setType("text/plain");
        startActivity(Intent.createChooser(sendintent, resources.getString(R.string.share_with)));
    }

    public void showOpenDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ViewRedditActivity.this);
        builder.setMessage(resources.getString(R.string.open_link))
                .setNegativeButton(resources.getString(R.string.content), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        openUrlExternally(postUrl);
                    }
                })
                .setPositiveButton(resources.getString(R.string.reddit_page), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        openUrlExternally("https://reddit.com" + postPermalink);
                    }
                });
        builder.create().show();
    }

    public void showShareDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ViewRedditActivity.this);
        builder.setMessage(resources.getString(R.string.share_url))
                .setNegativeButton(resources.getString(R.string.content), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        shareText(postUrl);
                    }
                }).setPositiveButton(resources.getString(R.string.both), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        shareText(postUrl+"\nhttps://reddit.com" + postPermalink);
                    }
                })
                .setNeutralButton(resources.getString(R.string.reddit_page), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        shareText("https://reddit.com" + postPermalink);
                    }
                });
        builder.create().show();
    }

    public void setTitleText(final String title){

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                actionBar.setTitle(title);

            }
        });
    }

    // VOTING STUFF
    private boolean voteinprogress = false;

    public boolean voteInProgress() {
        return voteinprogress;
    }

    private void upVote() {
        VoteTask task = new VoteTask(global, ViewRedditActivity.this, redditItemId, 1, curvote);
        voteinprogress = true;
        ViewRedditActivity.this.setTitleText(resources.getString(R.string.voting));
        task.execute();
    }

    private void downVote() {
        VoteTask task = new VoteTask(global, ViewRedditActivity.this, redditItemId, -1, curvote);
        voteinprogress = true;
        ViewRedditActivity.this.setTitleText(resources.getString(R.string.voting));
        task.execute();
    }

    @Override
    public void onVoteComplete(boolean result, RedditData.RedditApiException exception, String redditId, int direction, int netVote, int listPosition) {
        ViewRedditActivity.this.setTitleText(resources.getString(R.string.app_name)); // reset title
        voteinprogress = false;
        if (result) {
            int iconColor = Utilities.getActionbarIconColor();
            curvote = direction;
            switch (direction) {
                case -1:
                    upvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_up).color(iconColor).actionBarSize());
                    downvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_down).color(Color.parseColor(Reddinator.COLOR_DOWNVOTE_ACTIVE)).actionBarSize());
                    break;

                case 0:
                    upvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_up).color(iconColor).actionBarSize());
                    downvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_down).color(iconColor).actionBarSize());
                    break;

                case 1:
                    upvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_up).color(Color.parseColor(Reddinator.COLOR_UPVOTE_ACTIVE)).actionBarSize());
                    downvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_down).color(iconColor).actionBarSize());
                    break;
            }
            setVoteUpdateRecord(redditId, Utilities.voteDirectionToString(direction), netVote);
        } else {
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(ViewRedditActivity.this, false);
            // show error
            Toast.makeText(ViewRedditActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setVoteUpdateRecord(String redditId, String val, int netVote) {
        if (feedposition>=0) {
            global.setItemUpdate(feedposition, redditId, val, netVote);
            // save in feed data
            global.setItemVote(prefs, widgetId, feedposition, redditId, val, netVote);
        }
    }

    /**
     * (non-Javadoc)
     *
     * @see android.support.v4.app.FragmentActivity#onSaveInstanceState(android.os.Bundle)
     */
    protected void onSaveInstanceState(Bundle outState) {
        //outState.putString("tab", mTabHost.getCurrentTabTag()); //save the tab selected
        super.onSaveInstanceState(outState);
    }

    private boolean commentsLoaded = false; // don't pass comments to comments view on subsequent reloads
    @Override
    public void onPostLoaded(JSONArray result, RedditData.RedditApiException exception) {
        if (result!=null){
            try {
                postInfo = result.getJSONObject(0).getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
                JSONArray comments = result.getJSONObject(1).getJSONObject("data").getJSONArray("children");
                // pass comments to fragment
                if (!commentsLoaded && pageAdapter.getRegisteredFragment(1) instanceof TabCommentsFragment){
                    TabCommentsFragment fragment = (TabCommentsFragment) pageAdapter.getRegisteredFragment(1);
                    fragment.loadFromData(postInfo, comments);
                    commentsLoaded = true;
                }
                // load content view and set vote icons if url was not passed in extras
                if (postUrl==null){
                    userLikes = postInfo.getString("likes");
                    postUrl = postInfo.getString("url");
                    // use reddit mobile view
                    if (postUrl.contains("//www.reddit.com/")){
                        postUrl = postUrl.replace("//www.reddit.com", global.getDefaultCommentsMobileSite().substring(6));
                    }
                    TabWebFragment webfragment = (TabWebFragment) pageAdapter.getRegisteredFragment(0);
                    webfragment.load(postUrl);
                    setVoteIcons();
                }
                populateInfoPanel();
                //System.out.println(postInfo.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Could not load post info: "+exception.getMessage(), Toast.LENGTH_LONG).show();
        }
        setTitle(R.string.app_name);
    }

    private void populateInfoPanel(){
        try {
            String source = postInfo.getString("subreddit")+" - "+postInfo.getString("domain");
            sourceText.setText(source);
            titleText.setText(Utilities.fromHtml(postInfo.getString("title")));

            String infoStr = getString(R.string.submitted_details, DateUtils.getRelativeDateTimeString(this, Math.round(postInfo.getDouble("created_utc")) * 1000, DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_ABBREV_ALL), postInfo.getString("author"));
            infoText.setText(Utilities.fromHtml(infoStr));
            infoText.setMovementMethod(LinkMovementMethod.getInstance());

            int score = postInfo.getInt("score");
            double ratio = postInfo.getDouble("upvote_ratio");
            votesText.setText(getResources().getQuantityString(R.plurals.vote_details, score, score, Math.round(ratio * 100)));
            int comments = postInfo.getInt("num_comments");
            commentsText.setText(getResources().getQuantityString(R.plurals.num_comments, comments, comments));

            final String selftext = postInfo.getString("selftext_html");
            if (!selftext.equals("null")){
                selfTextButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        String html = "<html><head><style type=\"text/css\"> a { word-wrap: break-word; } </style></head><body>";
                        html += Utilities.fromHtml(selftext).toString();
                        html += "</body></html>";
                        HtmlDialog.init(ViewRedditActivity.this, getString(R.string.post_text), html);

                    }
                });
                selfTextButton.setVisibility(View.VISIBLE);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    class RedditPageAdapter extends FragmentPagerAdapter {

        SparseArray<Fragment> registeredFragments = new SparseArray<>();

        public RedditPageAdapter(FragmentManager fragmentManager){
            super(fragmentManager);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position){
                case 0: return resources.getString(R.string.content);
                case 1: return resources.getString(R.string.comments);
            }
            return resources.getString(R.string.app_name);
        }

        @Override
        public Fragment getItem(int position) {
            if (registeredFragments.indexOfKey(position)>-1)
                return registeredFragments.get(position);
            String url;
            int fontsize;
            boolean commentsPref = prefs.getBoolean("commentsfirstpref", false);
            int preloadPref = Integer.parseInt(prefs.getString("preloadpref", "3"));
            switch (position) {
                default:
                case 0: // content
                    // use reddit mobile view
                    System.out.println(postUrl);
                    if (postUrl !=null && postUrl.contains("//www.reddit.com/")){
                        postUrl = postUrl.replace("//www.reddit.com", global.getDefaultCommentsMobileSite().substring(6));
                        System.out.println(postUrl);
                    }
                    fontsize = Integer.parseInt(prefs.getString("contentfontpref", "18"));
                    return TabWebFragment.init(postUrl, fontsize, (postUrl!=null && (!commentsPref || (preloadPref==3 || preloadPref==1))));
                case 1: // comments
                    if (prefs.getBoolean("commentswebviewpref", false)) {
                        // reddit
                        url = global.getDefaultCommentsMobileSite() + postPermalink;
                        fontsize = Integer.parseInt(prefs.getString("reddit_content_font_pref", "21"));
                        return TabWebFragment.init(url, fontsize, (commentsPref || preloadPref>1));
                    } else {
                        // native
                        return TabCommentsFragment.init(redditItemId, postPermalink); // don't load comments, initial data now populated via onPostLoaded
                    }
            }
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            registeredFragments.put(position, fragment);
            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            registeredFragments.remove(position);
            super.destroyItem(container, position, object);
        }

        public Fragment getRegisteredFragment(int position) {
            return registeredFragments.get(position);
        }

    }

}
