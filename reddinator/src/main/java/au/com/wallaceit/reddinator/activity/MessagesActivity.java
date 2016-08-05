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
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import java.lang.reflect.Method;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.ui.AccountFeedFragment;
import au.com.wallaceit.reddinator.ui.ActionbarFragmentActivity;
import au.com.wallaceit.reddinator.ui.SimpleTabsWidget;

public class MessagesActivity extends ActionbarFragmentActivity implements AccountFeedFragment.ActivityInterface {

    private Reddinator global;
    private ActionBar actionBar;
    private BroadcastReceiver inboxReceiver;
    private ViewPager viewPager;
    private RedditPageAdapter pageAdapter;
    private SimpleTabsWidget tabsIndicator;
    private Resources resources;
    private int actionbarIconColor = Reddinator.getActionbarIconColor();
    public static final String ACTION_UNREAD = "unread";

    /**
     * (non-Javadoc)
     *
     * @see FragmentActivity#onCreate(Bundle)
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    protected void onCreate(Bundle savedInstanceState) {
        // set window flags
        getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().requestFeature(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        super.onCreate(savedInstanceState);

        global = ((Reddinator) MessagesActivity.this.getApplicationContext());
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
        setContentView(R.layout.activity_messages);
        // Setup View Pager and widget
        viewPager = (ViewPager) findViewById(R.id.tab_content);
        pageAdapter = new RedditPageAdapter(getSupportFragmentManager());
        viewPager.setAdapter(pageAdapter);
        LinearLayout tabLayout = (LinearLayout) findViewById(R.id.tab_widget);
        tabsIndicator = new SimpleTabsWidget(MessagesActivity.this, tabLayout);
        tabsIndicator.setViewPager(viewPager);
        // theme
        updateTheme();

        if (getIntent().getAction()==null || !getIntent().getAction().equals(ACTION_UNREAD)){
            viewPager.setCurrentItem(1);
            tabsIndicator.setTab(1);
        }

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
            @Override
            public void onPageSelected(int position) {
                Fragment fragment = pageAdapter.getRegisteredFragment(position);
                if (fragment!=null)
                    ((AccountFeedFragment) fragment).load();
            }
            @Override
            public void onPageScrollStateChanged(int state) {}
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==0) {
            if (resultCode == 3) {
                updateTheme();
                Fragment fragment;
                for (int i = 0; i < pageAdapter.registeredFragments.size(); i++) {
                    fragment = pageAdapter.getRegisteredFragment(i);
                    if (fragment != null && fragment.getClass().getSimpleName().equals("AccountFeedFragment"))
                        ((AccountFeedFragment) fragment).updateTheme();
                }
            }
        } else if (requestCode==1){
            if (resultCode==1)
                reloadSentMessages();
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.messages_menu, menu);
        // set options menu view
        (menu.findItem(R.id.menu_submit)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_pencil).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_refresh)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_refresh).color(actionbarIconColor).actionBarSize());
        MenuItem accountItem = (menu.findItem(R.id.menu_account));
        if (global.mRedditData.isLoggedIn())
            accountItem.setTitle(global.mRedditData.getUsername());
        accountItem.setIcon(new IconDrawable(this, Iconify.IconValue.fa_reddit_square).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_viewonreddit)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_globe).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_prefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_wrench).color(actionbarIconColor).actionBarSize());
        (menu.findItem(R.id.menu_about)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_info_circle).color(actionbarIconColor).actionBarSize());

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
                this.finish();
                break;

            case R.id.menu_submit:
                Intent submitIntent = new Intent(MessagesActivity.this, ComposeMessageActivity.class);
                startActivityForResult(submitIntent, 1);
                break;

            case R.id.menu_account:
                Intent accnIntent = new Intent(MessagesActivity.this, AccountActivity.class);
                startActivity(accnIntent);
                break;

            case R.id.menu_refresh:
                ((AccountFeedFragment) pageAdapter.getRegisteredFragment(viewPager.getCurrentItem())).reload();
                break;

            case R.id.menu_viewonreddit:
                Intent inboxIntent = new Intent(MessagesActivity.this, WebViewActivity.class);
                inboxIntent.putExtra("url", global.getDefaultMobileSite()+"/message/inbox/");
                startActivity(inboxIntent);
                break;

            case R.id.menu_prefs:
                Intent intent = new Intent(MessagesActivity.this, PrefsActivity.class);
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

    public void setTitleText(final String title){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                actionBar.setTitle(title);
            }
        });
    }

    public void reloadSentMessages(){
        Fragment fragment = pageAdapter.getRegisteredFragment(2);
        if (fragment!=null)
            ((AccountFeedFragment) fragment).reload();
    }

    /**
     * (non-Javadoc)
     *
     * @see FragmentActivity#onSaveInstanceState(Bundle)
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
                case 0: return resources.getString(R.string.unread);
                case 1: return resources.getString(R.string.inbox);
                case 2: return resources.getString(R.string.sent);
            }
            return resources.getString(R.string.app_name);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                default:
                case 0:
                    return AccountFeedFragment.init("unread", (getIntent().getAction() != null && getIntent().getAction().equals(ACTION_UNREAD)));
                case 1:
                    return AccountFeedFragment.init("inbox", getIntent().getAction() == null);
                case 2:
                    return AccountFeedFragment.init("sent", false);
            }
        }

        @Override
        public int getCount() {
            return 3;
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
