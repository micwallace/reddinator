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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.IconTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.joanzapata.android.iconify.Iconify;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;

public class MainActivity extends Activity {

    private Context context;
    private SharedPreferences prefs;
    private GlobalObjects global;
    private ReddinatorListAdapter listAdapter;
    private AbsListView listView;
    private View appView;
    private ActionBar actionBar;
    private ProgressBar loader;
    private TextView srtext;
    private IconTextView errorIcon;
    private IconTextView refreshbutton;
    private IconTextView configbutton;
    private ThemeManager.Theme theme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = MainActivity.this;
        global = ((GlobalObjects) context.getApplicationContext());
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setContentView(R.layout.activity_main);
        // Setup actionbar
        appView = findViewById(R.id.appview);
        actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setCustomView(R.layout.appheader);
        }
        // get actionBar Views
        loader = (ProgressBar) findViewById(R.id.appsrloader);
        errorIcon = (IconTextView) findViewById(R.id.apperroricon);
        refreshbutton = (IconTextView) findViewById(R.id.apprefreshbutton);
        configbutton = (IconTextView) findViewById(R.id.appprefsbutton);
        //configbutton.setImageDrawable(new IconDrawable(this, Iconify.IconValue.fa_wrench).actionBarSize());

        srtext = (TextView) findViewById(R.id.appsubreddittxt);

        // set theme colors
        setThemeColors();

        // setup button onclicks
        refreshbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listAdapter.reloadReddits();
            }
        });

        View.OnClickListener srclick = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent srintent = new Intent(context, SubredditSelectActivity.class);
                startActivityForResult(srintent, 0);
            }
        };

        View.OnClickListener configclick = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent prefsintent = new Intent(context, PrefsActivity.class);
                prefsintent.putExtra("fromapp", true);
                startActivityForResult(prefsintent, 0);
            }
        };

        configbutton.setOnClickListener(configclick);
        srtext.setOnClickListener(srclick);
        findViewById(R.id.app_logo).setOnClickListener(srclick);
        findViewById(R.id.appcaret).setOnClickListener(srclick);
        // Setup list adapter
        listView = (ListView) findViewById(R.id.applistview);
        listAdapter = new ReddinatorListAdapter(global, prefs);
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                openLink(position, 1);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                final int position = i;
                AlertDialog linkDialog = new AlertDialog.Builder(context).create(); //Read Update
                linkDialog.setTitle("Open in browser");

                linkDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Comments", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        openLink(position, 3);
                    }
                });

                linkDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Content", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        openLink(position, 2);
                    }
                });

                linkDialog.show();
                return true;
            }
        });

        // set the current subreddit
        srtext.setText(global.getSubredditManager().getCurrentFeedName(0));

        // Trigger reload?
        if (prefs.getBoolean("appreloadpref", false) || listAdapter.getCount() < 2)
            listAdapter.reloadReddits();

    }

    @Override
    protected void onResume() {
        super.onResume();
        Bundle update = global.getItemUpdate();
        if (update != null) {
            listAdapter.updateUiVote(update.getInt("position", 0), update.getString("id"), update.getString("val"));
        }
    }

    private void setThemeColors() {

        theme = global.mThemeManager.getActiveTheme("appthemepref");

        appView.setBackgroundColor(Color.parseColor(theme.getValue("background_color")));
        actionBar.getCustomView().setBackgroundColor(Color.parseColor(theme.getValue("header_color")));
        srtext.setTextColor(Color.parseColor(theme.getValue("header_text")));

        int iconColor = Color.parseColor(theme.getValue("default_icon"));
        int[] shadow = new int[]{3, 4, 4, Color.parseColor(theme.getValue("icon_shadow"))};
        configbutton.setTextColor(iconColor);
        configbutton.setShadowLayer(shadow[0], shadow[1], shadow[2], shadow[3]);
        refreshbutton.setTextColor(iconColor);
        refreshbutton.setShadowLayer(shadow[0], shadow[1], shadow[2], shadow[3]);
        ((IconTextView) findViewById(R.id.appcaret)).setTextColor(iconColor);
        ((IconTextView) findViewById(R.id.appcaret)).setTextColor(iconColor);
    }

    @Override
    protected void onActivityResult(int reqcode, int resultcode, Intent data) {
        switch (resultcode) {
            case 1: // reload feed prefs
                listAdapter.loadFeedPrefs();
                listView.invalidateViews();
                break;

            case 2: // reload feed prefs and update feed, subreddit has changed
                srtext.setText(global.getSubredditManager().getCurrentFeedName(0));
                listAdapter.loadFeedPrefs();
                listAdapter.reloadReddits();
                break;

            case 3: // reload theme
                setThemeColors();
                listAdapter.loadTheme();
                listView.invalidateViews();
                break;
        }
    }

    public void openLink(int position, int openType) {
        // get the item
        JSONObject item = listAdapter.getItem(position);
        switch (openType) {
            case 1:
                // open in the reddinator view
                Intent clickIntent1 = new Intent(context, ViewRedditActivity.class);
                Bundle extras = new Bundle();
                try {
                    extras.putString(WidgetProvider.ITEM_ID, item.getString("name"));
                    extras.putInt("itemposition", position);
                    extras.putString(WidgetProvider.ITEM_URL, item.getString("url"));
                    extras.putString(WidgetProvider.ITEM_PERMALINK, item.getString("permalink"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                clickIntent1.putExtras(extras);
                context.startActivity(clickIntent1);
                break;
            case 2:
                // open link in browser
                String url = null;
                try {
                    url = item.getString("url");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Intent clickIntent2 = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(clickIntent2);
                break;
            case 3:
                // open reddit comments page in browser
                String permalink = null;
                try {
                    permalink = item.getString("permalink");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Intent clickIntent3 = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.reddit.com" + permalink));
                context.startActivity(clickIntent3);
                break;
        }
    }

    public class ReddinatorListAdapter extends BaseAdapter {

        private JSONArray data;
        private GlobalObjects global;
        private SharedPreferences mSharedPreferences;
        private String titleFontSize = "16";
        private HashMap<String, Integer> themeColors;
        private boolean loadThumbnails = false;
        private boolean bigThumbs = false;
        private boolean hideInf = false;
        private boolean showItemSubreddit = false;
        private Bitmap[] images;

        protected ReddinatorListAdapter(GlobalObjects gobjects, SharedPreferences prefs) {

            global = gobjects;
            mSharedPreferences = prefs;
            // load the caches items
            data = global.getFeed(mSharedPreferences, 0);
            //System.out.println("cached Data length: "+data.length());
            if (data.length() != 0) {
                try {
                    lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name");
                } catch (JSONException e) {
                    lastItemId = "0"; // Could not get last item ID; perform a reload next time and show error view :(
                    e.printStackTrace();
                }
            }
            // load preferences
            loadTheme();
            loadFeedPrefs();
        }

        private void loadTheme() {
            themeColors = theme.getIntColors();

            int[] shadow = new int[]{3, 3, 3, themeColors.get("icon_shadow")};
            // load images
            images = new Bitmap[]{
                    GlobalObjects.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_star.character()), themeColors.get("votes_icon"), 12, shadow),
                    GlobalObjects.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_comment.character()), themeColors.get("comments_icon"), 12, shadow)
            };

            // get font size preference
            titleFontSize = mSharedPreferences.getString("titlefontpref", "16");
        }

        private void loadFeedPrefs() {
            // get thumbnail load preference for the widget
            loadThumbnails = mSharedPreferences.getBoolean("thumbnails-app", true);
            bigThumbs = mSharedPreferences.getBoolean("bigthumbs-app", false);
            hideInf = mSharedPreferences.getBoolean("hideinf-app", false);
            showItemSubreddit = global.getSubredditManager().isFeedMulti(0);
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
                View loadmorerow = getLayoutInflater().inflate(R.layout.listrowloadmore, parent, false);
                TextView loadtxtview = (TextView) loadmorerow.findViewById(R.id.loadmoretxt);
                if (endOfFeed) {
                    loadtxtview.setText("There's nothing more here");
                } else {
                    loadtxtview.setText("Load more...");
                }
                loadtxtview.setTextColor(themeColors.get("load_text"));
                loadmorerow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ((TextView) view.findViewById(R.id.loadmoretxt)).setText("Loading...");
                        loadMoreReddits();
                    }
                });
                return loadmorerow;
            } else {
                // inflate new view or load view holder if existing
                ViewHolder viewHolder = new ViewHolder();
                if (row == null || row.getTag() == null) {
                    // create remote view from specified layout
                    if (bigThumbs) {
                        row = getLayoutInflater().inflate(R.layout.applistrowbigthumb, parent, false);
                    } else {
                        row = getLayoutInflater().inflate(R.layout.applistrow, parent, false);
                    }
                    ((ImageView) row.findViewById(R.id.votesicon)).setImageBitmap(images[0]);
                    ((ImageView) row.findViewById(R.id.commentsicon)).setImageBitmap(images[1]);
                    viewHolder.listheading = (TextView) row.findViewById(R.id.listheading);
                    viewHolder.sourcetxt = (TextView) row.findViewById(R.id.sourcetxt);
                    viewHolder.votestxt = (TextView) row.findViewById(R.id.votestxt);
                    viewHolder.commentstxt = (TextView) row.findViewById(R.id.commentstxt);
                    viewHolder.thumbview = (ImageView) row.findViewById(R.id.thumbnail);
                    viewHolder.infview = row.findViewById(R.id.infbox);
                    viewHolder.upvotebtn = (ImageButton) row.findViewById(R.id.app_upvote);
                    viewHolder.downvotebtn = (ImageButton) row.findViewById(R.id.app_downvote);
                    viewHolder.nsfw = (TextView) row.findViewById(R.id.nsfwflag);
                } else {
                    viewHolder = (ViewHolder) row.getTag();
                }
                // collect data
                String name = "";
                String thumbnail = "";
                String domain = "";
                String id = "";
                String userLikes = "null";
                String subreddit = "";
                int score = 0;
                int numcomments = 0;
                boolean nsfw = false;
                try {
                    JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
                    name = tempobj.getString("title");
                    id = tempobj.getString("name");
                    domain = tempobj.getString("domain");
                    thumbnail = (String) tempobj.get("thumbnail"); // we have to call get and cast cause its not in quotes
                    score = tempobj.getInt("score");
                    numcomments = tempobj.getInt("num_comments");
                    userLikes = tempobj.getString("likes");
                    nsfw = tempobj.getBoolean("over_18");
                    subreddit = tempobj.getString("subreddit");
                } catch (JSONException e) {
                    e.printStackTrace();
                    // return null; // The view is invalid;
                }
                // Update view
                viewHolder.listheading.setText(Html.fromHtml(name).toString());
                viewHolder.listheading.setTextSize(Integer.valueOf(titleFontSize)); // use for compatibility setTextViewTextSize only introduced in API 16
                viewHolder.listheading.setTextColor(themeColors.get("headline_text"));
                viewHolder.sourcetxt.setText((showItemSubreddit?subreddit+" - ":"")+domain);
                viewHolder.sourcetxt.setTextColor(themeColors.get("source_text"));
                viewHolder.votestxt.setText(String.valueOf(score));
                viewHolder.votestxt.setTextColor(themeColors.get("votes_text"));
                viewHolder.commentstxt.setText(String.valueOf(numcomments));
                viewHolder.commentstxt.setTextColor(themeColors.get("comments_text"));
                viewHolder.nsfw.setVisibility((nsfw ? TextView.VISIBLE : TextView.GONE));
                row.findViewById(R.id.listdivider).setBackgroundColor(themeColors.get("divider"));

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
                        listAdapter.showAppLoader();
                        view = (View) view.getParent().getParent();
                        ListVoteTask listvote = new ListVoteTask(1, view, position);
                        listvote.execute();
                    }
                });
                viewHolder.downvotebtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listAdapter.showAppLoader();
                        view = (View) view.getParent().getParent();
                        ListVoteTask listvote = new ListVoteTask(-1, view, position);
                        listvote.execute();
                    }
                });

                // load thumbnail if they are enabled for this widget
                if (loadThumbnails) {
                    // load big image if preference is set
                    if (!thumbnail.equals("")) { // check for thumbnail; self is used to display the thinking logo on the reddit site, we'll just show nothing for now
                        if (thumbnail.equals("nsfw") || thumbnail.equals("self") || thumbnail.equals("default")) {
                            int resource = 0;
                            switch (thumbnail) {
                                case "nsfw":
                                    resource = R.drawable.nsfw;
                                    break;
                                case "default":
                                case "self":
                                    resource = R.drawable.self_default;
                                    break;
                            }
                            viewHolder.thumbview.setImageResource(resource);
                            viewHolder.thumbview.setVisibility(View.VISIBLE);
                            //System.out.println("Loading default image: "+thumbnail);
                        } else {
                            // check if the image is in cache
                            String fileurl = getCacheDir() + "/thumbcache-app/" + id + ".png";
                            if (new File(fileurl).exists()) {
                                Bitmap bitmap = BitmapFactory.decodeFile(fileurl);
                                if (bitmap == null) {
                                    viewHolder.thumbview.setVisibility(View.GONE);
                                } else {
                                    viewHolder.thumbview.setImageBitmap(bitmap);
                                    viewHolder.thumbview.setVisibility(View.VISIBLE);
                                }
                            } else {
                                // start the image load
                                loadImage(position, thumbnail, id);
                                viewHolder.thumbview.setVisibility(View.VISIBLE);
                                // set image source as default to prevent an image from a previous view being used
                                viewHolder.thumbview.setImageResource(android.R.drawable.screen_background_dark_transparent);
                            }
                        }
                    } else {
                        viewHolder.thumbview.setVisibility(View.GONE);
                    }
                } else {
                    viewHolder.thumbview.setVisibility(View.GONE);
                }
                // hide info bar if options set
                if (hideInf) {
                    viewHolder.infview.setVisibility(View.GONE);
                } else {
                    viewHolder.infview.setVisibility(View.VISIBLE);
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

        private void loadImage(final int itempos, final String urlstr, final String redditid) {
            new ImageLoader(itempos, urlstr, redditid).execute();
        }

        private void clearImageCache() {
            // delete all images in the cache folder.
            DeleteRecursive(new File(getCacheDir() + "/thumbcache-app"));
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        private void DeleteRecursive(File fileOrDirectory) {

            if (fileOrDirectory.isDirectory())
                for (File child : fileOrDirectory.listFiles())
                    DeleteRecursive(child);

            fileOrDirectory.delete();

        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        private boolean saveImageToStorage(Bitmap image, String redditid) {
            try {
                File file = new File(context.getCacheDir().getPath() + "/thumbcache-app/", redditid + ".png");
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(file);
                image.compress(Bitmap.CompressFormat.PNG, 100, fos);
                // 100 means no compression, the lower you go, the stronger the compression
                fos.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        class ViewHolder {
            TextView listheading;
            TextView sourcetxt;
            TextView votestxt;
            TextView commentstxt;
            ImageView thumbview;
            ImageButton upvotebtn;
            ImageButton downvotebtn;
            View infview;
            TextView nsfw;
        }

        class ImageLoader extends AsyncTask<Void, Integer, Bitmap> {
            int itempos;
            String urlstr;
            String redditid;

            ImageLoader(int position, String url, String id) {
                itempos = position;
                urlstr = url;
                redditid = id;
            }

            @Override
            protected Bitmap doInBackground(Void... voids) {
                URL url;
                try {
                    url = new URL(urlstr);
                    URLConnection con = url.openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(8000);
                    return BitmapFactory.decodeStream(con.getInputStream());
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    return null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap result) {
                View v = listView.getChildAt(itempos - listView.getFirstVisiblePosition());
                if (v == null) {
                    return;
                }
                // save bitmap to cache, the item name will be the reddit id
                saveImageToStorage(result, redditid);
                // update view if it's being shown
                ImageView img = ((ImageView) v.findViewById(R.id.thumbnail));
                if (img != null) {
                    if (result != null) {
                        img.setImageBitmap(result);
                    } else {
                        img.setVisibility(View.GONE);
                    }
                }
            }
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

        public void loadMoreReddits() {
            loadReddits(true);
        }

        public void reloadReddits() {
            clearImageCache();
            loadReddits(false);
        }

        private void loadReddits(boolean loadMore) {
            showAppLoader();
            new FeedLoader(loadMore).execute();
        }

        class FeedLoader extends AsyncTask<Void, Integer, Long> {

            private Boolean loadMore;
            private RedditData.RedditApiException exception;

            public FeedLoader(Boolean loadmore) {
                loadMore = loadmore;
            }

            @Override
            protected Long doInBackground(Void... none) {
                String curFeed = global.getSubredditManager().getCurrentFeedPath(0);
                boolean isAll = global.getSubredditManager().getCurrentFeedName(0).equals("all");
                String sort = mSharedPreferences.getString("sort-app", "hot");
                JSONArray tempArray;
                endOfFeed = false;
                if (loadMore) {
                    // fetch 25 more after current last item and append to the list
                    try {
                        tempArray = global.mRedditData.getRedditFeed(curFeed, sort, 25, lastItemId);
                    } catch (RedditData.RedditApiException e) {
                        e.printStackTrace();
                        exception = e;
                        return (long) 0;
                    }
                    if (tempArray.length() == 0) {
                        endOfFeed = true;
                    } else {
                        if (isAll)
                            tempArray = global.getSubredditManager().filterFeed(tempArray);

                        int i = 0;
                        while (i < tempArray.length()) {
                            try {
                                data.put(tempArray.get(i));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            i++;
                        }
                    }
                } else {
                    // reloading
                    int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));
                    try {
                        tempArray = global.mRedditData.getRedditFeed(curFeed, sort, limit, "0");
                    } catch (RedditData.RedditApiException e) {
                        e.printStackTrace();
                        exception = e;
                        return (long) 0;
                    }
                    // check if end of feed, if not process & set feed data
                    if (data.length() == 0) {
                        endOfFeed = true;
                    } else {
                        if (isAll)
                            tempArray = global.getSubredditManager().filterFeed(tempArray);
                    }
                    data = tempArray;
                }
                // save feed
                global.setFeed(prefs, 0, data);
                if (endOfFeed){
                    lastItemId = "0";
                } else {
                    try {
                        lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name"); // name is actually the unique id we want
                    } catch (JSONException e) {
                        lastItemId = "0"; // Could not get last item ID; perform a reload next time
                        endOfFeed = true;
                        e.printStackTrace();
                    }
                }
                return (long) 1;
            }

            @Override
            protected void onPostExecute(Long result) {
                if (result > 0) {
                    // hide loader
                    if (loadMore) {
                        hideAppLoader(false, false); // don't go to top of list
                    } else {
                        hideAppLoader(true, false); // go to top
                    }
                    listAdapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(context, exception.getMessage(), Toast.LENGTH_LONG).show();
                    hideAppLoader(false, true); // don't go to top of list and show error icon
                }
            }
        }

        class ListVoteTask extends AsyncTask<String, Integer, Boolean> {
            JSONObject item;
            private String redditid;
            private int direction;
            private String curVote;
            private int listposition;
            private ImageButton upvotebtn;
            private ImageButton downvotebtn;
            private RedditData.RedditApiException exception;

            public ListVoteTask(int dir, View view, int position) {
                direction = dir;
                upvotebtn = (ImageButton) view.findViewById(R.id.app_upvote);
                downvotebtn = (ImageButton) view.findViewById(R.id.app_downvote);
                // Get data by position in list
                listposition = position;
                item = listAdapter.getItem(listposition);
                try {
                    redditid = item.getString("name");
                    curVote = item.getString("likes");
                } catch (JSONException e) {
                    redditid = "null";
                    curVote = "null";
                }
            }

            @Override
            protected Boolean doInBackground(String... strings) {
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
                    exception = e;
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (result) {
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
                    listAdapter.updateUiVote(listposition, redditid, value);
                    global.setItemVote(prefs, 0, listposition, redditid, value);
                } else {
                    // check login required
                    if (exception.isAuthError()) global.mRedditData.initiateLogin(MainActivity.this);
                    // show error
                    Toast.makeText(MainActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
                }
                listAdapter.hideAppLoader(false, false);
            }
        }

        // hide loader
        private void hideAppLoader(boolean goToTopOfList, boolean showError) {
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
        }
    }

}


