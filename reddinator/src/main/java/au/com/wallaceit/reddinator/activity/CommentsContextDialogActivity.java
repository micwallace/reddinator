/*
 * Copyright 2016 Michael Boyde Wallace (http://wallaceit.com.au)
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
 *
 * File Created 09/04/2016
 */
package au.com.wallaceit.reddinator.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.IconButton;
import android.widget.IconTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.tasks.CommentTask;
import au.com.wallaceit.reddinator.tasks.VoteTask;
import au.com.wallaceit.reddinator.ui.HtmlDialog;

public class CommentsContextDialogActivity extends Activity implements VoteTask.Callback, CommentTask.Callback {
    Reddinator global;
    Resources resources;
    WebView webView;
    CommentsContextLoader commentsLoader;
    VoteTask commentsVoteTask;
    CommentTask commentTask;

    private SlidingUpPanelLayout panelLayout;
    private TextView sourceText;
    private TextView titleText;
    private TextView votesText;
    private IconTextView votesIcon;
    private TextView commentsText;
    private IconTextView commentsIcon;
    private TextView infoText;

    String url;
    String articleId;
    String commentId;
    String permalink;

    String currentSort = "best";
    int contextLevels = 3;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        global = (Reddinator) getApplicationContext();
        resources = getResources();
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.activity_comments_context_dialog);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        // get context url, extract permalink, id and comment id
        url = getIntent().getDataString();
        if (url!=null) {
            Pattern pattern = Pattern.compile(".*reddit.com(/r/[^/]*/comments/([^/]*)/[^/]*/)([^/]*)?");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()){
                //System.out.println(url + " " + matcher.group(1)+" "+matcher.group(2) + " " + matcher.group(3));
                permalink = matcher.group(1);
                articleId = "t3_"+matcher.group(2);
                commentId = matcher.group(3);
            } else {
                Toast.makeText(this, "Could not decode post URL", Toast.LENGTH_LONG).show();
                this.finish();
                return;
            }
        } else {
            showErrorAndFinish();
            return;
        }
        // setup info panel views
        sourceText = (TextView) findViewById(R.id.source_txt);
        votesText = (TextView) findViewById(R.id.votes_txt);
        votesIcon = (IconTextView) findViewById(R.id.votes_icon);
        commentsText = (TextView) findViewById(R.id.comments_txt);
        commentsIcon = (IconTextView) findViewById(R.id.comments_icon);
        titleText = (TextView) findViewById(R.id.post_title);
        infoText = (TextView) findViewById(R.id.info_txt);
        setTheme();
        // setup web view
        webView = (WebView) findViewById(R.id.commentswebview);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setWebViewClient(new CommentViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // enable ecmascript
        webSettings.setDomStorageEnabled(true); // some video sites require dom storage
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        int fontSize = Integer.parseInt(mSharedPreferences.getString("commentfontpref", "18"));
        webSettings.setDefaultFontSize(fontSize);
        //webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus(View.FOCUS_DOWN);
        WebInterface webInterface = new WebInterface(this);
        webView.addJavascriptInterface(webInterface, "Reddinator");
        registerForContextMenu(webView);
        webView.loadUrl("file:///android_asset/comments_context.html#"+articleId);
        // setup open comments button
        IconButton button = (IconButton) findViewById(R.id.commentsbutton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent commentsIntent = new Intent(CommentsContextDialogActivity.this, ViewRedditActivity.class);
                commentsIntent.setAction(Intent.ACTION_VIEW);
                commentsIntent.setData(Uri.parse(url));
                commentsIntent.putExtra("view_comments", true);
                startActivity(commentsIntent);
                finish();
            }
        });
        IconButton openbutton = (IconButton) findViewById(R.id.linkbutton);
        openbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent linkIntent = new Intent(CommentsContextDialogActivity.this, WebViewActivity.class);
                linkIntent.putExtra("url", url.replace("https://www.reddit.com", global.getDefaultMobileSite()));
                startActivity(linkIntent);
                finish();
            }
        });

        panelLayout = (SlidingUpPanelLayout) findViewById(R.id.sliding_layout);
        panelLayout.setFadeOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                panelLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            }
        });
    }

    private void setTheme(){
        ThemeManager.Theme theme = global.mThemeManager.getActiveTheme("appthemepref");
        int headerBg = Color.parseColor(theme.getValue("header_color"));
        int headerText = Color.parseColor(theme.getValue("header_text"));
        // info panel
        findViewById(R.id.info_panel).setBackgroundColor(headerBg);
        IconButton postButton = (IconButton) findViewById(R.id.commentsbutton);
        postButton.setBackgroundColor(headerBg);
        postButton.setTextColor(headerText);
        IconButton openButton = (IconButton) findViewById(R.id.linkbutton);
        openButton.setBackgroundColor(headerBg);
        openButton.setTextColor(headerText);
        sourceText.setTextColor(headerText);
        titleText.setTextColor(headerText);
        infoText.setTextColor(headerText);
        infoText.setLinkTextColor(headerText);
        votesText.setTextColor(headerText);
        commentsText.setTextColor(headerText);
        votesIcon.setTextColor(Color.parseColor(theme.getValue("votes_icon")));
        commentsIcon.setTextColor(Color.parseColor(theme.getValue("comments_icon")));

    }

    private void showErrorAndFinish(){
        Toast.makeText(this, "Could not decode post URL", Toast.LENGTH_LONG).show();
        this.finish();
    }

    private void populateInfoPanel(){
        try {
            String source = postInfo.getString("subreddit")+" - "+postInfo.getString("domain");
            sourceText.setText(source);
            titleText.setText(Utilities.fromHtml(postInfo.getString("title")));

            String infoStr = getString(R.string.submitted_details, DateUtils.getRelativeDateTimeString(this, Math.round(postInfo.getDouble("created_utc")) * 1000, DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_ABBREV_ALL), postInfo.getString("author"));
            infoText.setText(Utilities.fromHtml(infoStr));
            infoText.setMovementMethod(LinkMovementMethod.getInstance());

            int score = postInfo.getInt("score");
            double ratio = postInfo.getDouble("upvote_ratio");
            votesText.setText(getResources().getQuantityString(R.plurals.vote_details, score, score, Math.round(ratio * 100)));
            int comments = postInfo.getInt("num_comments");
            commentsText.setText(getResources().getQuantityString(R.plurals.num_comments, comments, comments));

            final String selftext = postInfo.getString("selftext_html");
            if (!selftext.equals("null")){
                IconTextView textButton = (IconTextView) findViewById(R.id.selftext_button);
                textButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        String html = "<html><head><style type=\"text/css\"> a { word-wrap: break-word; } </style></head><body>";
                        html += Utilities.fromHtml(selftext).toString();
                        html += "</body></html>";
                        HtmlDialog.init(CommentsContextDialogActivity.this, getString(R.string.post_text), html);

                    }
                });
                textButton.setVisibility(View.VISIBLE);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    class CommentViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {

            global.handleLink(CommentsContextDialogActivity.this, url);
            return true; // always override url
        }

        public void onPageFinished(WebView view, String url) {
            String themeStr = global.mThemeManager.getActiveTheme("appthemepref").getValuesString(true);
            webView.loadUrl("javascript:init(\"" + StringEscapeUtils.escapeJavaScript(themeStr) + "\", \""+global.mRedditData.getUsername()+"\")");

            loadComments(currentSort, contextLevels);
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
        public void reloadComments(String sort, int context) {
            loadComments(sort, context);
        }

        @JavascriptInterface
        public void loadChildren(String moreId, String children) {
            commentsLoader = new CommentsContextLoader(currentSort, moreId, children);
            commentsLoader.execute();
        }

        @JavascriptInterface
        public void vote(String thingId, int direction, int currentVote) {
            //setTitleText(resources.getString(R.string.voting));
            commentsVoteTask = new VoteTask(global, CommentsContextDialogActivity.this, thingId, direction, currentVote);
            commentsVoteTask.execute();
        }

        @JavascriptInterface
        public void comment(String parentId, String text) {
            //setTitleText(resources.getString(R.string.submitting));
            commentTask = new CommentTask(global, parentId, text, 0, CommentsContextDialogActivity.this);
            commentTask.execute();
        }

        @JavascriptInterface
        public void edit(String thingId, String text) {
            //setTitleText(resources.getString(R.string.submitting));
            commentTask = new CommentTask(global, thingId, text, 1, CommentsContextDialogActivity.this);
            commentTask.execute();
        }

        @JavascriptInterface
        public void delete(String thingId) {
            //setTitleText(resources.getString(R.string.deleting));
            commentTask = new CommentTask(global, thingId, null, -1, CommentsContextDialogActivity.this);
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

    @Override
    public void onVoteComplete(boolean result, RedditData.RedditApiException exception, String redditId, int direction, int netVote, int listPosition) {
        //setTitleText(resources.getString(R.string.app_name)); // reset title
        if (result) {
            webView.loadUrl("javascript:voteCallback(\"" + redditId + "\", \"" + direction + "\", "+netVote+")");
        } else {
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(this, false);
            // show error
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onCommentComplete(JSONObject result, RedditData.RedditApiException exception, int action, String redditId) {
        //setTitleText(resources.getString(R.string.app_name)); // reset title
        if (result!=null){
            switch (action){
                case -1:
                    webView.loadUrl("javascript:deleteCallback(\"" + redditId + "\")");
                    break;
                case 0:
                    webView.loadUrl("javascript:commentCallback(\"" + redditId + "\", \"" + StringEscapeUtils.escapeJavaScript(result.toString()) + "\")");
                    break;
                case 1:
                    webView.loadUrl("javascript:editCallback(\"" + redditId + "\", \"" + StringEscapeUtils.escapeJavaScript(result.toString()) + "\")");
                    break;
            }
        } else {
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(this, false);
            // show error
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
            webView.loadUrl("javascript:commentCallback(\"" + redditId + "\", false)");
        }
    }

    private void loadComments(String sort, int context) {
        if (sort != null)
            currentSort = sort;

        if (context != 0)
            contextLevels = context;

        commentsLoader = new CommentsContextLoader(currentSort, contextLevels);
        commentsLoader.execute();
    }

    private JSONObject postInfo;
    class CommentsContextLoader extends AsyncTask<Void, Integer, String> {

        private boolean loadMore = false;
        private String mSort = "best";
        private int mContext = 3;
        private String mMoreId;
        private String mChildren;

        public CommentsContextLoader(String sort, int context){
            mSort = sort;
            mContext = context;
        }

        public CommentsContextLoader(String sort, String moreId, String children) {
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
                    JSONArray commentObj = global.mRedditData.getCommentsContextFeed(permalink, commentId, mSort, mContext);
                    postInfo = commentObj.getJSONObject(0).getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
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
                        Utilities.executeJavascriptInWebview(webView, "showLoadingView('" + resources.getString(R.string.no_comments_here) + "');");
                    } else {
                        Utilities.executeJavascriptInWebview(webView, "noChildrenCallback('"+mMoreId+"');");
                    }
                    break;
                case "-1":
                    // show error
                    if (!loadMore) {
                        Utilities.executeJavascriptInWebview(webView, "showLoadingView('" + resources.getString(R.string.error_loading_comments) + "');");
                    } else {
                        // reset load more button
                        Utilities.executeJavascriptInWebview(webView, "resetMoreClickEvent('" + mMoreId + "');");
                    }
                    Toast.makeText(CommentsContextDialogActivity.this, lastError, Toast.LENGTH_LONG).show();
                    break;
                default:
                    if (loadMore) {
                        Utilities.executeJavascriptInWebview(webView, "populateChildComments(\"" + mMoreId + "\", \"" + StringEscapeUtils.escapeJavaScript(result) + "\");");
                    } else {
                        populateInfoPanel();
                        populateCommentsFromData(result);
                    }
                    break;
            }
        }
    }

    private void populateCommentsFromData(String data){
        String author = "";
        try {
            author = postInfo.getString("author");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (data.equals("[]")){
            Utilities.executeJavascriptInWebview(webView, "showLoadingView('" + resources.getString(R.string.no_comments_here) + "');");
        } else {
            Utilities.executeJavascriptInWebview(webView, "populateComments(\"" + author + "\",\"" + StringEscapeUtils.escapeJavaScript(data) + "\");");
        }
    }
}