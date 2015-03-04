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
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
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

public class OAuthView extends Activity {
    WebView wv;
    WebViewClient wvclient;
    Activity mActivity;
    GlobalObjects global;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        global = ((GlobalObjects) OAuthView.this.getApplicationContext());

        String oauthstate = this.getIntent().getStringExtra("oauthstate");
        // request loading bar
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        mActivity = OAuthView.this;
        setContentView(R.layout.webview);
        mActivity.setTitle(R.string.loading);
        // set and load webview
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
                        showErrorDialog("Login to Reddit failed: " + oauthUri.getQueryParameter("error"));
                    }
                } else {
                    new LoginTask().execute(oauthUri);
                }
            }
        }
    }

    ProgressDialog loginDialog;

    class LoginTask extends AsyncTask<Uri, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            wv.setVisibility(View.INVISIBLE);
            loginDialog = ProgressDialog.show(OAuthView.this, "Authenticating", "Connecting to your reddit account", true);
        }

        protected Boolean doInBackground(Uri... uris) {
            String code = uris[0].getQueryParameter("code");
            String state = uris[0].getQueryParameter("state");
            try {
                return global.mRedditData.retrieveToken(code, state);
            } catch (RedditData.RedditApiException e) {
                global.showAlertDialog("API Error", e.getMessage());
                e.printStackTrace();
                return false;
            }
        }

        protected void onPostExecute(Boolean success) {
            OAuthView.this.loginDialog.dismiss();
            if (success) {
                OAuthView.this.finish();
            } else {
                showErrorDialog("Login to Reddit failed: " + RedditData.OAUTH_ERROR);
            }
        }
    }

    private void showErrorDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Login Error");
        builder.setMessage(message);
        builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                OAuthView.this.finish();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
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

