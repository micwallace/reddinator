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
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ShareActionProvider;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;

public class ViewRedditActivity extends FragmentActivity implements TabHost.OnTabChangeListener {
 
    private TabHost mTabHost;
    private HashMap<String, TabInfo> mapTabInfo = new HashMap<String, TabInfo>();
    private TabInfo mLastTab = null;

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
         * @param context
         */
        public TabFactory(Context context) {
            mContext = context;
        }
 
        /** (non-Javadoc)
         * @see android.widget.TabHost.TabContentFactory#createTabContent(java.lang.String)
         */
        public View createTabContent(String tag) {
            View v = new View(mContext);
            v.setMinimumWidth(0);
            v.setMinimumHeight(0);
            return v;
        }
 
    }
    /** (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
     // set window flags
   		getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
   		getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        // request loading bar first
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        // get actionbar and set home button, pad the icon
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(R.layout.viewredditheader);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        ImageView view = (ImageView)findViewById(android.R.id.home);
        if (view != null){
        	view.setPadding(5, 0, 5, 0);
        }
        // set content view
        setContentView(R.layout.viewreddit);
        // Setup TabHost
        initialiseTabHost(savedInstanceState);
        if (savedInstanceState != null) {
            mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab")); //set the tab as per the saved state
        }
    }
    
    public void onBackPressed(){
    	TabWebFragment webview = (TabWebFragment) mapTabInfo.get(mTabHost.getCurrentTabTag()).fragment;
    	if (webview.mFullSView != null){
            webview.mChromeClient.onHideCustomView();
    	} else if (webview.mWebView.canGoBack()){
        	webview.mWebView.goBack();
        } else {
        	webview.mWebView.stopLoading();
        	webview.mWebView.loadData("", "text/html", "utf-8");
        	this.finish();
        }
    }
    
    private ShareActionProvider shareActionProvider;
    @SuppressLint("NewApi")
	@Override
    public boolean onCreateOptionsMenu(Menu menu){
    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.sharemenu, menu);
    	if (android.os.Build.VERSION.SDK_INT >= 14){
    		// Get the menu item.
    	    MenuItem menuItem = menu.findItem(R.id.menu_share);
    	    // Get the provider and hold onto it to set/change the share intent.
    	    shareActionProvider = (ShareActionProvider) menuItem.getActionProvider();
    		// Set the default share intent
    	    shareActionProvider.setShareIntent(getCurrentShareIntent());
    	    shareActionProvider.setShareHistoryFileName(ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);
    	}
    	return true;
    }
    
    @SuppressLint("InlinedApi")
	public Intent getCurrentShareIntent(){
    	Intent shareIntent = new Intent(Intent.ACTION_SEND);
    	shareIntent.setType("text/plain");
    	String contenturl = getIntent().getStringExtra(WidgetProvider.ITEM_URL);
    	String redditurl = "http://reddit.com"+getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK);
    	shareIntent.putExtra(Intent.EXTRA_TEXT, "Content: "+contenturl+" \nReddit: "+redditurl);
    	return shareIntent;
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }
 
    /** (non-Javadoc)
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
        mTabHost = (TabHost)findViewById(android.R.id.tabhost);
        mTabHost.setup();
        // add tabs
        TabInfo tabInfo = null;
        ViewRedditActivity.addTab(this, this.mTabHost, this.mTabHost.newTabSpec("Tab1").setIndicator("Content"), (tabInfo = new TabInfo("Tab1", TabWebFragment.class, args)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        ViewRedditActivity.addTab(this, this.mTabHost, this.mTabHost.newTabSpec("Tab2").setIndicator("Reddit"), (tabInfo = new TabInfo("Tab2", TabWebFragment.class, rargs)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        // Default to first tab
        // get shared preferences
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
    	if (prefs.getBoolean("commentsfirstpref", false)){
    		this.mTabHost.setCurrentTab(1);
    		this.onTabChanged("Tab2"); // load comments tab first
    	} else {
    		this.onTabChanged("Tab1");
    	}
    	// set change listener
        mTabHost.setOnTabChangedListener(this);
    }
 
    /**
     * @param activity
     * @param tabHost
     * @param tabSpec
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
 
    /** (non-Javadoc)
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
