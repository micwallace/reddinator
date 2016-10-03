package au.com.wallaceit.reddinator.ui;

/*
 * Copyright 2016 Michael Boyde Wallace (http://wallaceit.com.au)
 * This file is part of reddinator.
 *
 * reddinator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reddinator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reddinator (COPYING). If not, see <http://www.gnu.org/licenses/>.
 *
 * Created by michael on 3/10/16.
 */

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.joanzapata.android.iconify.Iconify;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.activity.ViewImageDialogActivity;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.service.WidgetProvider;
import au.com.wallaceit.reddinator.tasks.LoadImageBitmapTask;
import au.com.wallaceit.reddinator.tasks.VoteTask;

public class SubredditFeedAdapter extends BaseAdapter implements VoteTask.Callback {

    private Context context;
    private LayoutInflater inflater;
    private Reddinator global;
    private int feedId;
    private boolean canLoadMore = false;
    private JSONArray data;
    private ActivityInterface feedInterface;

    private Bitmap[] images;
    private ThemeManager.Theme theme;
    private HashMap<String, Integer> themeColors;
    private String titleFontSize = "16";
    private boolean loadPreviews = false;
    private boolean loadThumbnails = false;
    private boolean bigThumbs = false;
    private boolean hideInf = false;
    private boolean showItemSubreddit = false;

    public interface ActivityInterface {
        void loadMore();
        void showLoader();
        void hideLoader();
    }

    public SubredditFeedAdapter(Context context, ActivityInterface feedInterface, Reddinator global, ThemeManager.Theme theme, int feedId, JSONArray data, boolean canLoadMore, boolean hasMultipleSubs) {
        this.context = context;
        this.feedInterface = feedInterface;
        this.global = global;
        this.theme = theme;
        this.feedId = feedId;
        this.canLoadMore = canLoadMore;
        this.showItemSubreddit = hasMultipleSubs;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        // load the caches items
        if (data!=null) {
            this.data = data;
        } else {
            this.data = new JSONArray();
        }
        // load preferences
        loadTheme();
        loadFeedPrefs();
    }

    public void setFeed(JSONArray data, boolean canLoadMore, boolean hasMultipleSubs){
        this.data = data;
        this.canLoadMore = canLoadMore;
        this.showItemSubreddit = hasMultipleSubs;
        notifyDataSetChanged();
    }

    public void setTheme(ThemeManager.Theme theme) {
        this.theme = theme;
        loadTheme();
        notifyDataSetChanged();
    }

