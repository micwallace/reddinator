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
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Date;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.service.MailCheckService;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.ui.SimpleTabsWidget;
import au.com.wallaceit.reddinator.ui.TabCommentsFragment;
import au.com.wallaceit.reddinator.ui.TabWebFragment;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.service.WidgetProvider;

public class ViewRedditActivity extends FragmentActivity {

    private Reddinator global;
    private SharedPreferences prefs;
    private MenuItem upvote;
    private MenuItem downvote;
    private MenuItem messageIcon;

    private String userLikes = null; // string version of curvote, parsed when options menu generated.
    private String redditItemId;
    private int curvote = 0;
    private int feedposition = 0;
    private int widgetId = 0;
    private ActionBar actionBar;
    private BroadcastReceiver inboxReceiver;
    private RedditPageAdapter pageAdapter;
    private SimpleTabsWidget tabsIndicator;
    public ThemeManager.Theme theme;

    /**
     * (non-Javadoc)
     *
     * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        global = ((Reddinator) ViewRedditActivity.this.getApplicationContext());
        prefs = PreferenceManager.getDefaultSharedPreferences(ViewRedditActivity.this);
        // set window flags
        getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        // request loading bar first
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
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
        if (prefs.getBoolean("commentsfirstpref", false)) {
            viewPager.setCurrentItem(1);
        } else {
            viewPager.setCurrentItem(0);
        }
        // theme
        updateTheme();
        // setup needed members
        redditItemId = getIntent().getStringExtra(WidgetProvider.ITEM_ID);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        feedposition = getIntent().getIntExtra("itemposition", -1);
        // Get selected item from feed and user vote preference
        if (getIntent().getBooleanExtra("submitted", false)){
            userLikes = "true";
        } else {
            JSONObject currentFeedItem = global.getFeedObject(prefs, widgetId, feedposition, redditItemId);
            if (currentFeedItem!=null) {
                try {
                    userLikes = currentFeedItem.getString("likes");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        //System.out.println("User likes post: " + userLikes);
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
        theme = getCurrentTheme();
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
        inflater.inflate(R.menu.viewmenu, menu);
        // set options menu view
        int iconColor;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            iconColor = Color.parseColor("#8F8F8F");
        } else {
            iconColor = Color.parseColor("#DBDBDB");
        }
        (menu.findItem(R.id.menu_account)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_reddit_square).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_share)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_share_alt).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_open)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_globe).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_save)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_save).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_submit)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_pencil).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_prefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_wrench).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_about)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_info_circle).color(iconColor).actionBarSize());
        // determine vote drawables
        upvote = menu.findItem(R.id.menu_upvote);
        downvote = menu.findItem(R.id.menu_downvote);
        if (userLikes.equals("true")) {
            upvote.setIcon(R.drawable.upvote_active);
            curvote = 1;
        } else if (userLikes.equals("false")) {
            downvote.setIcon(R.drawable.downvote_active);
            curvote = -1;
        }
        // set inbox icon color based on inbox count
        messageIcon = (menu.findItem(R.id.menu_inbox));
        setInboxIcon();

        return super.onCreateOptionsMenu(menu);
    }

    private void setInboxIcon(){
        if (messageIcon!=null){
            int inboxColor = global.mRedditData.getInboxCount()>0?Color.parseColor("#E06B6C"):Color.parseColor("#DBDBDB");
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
                Intent accnIntent = new Intent(ViewRedditActivity.this, WebViewActivity.class);
                accnIntent.putExtra("url", global.getDefaultMobileSite()+"/u/"+global.mRedditData.getUsername()+"/");
                startActivity(accnIntent);
                break;

            case R.id.menu_inbox:
                String url;
                Intent inboxIntent = new Intent(ViewRedditActivity.this, WebViewActivity.class);
                if (global.mRedditData.getInboxCount()>0) {
                    url = global.getDefaultMobileSite()+"/message/unread/";
                    inboxIntent.setAction(WebViewActivity.ACTION_CLEAR_INBOX_COUNT);
                } else {
                    url = global.getDefaultMobileSite()+"/message/inbox/";
                }
                inboxIntent.putExtra("url", url);
                startActivity(inboxIntent);
                break;

            case R.id.menu_open:
                showOpenDialog();
                break;

            case R.id.menu_share:
                showShareDialog();
                break;

            case R.id.menu_save:
                ViewRedditActivity.this.setTitleText("Saving..."); // reset title
                (new SavePostTask()).execute("link", redditItemId);
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
        startActivity(Intent.createChooser(sendintent, "Share Url to..."));
    }

    public void showOpenDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ViewRedditActivity.this);
        builder.setMessage("Open Url")
                .setNegativeButton("Content", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        openUrlExternally(getIntent().getStringExtra(WidgetProvider.ITEM_URL));
                    }
                })
                .setPositiveButton("Reddit Page", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        openUrlExternally("https://reddit.com" + getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK));
                    }
                });
        // Create the AlertDialog
        builder.create().show();
    }

    public void showShareDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ViewRedditActivity.this);
        builder.setMessage("Share Url")
                .setNegativeButton("Content", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        shareText(getIntent().getStringExtra(WidgetProvider.ITEM_URL));
                    }
                }).setPositiveButton("Both", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        shareText(getIntent().getStringExtra(WidgetProvider.ITEM_URL)+"\nhttps://reddit.com" + getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK));
                    }
                })
                .setNeutralButton("Reddit Page", new DialogInterface.OnClickListener() {
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
            task = new VoteTask(feedposition, redditItemId, 0);
            //System.out.println("Neutral Vote");
        } else {
            task = new VoteTask(feedposition, redditItemId, 1);
            //System.out.println("Upvote");
        }
        voteinprogress = true;
        ViewRedditActivity.this.setTitleText("Voting...");
        task.execute();
    }

    private void downVote() {
        VoteTask task;
        if (curvote == -1) {
            task = new VoteTask(feedposition, redditItemId, 0);
            //System.out.println("Neutral Vote");
        } else {
            task = new VoteTask(feedposition, redditItemId, -1);
            //System.out.println("Downvote");
        }
        voteinprogress = true;
        ViewRedditActivity.this.setTitleText("Voting...");
        task.execute();
    }

    class VoteTask extends AsyncTask<String, Integer, Boolean> {
        private String redditid;
        private int direction;
        private int feedposition;
        private RedditData.RedditApiException exception;

        public VoteTask(int position, String id, int dir) {
            redditid = id;
            direction = dir;
            feedposition = position;
        }

        @Override
        protected Boolean doInBackground(String... strings) {
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
            ViewRedditActivity.this.setTitleText("Reddinator"); // reset title
            voteinprogress = false;
            if (result) {
                curvote = direction;
                switch (direction) {
                    case -1:
                        upvote.setIcon(R.drawable.upvote);
                        downvote.setIcon(R.drawable.downvote_active);
                        setUpdateRecord("false");
                        break;

                    case 0:
                        upvote.setIcon(R.drawable.upvote);
                        downvote.setIcon(R.drawable.downvote);
                        setUpdateRecord("null");
                        break;

                    case 1:
                        upvote.setIcon(R.drawable.upvote_active);
                        downvote.setIcon(R.drawable.downvote);
                        setUpdateRecord("true");
                        break;
                }
            } else {
                // check login required
                if (exception.isAuthError()) global.mRedditData.initiateLogin(ViewRedditActivity.this);
                // show error
                Toast.makeText(ViewRedditActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        private void setUpdateRecord(String val) {
            if (feedposition>=0) {
                global.setItemUpdate(feedposition, redditid, val);
                // save in feed preferences
                global.setItemVote(prefs, widgetId, feedposition, redditid, val);
            }
        }
    }

    class SavePostTask extends AsyncTask<String, Long, Boolean> {
        private RedditData.RedditApiException exception;

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                global.mRedditData.save(params[0], params[1]);
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                exception = e;
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            ViewRedditActivity.this.setTitleText("Reddinator"); // reset title
            if (!result){
                // check login required
                if (exception.isAuthError()) global.mRedditData.initiateLogin(ViewRedditActivity.this);
                // show error
                Toast.makeText(ViewRedditActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
            }
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
                case 0: return "Content";
                case 1: return "Reddit";
            }
            return "Reddinator";
        }

        @Override
        public Fragment getItem(int position) {
            String url;
            int fontsize;
            boolean commentsPref = prefs.getBoolean("commentsfirstpref", false);
            int preloadPref = Integer.parseInt(prefs.getString("preloadpref", "3"));
            switch (position) {
                default:
                case 0: // content
                    url = getIntent().getStringExtra(WidgetProvider.ITEM_URL);
                    Log.w(getPackageName(), url);
                    // use reddit mobile view
                    if (url.contains("//www.reddit.com/")){
                        url = url.replace("//www.reddit.com", global.getDefaultCommentsMobileSite().substring(6));
                    }
                    fontsize = Integer.parseInt(prefs.getString("contentfontpref", "18"));
                    return TabWebFragment.init(url, fontsize, (!commentsPref || (preloadPref==3 || preloadPref==1)));
                case 1: // comments
                    if (prefs.getBoolean("commentswebviewpref", false)) {
                        // reddit
                        url = global.getDefaultCommentsMobileSite() + getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK);
                        fontsize = Integer.parseInt(prefs.getString("reddit_content_font_pref", "21"));
                        return TabWebFragment.init(url, fontsize, (commentsPref || preloadPref>1));
                    } else {
                        // native
                        return TabCommentsFragment.init((commentsPref || preloadPref>1));
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
