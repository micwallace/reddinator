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
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;
import com.kobakei.ratethisapp.RateThisApp;

import java.lang.reflect.Method;
import java.util.Date;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.service.MailCheckService;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.tasks.SavePostTask;
import au.com.wallaceit.reddinator.tasks.VoteTask;
import au.com.wallaceit.reddinator.ui.SimpleTabsWidget;
import au.com.wallaceit.reddinator.ui.TabCommentsFragment;
import au.com.wallaceit.reddinator.ui.TabWebFragment;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.service.WidgetProvider;

public class ViewRedditActivity extends FragmentActivity implements VoteTask.Callback {

    private Reddinator global;
    private SharedPreferences prefs;
    private MenuItem upvote;
    private MenuItem downvote;
    private MenuItem messageIcon;
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
    private int actionbarIconColor = Reddinator.getActionbarIconColor();

    public static final String ACTION_VIEW_POST = "view_post";

    /**
     * (non-Javadoc)
     *
     * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // set window flags
        getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        // request loading bar first
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
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
        // set content view
        setContentView(R.layout.viewreddit);
        // Setup View Pager and widget
        ViewPager viewPager = (ViewPager) findViewById(R.id.tab_content);
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
                if (fragment!=null && fragment instanceof TabWebFragment) {
                    ((TabWebFragment) fragment).load();
                } else if (fragment instanceof TabCommentsFragment) {
                    ((TabCommentsFragment) fragment).load();
                }
            }
            @Override
            public void onPageScrollStateChanged(int state) {}
        });
        if (prefs.getBoolean("commentsfirstpref", false)) {
            viewPager.setCurrentItem(1);
        } else {
            viewPager.setCurrentItem(0);
        }
        // theme
        updateTheme();
        // setup needed members
        if (getIntent().getAction()!=null && getIntent().getAction().equals(ACTION_VIEW_POST)){
            redditItemId = getIntent().getStringExtra("id");
            postUrl = getIntent().getStringExtra("url");
            postPermalink = getIntent().getStringExtra("permalink");
            userLikes = getIntent().getStringExtra("likes");
        } else {
            redditItemId = getIntent().getStringExtra(WidgetProvider.ITEM_ID);
            postUrl = getIntent().getStringExtra(WidgetProvider.ITEM_URL);
            postPermalink = getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK);
            widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            feedposition = getIntent().getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1);
            userLikes = getIntent().getStringExtra(WidgetProvider.ITEM_USERLIKES);
            // Get selected item from feed and user vote preference
            if (getIntent().getBooleanExtra("submitted", false)){
                userLikes = "true";
            }
        }
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
        if (resultCode == 3) {
            updateTheme();
            if (pageAdapter.getRegisteredFragment(1)!=null && pageAdapter.getRegisteredFragment(1).getClass().getSimpleName().equals("TabCommentsFragment"))
                ((TabCommentsFragment) pageAdapter.getRegisteredFragment(1)).updateTheme();
        }
    }

    private void updateTheme(){
        ThemeManager.Theme theme = getCurrentTheme();
        tabsIndicator.setBackgroundColor(Color.parseColor(theme.getValue("header_color")));
        tabsIndicator.setInidicatorColor(Color.parseColor(theme.getValue("tab_indicator")));
        tabsIndicator.setTextColor(Color.parseColor(theme.getValue("header_text")));
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
        ViewGroup view = (ViewGroup) getWindow().getDecorView();
        view.removeAllViews();
        super.finish();
    }

    public void onBackPressed() {
        TabWebFragment webFragment = (TabWebFragment) pageAdapter.getRegisteredFragment(0);
        if (webFragment != null)
        if (webFragment.mFullSView != null) {
            webFragment.mChromeClient.onHideCustomView();
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

        (menu.findItem(R.id.menu_account)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_reddit_square).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_share)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_share_alt).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_open)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_globe).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_save)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_save).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_submit)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_pencil).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_prefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_wrench).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_about)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_info_circle).color(actionbarIconColor).actionBarSize());
        // determine vote drawables
        upvote = menu.findItem(R.id.menu_upvote);
        downvote = menu.findItem(R.id.menu_downvote);
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
        // set inbox icon color based on inbox count
        messageIcon = (menu.findItem(R.id.menu_inbox));
        setInboxIcon();

        return super.onCreateOptionsMenu(menu);
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
                Intent accnIntent = new Intent(ViewRedditActivity.this, AccountActivity.class);
                startActivity(accnIntent);
                break;

            case R.id.menu_inbox:
                Intent inboxIntent = new Intent(ViewRedditActivity.this, MessagesActivity.class);
                if (global.mRedditData.getInboxCount()>0) {
                    inboxIntent.setAction(MessagesActivity.ACTION_UNREAD);
                }
                startActivity(inboxIntent);
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
                Reddinator.showInfoDialog(this, true);
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
                        openUrlExternally(getIntent().getStringExtra(WidgetProvider.ITEM_URL));
                    }
                })
                .setPositiveButton(resources.getString(R.string.reddit_page), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        openUrlExternally("https://reddit.com" + getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK));
                    }
                });
        // Create the AlertDialog
        builder.create().show();
    }

    public void showShareDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ViewRedditActivity.this);
        builder.setMessage(resources.getString(R.string.share_url))
                .setNegativeButton(resources.getString(R.string.content), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        shareText(getIntent().getStringExtra(WidgetProvider.ITEM_URL));
                    }
                }).setPositiveButton(resources.getString(R.string.both), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        shareText(getIntent().getStringExtra(WidgetProvider.ITEM_URL)+"\nhttps://reddit.com" + getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK));
                    }
                })
                .setNeutralButton(resources.getString(R.string.reddit_page), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        shareText("https://reddit.com" + getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK));
                    }
                });
        // Create the AlertDialog
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
        VoteTask task;
        if (curvote == 1) {
            task = new VoteTask(global, ViewRedditActivity.this, redditItemId, 0);
            //System.out.println("Neutral Vote");
        } else {
            task = new VoteTask(global, ViewRedditActivity.this, redditItemId, 1);
            //System.out.println("Upvote");
        }
        voteinprogress = true;
        ViewRedditActivity.this.setTitleText(resources.getString(R.string.voting));
        task.execute();
    }

    private void downVote() {
        VoteTask task;
        if (curvote == -1) {
            task = new VoteTask(global, ViewRedditActivity.this, redditItemId, 0);
            //System.out.println("Neutral Vote");
        } else {
            task = new VoteTask(global, ViewRedditActivity.this, redditItemId, -1);
            //System.out.println("Downvote");
        }
        voteinprogress = true;
        ViewRedditActivity.this.setTitleText(resources.getString(R.string.voting));
        task.execute();
    }

    @Override
    public void onVoteComplete(boolean result, RedditData.RedditApiException exception, String redditId, int direction) {
        ViewRedditActivity.this.setTitleText(resources.getString(R.string.app_name)); // reset title
        voteinprogress = false;
        if (result) {
            int iconColor = Reddinator.getActionbarIconColor();
            curvote = direction;
            switch (direction) {
                case -1:
                    upvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_up).color(iconColor).actionBarSize());
                    downvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_down).color(Color.parseColor(Reddinator.COLOR_DOWNVOTE_ACTIVE)).actionBarSize());
                    setVoteUpdateRecord(redditId, "false");
                    break;

                case 0:
                    upvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_up).color(iconColor).actionBarSize());
                    downvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_down).color(iconColor).actionBarSize());
                    setVoteUpdateRecord(redditId, "null");
                    break;

                case 1:
                    upvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_up).color(Color.parseColor(Reddinator.COLOR_UPVOTE_ACTIVE)).actionBarSize());
                    downvote.setIcon(new IconDrawable(ViewRedditActivity.this, Iconify.IconValue.fa_arrow_down).color(iconColor).actionBarSize());
                    setVoteUpdateRecord(redditId, "true");
                    break;
            }
        } else {
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(ViewRedditActivity.this, false);
            // show error
            Toast.makeText(ViewRedditActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setVoteUpdateRecord(String redditId, String val) {
        if (feedposition>=0) {
            global.setItemUpdate(feedposition, redditId, val);
            // save in feed data
            global.setItemVote(prefs, widgetId, feedposition, redditId, val);
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

    class RedditPageAdapter extends FragmentPagerAdapter {

        SparseArray<Fragment> registeredFragments = new SparseArray<>();

        public RedditPageAdapter(FragmentManager fragmentManager){
            super(fragmentManager);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position){
                case 0: return resources.getString(R.string.content);
                case 1: return resources.getString(R.string.reddit);
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

                    Log.w(getPackageName(), postUrl);
                    // use reddit mobile view
                    if (postUrl.contains("//www.reddit.com/")){
                        postUrl = postUrl.replace("//www.reddit.com", global.getDefaultCommentsMobileSite().substring(6));
                    }
                    fontsize = Integer.parseInt(prefs.getString("contentfontpref", "18"));
                    return TabWebFragment.init(postUrl, fontsize, (!commentsPref || (preloadPref==3 || preloadPref==1)));
                case 1: // comments
                    if (prefs.getBoolean("commentswebviewpref", false)) {
                        // reddit
                        url = global.getDefaultCommentsMobileSite() + postPermalink;
                        fontsize = Integer.parseInt(prefs.getString("reddit_content_font_pref", "21"));
                        return TabWebFragment.init(url, fontsize, (commentsPref || preloadPref>1));
                    } else {
                        // native
                        return TabCommentsFragment.init(redditItemId, postPermalink, (commentsPref || preloadPref>1));
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