    public void removePostAtPosition(int position) {
        if (position>-1) {
            JSONArray tempArr = new JSONArray();
            for (int i = 0; i<data.length(); i++){
                if (i!=position)
                    try {
                        tempArr.put(data.get(i));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
            }
            data = tempArr;
            notifyDataSetChanged();
        }
    }

    public void initialiseVote(int listposition, int direction){
        feedInterface.showLoader();
        // Get data by position in list
        JSONObject item = getItem(listposition);
        String redditid;
        int curVote = 0;
        try {
            redditid = item.getString("name");
            if (item.has("likes"))
                curVote = Utilities.voteDirectionToInt(item.getString("likes"));
            new VoteTask(global, this, redditid, listposition, direction, curVote).execute();
        } catch (JSONException e) {
            Toast.makeText(context, "Error initializing vote: "+e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onVoteComplete(boolean result, RedditData.RedditApiException exception, String redditId, int direction, int netVote, int listposition) {
        if (result) {
            String voteVal = Utilities.voteDirectionToString(direction);
            updateUiVote(listposition, redditId, voteVal, netVote);
            global.setItemVote(0, listposition, redditId, voteVal, netVote);
        } else {
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(context, false);
            // show error
            Toast.makeText(context, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
        feedInterface.hideLoader();
    }

    public Bundle getItemExtras(int position){
        JSONObject item = getItem(position);
        Bundle extras = new Bundle();
        if (item==null){
            return null;
        }
        try {
            extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, feedId);
            extras.putString(WidgetProvider.ITEM_ID, item.getString("name"));
            extras.putInt(WidgetProvider.ITEM_FEED_POSITION, position);
            extras.putString(WidgetProvider.ITEM_URL, StringEscapeUtils.unescapeHtml4(item.getString("url")));
            extras.putString(WidgetProvider.ITEM_PERMALINK, item.getString("permalink"));
            extras.putString(WidgetProvider.ITEM_DOMAIN, item.getString("domain"));
            extras.putString(WidgetProvider.ITEM_SUBREDDIT, item.getString("subreddit"));
            extras.putString(WidgetProvider.ITEM_USERLIKES, item.getString("likes"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return extras;
    }

    public void updateUiVote(int position, String id, String val, int netVote) {
        try {
            // Incase the feed updated after opening reddinator view, check that the id's match to update the correct view.
            boolean recordexists = data.getJSONObject(position).getJSONObject("data").getString("name").equals(id);
            if (recordexists) {
                // update in current data (already updated in saved feed)
                JSONObject post = data.getJSONObject(position).getJSONObject("data");
                post.put("likes", val);
                post.put("score", post.getInt("score")+netVote);
                notifyDataSetChanged();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void loadTheme() {
        themeColors = theme.getIntColors();

        int[] shadow = new int[]{3, 3, 3, themeColors.get("icon_shadow")};
        // load images
        images = new Bitmap[]{
                Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_star.character()), themeColors.get("votes_icon"), 12, shadow),
                Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_comment.character()), themeColors.get("comments_icon"), 12, shadow),
                Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_arrow_up.character()), Color.parseColor(Reddinator.COLOR_VOTE), 28, shadow),
                Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_arrow_up.character()), Color.parseColor(Reddinator.COLOR_UPVOTE_ACTIVE), 28, shadow),
                Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_arrow_down.character()), Color.parseColor(Reddinator.COLOR_VOTE), 28, shadow),
                Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_arrow_down.character()), Color.parseColor(Reddinator.COLOR_DOWNVOTE_ACTIVE), 28, shadow),
                Utilities.getFontBitmap(context, String.valueOf(Iconify.IconValue.fa_expand.character()), themeColors.get("comments_count"), 12, shadow)
        };

