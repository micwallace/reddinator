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

package au.com.wallaceit.reddinator.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.activity.AccountActivity;
import au.com.wallaceit.reddinator.activity.ViewRedditActivity;
import au.com.wallaceit.reddinator.activity.WebViewActivity;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.tasks.CommentTask;
import au.com.wallaceit.reddinator.tasks.MarkMessageTask;
import au.com.wallaceit.reddinator.tasks.VoteTask;

public class AccountFeedFragment extends Fragment implements VoteTask.Callback, CommentTask.Callback {
    private Context mContext;
    private Resources resources;
    public WebView mWebView;
    private boolean mFirstTime = true;
    private LinearLayout ll;
    private Reddinator global;
    private boolean isMessages = false;
    private String type; // end part of the reddit url ie. overview, upvoted, downvoted, inbox, sent etc
    private String currentSort = "new";
    FeedLoader feedLoader;
    VoteTask commentsVoteTask;
    CommentTask commentTask;

    public static AccountFeedFragment init(String type, boolean load) {
        AccountFeedFragment commentsTab = new AccountFeedFragment();
        Bundle args = new Bundle();
        args.putBoolean("load", load);
        args.putString("type", type);
        commentsTab.setArguments(args);
        return commentsTab;
    }

    boolean loaded = false;
    public void load(){
        if (!loaded) {
            loadComments("new");
            loaded = true;
        }
    }

