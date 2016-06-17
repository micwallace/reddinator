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

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.service.MailCheckReceiver;

public class OAuthView extends Activity {
    WebView wv;
    WebViewClient wvclient;
    Activity mActivity;
    Reddinator global;
    Resources resources;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        global = ((Reddinator) OAuthView.this.getApplicationContext());
        resources = getResources();

        String oauthstate = this.getIntent().getStringExtra("oauthstate");
        // request loading bar
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        mActivity = OAuthView.this;
        setContentView(R.layout.activity_webview);
        mActivity.setTitle(R.string.loading);
        // set and load activity_webview
        wv = (WebView) findViewById(R.id.webView);
        wvclient = new OverrideClient();

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
        wv.getSettings().setDomStorageEnabled(true);
        //wv.getSettings().setDefaultFontSize(22);
        // enable cookies
        CookieManager.getInstance().setAcceptCookie(true);

        wv.loadUrl("https://www.reddit.com/api/v1/authorize.compact?client_id=" + RedditData.OAUTH_CLIENTID + "&response_type=code&state=" + oauthstate + "&redirect_uri=" + RedditData.OAUTH_REDIRECT + "&duration=permanent&scope=" + RedditData.OAUTH_SCOPES);
    }

    class OverrideClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url != null && url.startsWith(RedditData.OAUTH_REDIRECT)) {
                // System.out.println("Processing incoming oauth request: " + url);
                Uri oauthUri = Uri.parse(url);
                if (oauthUri.getQueryParameter("error") != null) {
                    if (oauthUri.getQueryParameter("error").equals("access_denied")) {
                        OAuthView.this.finish();
                    } else {
                        Toast.makeText(OAuthView.this, resources.getString(R.string.reddit_login_failed) + oauthUri.getQueryParameter("error"), Toast.LENGTH_LONG).show();
                    }
                } else {
                    new LoginTask().execute(oauthUri);
                }
            }
            super.onPageStarted(view, url, favicon);
        }
    }

    ProgressDialog loginDialog;

    class LoginTask extends AsyncTask<Uri, String, Boolean> {
        RedditData.RedditApiException exception;
        boolean loginSuccess = false;

        @Override
        protected void onPreExecute() {
            wv.setVisibility(View.INVISIBLE);
            loginDialog = ProgressDialog.show(OAuthView.this, resources.getString(R.string.authenticating), resources.getString(R.string.connecting_reddit_account), true);
        }

        protected Boolean doInBackground(Uri... uris) {
            String code = uris[0].getQueryParameter("code");
            String state = uris[0].getQueryParameter("state");
            try {
                global.mRedditData.retrieveToken(code, state);
                loginSuccess = true;
                // load subreddits & multis
                publishProgress(resources.getString(R.string.loading_subreddits));
                global.loadAccountSubreddits();
                publishProgress(resources.getString(R.string.loading_multis));
                global.loadAccountMultis();
                return true;
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                exception = e;
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(String... statusText){
            loginDialog.setTitle(resources.getString(R.string.loading));
            loginDialog.setMessage(statusText[0]);
        }

        protected void onPostExecute(Boolean success) {
            OAuthView.this.loginDialog.dismiss();
            if (!success){
                if (!loginSuccess){
                    Toast.makeText(OAuthView.this, resources.getString(R.string.reddit_login_failed) + "\n" + exception.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                } else {
                    Toast.makeText(OAuthView.this, resources.getString(R.string.account_sub_load_failed) + "\n" + exception.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            // add mail check alarm
            MailCheckReceiver.setAlarm(OAuthView.this);
            OAuthView.this.finish();
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
    public void onDestroy(){
        super.onDestroy();
        if (wv != null) {
            wv.removeAllViews();
            wv.destroy();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                wv.stopLoading();
                wv.loadData("", "text/html", "utf-8");
                this.finish();
                return true;
        }
        return false;
    }

}

