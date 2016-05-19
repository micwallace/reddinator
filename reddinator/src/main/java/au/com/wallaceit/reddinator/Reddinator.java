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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.util.TypedValue;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.wallaceit.reddinator.activity.CommentsContextDialogActivity;
import au.com.wallaceit.reddinator.activity.MainActivity;
import au.com.wallaceit.reddinator.activity.ViewRedditActivity;
import au.com.wallaceit.reddinator.activity.WebViewActivity;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.SubredditManager;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.ui.SimpleTabsAdapter;
import au.com.wallaceit.reddinator.ui.SimpleTabsWidget;
import de.cketti.library.changelog.ChangeLog;

import static au.com.wallaceit.reddinator.R.layout.dialog_info;

public class Reddinator extends Application {

    private ArrayList<JSONObject> mSubredditList; // cached popular subreddits
    public final static String THUMB_CACHE_DIR = "/thumbnail_cache/";
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
    }

    // app feed update from view reddit activity; if the user voted, that data is stored here for the MainActivity to access in on resume
    Bundle itemupdate;

    public Bundle getItemUpdate() {
        if (itemupdate == null) {
            return null;
        }
        Bundle tempdata = itemupdate;
        itemupdate = null; // prevent duplicate updates
        return tempdata;
    }

    public void setItemUpdate(int position, String id, String val) {
        itemupdate = new Bundle();
        itemupdate.putInt("position", position);
        itemupdate.putString("id", id);
        itemupdate.putString("val", val);
    }

    // methods for setting/getting vote statuses, this keeps vote status persistent accross apps and widgets
    public void setItemVote(SharedPreferences prefs, int widgetId, int position, String id, String val) {
        try {
            JSONArray data = new JSONArray(prefs.getString("feeddata-" + (widgetId == 0 ? "app" : widgetId), "[]"));
            JSONObject record = data.getJSONObject(position).getJSONObject("data");
            if (record.getString("name").equals(id)) {
                data.getJSONObject(position).getJSONObject("data").put("likes", val);
                // commit to shared prefs
                SharedPreferences.Editor mEditor = prefs.edit();
                mEditor.putString("feeddata-" + (widgetId == 0 ? "app" : widgetId), data.toString());
                mEditor.apply();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // set get current feeds from cache
    public void setFeed(SharedPreferences prefs, int widgetId, JSONArray feeddata) {
        SharedPreferences.Editor mEditor = prefs.edit();
        mEditor.putString("feeddata-" + (widgetId == 0 ? "app" : widgetId), feeddata.toString());
        mEditor.apply();
    }

    public JSONArray getFeed(SharedPreferences prefs, int widgetId) {
        JSONArray data;
        try {
            data = new JSONArray(prefs.getString("feeddata-" + (widgetId == 0 ? "app" : widgetId), "[]"));
        } catch (JSONException e) {
            data = new JSONArray();
            e.printStackTrace();
        }
        return data;
    }

    public JSONObject getFeedObject(SharedPreferences prefs, int widgetId, int position, String redditId) {
        JSONArray data = getFeed(prefs, widgetId);
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
        JSONArray data = getFeed(mSharedPreferences, widgetId);
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
                setFeed(mSharedPreferences, widgetId, finalData);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    // used when appwidgets are destroyed
    public void clearFeedData(int widgetId){
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.remove("currentfeed-" + widgetId);
        editor.remove("widgettheme-"+ widgetId);
        String widgetIdStr = (widgetId == 0 ? "app" : String.valueOf(widgetId));
        editor.remove("feeddata-" + widgetIdStr);
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
            mSubManager = new SubredditManager(mSharedPreferences);

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
            return "https://m.reddit.com";
        } else {
            return "https://i.reddit.com";
        }
    }

    public String getDefaultMobileSite(){
        return getRedditMobileSite(mSharedPreferences.getBoolean("redditmobilepref", false));
    }

    public String getDefaultCommentsMobileSite(){
        return getRedditMobileSite(mSharedPreferences.getBoolean("mobilecommentspref", true));
    }

    public void handleLink(Context context, String url){
        if (url.indexOf("https://www.reddit.com/")==0){
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
            url = url.replace("https://www.reddit.com", getDefaultMobileSite());
            i.putExtra("url", url);
        }
        context.startActivity(i);
    }

    public static Bitmap getFontBitmap(Context context, String text, int color, int fontSize, int[] shadow) {
        fontSize = convertDiptoPix(context, fontSize);
        int pad = (fontSize / 9);
        Paint paint = new Paint();
        Typeface typeface = Typeface.createFromAsset(context.getAssets(), "css/fonts/fontawesome-webfont.ttf");
        paint.setAntiAlias(true);
        paint.setTypeface(typeface);
        paint.setColor(color);
        paint.setTextSize(fontSize);
        paint.setShadowLayer(shadow[0], shadow[1], shadow[2], shadow[3]);

        int textWidth = (int) (paint.measureText(text) + pad * 2);
        int height = (int) (fontSize / 0.75);
        Bitmap bitmap = Bitmap.createBitmap(textWidth, height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(text, (float) pad, fontSize, paint);
        return bitmap;
    }

    public static int getActionbarIconColor(){
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Color.parseColor("#A5A5A5");
        }
        return Color.parseColor("#DBDBDB");
    }

    public static int convertDiptoPix(Context context, float dip) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, context.getResources().getDisplayMetrics());
    }

    public static PackageInfo getPackageInfo(Context context){
        PackageInfo pInfo = null;
        try {
            pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return pInfo;
    }

    public static void doShowWelcomeDialog(final Activity context){
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData.getBoolean("suppressChangelog"))
                return;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean aboutDismissed = preferences.getBoolean("changelogDialogShown-" + getPackageInfo(context).versionName, false);
        if (!aboutDismissed){
            showInfoDialog(context, false);
        }
    }

    public static void showInfoDialog(final Activity context, final boolean isInfo){
        Resources resources = context.getResources();
        LinearLayout aboutView = (LinearLayout) context.getLayoutInflater().inflate(dialog_info, null);
        // setup view pager
        final ViewPager pager = (ViewPager) aboutView.findViewById(R.id.pager);
        pager.setOffscreenPageLimit(3);
        pager.setAdapter(new SimpleTabsAdapter(new String[]{resources.getString(R.string.about), resources.getString(R.string.credits), resources.getString(R.string.changelog)}, new int[]{R.id.info_about, R.id.info_credits, R.id.info_changelog}, context, aboutView));
        LinearLayout tabsLayout = (LinearLayout) aboutView.findViewById(R.id.tab_widget);
        SimpleTabsWidget tabs = new SimpleTabsWidget(context, tabsLayout);
        tabs.setViewPager(pager);
        ThemeManager.Theme theme = ((Reddinator) context.getApplicationContext()).mThemeManager.getActiveTheme("appthemepref");
        int headerColor = Color.parseColor(theme.getValue("header_color"));
        tabs.setBackgroundColor(headerColor);
        tabs.setInidicatorColor(Color.parseColor(theme.getValue("tab_indicator")));
        tabs.setTextColor(Color.parseColor(theme.getValue("header_text")));
        // do install/upgrade dialog specific stuff
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!isInfo) {
            if (prefs.getBoolean("welcomeDialogShown", false)){
                pager.setCurrentItem(2); // show changelog view on upgrade
            } else {
                prefs.edit().putBoolean("welcomeDialogShown", true).apply(); // show details view on first run
            }
            prefs.edit().putBoolean("changelogDialogShown-" + getPackageInfo(context).versionName, true).apply();
        }
        // setup about view
        ((TextView) aboutView.findViewById(R.id.version)).setText(context.getResources().getString(R.string.version_label, getPackageInfo(context).versionName));
        aboutView.findViewById(R.id.github).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/micwallace/reddinator"));
                context.startActivity(intent);
            }
        });
        aboutView.findViewById(R.id.donate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=RFUQJ6EP5FLD2"));
                context.startActivity(intent);
            }
        });
        aboutView.findViewById(R.id.gold).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/gold?goldtype=gift&months=1&thing=t3_4e10jl"));
                context.startActivity(intent);
            }
        });
        // setup credits
        WebView cwv = (WebView) aboutView.findViewById(R.id.info_credits);
        cwv.loadUrl("file:///android_asset/credits.html");
        // setup changelog_master
        ChangeLog cl = new ChangeLog(context);
        WebView wv = (WebView) aboutView.findViewById(R.id.info_changelog);
        wv.loadData(cl.getLog(), "text/html", "UTF-8");
        // initialize dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(aboutView)
        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        }).show();
    }

    public boolean saveThumbnailToCache(Bitmap image, String redditId){
        try {
            File file = new File(getCacheDir().getPath() + Reddinator.THUMB_CACHE_DIR, redditId + ".png");
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

    public void triggerThunbnailCacheClean(){
        File cacheDir = new File(getCacheDir() + THUMB_CACHE_DIR);
        if (cacheDir.exists() && cacheDir.isDirectory())
            for (File file : cacheDir.listFiles()) {
                long diff = System.currentTimeMillis() - file.lastModified();
                if (diff > 86400000) // delete cached images older than 24 hours
                    file.delete();
            }
    }

    public static boolean isImageUrl(String url) {
        if (url == null)
            return false;
        // Check image extension
        if (hasImageExtension(url))
            return true;
        // Check for imgur url without file extension (should not be album)
        return url.toLowerCase().matches("(https?://(.*imgur.com/[^galery/][^a/].*)$)");
    }

    public static boolean hasImageExtension(String url){
        return url.toLowerCase().matches("([^\\s]+(\\.(?i)(jpe?g|png|gif?v|bmp))$)");
    }
}
