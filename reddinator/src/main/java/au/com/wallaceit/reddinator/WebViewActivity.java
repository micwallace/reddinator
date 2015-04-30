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

import android.app.ActionBar;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import java.lang.reflect.Method;

public class WebViewActivity extends Activity {
    WebView wv;
    WebViewClient wvclient;
    Activity mActivity;
    GlobalObjects global;

    public static final String ACTION_CLEAR_INBOX_COUNT= "clearInboxCount";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        global = ((GlobalObjects) WebViewActivity.this.getApplicationContext());
        // request loading bar
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        mActivity = WebViewActivity.this;
        setContentView(R.layout.webview);
        mActivity.setTitle(R.string.loading);
        // set and load webview
        wv = (WebView) findViewById(R.id.webView);
        wvclient = new WebViewClient();
        wv.setWebViewClient(wvclient);
        wv.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                mActivity.setProgress(progress * 100); //Make the bar disappear after URL is loaded
                // Return to the app name after loading
                if (progress == 100) {
                    mActivity.setTitle(R.string.app_name);
                }
            }
        });
        wv.getSettings().setJavaScriptEnabled(true);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        wv.getSettings().setDefaultFontSize(Integer.parseInt(prefs.getString("reddit_content_font_pref", "21")));
        // enable cookies
        CookieManager.getInstance().setAcceptCookie(true);
        // get url from extra
        String url = getIntent().getStringExtra("url");
        if (url==null){
            url = "http://www.reddit.com/.compact";
        }
        wv.loadUrl(url);
        // check for clear inbox flag
        String action = getIntent().getAction();
        if (action!=null)
        if (action.equals(ACTION_CLEAR_INBOX_COUNT)) {
            global.mRedditData.clearStoredInboxCount();
            // Also clear notification that may be present (created in CheckMailService)
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.cancel(1);
        }
    }

    public void onBackPressed() {
        if (wv.canGoBack()) {
            wv.goBack();
        } else {
            wv.stopLoading();
            wv.loadData("", "text/html", "utf-8");
            this.finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.webviewmenu, menu);
        // set options menu view
        int iconColor = Color.parseColor("#DBDBDB");
        (menu.findItem(R.id.menu_share)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_share_alt).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_open)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_globe).color(iconColor).actionBarSize());

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
                wv.stopLoading();
                wv.loadData("", "text/html", "utf-8");
                this.finish();
                break;

            case R.id.menu_open:
            openLink(wv.getUrl());
            break;

            case R.id.menu_share:
            shareLink(wv.getUrl());
            break;

            default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    public void openLink(String url) {
        Intent openintent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(openintent);
    }

    public void shareLink(String txt) {
        Intent sendintent = new Intent(Intent.ACTION_SEND);
        sendintent.setAction(Intent.ACTION_SEND);
        sendintent.putExtra(Intent.EXTRA_TEXT, txt);
        sendintent.setType("text/plain");
        startActivity(Intent.createChooser(sendintent, "Share Url with..."));
    }
}

