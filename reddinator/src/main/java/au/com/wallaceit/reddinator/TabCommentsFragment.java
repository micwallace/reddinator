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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

public class TabCommentsFragment extends Fragment {
    private Context mContext;
    public WebView mWebView;
    private boolean mFirstTime = true;
    private LinearLayout ll;
    private GlobalObjects global;
    public String articleId;
    public String permalink;
    private String currentSort = "best";
    CommentsLoader commentsLoader;
    CommentsVoteTask commentsVoteTask;
    CommentTask commentTask;

    static TabCommentsFragment init(boolean load) {
        TabCommentsFragment commentsTab = new TabCommentsFragment();
        Bundle args = new Bundle();
        args.putBoolean("load", load);
        commentsTab.setArguments(args);
        return commentsTab;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

    }

    boolean loaded = false;
    public void load(){
        if (!loaded) {
            loadComments("best");
            loaded = true;
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this.getActivity();
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        global = (GlobalObjects) mContext.getApplicationContext();
        final boolean load = getArguments().getBoolean("load");

        // get needed activity values
        articleId = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_ID);
        permalink = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK);

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
                if (url.indexOf("file://")==0){ // fix for short sub and user links
                    url = url.replace("file://", global.getDefaultMobileSite())+"/";
                    redditLink = true;
                }
                if (url.indexOf("https://www.reddit.com/")==0){ // catch other reddit links
                    url = url.replace("https://www.reddit.com", global.getDefaultMobileSite());
                    redditLink = true;
                }
                if (redditLink){
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
                mWebView.loadUrl("javascript:init(\"" + StringEscapeUtils.escapeJavaScript(themeStr) + "\", \""+global.mRedditData.getUsername()+"\")");
                if (load) load();
            }
        });
        mWebView.setWebChromeClient(new WebChromeClient());

        mWebView.requestFocus(View.FOCUS_DOWN);
        WebInterface webInterface = new WebInterface(mContext);
        mWebView.addJavascriptInterface(webInterface, "Reddinator");

        mWebView.loadUrl("file:///android_asset/comments.html#"+articleId);
    }

    public void updateTheme() {
        String themeStr = ((ViewRedditActivity) getActivity()).getCurrentTheme().getValuesString();
        mWebView.loadUrl("javascript:setTheme(\"" + StringEscapeUtils.escapeJavaScript(themeStr) + "\")");
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (container == null) {
            return null;
        }
        if (mFirstTime) {
            mFirstTime = false;
        } else {
            ((ViewGroup) ll.getParent()).removeView(ll);
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
        if (commentsLoader!=null)
            commentsLoader.cancel(true);
        if (commentsVoteTask!=null)
            commentsVoteTask.cancel(false);
        if (commentTask!=null)
            commentTask.cancel(false);
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
        public void reloadComments(String sort) {
            loadComments(sort);
        }

        @JavascriptInterface
        public void loadChildren(String moreId, String children) {
            commentsLoader = new CommentsLoader(currentSort, moreId, children);
            commentsLoader.execute();
        }

        @JavascriptInterface
        public void vote(String thingId, int direction) {
            ((ViewRedditActivity) getActivity()).setTitleText("Voting...");
            commentsVoteTask = new CommentsVoteTask(thingId, direction);
            commentsVoteTask.execute();
        }

        @JavascriptInterface
        public void comment(String parentId, String text) {
            ((ViewRedditActivity) getActivity()).setTitleText("Submitting...");
            commentTask = new CommentTask(parentId, text, 0);
            commentTask.execute();
        }

        @JavascriptInterface
        public void edit(String thingId, String text) {
            ((ViewRedditActivity) getActivity()).setTitleText("Submitting...");
            commentTask = new CommentTask(thingId, text, 1);
            commentTask.execute();
        }

        @JavascriptInterface
        public void delete(String thingId) {
            ((ViewRedditActivity) getActivity()).setTitleText("Deleting...");
            commentTask = new CommentTask(thingId, null, -1);
            commentTask.execute();
        }

        @JavascriptInterface
        public void openCommentLink(String thingId) {
            Intent intent = new Intent(mContext, WebViewActivity.class);
            intent.putExtra("url", global.getDefaultMobileSite() + permalink + thingId.substring(3));
            //System.out.println("http://www.reddit.com"+permalink+thingId+".compact");
            startActivity(intent);
        }
    }

    private void loadComments(String sort) {
        if (sort != null)
            currentSort = sort;
        commentsLoader = new CommentsLoader(currentSort);
        commentsLoader.execute();
    }

    private JSONObject subData;
    class CommentsLoader extends AsyncTask<Void, Integer, String> {

        private boolean loadMore = false;
        private String mSort = "best";
        private String mMoreId;
        private String mChildren;

        public CommentsLoader(String sort){
            mSort = sort;
        }

        public CommentsLoader(String sort, String moreId, String children) {
            mSort = sort;
            if (children != null && !children.equals("")) {
                loadMore = true;
                mMoreId = moreId;
                mChildren = children;
            }
        }

        private String lastError;
        @Override
        protected String doInBackground(Void... none) {
            JSONArray data;

            try {
                if (loadMore) {
                    data = global.mRedditData.getChildComments(mMoreId, articleId, mChildren, mSort);
                } else {
                    // reloading
                    JSONArray commentObj = global.mRedditData.getCommentsFeed(permalink, mSort, 25);
                    subData = commentObj.getJSONObject(0).getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
                    data = commentObj.getJSONObject(1).getJSONObject("data").getJSONArray("children");
                }
            } catch (JSONException | RedditData.RedditApiException e) {
                e.printStackTrace();
                lastError = e.getMessage();
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
                        mWebView.loadUrl("javascript:showLoadingView('No comments here')");
                    } else {
                        mWebView.loadUrl("javascript:noChildrenCallback('"+mMoreId+"')");
                    }
                    break;
                case "-1":
                    // show error
                    if (!loadMore) {
                        mWebView.loadUrl("javascript:showLoadingView('Error loading comments')");
                    } else {
                        // reset load more button
                        mWebView.loadUrl("javascript:resetMoreClickEvent('"+mMoreId+"')");
                    }
                    Toast.makeText(getActivity(), lastError, Toast.LENGTH_LONG).show();
                    break;
                default:
                    if (loadMore) {
                        mWebView.loadUrl("javascript:populateChildComments(\"" + mMoreId + "\", \"" + StringEscapeUtils.escapeJavaScript(result) + "\")");
                    } else {
                        String author = "";
                        try {
                            author = subData.getString("author");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        mWebView.loadUrl("javascript:populateComments(\""+author+"\",\"" + StringEscapeUtils.escapeJavaScript(result) + "\")");
                    }
                    break;
            }
        }
    }

    class CommentsVoteTask extends AsyncTask<String, Integer, Boolean> {
        JSONObject item;
        private String redditId;
        private int direction;
        private RedditData.RedditApiException exception;

        public CommentsVoteTask(String thingId, int dir) {
            direction = dir;
            redditId = thingId;
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            // Do the vote
            try {
                return global.mRedditData.vote(redditId, direction);
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                exception = e;
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            ((ViewRedditActivity) getActivity()).setTitleText("Reddinator"); // reset title
            if (result) {
                mWebView.loadUrl("javascript:voteCallback(\"" + redditId + "\", \"" + direction + "\")");
            } else {
                // check login required
                if (exception.isAuthError()) global.mRedditData.initiateLogin(getActivity());
                // show error
                Toast.makeText(getActivity(), exception.getMessage(), Toast.LENGTH_LONG).show();
            }
            //listAdapter.hideAppLoader(false, false);
        }
    }

    class CommentTask extends AsyncTask<String, Integer, JSONObject> {
        JSONObject item;
        private String redditId;
        private String messageText;
        private int action = 0; // 0=add, 1=edit, -1=delete
        private RedditData.RedditApiException exception;

        public CommentTask(String thingId, String text, int mode) {
            messageText = text;
            redditId = thingId;
            action = mode;
        }

        @Override
        protected JSONObject doInBackground(String... strings) {
            // Do the vote
            JSONObject result = null;
            try {
                switch (action){
                    case -1:
                        result = global.mRedditData.deleteComment(redditId)?new JSONObject():null;
                        break;
                    case 0:
                        result = global.mRedditData.postComment(redditId, messageText);
                        break;
                    case 1:
                        result = global.mRedditData.editComment(redditId, messageText);
                        break;
                }

            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                exception = e;
                return null;
            }

            return result;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            ((ViewRedditActivity) getActivity()).setTitleText("Reddinator"); // reset title
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
                if (exception.isAuthError()) global.mRedditData.initiateLogin(getActivity());
                // show error
                Toast.makeText(getActivity(), exception.getMessage(), Toast.LENGTH_LONG).show();
                mWebView.loadUrl("javascript:commentCallback(\"" + redditId + "\", false)");
            }
        }
    }
}