    public void reload(){
        mWebView.loadUrl("javascript:loadFeedStart();");
        loadComments(null);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this.getActivity();
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        global = (Reddinator) mContext.getApplicationContext();
        resources = getResources();
        final boolean load = getArguments().getBoolean("load");
        type = getArguments().getString("type");

        ll = new LinearLayout(mContext);
        ll.setLayoutParams(new WebView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0));
        // fixes for webview not taking keyboard input on some devices
        mWebView = new WebView(mContext);
        mWebView.setLayoutParams(new WebView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0));
        ll.addView(mWebView);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true); // enable ecmascript
        webSettings.setDomStorageEnabled(true); // some video sites require dom storage
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        int fontSize = Integer.parseInt(mSharedPreferences.getString("commentfontpref", "18"));
        webSettings.setDefaultFontSize(fontSize);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        mSharedPreferences.getString("titlefontpref", "16");

        final String themeStr = global.mThemeManager.getActiveTheme("appthemepref").getValuesString();
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                boolean redditLink = false;
                //System.out.println(url);
                if (url.indexOf("file://") == 0) { // fix for short sub and user links
                    url = url.replace("file://", global.getDefaultMobileSite()) + "/";
                    redditLink = true;
                }
                if (url.indexOf("https://www.reddit.com/") == 0) { // catch other reddit links
                    url = url.replace("https://www.reddit.com", global.getDefaultMobileSite());
                    redditLink = true;
                }
                if (redditLink) {
                    Intent i = new Intent(mContext, WebViewActivity.class);
                    i.putExtra("url", url);
                    startActivity(i);
                } else {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                }
                return true; // always override url
            }

            public void onPageFinished(WebView view, String url) {
                mWebView.loadUrl("javascript:init(\"" + StringEscapeUtils.escapeJavaScript(themeStr) + "\", \"" + global.mRedditData.getUsername() + "\")");
                if (load) load();
            }
        });
        mWebView.setWebChromeClient(new WebChromeClient());

        mWebView.requestFocus(View.FOCUS_DOWN);
        WebInterface webInterface = new WebInterface(mContext);
        mWebView.addJavascriptInterface(webInterface, "Reddinator");

        if (type.equals("unread") || type.equals("inbox") || type.equals("sent"))
            isMessages = true;

        mWebView.loadUrl("file:///android_asset/"+(isMessages?"messages":"account")+".html");
    }

    public void updateTheme() {
        String themeStr = ((AccountActivity) getActivity()).getCurrentTheme().getValuesString();
        mWebView.loadUrl("javascript:setTheme(\"" + StringEscapeUtils.escapeJavaScript(themeStr) + "\")");
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (container == null) {
            return null;
        }
        if (mFirstTime) {
            mFirstTime = false;
        }

        return ll;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //mWebView.saveState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        //mWebView.saveState(WVState);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if (feedLoader !=null)
            feedLoader.cancel(true);
        if (commentsVoteTask!=null)
            commentsVoteTask.cancel(false);
        if (commentTask!=null)
            commentTask.cancel(false);
    }

    @Override
    public void onVoteComplete(boolean result, RedditData.RedditApiException exception, String redditId, int direction) {
        ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.app_name)); // reset title
        if (result) {
            mWebView.loadUrl("javascript:voteCallback(\"" + redditId + "\", \"" + direction + "\")");
        } else {
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(getActivity(), false);
            // show error
            Toast.makeText(getActivity(), exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onCommentComplete(JSONObject result, RedditData.RedditApiException exception, int action, String redditId) {
        ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.app_name)); // reset title
        if (result!=null){
            switch (action){
                case -1:
                    mWebView.loadUrl("javascript:deleteCallback(\"" + redditId + "\")");
                    break;
                case 0:
                    mWebView.loadUrl("javascript:commentCallback(\"" + redditId + "\", \"" + StringEscapeUtils.escapeJavaScript(result.toString()) + "\")");
                    break;
                case 1:
                    mWebView.loadUrl("javascript:editCallback(\"" + redditId + "\", \"" + StringEscapeUtils.escapeJavaScript(result.toString()) + "\")");
                    break;
            }
        } else {
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(getActivity(), false);
            // show error
            Toast.makeText(getActivity(), exception.getMessage(), Toast.LENGTH_LONG).show();
            mWebView.loadUrl("javascript:commentCallback(\"" + redditId + "\", false)");
        }
    }

    public class WebInterface {
        Context mContext;

        /**
         * Instantiate the interface and set the context
         */
        WebInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void reloadFeed(String sort) {
            loadComments(sort);
        }

        @JavascriptInterface
        public void loadMore(String moreId) {
            feedLoader = new FeedLoader(currentSort, moreId);
            feedLoader.execute();
        }

        @JavascriptInterface
        public void vote(String thingId, int direction) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.voting));
            commentsVoteTask = new VoteTask(global, AccountFeedFragment.this, thingId, direction);
            commentsVoteTask.execute();
        }

        @JavascriptInterface
        public void comment(String parentId, String text) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.submitting));
            commentTask = new CommentTask(global, parentId, text, CommentTask.ACTION_ADD, AccountFeedFragment.this);
            commentTask.execute();
        }

        @JavascriptInterface
        public void edit(String thingId, String text) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.submitting));
            commentTask = new CommentTask(global, thingId, text, CommentTask.ACTION_EDIT, AccountFeedFragment.this);
            commentTask.execute();
        }

        @JavascriptInterface
        public void delete(String thingId) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.deleting));
            commentTask = new CommentTask(global, thingId, null, CommentTask.ACTION_DELETE, AccountFeedFragment.this);
            commentTask.execute();
        }

        @JavascriptInterface
        public void openCommentLink(String link) {
            Intent intent = new Intent(mContext, WebViewActivity.class);
            intent.putExtra("url", global.getDefaultMobileSite() + link);
            //System.out.println("http://www.reddit.com"+permalink+thingId+".compact");
            startActivity(intent);
        }

        @JavascriptInterface
        public void openRedditPost(String redditId, String postUrl, String permaLink, String userLikes) {
            Intent intent = new Intent(mContext, ViewRedditActivity.class);
            intent.setAction(ViewRedditActivity.ACTION_VIEW_POST);
            intent.putExtra("id", redditId);
            intent.putExtra("url", postUrl);
            intent.putExtra("permalink", permaLink);
            intent.putExtra("likes", userLikes);
            startActivity(intent);
        }
    }

    public interface ActivityInterface {
        void setTitleText(String titleText);
    }

    private void loadComments(String sort) {
        if (sort != null)
            currentSort = sort;
        feedLoader = new FeedLoader(currentSort);
        feedLoader.execute();
    }

    class FeedLoader extends AsyncTask<Void, Integer, String> {

        private boolean loadMore = false;
        private String mSort = "best";
        private String mMoreId = null;
        private ArrayList<String> unreadIds = null;
        private RedditData.RedditApiException exception;

        public FeedLoader(String sort){
            mSort = sort;
        }

        public FeedLoader(String sort, String moreId) {
            mSort = sort;
            if (moreId != null && !moreId.equals("")) {
                loadMore = true;
                mMoreId = moreId;
            }
        }

        @Override
        protected String doInBackground(Void... none) {
            JSONArray data;
            try {
                if (isMessages) {
                    JSONArray cached = global.getUnreadMessages();
                    if (type.equals("unread") && cached.length()>0){
                        data = cached;
                    } else {
                        data = global.mRedditData.getMessageFeed(type, 25, mMoreId);
                    }
                    // collect ids of unread messages to mark them read below
                    if (type.equals("unread") && data.length()>0){
                        unreadIds = new ArrayList<>();
                        for (int i=0; i<data.length(); i++){
                            try {
                                unreadIds.add(data.getJSONObject(i).getJSONObject("data").getString("name"));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    data = global.mRedditData.getAccountFeed(type, mSort, 25, mMoreId);
                }
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                exception = e;
                return "-1"; // Indicate error
            }

            if (data.length()>0) {
                return data.toString();
            }

            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            switch (result) {
                case "":
                    if (!loadMore) {
                        mWebView.loadUrl("javascript:showLoadingView(\""+StringEscapeUtils.escapeJavaScript(resources.getString(R.string.nothing_more_here))+"\")");
                    } else {
                        mWebView.loadUrl("javascript:noMoreCallback('"+mMoreId+"')");
                    }
                    break;
                case "-1":
                    // show error
                    if (!loadMore) {
                        mWebView.loadUrl("javascript:showLoadingView('"+resources.getString(R.string.error_loading_comments)+"')");
                    } else {
                        // reset load more button
                        mWebView.loadUrl("javascript:resetMoreClickEvent('"+mMoreId+"')");
                    }
                    // check login required
                    if (exception.isAuthError()) global.mRedditData.initiateLogin(getActivity(), false);

                    Toast.makeText(getActivity(), exception.getMessage(), Toast.LENGTH_LONG).show();
                    break;
                default:
                    if (loadMore) {
                        mWebView.loadUrl("javascript:populateFeed(\"" + StringEscapeUtils.escapeJavaScript(result) + "\", true)");
                    } else {
                        mWebView.loadUrl("javascript:populateFeed(\"" + StringEscapeUtils.escapeJavaScript(result) + "\", false)");
                    }
                    // Mark messages read; this clears cached messages and count once completed
                    if (unreadIds!=null && unreadIds.size()>0){
                        new MarkMessageTask(global, unreadIds).execute();
                    }
                    break;
            }
        }
    }
}
