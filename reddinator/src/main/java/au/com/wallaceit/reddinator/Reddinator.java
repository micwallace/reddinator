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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.wallaceit.reddinator.activity.AboutDialog;
import au.com.wallaceit.reddinator.activity.CommentsContextDialogActivity;
import au.com.wallaceit.reddinator.activity.MainActivity;
import au.com.wallaceit.reddinator.activity.ViewRedditActivity;
import au.com.wallaceit.reddinator.activity.WebViewActivity;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.SubredditManager;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.core.Utilities;

public class Reddinator extends Application {

    private ArrayList<JSONObject> mSubredditList; // cached popular subreddits
    public final static String REDDIT_BASE_URL = "https://www.reddit.com";
    public final static String REDDIT_MOBILE_BETA_URL = "https://m.reddit.com";
    public final static String REDDIT_MOBILE_URL = "https://i.reddit.com";
    public final static String IMAGE_CACHE_DIR = "/images/";
    public final static String FEED_DATA_DIR = "/feeds/";
    public final static int LOADTYPE_LOAD = 0;
    public final static int LOADTYPE_LOADMORE = 1;
    public final static int LOADTYPE_REFRESH_VIEW = 3;
    public final static String COLOR_VOTE = "#A5A5A5";
    public final static String COLOR_UPVOTE_ACTIVE = "#FF8B60";
    public final static String COLOR_DOWNVOTE_ACTIVE = "#9494FF";
    private int loadtype = 0; // tells the service what to do when notifyAppDataChanged is fired
    private boolean bypassCache = false; // tells the factory to bypass the cache when creating a new remoteviewsfacotry
    public RedditData mRedditData;
    public ThemeManager mThemeManager;
    private SubredditManager mSubManager;
    public SharedPreferences mSharedPreferences;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mSubredditList == null) {
            mSubredditList = new ArrayList<>();
        }
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(Reddinator.this.getApplicationContext());
        mRedditData = new RedditData(Reddinator.this.getApplicationContext());
        mThemeManager = new ThemeManager(Reddinator.this.getApplicationContext(), mSharedPreferences);
        // make webviews debuggable when running debug version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE))
                WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    // app feed update from view reddit activity; if the user voted, that data is stored here for the MainActivity to access in on resume
    private Bundle itemupdate;

    public Bundle getItemUpdate() {
        if (itemupdate == null) {
            return null;
        }
        Bundle tempdata = itemupdate;
        itemupdate = null; // prevent duplicate updates
        return tempdata;
    }

    public void setItemUpdate(int position, String id, String val, int netVote) {
        itemupdate = new Bundle();
        itemupdate.putInt("position", position);
        itemupdate.putString("id", id);
        itemupdate.putString("val", val);
        itemupdate.putInt("netvote", netVote);
    }

    // methods for setting/getting vote statuses, this keeps vote status persistent accross apps and widgets
    public void setItemVote(int feedId, int position, String id, String val, int netVote) {
        try {
            JSONArray data = getFeed(feedId);
            JSONObject record = data.getJSONObject(position).getJSONObject("data");
            if (record.getString("name").equals(id)) {
                JSONObject postData = data.getJSONObject(position).getJSONObject("data");
                postData.put("likes", val);
                postData.put("score", postData.getInt("score")+netVote);
                // save feed
                setFeed(feedId, data);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // set get current feeds from cache
    public void setFeed(int feedId, JSONArray feedData) {
        File feedFile = new File(getApplicationInfo().dataDir + FEED_DATA_DIR, "feed_" + feedId + ".json");
        if (!feedFile.exists() && !feedFile.getParentFile().exists()){
            //noinspection ResultOfMethodCallIgnored
            feedFile.getParentFile().mkdirs();
        }
        try {
            FileWriter writer = new FileWriter(feedFile);
            writer.write(feedData.toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public JSONArray getFeed(int feedId) {
        File feedFile = new File(getApplicationInfo().dataDir + FEED_DATA_DIR, "feed_" + feedId + ".json");
        if (feedFile.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(feedFile));
                String line, result = "";
                while ((line = reader.readLine()) != null) {
                    result += line;
                }
                reader.close();

                return new JSONArray(result);
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
        return new JSONArray();
    }

    public void deleteFeed(int feedId){
        File feedFile = new File(getApplicationInfo().dataDir + FEED_DATA_DIR, "feed_" + feedId + ".json");
        if (feedFile.exists())
            //noinspection ResultOfMethodCallIgnored
            feedFile.delete();
    }

    public JSONObject getFeedObject(int widgetId, int position, String redditId) {
        JSONArray data = getFeed(widgetId);
        try {
            JSONObject item = data.getJSONObject(position).getJSONObject("data");
            if (item.getString("name").equals(redditId)) {
                return item;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void removePostFromFeed(int widgetId, int position, String redditId){
        JSONArray data = getFeed(widgetId);
        try {
            JSONObject item = data.getJSONObject(position).getJSONObject("data");
            if (item.getString("name").equals(redditId)) {
                // remove post: fuck android for not having REMOVE in JSONArray until API 19.
                JSONArray finalData = new JSONArray();
                for (int i = 0; i<data.length(); i++){
                    if (i!=position)
                        finalData.put(data.get(i));
                }
                // save new feed
                setFeed(widgetId, finalData);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // used when appwidgets are destroyed
    public void clearFeedDataAndPreferences(int feedId){
        deleteFeed(feedId);
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.remove("currentfeed-" + feedId);
        editor.remove("widgettheme-"+ feedId);
        String widgetIdStr = (feedId == 0 ? "app" : String.valueOf(feedId));
        editor.remove("sort-" + widgetIdStr);
        editor.remove("thumbnails-" + widgetIdStr);
        editor.remove("bigthumbs-" + widgetIdStr);
        editor.remove("hideinf-" + widgetIdStr);
        editor.apply();
    }

    // cached popular subreddits
    public boolean isSrlistCached() {
        return !mSubredditList.isEmpty();
    }

    public void putSrList(ArrayList<JSONObject> list) {
        mSubredditList.clear();
        mSubredditList.addAll(list);
    }

    public ArrayList<JSONObject> getSrList() {
        return mSubredditList;
    }

    // subreddit list, settings & filter management
    public SubredditManager getSubredditManager(){
        if (mSubManager==null)
            mSubManager = new SubredditManager(mRedditData, mSharedPreferences);

        return mSubManager;
    }

    public int loadAccountSubreddits() throws RedditData.RedditApiException {

        final JSONArray list= mRedditData.getMySubreddits();
        getSubredditManager().setSubreddits(list);
        return list.length();
    }

    public int loadAccountMultis() throws RedditData.RedditApiException {

        JSONArray list = mRedditData.getMyMultis();
        getSubredditManager().addMultis(list, true);
        return list.length();
    }
    // unread message storage
    public void setUnreadMessages(JSONArray messages){
        mSharedPreferences.edit().putString("unreadMail", messages.toString()).apply();
    }
    public JSONArray getUnreadMessages(){
        try {
            return new JSONArray(mSharedPreferences.getString("unreadMail", "[]"));
        } catch (JSONException e) {
            e.printStackTrace();

        }
        return new JSONArray();
    }
    public void clearUnreadMessages(){
        // clear unread message cache and count
        mSharedPreferences.edit().remove("unreadMail").apply();
        mRedditData.clearStoredInboxCount();
        // Also clear notification that may be present (created in CheckMailService)
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(1);
    }

    // widget data loadtype functions; a bypass for androids restrictive widget api
    public int getLoadType() {
        return loadtype;
    }

    public void setLoadMore() {
        loadtype = LOADTYPE_LOADMORE;
    }

    public void setLoad() {
        loadtype = LOADTYPE_LOAD;
    }

    public void setRefreshView() {
        loadtype = LOADTYPE_REFRESH_VIEW;
    }

    // data cache functions
    public boolean getBypassCache() {
        return bypassCache;
    }

    public void setBypassCache(boolean bypassed) {
        bypassCache = bypassed;
    }

    public void showAlertDialog(Context context, String title, String message) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    public String getRedditMobileSite(boolean beta){
        if (beta){
            return REDDIT_MOBILE_BETA_URL;
        } else {
            return REDDIT_MOBILE_URL;
        }
    }

    public String getDefaultMobileSite(){
        return getRedditMobileSite(mSharedPreferences.getBoolean("redditmobilepref", false));
    }

    public String getDefaultCommentsMobileSite(){
        return getRedditMobileSite(mSharedPreferences.getBoolean("mobilecommentspref", true));
    }

    public void handleLink(Context context, String url){
        if (url.indexOf(REDDIT_BASE_URL)==0){
            // open in native view if supported
            handleRedditLink(context, url);
        } else {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(i);
        }
    }

    public void handleRedditLink(Context context, String url){
        //System.out.println(url);
        Pattern pattern = Pattern.compile(".*reddit.com(/r/[^/]*)(/comments/[^/]*/[^/]*/)?([^/]*)?/?$");
        Matcher matcher = pattern.matcher(url);
        Intent i;
        boolean match = matcher.find();
        if (match && matcher.group(3)!=null && !matcher.group(3).equals("")){
            // reddit comment links
            i = new Intent(context, CommentsContextDialogActivity.class);
            i.setData(Uri.parse(url));
        } else if (match && matcher.group(2)!=null){
            // reddit post link
            i = new Intent(context, ViewRedditActivity.class);
            i.setAction(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
        } else if (match && matcher.group(1)!=null) {
            // subreddit feed
            i = new Intent(context, MainActivity.class);
            i.setAction(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
        } else {
            // link is unsupported in native views; open in activity_webview
            i = new Intent(context, WebViewActivity.class);
            url = url.replace(REDDIT_BASE_URL, getDefaultMobileSite());
            i.putExtra("url", url);
        }
        context.startActivity(i);
    }

    public static boolean doShowWelcomeDialog(final Activity context){
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData.getBoolean("suppressChangelog"))
                return false;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean aboutDismissed = preferences.getBoolean("changelogDialogShown-" + Utilities.getPackageInfo(context).versionName, false);
        if (!aboutDismissed){
            AboutDialog.show(context, false);
            return true;
        }
        return false;
    }

    public boolean saveThumbnailToCache(Bitmap image, String imageId){
        try {
            File file = new File(getCacheDir().getPath() + Reddinator.IMAGE_CACHE_DIR, imageId + ".png");
            if (!file.getParentFile().exists()) {
                //noinspection ResultOfMethodCallIgnored
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

    public void triggerThunbnailCacheClean(){
        clearImageCache(86400000); // clear images older than 24h
    }

    // clears cache files older than the specified time, or all if time == 0
    public void clearImageCache(int time){
        File cacheDir = new File(getCacheDir().getPath() + IMAGE_CACHE_DIR);
        clearDir(cacheDir, time);
    }

    public void clearFeedData(){
        File feedDir = new File(getApplicationInfo().dataDir + FEED_DATA_DIR);
        clearDir(feedDir, 0);
    }

    public void clearDir(File dir, int time){
        if (dir.exists() && dir.isDirectory())
            for (File file : dir.listFiles()) {
                if (time>0) {
                    long diff = System.currentTimeMillis() - file.lastModified();
                    if (diff < time) // don't delete the image if age is less than specified
                        continue;
                }
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
    }
}
