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
package au.com.wallaceit.reddinator.service;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.widget.TextView;
import android.widget.Toast;

import com.joanzapata.android.iconify.Iconify;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;

public class WidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new ListRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

class ListRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context mContext = null;
    private int appWidgetId;
    private JSONArray data;
    private Reddinator global;
    private SharedPreferences mSharedPreferences;
    private String titleFontSize = "16";
    private HashMap<String, Integer> themeColors;
    private boolean loadCached = false; // tells the ondatasetchanged function that it should not download any further items, cache is loaded
    private boolean loadPreviews = false;
    private boolean loadThumbnails = false;
    private boolean bigThumbs = false;
    private boolean hideInf = false;
    private boolean showItemSubreddit = false;
    private Bitmap[] images;

    public ListRemoteViewsFactory(Context context, Intent intent) {
        this.mContext = context;
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        global = ((Reddinator) context.getApplicationContext());
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        //System.out.println("New view factory created for widget ID:"+appWidgetId);
        // Set thread network policy to prevent network on main thread exceptions.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        // if this is a user request (apart from 'loadmore') or an auto update, do not attempt to load cache.
        // when a user clicks load more and a new view factory needs to be created we don't want to bypass cache, we want to load the cached items
        int loadType = global.getLoadType();
        if (!global.getBypassCache() || loadType == Reddinator.LOADTYPE_LOADMORE) {
            // load cached data
            data = global.getFeed(mSharedPreferences, appWidgetId);
            if (data.length() != 0) {
                titleFontSize = mSharedPreferences.getString(context.getString(R.string.title_font_pref), "16");
                try {
                    lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name");
                } catch (JSONException e) {
                    lastItemId = "0"; // Could not get last item ID; perform a reload next time and show error view :(
                    e.printStackTrace();
                }
                if (loadType == Reddinator.LOADTYPE_LOAD) {
                    loadCached = true; // this isn't a loadmore request, the cache is loaded and we're done
                    //System.out.println("Cache loaded, no user request received.");
                }
            } else {
                loadReddits(false); // No feed items; do a reload.
            }
        } else {
            data = new JSONArray(); // set empty data to prevent any NPE
        }
        // System.out.println("New RemoteViewsFactory created");
    }

    @Override
    public void onCreate() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        endOfFeed = false;

        loadFeedPrefs();
    }

    public void loadFeedPrefs(){
        themeColors = global.mThemeManager.getActiveTheme("widgettheme-"+appWidgetId).getIntColors();
        //int iconColor = Color.parseColor(themeColors[6]);
        int[] shadow = new int[]{3, 4, 4, themeColors.get("icon_shadow")};
        images = new Bitmap[]{
                Reddinator.getFontBitmap(mContext, String.valueOf(Iconify.IconValue.fa_star.character()), themeColors.get("votes_icon"), 12, shadow),
                Reddinator.getFontBitmap(mContext, String.valueOf(Iconify.IconValue.fa_comment.character()), themeColors.get("comments_icon"), 12, shadow),
                Reddinator.getFontBitmap(mContext, String.valueOf(Iconify.IconValue.fa_cogs.character()), themeColors.get("default_icon"), 26, shadow),
                Reddinator.getFontBitmap(mContext, String.valueOf(Iconify.IconValue.fa_arrow_up.character()), Color.parseColor(Reddinator.COLOR_VOTE), 28, shadow),
                Reddinator.getFontBitmap(mContext, String.valueOf(Iconify.IconValue.fa_arrow_up.character()), Color.parseColor(Reddinator.COLOR_UPVOTE_ACTIVE), 28, shadow),
                Reddinator.getFontBitmap(mContext, String.valueOf(Iconify.IconValue.fa_arrow_down.character()), Color.parseColor(Reddinator.COLOR_VOTE), 28, shadow),
                Reddinator.getFontBitmap(mContext, String.valueOf(Iconify.IconValue.fa_arrow_down.character()), Color.parseColor(Reddinator.COLOR_DOWNVOTE_ACTIVE), 28, shadow),
                Reddinator.getFontBitmap(mContext, String.valueOf(Iconify.IconValue.fa_expand.character()), themeColors.get("comments_text"), 12, shadow)
        };
        titleFontSize = mSharedPreferences.getString("titlefontpref", "16");
        loadPreviews = mSharedPreferences.getBoolean("imagepreviews-" + appWidgetId, true);
        loadThumbnails = mSharedPreferences.getBoolean("thumbnails-" + appWidgetId, true);
        bigThumbs = mSharedPreferences.getBoolean("bigthumbs-" + appWidgetId, false);
        hideInf = mSharedPreferences.getBoolean("hideinf-" + appWidgetId, false);
        showItemSubreddit = global.getSubredditManager().isFeedMulti(appWidgetId);
    }

    @Override
    public void onDestroy() {
        // no-op
        // System.out.println("RemoteViewsFactory destroyed");
    }

    @Override
    public int getCount() {
        return (data.length() + 1); // plus 1 advertises the "load more" item to the listview without having to add it to the data source
    }

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews row;
        if (position > data.length()) {
            return null; //  prevent errornous views
        }
        // check if its the last view and return loading view instead of normal row
        if (position == data.length()) {
            // build load more item
            //System.out.println("load more getViewAt("+position+") firing");
            RemoteViews loadmorerow = new RemoteViews(mContext.getPackageName(), R.layout.listrowloadmore);
            if (endOfFeed) {
                loadmorerow.setTextViewText(R.id.loadmoretxt, mContext.getResources().getString(R.string.nothing_more_here));
            } else {
                loadmorerow.setTextViewText(R.id.loadmoretxt, mContext.getResources().getString(R.string.load_more));
            }
            loadmorerow.setTextColor(R.id.loadmoretxt, themeColors.get("load_text"));
            Intent i = new Intent();
            Bundle extras = new Bundle();
            extras.putString(WidgetProvider.ITEM_ID, "0"); // zero will be an indicator in the onreceive function of widget provider if its not present it forces a reload
            i.putExtras(extras);
            loadmorerow.setOnClickFillInIntent(R.id.listrowloadmore, i);
            return loadmorerow;
        } else {
            // create remote view from specified layout
            row = new RemoteViews(mContext.getPackageName(), R.layout.listrow);
            // build normal item
            String title, url, permalink, thumbnail, domain, id, subreddit, userLikes, previewUrl = null;
            int score;
            int numcomments;
            boolean nsfw;
            try {
                JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
                title = tempobj.getString("title");
                userLikes = tempobj.getString("likes");
                domain = tempobj.getString("domain");
                id = tempobj.getString("name");
                url = tempobj.getString("url");
                permalink = tempobj.getString("permalink");
                thumbnail = (String) tempobj.get("thumbnail"); // we have to call get and cast cause its not in quotes
                score = tempobj.getInt("score");
                numcomments = tempobj.getInt("num_comments");
                nsfw = tempobj.getBoolean("over_18");
                subreddit = tempobj.getString("subreddit");

                // check and select preview url
                if (tempobj.has("preview")) {
                    JSONObject prevObj = tempobj.getJSONObject("preview");
                    if (prevObj.has("images")) {
                        JSONArray arr = prevObj.getJSONArray("images");
                        if (arr.length()>0) {
                            prevObj = arr.getJSONObject(0);
                            arr = prevObj.getJSONArray("resolutions");
                            // get third resolution (320px wide)
                            prevObj = arr.length() < 3 ? arr.getJSONObject(arr.length()-1) : arr.getJSONObject(2);
                            previewUrl = Html.fromHtml(prevObj.getString("url")).toString();
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                return row;
            }
            // build view
            row.setImageViewBitmap(R.id.votesicon, images[0]);
            row.setImageViewBitmap(R.id.commentsicon, images[1]);
            row.setBitmap(R.id.widget_item_options, "setImageBitmap", images[2]);
            row.setTextViewText(R.id.listheading, Html.fromHtml(title).toString());
            row.setFloat(R.id.listheading, "setTextSize", Integer.valueOf(titleFontSize)); // use for compatibility setTextViewTextSize only introduced in API 16
            row.setTextColor(R.id.listheading, themeColors.get("headline_text"));
            row.setTextViewText(R.id.sourcetxt, (showItemSubreddit ? subreddit + " - " :"")+domain);
            row.setTextColor(R.id.sourcetxt, themeColors.get("source_text"));
            row.setTextColor(R.id.votestxt, themeColors.get("votes_text"));
            row.setTextColor(R.id.commentstxt, themeColors.get("comments_text"));
            row.setTextViewText(R.id.votestxt, String.valueOf(score));
            row.setTextViewText(R.id.commentstxt, String.valueOf(numcomments));
            row.setInt(R.id.listdivider, "setBackgroundColor", themeColors.get("divider"));
            row.setViewVisibility(R.id.nsfwflag, nsfw ? TextView.VISIBLE : TextView.GONE);
            // set vote icons
            if (!userLikes.equals("null")) {
                if (userLikes.equals("true")) {
                    row.setBitmap(R.id.widget_upvote, "setImageBitmap", images[4]);
                    row.setBitmap(R.id.widget_downvote, "setImageBitmap", images[5]);
                } else {
                    row.setBitmap(R.id.widget_upvote, "setImageBitmap", images[3]);
                    row.setBitmap(R.id.widget_downvote, "setImageBitmap", images[6]);
                }
            } else {
                row.setBitmap(R.id.widget_upvote, "setImageBitmap", images[3]);
                row.setBitmap(R.id.widget_downvote, "setImageBitmap", images[5]);
            }
            // add extras and set click intent
            Intent i = new Intent();
            Bundle extras = new Bundle();
            extras.putString(WidgetProvider.ITEM_ID, id);
            extras.putInt(WidgetProvider.ITEM_FEED_POSITION, position);
            extras.putString(WidgetProvider.ITEM_URL, url);
            extras.putString(WidgetProvider.ITEM_PERMALINK, permalink);
            extras.putString(WidgetProvider.ITEM_DOMAIN, domain);
            extras.putString(WidgetProvider.ITEM_SUBREDDIT, subreddit);
            extras.putString(WidgetProvider.ITEM_USERLIKES, userLikes);
            extras.putInt(WidgetProvider.ITEM_CLICK_MODE, WidgetProvider.ITEM_CLICK_OPEN);
            i.putExtras(extras);
            row.setOnClickFillInIntent(R.id.listrow, i);
            // add intent for upvote
            Intent uvintent =  new Intent();
            Bundle uvextras = (Bundle) extras.clone();
            uvextras.putInt(WidgetProvider.ITEM_CLICK_MODE, WidgetProvider.ITEM_CLICK_UPVOTE);
            uvintent.putExtras(uvextras);
            row.setOnClickFillInIntent(R.id.widget_upvote, uvintent);
            // add intent for downvote
            Intent dvintent =  new Intent();
            Bundle dvextras = (Bundle) extras.clone();
            dvextras.putInt(WidgetProvider.ITEM_CLICK_MODE, WidgetProvider.ITEM_CLICK_DOWNVOTE);
            dvintent.putExtras(dvextras);
            row.setOnClickFillInIntent(R.id.widget_downvote, dvintent);
            // add intent for post options
            Intent ointent =  new Intent();
            Bundle oextras = (Bundle) extras.clone();
            oextras.putInt(WidgetProvider.ITEM_CLICK_MODE, WidgetProvider.ITEM_CLICK_OPTIONS);
            ointent.putExtras(oextras);
            row.setOnClickFillInIntent(R.id.widget_item_options, ointent);

            // Get thumbnail view & hide the other
            int thumbView;
            if (bigThumbs){
                thumbView = R.id.thumbnail_top;
                row.setViewVisibility(R.id.thumbnail, View.GONE);
            } else {
                thumbView = R.id.thumbnail;
                row.setViewVisibility(R.id.thumbnail_top, View.GONE);
            }
            // check for preview images & thumbnails
            String imageUrl = null;
            int imageLoadFlag = 0; // 1 for thumbnail, 2 for preview, 3 for default thumbnail
            if (loadPreviews  && !nsfw && previewUrl!=null){
                imageUrl = previewUrl;
                imageLoadFlag = 2;
                row.setViewVisibility(thumbView, View.GONE);
                row.setViewVisibility(R.id.thumbnail_expand, View.GONE);
            } else if (loadThumbnails && !thumbnail.equals("")) {
                // hide preview view
                row.setViewVisibility(R.id.preview, View.GONE);
                // check for default thumbnails
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
                    row.setImageViewResource(thumbView, resource);
                    row.setViewVisibility(thumbView, View.VISIBLE);
                    imageLoadFlag = 3;
                    //System.out.println("Loading default image: "+thumbnail);
                } else {
                    imageUrl = thumbnail;
                    imageLoadFlag = 1;
                }
            } else {
                // hide preview and thumbnails
                row.setViewVisibility(thumbView, View.GONE);
                row.setViewVisibility(R.id.thumbnail_expand, View.GONE);
                row.setViewVisibility(R.id.preview, View.GONE);
            }
            // load external images into view
            if (imageLoadFlag>0){
                int imageView = imageLoadFlag == 1 ? thumbView : R.id.preview;
                // skip if default thumbnail, just check for image
                if (imageLoadFlag!=3) {
                    // check if the image is in cache
                    Bitmap bitmap;
                    String fileurl = mContext.getCacheDir() + Reddinator.THUMB_CACHE_DIR + id + (imageLoadFlag == 2 ? "-preview" : "") + ".png";
                    // check if the image is in cache
                    if (new File(fileurl).exists()) {
                        bitmap = BitmapFactory.decodeFile(fileurl);
                    } else {
                        // download the image
                        bitmap = loadImage(imageUrl, id + (imageLoadFlag == 2 ? "-preview" : ""));
                    }
                    if (bitmap != null) {
                        row.setImageViewBitmap(imageView, bitmap);
                        row.setViewVisibility(imageView, View.VISIBLE);
                    } else {
                        // row.setImageViewResource(R.id.thumbnail, android.R.drawable.stat_notify_error); for later
                        row.setViewVisibility(imageView, View.GONE);
                    }
                }
                // check if url is image, if so, add ViewImageDialog intent and show indicator
                if (Reddinator.isImageUrl(url)){
                    Intent imageintent =  new Intent();
                    Bundle imageextras = (Bundle) extras.clone();
                    imageextras.putInt(WidgetProvider.ITEM_CLICK_MODE, WidgetProvider.ITEM_CLICK_IMAGE);
                    imageintent.putExtras(imageextras);
                    row.setOnClickFillInIntent(imageView, imageintent);
                    row.setImageViewBitmap(R.id.thumbnail_expand, images[7]);
                    row.setViewVisibility(R.id.thumbnail_expand, View.VISIBLE);
                } else {
                    row.setOnClickFillInIntent(imageView, i);
                    row.setViewVisibility(R.id.thumbnail_expand, View.GONE);
                }
            }
            // hide info bar if options set
            if (hideInf) {
                row.setViewVisibility(R.id.infbox, View.GONE);
            } else {
                row.setViewVisibility(R.id.infbox, View.VISIBLE);
            }
        }
        //System.out.println("getViewAt("+position+");");
        return row;
    }

    private Bitmap loadImage(String urlstr, String redditId) {
        URL url;
        Bitmap bmp;
        try {
            url = new URL(urlstr);
            URLConnection con = url.openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            bmp = BitmapFactory.decodeStream(con.getInputStream());
            global.saveThumbnailToCache(bmp, redditId);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return bmp;
    }

    @Override
    public RemoteViews getLoadingView() {
        RemoteViews rowload = new RemoteViews(mContext.getPackageName(), R.layout.listrowload);
        rowload.setTextColor(R.id.listloadtxt, themeColors.get("load_text"));
        return rowload;
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

    @Override
    public void onDataSetChanged() {
        // load theme & feed prefs
        loadFeedPrefs();

        int loadType = global.getLoadType();
        if (!loadCached) {
            loadCached = (loadType == Reddinator.LOADTYPE_REFRESH_VIEW); // see if its just a call to refresh view and set var accordingly but only check it if load cached is not already set true in the above constructor
        }
        //System.out.println("Loading type "+loadtype);
        if (!loadCached) {
            // refresh data
            if (loadType == Reddinator.LOADTYPE_LOADMORE && !lastItemId.equals("0")) { // do not attempt a "loadmore" if we don't have a valid item ID; this would append items to the list, instead perform a full reload
                global.setLoad();
                loadMoreReddits();
            } else {
                //System.out.println("loadReddits();");
                loadReddits(false);
            }
            global.setBypassCache(false); // don't bypass the cache check the next time the service starts
        } else {
            loadCached = false;
            global.setLoad();
            data = global.getFeed(mSharedPreferences, appWidgetId);
            // hide loader
            hideWidgetLoader(false, false, null); // don't go to top as the user is probably interacting with the list
        }

    }

    private String lastItemId = "0";
    private boolean endOfFeed = false;

    private void loadMoreReddits() {
        //System.out.println("loadMoreReddits();");
        loadReddits(true);
    }

    private void loadReddits(boolean loadMore) {
        String curFeed = global.getSubredditManager().getCurrentFeedPath(appWidgetId);
        boolean isAll = global.getSubredditManager().getCurrentFeedName(appWidgetId).equals("all");
        String sort = mSharedPreferences.getString("sort-" + appWidgetId, "hot");
        JSONArray tempArray;
        endOfFeed = false;
        // Load more or initial load/reload?
        if (loadMore) {
            // fetch 25 more after current last item and append to the list
            try {
                tempArray = global.mRedditData.getRedditFeed(curFeed, sort, 25, lastItemId);
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                hideWidgetLoader(false, true, e.getMessage()); // don't go to top of list and show error icon
                return;
            }
            if (tempArray.length() == 0) {
                endOfFeed = true;
            } else {
                tempArray = global.getSubredditManager().filterFeed(appWidgetId, tempArray, data, isAll, !global.mRedditData.isLoggedIn());

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
            // trigger cache clean
            global.triggerThunbnailCacheClean();
            // reload feed
            int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));
            try {
                tempArray = global.mRedditData.getRedditFeed(curFeed, sort, limit, "0");
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                hideWidgetLoader(false, true, e.getMessage()); // don't go to top of list and show error icon
                return;
            }
            // check if end of feed, if not process & set feed data
            if (tempArray.length() == 0) {
                endOfFeed = true;
            } else {
                tempArray = global.getSubredditManager().filterFeed(appWidgetId, tempArray, null, isAll, !global.mRedditData.isLoggedIn());
            }
            data = tempArray;
        }
        // Save feed data
        global.setFeed(mSharedPreferences, appWidgetId, data);
        // set last item id for "loadmore use"
        // Damn reddit doesn't allow you to specify a start index for the data, instead you have to reference the last item id from the prev page :(
        if (endOfFeed){
            lastItemId = "0";
        } else {
            try {
                lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name"); // name is actually the unique id we want
            } catch (JSONException e) {
                lastItemId = "0"; // Could not get last item ID :(
                endOfFeed = true;
                e.printStackTrace();
            }
        }

        // hide loader
        if (loadMore) {
            hideWidgetLoader(false, false, null); // don't go to top of list
        } else {
            hideWidgetLoader(true, false, null); // go to top
        }
    }

    // hide appwidget loader
    private void hideWidgetLoader(boolean goToTopOfList, boolean showError, final String errorTxt) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(mContext);
        // hide loader
        RemoteViews views = new RemoteViews(mContext.getPackageName(), R.layout.widget);
        views.setViewVisibility(R.id.srloader, View.INVISIBLE);
        // go to the top of the list view
        if (goToTopOfList) {
            views.setScrollPosition(R.id.listview, 0);
        }
        if (showError) {
            views.setViewVisibility(R.id.erroricon, View.VISIBLE);
        }
        mgr.partiallyUpdateAppWidget(appWidgetId, views);
        // show error text if available
        if (errorTxt!=null) {
            Handler handler = new Handler(mContext.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, errorTxt, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
