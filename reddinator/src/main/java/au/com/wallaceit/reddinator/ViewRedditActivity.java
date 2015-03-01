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
package au.com.wallaceit.reddinator;

import java.util.HashMap;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public class ViewRedditActivity extends FragmentActivity implements TabHost.OnTabChangeListener {

    private TabHost mTabHost;
    private HashMap<String, TabInfo> mapTabInfo = new HashMap<String, TabInfo>();
    private TabInfo mLastTab = null;
    private GlobalObjects global;
    private SharedPreferences prefs;
    private MenuItem upvote;
    private MenuItem downvote;

    private String userLikes = null; // string version of curvote, parsed when options menu generated.
    private String redditItemId;
    private int curvote = 0;
    private int feedposition = 0;
    private int widgetId = 0;

    private class TabInfo {
        private String tag;
        private Class<TabWebFragment> clss;
        private Bundle args;
        private Fragment fragment;

        TabInfo(String tag, Class<TabWebFragment> clazz, Bundle args) {
            this.tag = tag;
            this.clss = clazz;
            this.args = args;
        }

    }

    class TabFactory implements TabContentFactory {

        private final Context mContext;

        /**
         * @param context; activity context
         */
        public TabFactory(Context context) {
            mContext = context;
        }

        /**
         * (non-Javadoc)
         *
         * @see android.widget.TabHost.TabContentFactory#createTabContent(java.lang.String)
         */
        public View createTabContent(String tag) {
            View v = new View(mContext);
            v.setMinimumWidth(0);
            v.setMinimumHeight(0);
            return v;
        }

    }

    /**
     * (non-Javadoc)
     *
     * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        global = ((GlobalObjects) ViewRedditActivity.this.getApplicationContext());
        prefs = PreferenceManager.getDefaultSharedPreferences(ViewRedditActivity.this);
        // set window flags
        getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        // request loading bar first
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        // get actionbar and set home button, pad the icon
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ImageView view = (ImageView) findViewById(android.R.id.home);
        if (view != null) {
            view.setPadding(5, 0, 5, 0);
        }
        // set content view
        setContentView(R.layout.viewreddit);
        // Setup TabHost
        initialiseTabHost(savedInstanceState);
        if (savedInstanceState != null) {
            mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab")); //set the tab as per the saved state
        }
        redditItemId = getIntent().getStringExtra(WidgetProvider.ITEM_ID);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        feedposition = getIntent().getIntExtra("itemposition", 0);
        // Get selected item from feed and user vote preference
        JSONObject currentFeedItem = global.getFeedObject(prefs, widgetId, feedposition, redditItemId);
        try {
            userLikes = currentFeedItem.getString("likes");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //System.out.println("User likes post: " + userLikes);
    }

    public void onBackPressed() {
        TabWebFragment webview = (TabWebFragment) mapTabInfo.get(mTabHost.getCurrentTabTag()).fragment;
        if (webview.mFullSView != null) {
            webview.mChromeClient.onHideCustomView();
        } else if (webview.mWebView.canGoBack()) {
            webview.mWebView.goBack();
        } else {
            webview.mWebView.stopLoading();
            webview.mWebView.loadData("", "text/html", "utf-8");
            this.finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.sharemenu, menu);
        // set options menu view
        upvote = menu.findItem(R.id.menu_upvote);
        downvote = menu.findItem(R.id.menu_downvote);

        if (userLikes.equals("true")) {
            upvote.setIcon(R.drawable.upvote_active);
            curvote = 1;
        } else if (userLikes.equals("false")) {
            downvote.setIcon(R.drawable.downvote_active);
            curvote = -1;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                TabWebFragment webview = (TabWebFragment) mapTabInfo.get(mTabHost.getCurrentTabTag()).fragment;
                webview.mWebView.stopLoading();
                webview.mWebView.loadData("", "text/html", "utf-8");
                this.finish();
                return true;

            case R.id.menu_upvote:
                upVote();
                break;

            case R.id.menu_downvote:
                downVote();
                break;

            case R.id.menu_account:
                Intent accnIntent = new Intent(ViewRedditActivity.this, AccountWebView.class);
                startActivity(accnIntent);
                break;

            case R.id.menu_open:
                showOpenDialog();
                break;

            case R.id.menu_share:
                showShareDialog();
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
                        openUrlExternally("http://reddit.com" + getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK));
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
                })
                .setPositiveButton("Reddit Page", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        shareText("http://reddit.com" + getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK));
                    }
                });
        // Create the AlertDialog
        builder.create().show();
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
        ViewRedditActivity.this.setTitle("Voting...");
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
        ViewRedditActivity.this.setTitle("Voting...");
        task.execute();
    }

    class VoteTask extends AsyncTask<String, Integer, String> {
        private String redditid;
        private int direction;
        private int feedposition;

        public VoteTask(int position, String id, int dir) {
            redditid = id;
            direction = dir;
            feedposition = position;
        }

        @Override
        protected String doInBackground(String... strings) {
            try {
                return global.mRedditData.vote(redditid, direction);
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            ViewRedditActivity.this.setTitle("Reddinator"); // reset title
            voteinprogress = false;
            if (result.equals("OK")) {
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
            } else if (result.equals("LOGIN")) {
                global.mRedditData.initiateLogin(ViewRedditActivity.this);
            } else {
                // show error
                Toast.makeText(ViewRedditActivity.this, "API Error: " + result, Toast.LENGTH_LONG).show();
            }

        }

        private void setUpdateRecord(String val) {
            global.setItemUpdate(feedposition, redditid, val);
            // save in feed preferences
            global.setItemVote(prefs, widgetId, feedposition, redditid, val);
        }
    }

    /**
     * (non-Javadoc)
     *
     * @see android.support.v4.app.FragmentActivity#onSaveInstanceState(android.os.Bundle)
     */
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("tab", mTabHost.getCurrentTabTag()); //save the tab selected
        super.onSaveInstanceState(outState);
    }

    /**
     * Step 2: Setup TabHost
     */
    private void initialiseTabHost(Bundle args) {
        Bundle rargs = new Bundle();
        rargs.putBoolean("loadcom", true);
        //rargs.putString("cookie", global.mRedditData.getSessionCookie());
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();
        if (prefs.getString("widgetthemepref", "1").equals("1")) {
            mTabHost.getTabWidget().setBackgroundColor(Color.parseColor("#CEE3F8")); // set light theme
        } else {
            mTabHost.getTabWidget().setBackgroundColor(Color.parseColor("#5F99CF")); // set dark theme
        }
        // add tabs
        TabInfo tabInfo;
        ViewRedditActivity.addTab(this, this.mTabHost, this.mTabHost.newTabSpec("Tab1").setIndicator("Content"), (tabInfo = new TabInfo("Tab1", TabWebFragment.class, args)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        ViewRedditActivity.addTab(this, this.mTabHost, this.mTabHost.newTabSpec("Tab2").setIndicator("Reddit"), (tabInfo = new TabInfo("Tab2", TabWebFragment.class, rargs)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        // Default to first tab
        // get shared preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ViewRedditActivity.this);
        if (prefs.getBoolean("commentsfirstpref", false)) {
            this.mTabHost.setCurrentTab(1);
            this.onTabChanged("Tab2"); // load comments tab first
        } else {
            this.onTabChanged("Tab1");
        }
        // set change listener
        mTabHost.setOnTabChangedListener(this);
    }

    /**
     * @param activity;
     * @param tabHost;
     * @param tabSpec;
     */
    private static void addTab(ViewRedditActivity activity, TabHost tabHost, TabHost.TabSpec tabSpec, TabInfo tabInfo) {
        // Attach a Tab view factory to the spec
        tabSpec.setContent(activity.new TabFactory(activity));
        String tag = tabSpec.getTag();
        // Check to see if we already have a fragment for this tab, probably
        // from a previously saved state.  If so, deactivate it, because our
        // initial state is that a tab isn't shown.
        tabInfo.fragment = activity.getSupportFragmentManager().findFragmentByTag(tag);
        if (tabInfo.fragment != null && !tabInfo.fragment.isDetached()) {
            android.support.v4.app.FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
            ft.detach(tabInfo.fragment);
            ft.commit();
            activity.getSupportFragmentManager().executePendingTransactions();
        }

        tabHost.addTab(tabSpec);
    }

    /**
     * (non-Javadoc)
     *
     * @see android.widget.TabHost.OnTabChangeListener#onTabChanged(java.lang.String)
     */
    public void onTabChanged(String tag) {
        TabInfo newTab = this.mapTabInfo.get(tag);
        if (mLastTab != newTab) {
            android.support.v4.app.FragmentTransaction ft = this.getSupportFragmentManager().beginTransaction();
            if (mLastTab != null) {
                if (mLastTab.fragment != null) {
                    ft.detach(mLastTab.fragment);
                }
            }
            if (newTab != null) {
                if (newTab.fragment == null) {
                    newTab.fragment = Fragment.instantiate(this,
                            newTab.clss.getName(), newTab.args);
                    ft.add(R.id.realtabcontent, newTab.fragment, newTab.tag);
                } else {
                    ft.attach(newTab.fragment);
                }
            }

            mLastTab = newTab;
            ft.commit();
            this.getSupportFragmentManager().executePendingTransactions();
        }
    }

}