        // get font size preference
        titleFontSize = global.mSharedPreferences.getString("titlefontpref", "16");
    }

    public void loadFeedPrefs() {
        // get thumbnail load preference for the widget
        loadPreviews = global.mSharedPreferences.getBoolean("imagepreviews-app", true);
        loadThumbnails = global.mSharedPreferences.getBoolean("thumbnails-app", true);
        bigThumbs = global.mSharedPreferences.getBoolean("bigthumbs-app", false);
        hideInf = global.mSharedPreferences.getBoolean("hideinf-app", false);
    }

    @Override
    public int getCount() {
        return data.length()>0 ? (data.length() + 1) : 0; // plus 1 advertises the "load more" item to the listview without having to add it to the data source
    }

    @Override
    public View getView(final int position, View row, ViewGroup parent) {
        if (position > data.length()) {
            return null; //  prevent errornous views
        }
        // check if its the last view and return loading view instead of normal row
        if (position == data.length()) {
            // build load more item
            View loadmorerow = inflater.inflate(R.layout.listrowloadmore, parent, false);
            TextView loadtxtview = (TextView) loadmorerow.findViewById(R.id.loadmoretxt);
            if (canLoadMore) {
                loadtxtview.setText(R.string.load_more);
            } else {
                loadtxtview.setText(R.string.nothing_more_here);
            }
            loadtxtview.setTextColor(themeColors.get("load_text"));
            loadmorerow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ((TextView) view.findViewById(R.id.loadmoretxt)).setText(R.string.loading);
                    feedInterface.loadMore();
                }
            });
            return loadmorerow;
        } else {
            // inflate new view or load view holder if existing
            ViewHolder viewHolder = new ViewHolder();
            if (row == null || row.getTag() == null) {
                // put views into viewholder
                row = inflater.inflate(R.layout.applistrow, parent, false);
                ((ImageView) row.findViewById(R.id.votesicon)).setImageBitmap(images[0]);
                ((ImageView) row.findViewById(R.id.commentsicon)).setImageBitmap(images[1]);
                viewHolder.listheading = (TextView) row.findViewById(R.id.listheading);
                viewHolder.sourcetxt = (TextView) row.findViewById(R.id.sourcetxt);
                viewHolder.votestxt = (TextView) row.findViewById(R.id.votestxt);
                viewHolder.commentstxt = (TextView) row.findViewById(R.id.commentstxt);
                viewHolder.thumbview_top = (ImageView) row.findViewById(R.id.thumbnail_top);
                viewHolder.thumbview = (ImageView) row.findViewById(R.id.thumbnail);
                viewHolder.thumbview_expand = (ImageView) row.findViewById(R.id.thumbnail_expand);
                viewHolder.preview = (ImageView) row.findViewById(R.id.preview);
                viewHolder.infview = row.findViewById(R.id.infbox);
                viewHolder.upvotebtn = (ImageButton) row.findViewById(R.id.app_upvote);
                viewHolder.downvotebtn = (ImageButton) row.findViewById(R.id.app_downvote);
                viewHolder.nsfw = (TextView) row.findViewById(R.id.nsfwflag);
                row.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) row.getTag();
            }
            // collect data
            String name, thumbnail, domain, id, url, userLikes, subreddit, previewUrl = null;
            int score;
            int numcomments;
            boolean nsfw;
            try {
                JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
                name = tempobj.getString("title");
                id = tempobj.getString("name");
                url = tempobj.getString("url");
                domain = tempobj.getString("domain");
                thumbnail = tempobj.getString("thumbnail");
                score = tempobj.getInt("score");
                numcomments = tempobj.getInt("num_comments");
                userLikes = tempobj.getString("likes");
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
                            if (arr.length() > 0){
                                prevObj = arr.length() < 3 ? arr.getJSONObject(arr.length() - 1) : arr.getJSONObject(2);
                                previewUrl = Utilities.fromHtml(prevObj.getString("url")).toString();
                            } else {
                                // or default to source
                                previewUrl = Utilities.fromHtml(prevObj.getJSONObject("source").getString("url")).toString();
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                return row; // The view is invalid;
            }
            // Update view
            viewHolder.listheading.setText(Utilities.fromHtml(name).toString());
            viewHolder.listheading.setTextSize(Integer.valueOf(titleFontSize)); // use for compatibility setTextViewTextSize only introduced in API 16
            viewHolder.listheading.setTextColor(themeColors.get("headline_text"));
            String sourceText = (showItemSubreddit?subreddit+" - ":"")+domain;
            viewHolder.sourcetxt.setText(sourceText);
            viewHolder.sourcetxt.setTextColor(themeColors.get("source_text"));
            viewHolder.votestxt.setText(String.valueOf(score));
            viewHolder.votestxt.setTextColor(themeColors.get("votes_text"));
            viewHolder.commentstxt.setText(String.valueOf(numcomments));
            viewHolder.commentstxt.setTextColor(themeColors.get("comments_count"));
            viewHolder.nsfw.setVisibility((nsfw ? TextView.VISIBLE : TextView.GONE));
            row.findViewById(R.id.listdivider).setBackgroundColor(themeColors.get("divider"));
            // set vote button
            if (!userLikes.equals("null")) {
                if (userLikes.equals("true")) {
                    viewHolder.upvotebtn.setImageBitmap(images[3]);
                    viewHolder.downvotebtn.setImageBitmap(images[4]);
                } else {
                    viewHolder.upvotebtn.setImageBitmap(images[2]);
                    viewHolder.downvotebtn.setImageBitmap(images[5]);
                }
            } else {
                viewHolder.upvotebtn.setImageBitmap(images[2]);
                viewHolder.downvotebtn.setImageBitmap(images[4]);
            }
            // Set vote onclick listeners
            viewHolder.upvotebtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    initialiseVote(position, 1);
                }
            });
            viewHolder.downvotebtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    initialiseVote(position, -1);
                }
            });
            // Get thumbnail view & hide the other
            ImageView thumbView;
            if (bigThumbs){
                thumbView = viewHolder.thumbview_top;
                viewHolder.thumbview.setVisibility(View.GONE);
            } else {
                thumbView = viewHolder.thumbview;
                viewHolder.thumbview_top.setVisibility(View.GONE);
            }
            // check for preview images & thumbnails
            String imageUrl = null;
            int imageLoadFlag = 0; // 1 for thumbnail, 2 for preview, 3 for default thumbnail
            if (loadPreviews && !nsfw && previewUrl!=null){
                imageUrl = previewUrl;
                imageLoadFlag = 2;
                thumbView.setVisibility(View.GONE);
                viewHolder.thumbview_expand.setVisibility(View.GONE);
            } else if (loadThumbnails && thumbnail!=null && !thumbnail.equals("")) {
                // hide preview view
                viewHolder.preview.setVisibility(View.GONE);
                // check for default thumbnails
                if (thumbnail.equals("nsfw") || thumbnail.equals("self") || thumbnail.equals("default") || thumbnail.equals("image")) {
                    int resource = 0;
                    switch (thumbnail) {
                        case "nsfw":
                            resource = R.drawable.nsfw;
                            break;
                        case "image":
                            resource = R.drawable.noimage;
                            break;
                        case "default":
                        case "self":
                            resource = R.drawable.self_default;
                            break;
                    }
                    thumbView.setImageResource(resource);
                    thumbView.setVisibility(View.VISIBLE);
                    imageLoadFlag = 3;
                    //System.out.println("Loading default image: "+thumbnail);
                } else {
                    imageUrl = thumbnail;
                    imageLoadFlag = 1;
                }
            } else {
                // hide preview and thumbnails
                thumbView.setVisibility(View.GONE);
                viewHolder.thumbview_expand.setVisibility(View.GONE);
                viewHolder.preview.setVisibility(View.GONE);
            }
            // load external images into view
            if (imageLoadFlag>0){
                ImageView imageView = imageLoadFlag == 2 ? viewHolder.preview : thumbView;
                // set id so loadImage callback knows if it should update the view
                // this is done instead of calling notifyDataSetChanged for each image
                imageView.setTag(id);
                // skip if default thumbnail, just check for image
                if (imageLoadFlag!=3) {
                    // check if the image is in cache
                    String fileurl = context.getCacheDir() + Reddinator.IMAGE_CACHE_DIR + id + (imageLoadFlag == 2 ? "-preview" : "") + ".png";
                    if (new File(fileurl).exists()) {
                        Bitmap bitmap = BitmapFactory.decodeFile(fileurl);
                        if (bitmap == null) {
                            imageView.setVisibility(View.GONE);
                        } else {
                            imageView.setImageBitmap(bitmap);
                            imageView.setVisibility(View.VISIBLE);
                        }
                    } else {
                        // start the image load
                        loadImage(imageView, viewHolder.thumbview_expand, imageUrl, id, (imageLoadFlag == 2 ? "-preview" : ""));
                        imageView.setVisibility(View.VISIBLE);
                        // set image source as default to prevent an image from a previous view being used
                        imageView.setImageResource(android.R.drawable.screen_background_dark_transparent);
                    }
                }
                // check if url is image, if so, add ViewImageDialog intent and show indicator
                if (Utilities.isImageUrl(url)){
                    imageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(context, ViewImageDialogActivity.class);
                            intent.putExtras(getItemExtras(position));
                            context.startActivity(intent);
                        }
                    });
                    viewHolder.thumbview_expand.setImageBitmap(images[6]);
                    viewHolder.thumbview_expand.setVisibility(View.VISIBLE);
                } else {
                    imageView.setClickable(false);
                    viewHolder.thumbview_expand.setVisibility(View.GONE);
                }
            }
            // hide info bar if options set
            if (hideInf) {
                viewHolder.infview.setVisibility(View.GONE);
            } else {
                viewHolder.infview.setVisibility(View.VISIBLE);
            }
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

    private void loadImage(final ImageView view, final ImageView expandView, final String urlstr, final String redditid, final String type) {
        new LoadImageBitmapTask(urlstr, new LoadImageBitmapTask.ImageCallback(){
            @Override
            public void run() {
                if (image!=null){
                    // save bitmap to cache, the item name will be the reddit id
                    global.saveThumbnailToCache(image, redditid+type);
                    // only update view if the tag is still the same, as we can be sure the view hasn't been recycled
                    if (view.getTag()==redditid){
                        view.setImageBitmap(image);
                    }
                } else if (view.getTag()==redditid) {
                    view.setVisibility(View.GONE);
                    if (expandView != null)
                        expandView.setVisibility(View.GONE);
                }
            }
        }).execute();
    }

    private class ViewHolder {
        TextView listheading;
        TextView sourcetxt;
        TextView votestxt;
        TextView commentstxt;
        ImageView thumbview;
        ImageView thumbview_top;
        ImageView thumbview_expand;
        ImageView preview;
        ImageButton upvotebtn;
        ImageButton downvotebtn;
        View infview;
        TextView nsfw;
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

}
