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

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TabCommentsFragment extends Fragment {
    private Context mContext;
    private boolean mFirstTime = true;
    private LinearLayout ll;
    private ListView listView;
    private CommentsListAdapter listAdapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //mWebView.restoreState(savedInstanceState);

    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContext = this.getActivity();

        if (container == null) {
            return null;
        }
        if (mFirstTime) {
            ll = (LinearLayout) inflater.inflate(R.layout.commentstab, container, false);
            mFirstTime = false;

            listAdapter = new CommentsListAdapter();
            listView = (ListView) ll.findViewById(R.id.comments_list);
            listView.setAdapter(listAdapter);

            listAdapter.reloadComments();
            //System.out.println("Created fragment");
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

    public class CommentsListAdapter extends BaseAdapter {

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
            return (data.length() + 1); // plus 1 advertises the "load more" item to the listview without having to add it to the data source
        }

        @Override
        public View getView(final int position, View row, ViewGroup parent) {
            if (position > data.length()) {
                return null; //  prevent errornous views
            }
            // check if its the last view and return loading view instead of normal row
            if (position == data.length()) {
                // build load more item
                View loadmorerow = getActivity().getLayoutInflater().inflate(R.layout.listrowloadmore, parent, false);
                TextView loadtxtview = (TextView) loadmorerow.findViewById(R.id.loadmoretxt);
                if (endOfFeed) {
                    loadtxtview.setText("There's nothing more here");
                } else {
                    loadtxtview.setText("Load more...");
                }
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
                    if (bigThumbs) {
                        row = getActivity().getLayoutInflater().inflate(R.layout.applistrowbigthumb, parent, false);
                    } else {
                        row = getActivity().getLayoutInflater().inflate(R.layout.applistrow, parent, false);
                    }
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
                try {
                    JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
                    body = tempobj.getString("body_html");
                    id = tempobj.getString("name");
                    user = "/r/" + tempobj.getString("author");
                    score = tempobj.getInt("score");
                    numreplies = tempobj.getJSONObject("replies").getJSONObject("data").getJSONObject("children").length();
                    userLikes = tempobj.getString("likes");
                } catch (JSONException e) {
                    e.printStackTrace();
                    // return null; // The view is invalid;
                }
                // Update view
                viewHolder.listheading.setText(Html.fromHtml(body).toString());
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
                        CommentsVoteTask listvote = new CommentsVoteTask(1, view, position);
                        listvote.execute();
                    }
                });
                viewHolder.downvotebtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //listAdapter.showAppLoader();
                        view = (View) view.getParent().getParent();
                        CommentsVoteTask listvote = new CommentsVoteTask(-1, view, position);
                        listvote.execute();
                    }
                });

                // hide info bar if options set
                /*if (hideInf) {
                    viewHolder.infview.setVisibility(View.GONE);
                } else {
                    viewHolder.infview.setVisibility(View.VISIBLE);
                }*/

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
        private boolean endOfFeed = false;

        public void loadMoreComments() {
            loadReddits(true);
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
                    // fetch 25 more after current last item and append to the list
                    JSONArray tempData = global.mRedditData.getCommentsFeed(permalink, "hot", 25, lastItemId);
                    if (!isError(tempData)) {
                        if (tempData.length() == 0) {
                            endOfFeed = true;
                        } else {
                            endOfFeed = false;
                            int i = 0;
                            while (i < tempData.length()) {
                                try {
                                    data.put(tempData.get(i));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                i++;
                            }
                            // save feed
                            //global.setFeed(mSharedPreferences, 0, data);
                        }
                    } else {
                        return (long) 0;
                    }
                } else {
                    endOfFeed = false;
                    // reloading
                    int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));
                    JSONArray tempArray = global.mRedditData.getCommentsFeed(permalink, "hot", limit, "0");
                    // check if data is valid; if the getredditfeed function fails to create a connection it returns -1 in the first value of the array
                    if (!isError(tempArray)) {
                        data = tempArray;
                        if (data.length() == 0) {
                            endOfFeed = true;
                        }
                        // save feed
                        //global.setFeed(mSharedPreferences, 0, data);
                    } else {
                        return (long) 0;
                    }
                }

                try {
                    lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name"); // name is actually the unique id we want
                } catch (JSONException e) {
                    lastItemId = "0"; // Could not get last item ID; perform a reload next time and show error view :(
                    e.printStackTrace();
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

        class CommentsVoteTask extends AsyncTask<String, Integer, String> {
            JSONObject item;
            private String redditid;
            private int direction;
            private String curVote;
            private int listposition;
            private ImageButton upvotebtn;
            private ImageButton downvotebtn;

            public CommentsVoteTask(int dir, View view, int position) {
                direction = dir;
                upvotebtn = (ImageButton) view.findViewById(R.id.app_upvote);
                downvotebtn = (ImageButton) view.findViewById(R.id.app_downvote);
                // Get data by position in list
                listposition = position;
                item = (JSONObject) listAdapter.getItem(listposition);
                try {
                    redditid = item.getString("name");
                    curVote = item.getString("likes");
                } catch (JSONException e) {
                    redditid = "null";
                    curVote = "null";
                }
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
    }
}
