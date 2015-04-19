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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TabCommentsFragment extends Fragment {
    private Context mContext;
    public WebView mWebView;
    private boolean mFirstTime = true;
    private LinearLayout ll;
    private GlobalObjects global;
    private SharedPreferences mSharedPreferences;
    private CommentsLoader commentsLoader;
    public String articleId;
    private String currentSort = "best";

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (container == null) {
            return null;
        }
        if (mFirstTime) {
            mContext = this.getActivity();
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
            global = (GlobalObjects) mContext.getApplicationContext();
            // get shared preferences
            articleId = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_ID);

            // setup progressbar
            ll = (LinearLayout) inflater.inflate(R.layout.commentstab, container, false);
            mWebView = (WebView) ll.findViewById(R.id.comments_web_view);
            // fixes for webview not taking keyboard input on some devices
            WebSettings webSettings = mWebView.getSettings();
            webSettings.setJavaScriptEnabled(true); // enable ecmascript
            webSettings.setDomStorageEnabled(true); // some video sites require dom storage
            webSettings.setSupportZoom(false);
            webSettings.setBuiltInZoomControls(false);
            webSettings.setDisplayZoomControls(false);
            int fontSize = Integer.parseInt(mSharedPreferences.getString("commentfontpref", "22"));
            webSettings.setDefaultFontSize(fontSize);
            webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            webSettings.setDisplayZoomControls(false);

            String[] themeColors = global.getThemeColorHex();
            mSharedPreferences.getString("titlefontpref", "16");


            final String themeStr = StringUtils.join(themeColors, ",");
            mWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {

                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);

                    return false; // always override url
                }

                public void onPageFinished(WebView view, String url) {
                    mWebView.loadUrl("javascript:setTheme(\"" + StringEscapeUtils.escapeJavaScript(themeStr) + "\")");
                    loadComments("best");
                }
            });

            mWebView.requestFocus(View.FOCUS_DOWN);
            WebInterface webInterface = new WebInterface(mContext);
            mWebView.addJavascriptInterface(webInterface, "Reddinator");

            mWebView.loadUrl("file:///android_asset/comments.html");
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

    public class WebInterface {
        Context mContext;

        /**
         * Instantiate the interface and set the context
         */
        WebInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void loadChildren(String moreId, String children) {
            System.out.println("Load more command received");
            commentsLoader = new CommentsLoader(currentSort, moreId, children);
            commentsLoader.execute();
        }

        @JavascriptInterface
        public void vote(String thingId, String direction) {
            System.out.println("Vote command received");
            //commentsLoader = new CommentsLoader(currentSort, moreId, children);
            //commentsLoader.execute();
        }
    }

    private void loadComments(String sort) {
        if (sort != null)
            currentSort = sort;
        commentsLoader = new CommentsLoader(currentSort);
        commentsLoader.execute();
    }

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

        @Override
        protected String doInBackground(Void... none) {
            //String sort = mSharedPreferences.getString("sort-app", "hot");
            JSONArray data;

            JSONArray tempArray;
            if (loadMore) {
                String articleId = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_ID);
                tempArray = global.mRedditData.getChildComments(mMoreId, articleId, mChildren, mSort);
            } else {
                String permalink = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK);
                // reloading
                //int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));
                tempArray = global.mRedditData.getCommentsFeed(permalink, mSort, 25);
            }
            if (!isError(tempArray)) {
                data = tempArray;
                if (data.length() == 0) {
                    return "";
                }
                // save feed
                //global.setFeed(mSharedPreferences, 0, data);
            } else {
                return "";
            }

            return data.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            if (!result.equals("")) {
                // hide loader
                /*if (loadMore) {
                    //hideAppLoader(false, false); // don't go to top of list
                } else {
                    //hideAppLoader(true, false); // go to top
                }*/
                if (loadMore){
                    mWebView.loadUrl("javascript:populateChildComments(\""+mMoreId+"\", \"" + StringEscapeUtils.escapeJavaScript(result) + "\")");
                } else {
                    mWebView.loadUrl("javascript:populateComments(\"" + StringEscapeUtils.escapeJavaScript(result) + "\")");
                }
            } else {
                //hideAppLoader(false, true); // don't go to top of list and show error icon
                // TODO: handle error
            }

        }

        // check if the array is an error array
        private boolean isError(JSONArray tempArray) {
            boolean error;
            if (tempArray == null) {
                return true; // null error
            }
            if (tempArray.length() > 0) {
                try {
                    error = tempArray.getString(0).equals("-1");
                } catch (JSONException e) {
                    error = true;
                    e.printStackTrace();
                }
            } else {
                error = false; // empty array means no more feed items
            }
            return error;
        }
    }

    class CommentsVoteTask extends AsyncTask<String, Integer, String> {
        JSONObject item;
        private String redditid;
        private int direction;
        private String curVote;
        private ImageButton upvotebtn;
        private ImageButton downvotebtn;

        public CommentsVoteTask(int dir, View view, JSONObject data) {
            direction = dir;
            item = data;
            upvotebtn = (ImageButton) view.findViewById(R.id.app_upvote);
            downvotebtn = (ImageButton) view.findViewById(R.id.app_downvote);

            try {
                redditid = item.getString("name");
            } catch (JSONException e) {
                redditid = "null";
            }
            curVote = (String) upvotebtn.getTag();
        }

        @Override
        protected String doInBackground(String... strings) {
            // enumerate current vote and clicked direction
            if (direction == 1) {
                if (curVote.equals("true")) { // if already upvoted, neutralize.
                    direction = 0;
                }
            } else { // downvote
                if (curVote.equals("false")) {
                    direction = 0;
                }
            }
            // Do the vote
            try {
                return global.mRedditData.vote(redditid, direction);
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.equals("OK")) {
                // set icon + current "likes" in the data array, this way ViewRedditActivity will get the new version without updating the hole feed.
                String value = "null";
                switch (direction) {
                    case -1:
                        upvotebtn.setImageResource(R.drawable.upvote);
                        downvotebtn.setImageResource(R.drawable.downvote_active);
                        value = "false";
                        break;

                    case 0:
                        upvotebtn.setImageResource(R.drawable.upvote);
                        downvotebtn.setImageResource(R.drawable.downvote);
                        value = "null";
                        break;

                    case 1:
                        upvotebtn.setImageResource(R.drawable.upvote_active);
                        downvotebtn.setImageResource(R.drawable.downvote);
                        value = "true";
                        break;
                }
                upvotebtn.setTag(value);
                //listAdapter.updateUiVote(listposition, redditid, value);
                //global.setItemVote(mSharedPreferences, 0, listposition, redditid, value);
            } else if (result.equals("LOGIN")) {
                global.mRedditData.initiateLogin(getActivity());
            } else {
                // show error
                Toast.makeText(getActivity(), "API Error: " + result, Toast.LENGTH_LONG).show();
            }
            //listAdapter.hideAppLoader(false, false);
        }
    }

    // hide loader
        /*private void hideAppLoader(boolean goToTopOfList, boolean showError) {
            // get theme layout id
            loader.setVisibility(View.GONE);
            // go to the top of the list view
            if (goToTopOfList) {
                listView.smoothScrollToPosition(0);
            }
            if (showError) {
                errorIcon.setVisibility(View.VISIBLE);
            }
        }

        private void showAppLoader() {
            errorIcon.setVisibility(View.GONE);
            loader.setVisibility(View.VISIBLE);
        }*/

    /*public class CommentsListAdapter extends BaseAdapter {

        private JSONArray data;
        private GlobalObjects global;
        private SharedPreferences mSharedPreferences;
        private String titleFontSize = "16";
        private int[] themeColors;
        private boolean bigThumbs = false;

        protected CommentsListAdapter() {

            global = (GlobalObjects) mContext.getApplicationContext();
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
            // load the caches items
            //data = global.getFeed(mSharedPreferences, 0);
            data = new JSONArray();
            //System.out.println("cached Data length: "+data.length());
            if (data.length() != 0) {
                titleFontSize = mSharedPreferences.getString("titlefontpref", "16");
                try {
                    lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name");
                } catch (JSONException e) {
                    lastItemId = "0"; // Could not get last item ID; perform a reload next time and show error view :(
                    e.printStackTrace();
                }

            }
            // load preferences
            loadAppPrefs();
        }

        private void loadAppPrefs() {
            switch (Integer.valueOf(mSharedPreferences.getString("widgetthemepref", "1"))) {
                // set colors array: healine text, load more text, divider, domain text, vote & comments
                case 1:
                    themeColors = new int[]{Color.BLACK, Color.BLACK, Color.parseColor("#D7D7D7"), Color.parseColor("#336699"), Color.parseColor("#FF4500")};
                    ll.setBackgroundColor(Color.WHITE);
                    break;
                case 2:
                    themeColors = new int[]{Color.WHITE, Color.WHITE, Color.parseColor("#646464"), Color.parseColor("#5F99CF"), Color.parseColor("#FF8B60")};
                    ll.setBackgroundColor(Color.BLACK);
                    break;
                case 3:
                case 4:
                case 5:
                    themeColors = new int[]{Color.WHITE, Color.WHITE, Color.parseColor("#646464"), Color.parseColor("#CEE3F8"), Color.parseColor("#FF8B60")};
                    ll.setBackgroundColor(Color.BLACK);
                    break;
            }
            // user title color override
            if (!mSharedPreferences.getString("titlecolorpref", "0").equals("0")) {
                themeColors[0] = Color.parseColor(mSharedPreferences.getString("titlecolorpref", "#000"));
            }

            // get font size preference
            titleFontSize = mSharedPreferences.getString("titlefontpref", "16");
        }

        @Override
        public int getCount() {
            return data.length();
        }


        private View getNestedReplies(View parent, JSONArray replies) {

            final LinearLayout replyView = (LinearLayout) parent.findViewById(R.id.comment_replies);
            //replyView.setBackgroundResource(R.drawable.comment_border);

            for (int i = 0; i < replies.length(); i++) {

                boolean ismore = false;
                try {
                    ismore = replies.getJSONObject(i).getString("kind").equals("more");
                } catch (JSONException e) {
                    e.printStackTrace();
                    continue;
                }
                if (ismore) {
                    // build load more item
                    final JSONArray children;
                    try {
                        children = replies.getJSONObject(i).getJSONObject("data").getJSONArray("children");
                    } catch (JSONException e) {
                        continue;
                    }
                    View loadmorerow = getActivity().getLayoutInflater().inflate(R.layout.listrowloadreplies, null, false);
                    TextView loadtxtview = (TextView) loadmorerow.findViewById(R.id.loadmoretxt);
                    loadtxtview.setText(replies.length() == 1 ? "Load replies..." : "Load more replies...");

                    loadtxtview.setTextColor(themeColors[1]);
                    loadmorerow.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ((TextView) view.findViewById(R.id.loadmoretxt)).setText("Loading...");
                            addChildComments(replyView, children);
                        }
                    });

                    replyView.addView(loadmorerow, i);
                    continue;
                }

                ViewHolder viewHolder = new ViewHolder();
                // create remote view from specified layout
                View row = getActivity().getLayoutInflater().inflate(R.layout.commentslistrow, null, false);
                viewHolder.listheading = (TextView) row.findViewById(R.id.listheading);
                viewHolder.sourcetxt = (TextView) row.findViewById(R.id.sourcetxt);
                viewHolder.votestxt = (TextView) row.findViewById(R.id.votestxt);
                viewHolder.commentstxt = (TextView) row.findViewById(R.id.commentstxt);
                viewHolder.infview = row.findViewById(R.id.infbox);
                viewHolder.upvotebtn = (ImageButton) row.findViewById(R.id.app_upvote);
                viewHolder.downvotebtn = (ImageButton) row.findViewById(R.id.app_downvote);
                // collect data
                String body = "";
                String user = "";
                String id = "";
                String userLikes = "null";
                int score = 0;
                int numreplies = 0;
                JSONObject tempobj;
                JSONArray subReplies = new JSONArray();
                try {
                    tempobj = replies.getJSONObject(i).getJSONObject("data");
                    body = tempobj.getString("body_html");
                    id = tempobj.getString("name");
                    user = "/u/" + tempobj.getString("author");
                    score = tempobj.getInt("score");
                    if (!tempobj.isNull("replies") && !tempobj.get("replies").equals("")) {
                        numreplies = tempobj.getJSONObject("replies").getJSONObject("data").getJSONArray("children").length();
                        if (numreplies > 0)
                            subReplies = tempobj.getJSONObject("replies").getJSONObject("data").getJSONArray("children");
                    }
                    userLikes = tempobj.getString("likes");
                } catch (JSONException e) {
                    e.printStackTrace();
                    tempobj = new JSONObject();
                }
                final JSONObject commentObj = tempobj;
                // Update view
                Spanned span = Html.fromHtml(Html.fromHtml(body).toString());
                viewHolder.listheading.setText(span);
                viewHolder.listheading.setMovementMethod(LinkMovementMethod.getInstance());
                viewHolder.listheading.setTextSize(Integer.valueOf(titleFontSize)); // use for compatibility setTextViewTextSize only introduced in API 16
                viewHolder.listheading.setTextColor(themeColors[0]);
                viewHolder.sourcetxt.setText(user);
                viewHolder.sourcetxt.setTextColor(themeColors[3]);
                viewHolder.votestxt.setText(String.valueOf(score));
                viewHolder.votestxt.setTextColor(themeColors[4]);
                viewHolder.commentstxt.setText(String.valueOf(numreplies));
                viewHolder.commentstxt.setTextColor(themeColors[4]);
                row.findViewById(R.id.listdivider).setBackgroundColor(themeColors[2]);

                // set vote button
                if (!userLikes.equals("null")) {
                    if (userLikes.equals("true")) {
                        viewHolder.upvotebtn.setImageResource(R.drawable.upvote_active);
                        viewHolder.downvotebtn.setImageResource(R.drawable.downvote);
                    } else {
                        viewHolder.upvotebtn.setImageResource(R.drawable.upvote);
                        viewHolder.downvotebtn.setImageResource(R.drawable.downvote_active);
                    }
                } else {
                    viewHolder.upvotebtn.setImageResource(R.drawable.upvote);
                    viewHolder.downvotebtn.setImageResource(R.drawable.downvote);
                }
                // Set vote onclick listeners
                viewHolder.upvotebtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //listAdapter.showAppLoader();
                        view = (View) view.getParent().getParent();
                        CommentsVoteTask listvote = new CommentsVoteTask(1, view, commentObj);
                        listvote.execute();
                    }
                });
                viewHolder.downvotebtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //listAdapter.showAppLoader();
                        view = (View) view.getParent().getParent();
                        CommentsVoteTask listvote = new CommentsVoteTask(-1, view, commentObj);
                        listvote.execute();
                    }
                });

                // check if comment has replies
                if (subReplies.length() > 0) {
                    row = getNestedReplies(row, subReplies);
                }

                // hide info bar if options set
                if (hideInf) {
                    viewHolder.infview.setVisibility(View.GONE);
                } else {
                    viewHolder.infview.setVisibility(View.VISIBLE);
                }

                //row.setTag(viewHolder);

                //replyView.addView(row, i);
            }

            return parent;
        }

        @Override
        public View getView(final int position, View row, ViewGroup parent) {
            if (position > data.length()) {
                return null; //  prevent errornous views
            }
            boolean ismore = false;
            if ((position == data.length() - 1)) {
                try {
                    ismore = data.getJSONObject(position).getString("kind").equals("more");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            // check if the last element is the "more" element returned by reddit, show load more view
            if (ismore) {
                    // build load more item
                    View loadmorerow = getActivity().getLayoutInflater().inflate(R.layout.listrowloadmore, parent, false);
                    TextView loadtxtview = (TextView) loadmorerow.findViewById(R.id.loadmoretxt);
                    loadtxtview.setText("Load more...");
                    loadtxtview.setTextColor(themeColors[1]);
                    loadmorerow.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ((TextView) view.findViewById(R.id.loadmoretxt)).setText("Loading...");
                            loadMoreComments();
                        }
                    });
                    return loadmorerow;
            } else {
                // inflate new view or load view holder if existing
                ViewHolder viewHolder = new ViewHolder();
                if (row == null || row.getTag() == null) {
                    // create remote view from specified layout
                    row = getActivity().getLayoutInflater().inflate(R.layout.commentslistrow, parent, false);
                    viewHolder.listheading = (TextView) row.findViewById(R.id.listheading);
                    viewHolder.sourcetxt = (TextView) row.findViewById(R.id.sourcetxt);
                    viewHolder.votestxt = (TextView) row.findViewById(R.id.votestxt);
                    viewHolder.commentstxt = (TextView) row.findViewById(R.id.commentstxt);
                    viewHolder.infview = row.findViewById(R.id.infbox);
                    viewHolder.upvotebtn = (ImageButton) row.findViewById(R.id.app_upvote);
                    viewHolder.downvotebtn = (ImageButton) row.findViewById(R.id.app_downvote);
                } else {
                    viewHolder = (ViewHolder) row.getTag();
                }
                // collect data
                String body = "";
                String user = "";
                String id = "";
                String userLikes = "null";
                int score = 0;
                int numreplies = 0;
                JSONObject tempobj;
                JSONArray replies = new JSONArray();
                try {
                    tempobj = data.getJSONObject(position).getJSONObject("data");
                    body = tempobj.getString("body_html");
                    id = tempobj.getString("name");
                    user = "/u/" + tempobj.getString("author");
                    score = tempobj.getInt("score");
                    if (!tempobj.isNull("replies") && !tempobj.get("replies").equals("")) {
                        numreplies = tempobj.getJSONObject("replies").getJSONObject("data").getJSONArray("children").length();
                        if (numreplies > 0)
                            replies = tempobj.getJSONObject("replies").getJSONObject("data").getJSONArray("children");
                    }
                    userLikes = tempobj.getString("likes");
                } catch (JSONException e) {
                    e.printStackTrace();
                    tempobj = new JSONObject();
                }
                final JSONObject commentObj = tempobj;
                // Update view
                Spanned span = Html.fromHtml(Html.fromHtml(body).toString());
                viewHolder.listheading.setText(span);
                viewHolder.listheading.setMovementMethod(LinkMovementMethod.getInstance());
                viewHolder.listheading.setTextSize(Integer.valueOf(titleFontSize)); // use for compatibility setTextViewTextSize only introduced in API 16
                viewHolder.listheading.setTextColor(themeColors[0]);
                viewHolder.sourcetxt.setText(user);
                viewHolder.sourcetxt.setTextColor(themeColors[3]);
                viewHolder.votestxt.setText(String.valueOf(score));
                viewHolder.votestxt.setTextColor(themeColors[4]);
                viewHolder.commentstxt.setText(String.valueOf(numreplies));
                viewHolder.commentstxt.setTextColor(themeColors[4]);
                row.findViewById(R.id.listdivider).setBackgroundColor(themeColors[2]);

                // set vote button
                if (!userLikes.equals("null")) {
                    if (userLikes.equals("true")) {
                        viewHolder.upvotebtn.setImageResource(R.drawable.upvote_active);
                        viewHolder.downvotebtn.setImageResource(R.drawable.downvote);
                    } else {
                        viewHolder.upvotebtn.setImageResource(R.drawable.upvote);
                        viewHolder.downvotebtn.setImageResource(R.drawable.downvote_active);
                    }
                } else {
                    viewHolder.upvotebtn.setImageResource(R.drawable.upvote);
                    viewHolder.downvotebtn.setImageResource(R.drawable.downvote);
                }
                viewHolder.upvotebtn.setTag(userLikes);
                // Set vote onclick listeners
                viewHolder.upvotebtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //listAdapter.showAppLoader();
                        view = (View) view.getParent().getParent();
                        CommentsVoteTask listvote = new CommentsVoteTask(1, view, commentObj);
                        listvote.execute();
                    }
                });
                viewHolder.downvotebtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //listAdapter.showAppLoader();
                        view = (View) view.getParent().getParent();
                        CommentsVoteTask listvote = new CommentsVoteTask(-1, view, commentObj);
                        listvote.execute();
                    }
                });

                // hide info bar if options set
                if (hideInf) {
                    viewHolder.infview.setVisibility(View.GONE);
                } else {
                    viewHolder.infview.setVisibility(View.VISIBLE);
                }

                // check if comment has replies
                if (replies.length() > 0) {
                    row = getNestedReplies(row, replies);
                }

                row.setTag(viewHolder);
            }
            //System.out.println("getViewAt("+position+");");
            return row;
        }

        @Override
        public JSONObject getItem(int position) {
            try {
                return data.getJSONObject(position).getJSONObject("data");
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }

        public void updateUiVote(int position, String id, String val) {
            try {
                // Incase the feed updated after opening reddinator view, check that the id's match to update the correct view.
                boolean recordexists = data.getJSONObject(position).getJSONObject("data").getString("name").equals(id);
                if (recordexists) {
                    // update in current data (already updated in saved feed)
                    data.getJSONObject(position).getJSONObject("data").put("likes", val);
                    // refresh view; unfortunately we have to refresh them all :( invalidateViewAtPosition(); please android?
                    listView.invalidateViews();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }


        class ViewHolder {
            TextView listheading;
            TextView sourcetxt;
            TextView votestxt;
            TextView commentstxt;
            ImageButton upvotebtn;
            ImageButton downvotebtn;
            View infview;
        }

        @Override
        public int getViewTypeCount() {
            return (3);
        }

        @Override
        public long getItemId(int position) {
            return (position);
        }

        @Override
        public boolean hasStableIds() {
            return (false);
        }

        private String lastItemId = "0";

        public void loadMoreComments() {
            loadReddits(true);
        }

        public void addChildComments(LinearLayout replyView, JSONArray children){
            JSONArray feed = global.mRedditData.getChildComments(children);
            for (int i = 0; i<feed.length(); i++){
                getNestedReplies(replyView, children);
            }
        }

        public void reloadComments() {
            loadReddits(false);
        }

        private void loadReddits(boolean loadMore) {
            //showAppLoader();
            new FeedLoader(loadMore).execute();
        }

        class FeedLoader extends AsyncTask<Void, Integer, Long> {

            private Boolean loadMore;

            public FeedLoader(Boolean loadmore) {
                loadMore = loadmore;
            }

            @Override
            protected Long doInBackground(Void... none) {
                //String sort = mSharedPreferences.getString("sort-app", "hot");
                String permalink = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK);
                if (loadMore) {
                    //String id = "";
                    //String link_id = "";
                    JSONArray children = new JSONArray();
                    try {
                        JSONObject moreObject = data.getJSONObject(data.length()-1).getJSONObject("data");
                        //id = moreObject.getString("name");
                        //link_id = moreObject.getString("parent_id");
                        children = moreObject.getJSONArray("children");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    // fetch 10 more after current last item and append to the list
                    JSONArray tempData = global.mRedditData.getMoreComments();
                    if (!isError(tempData)) {

                            JSONArray prevData = data;
                            data = new JSONArray();

                            int i = 0;
                            while (i < prevData.length() - 1) {
                                try {
                                    data.put(prevData.get(i));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                i++;
                            }

                            i = 0;
                            while (i < tempData.length()) {
                                try {
                                    if (tempData.getJSONObject(i).getString("kind").equals("t1") || i == (tempData.length() - 1)) // for some reason reddits comment API is FUCKED
                                        data.put(tempData.get(i));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                i++;
                            }
                            // save feed
                            //global.setFeed(mSharedPreferences, 0, data);
                    } else {
                        return (long) 0;
                    }
                } else {
                    // reloading
                    //int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));

                    JSONArray tempArray = global.mRedditData.getCommentsFeed(permalink, "best", 25);
                    // check if data is valid; if the getredditfeed function fails to create a connection it returns -1 in the first value of the array
                    if (!isError(tempArray)) {
                        data = tempArray;
                        if (data.length() == 0) {
                            //endOfFeed = true;
                        }
                        // save feed
                        //global.setFeed(mSharedPreferences, 0, data);
                    } else {
                        return (long) 0;
                    }
                }

                return (long) 1;
            }

            @Override
            protected void onPostExecute(Long result) {
                if (result > 0) {
                    // hide loader
                    if (loadMore) {
                        //hideAppLoader(false, false); // don't go to top of list
                    } else {
                        //hideAppLoader(true, false); // go to top
                    }
                    listAdapter.notifyDataSetChanged();
                } else {
                    //hideAppLoader(false, true); // don't go to top of list and show error icon
                }
            }

            // check if the array is an error array
            private boolean isError(JSONArray tempArray) {
                boolean error;
                if (tempArray == null) {
                    return true; // null error
                }
                if (tempArray.length() > 0) {
                    try {
                        error = tempArray.getString(0).equals("-1");
                    } catch (JSONException e) {
                        error = true;
                        e.printStackTrace();
                    }
                } else {
                    error = false; // empty array means no more feed items
                }
                return error;
            }
        }
    }*/
}
