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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class MainActivity extends Activity {

    private Context context;
    private SharedPreferences prefs;
    private ReddinatorListAdapter listAdapter;
    private AbsListView listView;
    private View appView;
    private ActionBar actionBar;
    private ProgressBar loader;
    private TextView srtext;
    private ImageView errorIcon;
    private ImageButton refreshbutton;
    private ImageButton configbutton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = MainActivity.this;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setContentView(R.layout.activity_main);
        // Setup actionbar
        appView = findViewById(R.id.appview);
        actionBar = getActionBar();
        View actionView = this.getLayoutInflater().inflate(R.layout.appheader, null);
        actionBar.setCustomView(actionView);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        loader = (ProgressBar) actionView.findViewById(R.id.appsrloader);
        errorIcon = (ImageView) actionView.findViewById(R.id.apperroricon);
        refreshbutton = (ImageButton) actionView.findViewById(R.id.apprefreshbutton);
        configbutton = (ImageButton) actionView.findViewById(R.id.appprefsbutton);
        srtext = (TextView) actionView.findViewById(R.id.appsubreddittxt);

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
                Intent srintent = new Intent(MainActivity.this, SubredditSelectActivity.class);
                startActivityForResult(srintent, 0);
            }
        };

        View.OnClickListener configclick = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent prefsintent = new Intent(MainActivity.this, PrefsActivity.class);
                prefsintent.putExtra("fromapp", true);
                startActivityForResult(prefsintent, 0);
            }
        };

        srtext.setOnClickListener(srclick);
        configbutton.setOnClickListener(configclick);
        findViewById(R.id.app_logo).setOnClickListener(srclick);

        // Setup list adapter
        listView = (ListView) findViewById(R.id.applistview);
        listAdapter = new ReddinatorListAdapter(context);
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
                AlertDialog linkDialog = new AlertDialog.Builder(MainActivity.this).create(); //Read Update
                linkDialog.setTitle("Open in browser");

                linkDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Comments", new DialogInterface.OnClickListener(){
                    public void onClick (DialogInterface dialog, int which){
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
        srtext.setText(prefs.getString("currentfeed-app", "technology"));

        // Trigger reload?
        if (prefs.getBoolean("appreloadpref", false)) listAdapter.reloadReddits();

    }

    private void setThemeColors(){
        int themenum = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.widget_theme_pref), "1"));
        switch (themenum) {
            case 1:
                actionBar.getCustomView().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#CEE3F8")));
                appView.setBackgroundColor(Color.WHITE);
                configbutton.setBackgroundColor(Color.parseColor("#CEE3F8"));
                refreshbutton.setBackgroundColor(Color.parseColor("#CEE3F8"));
                errorIcon.setBackgroundColor(Color.parseColor("#CEE3F8"));
                srtext.setTextColor(Color.parseColor("#000000"));
                break;
            case 3:
                Drawable header = getResources().getDrawable(android.R.drawable.dark_header);
                actionBar.getCustomView().setBackgroundDrawable(header);
                appView.setBackgroundColor(Color.BLACK);
                configbutton.setBackgroundDrawable(header);
                refreshbutton.setBackgroundDrawable(header);
                errorIcon.setBackgroundDrawable(header);
                srtext.setTextColor(Color.parseColor("#FFFFFF"));
                break;
            case 4:
            case 2:
                actionBar.getCustomView().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#5F99CF")));
                appView.setBackgroundColor(Color.BLACK);
                configbutton.setBackgroundColor(Color.parseColor("#5F99CF"));
                refreshbutton.setBackgroundColor(Color.parseColor("#5F99CF"));
                errorIcon.setBackgroundColor(Color.parseColor("#5F99CF"));
                srtext.setTextColor(Color.parseColor("#000000"));
                break;
        }
    }

    @Override
    protected void onActivityResult(int reqcode, int resultcode, Intent data){
        switch(resultcode){
            case 1: // reload feed prefs
                listAdapter.loadFeedPrefs();
                listView.invalidateViews();
                break;

            case 2: // reload feed prefs and update feed, subreddit has changed
                srtext.setText(PreferenceManager.getDefaultSharedPreferences(context).getString("currentfeed-app", "technology"));
                listAdapter.loadFeedPrefs();
                listAdapter.reloadReddits();
                break;

            case 3: // reload theme
                setThemeColors();
                listAdapter.loadAppPrefs();
                listView.invalidateViews();
                break;
        }
    }

    public void openLink(int position, int openType){
        // get the item
        JSONObject item = listAdapter.getItem(position);
        switch (openType){
            case 1:
                // open in the reddinator view
                Intent clickIntent1 = new Intent(context, ViewRedditActivity.class);
                Bundle extras = new Bundle();
                try {
                    extras.putString(WidgetProvider.ITEM_ID, item.getString("name"));
                    extras.putString(WidgetProvider.ITEM_URL, item.getString("url"));
                    extras.putString(WidgetProvider.ITEM_PERMALINK, item.getString("permalink"));
                    extras.putString(WidgetProvider.ITEM_TXT, item.getString("title"));
                    extras.putString(WidgetProvider.ITEM_DOMAIN, item.getString("domain"));
                    extras.putInt(WidgetProvider.ITEM_VOTES, item.getInt("score"));
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
                Intent clickIntent3 = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.reddit.com"+permalink));
                context.startActivity(clickIntent3);
                break;
        }
    }

    public class ReddinatorListAdapter extends BaseAdapter {

        private Context mContext = null;
        private JSONArray data;
        private GlobalObjects global;
        private SharedPreferences mSharedPreferences;
        private SharedPreferences.Editor mEditor;
        private String titleFontSize = "16";
        private int[] themeColors;
        private boolean loadThumbnails = false;
        private boolean bigThumbs = false;
        private boolean hideInf = false;


        protected ReddinatorListAdapter(Context context) {

            this.mContext = context;
            global = ((GlobalObjects) context.getApplicationContext());
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            mEditor = mSharedPreferences.edit();
            // load the caches items
            try {
                data = new JSONArray(mSharedPreferences.getString("feeddata-app", "[]"));
            } catch (JSONException e) {
                data = new JSONArray();
                e.printStackTrace();
            }
            //System.out.println("cached Data length: "+data.length());
            if (data.length() != 0) {
                titleFontSize = mSharedPreferences.getString(context.getString(R.string.widget_theme_pref), "16");
                try {
                    lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name");
                } catch (JSONException e) {
                    lastItemId = "0"; // Could not get last item ID; perform a reload next time and show error view :(
                    e.printStackTrace();
                }

            }
            // load preferences
            loadAppPrefs();
            loadFeedPrefs();
        }

        private void loadAppPrefs() {
            switch (Integer.valueOf(mSharedPreferences.getString("widgetthemepref", "1"))) {
                // set colors array: healine text, load more text, divider, domain text, vote & comments
                case 1:
                    themeColors = new int[]{Color.BLACK, Color.BLACK, Color.parseColor("#D7D7D7"), Color.parseColor("#336699"), Color.parseColor("#FF4500")};
                    break;
                case 2:
                    themeColors = new int[]{Color.WHITE, Color.WHITE, Color.parseColor("#646464"), Color.parseColor("#5F99CF"), Color.parseColor("#FF8B60")};
                    break;
                case 3:
                case 4:
                    themeColors = new int[]{Color.WHITE, Color.WHITE, Color.parseColor("#646464"), Color.parseColor("#CEE3F8"), Color.parseColor("#FF8B60")};
                    break;
            }
            // user title color override
            if (!mSharedPreferences.getString("titlecolorpref", "0").equals("0")) {
                themeColors[0] = Color.parseColor(mSharedPreferences.getString("titlecolorpref", "#000"));
            }
            // get font size preference
            titleFontSize = mSharedPreferences.getString("titlefontpref", "16");
        }

        private void loadFeedPrefs(){
            // get thumbnail load preference for the widget
            loadThumbnails = mSharedPreferences.getBoolean("thumbnails-app", true);
            bigThumbs = mSharedPreferences.getBoolean("bigthumbs-app", false);
            hideInf = mSharedPreferences.getBoolean("hideinf-app", false);
        }

        @Override
        public int getCount() {
            return (data.length() + 1); // plus 1 advertises the "load more" item to the listview without having to add it to the data source
        }

        @Override
        public View getView(int position, View row, ViewGroup parent) {
            if (position > data.length()) {
                return null; //  prevent errornous views
            }
            // check if its the last view and return loading view instead of normal row
            if (position == data.length()) {
                // build load more item
                //System.out.println("load more getViewAt("+position+") firing");
                View loadmorerow = getLayoutInflater().inflate(R.layout.listrowloadmore, parent, false);
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
                        loadMoreReddits();
                    }
                });
                return loadmorerow;
            } else {
                // inflate new view or load view holder if existing
                ViewHolder viewHolder = new ViewHolder();
                if (row==null || row.getTag()==null){
                    // create remote view from specified layout
                    if (bigThumbs) {
                        row = getLayoutInflater().inflate(R.layout.listrowbigthumb, parent, false);
                    } else {
                        row = getLayoutInflater().inflate(R.layout.listrow, parent, false);
                    }
                    viewHolder.listheading = (TextView) row.findViewById(R.id.listheading);
                    viewHolder.sourcetxt = (TextView) row.findViewById(R.id.sourcetxt);
                    viewHolder.votestxt = (TextView) row.findViewById(R.id.votestxt);
                    viewHolder.commentstxt = (TextView) row.findViewById(R.id.commentstxt);
                    viewHolder.thumbview = (ImageView) row.findViewById(R.id.thumbnail);
                    viewHolder.infview = row.findViewById(R.id.infbox);
                } else {
                    viewHolder = (ViewHolder) row.getTag();
                }
                // collect data
                String name = "";
                String url = "";
                String permalink = "";
                String thumbnail = "";
                String domain = "";
                String id = "";
                int score = 0;
                int numcomments = 0;
                try {
                    JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
                    name = tempobj.getString("title");
                    id = tempobj.getString("name");
                    domain = tempobj.getString("domain");
                    thumbnail = (String) tempobj.get("thumbnail"); // we have to call get and cast cause its not in quotes
                    score = tempobj.getInt("score");
                    numcomments = tempobj.getInt("num_comments");
                } catch (JSONException e) {
                    e.printStackTrace();
                    // return null; // The view is invalid;
                }
                // Update view
                viewHolder.listheading.setText(Html.fromHtml(name).toString());
                viewHolder.listheading.setTextSize(Integer.valueOf(titleFontSize)); // use for compatibility setTextViewTextSize only introduced in API 16
                viewHolder.listheading.setTextColor(themeColors[0]);
                viewHolder.sourcetxt.setText(domain);
                viewHolder.sourcetxt.setTextColor(themeColors[3]);
                viewHolder.votestxt.setText(String.valueOf(score));
                viewHolder.votestxt.setTextColor(themeColors[4]);
                viewHolder.commentstxt.setText(String.valueOf(numcomments));
                viewHolder.commentstxt.setTextColor(themeColors[4]);
                row.findViewById(R.id.listdivider).setBackgroundColor(themeColors[2]);
                // load thumbnail if they are enabled for this widget
                if (loadThumbnails) {
                    // load big image if preference is set
                    if (!thumbnail.equals("") && !thumbnail.equals("self")) { // check for thumbnail; self is used to display the thinking logo on the reddit site, we'll just show nothing for now
                        // check if the image is in cache
                        if (new File(getCacheDir()+"/thumbcache-app/"+id+".png").exists()){
                            Bitmap bitmap = BitmapFactory.decodeFile(getCacheDir()+"/thumbcache-app/"+id+".png");
                            viewHolder.thumbview.setImageBitmap(bitmap);
                        } else {
                            // start the image load
                            loadImage(position, thumbnail, id);
                        }
                        viewHolder.thumbview.setVisibility(View.VISIBLE);
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

        private void loadImage(final int itempos, final String urlstr, final String redditid) {
            new ImageLoader(itempos, urlstr, redditid).execute();
        }

        private void clearImageCache(){
            // delete all images in the cache folder.
            DeleteRecursive(new File(getCacheDir()+"/thumbcache-app"));
        }

        void DeleteRecursive(File fileOrDirectory) {

            if (fileOrDirectory.isDirectory())
                for (File child : fileOrDirectory.listFiles())
                    DeleteRecursive(child);

            fileOrDirectory.delete();

        }

        private boolean saveImageToStorage(Bitmap image, String redditid){
                try {
                    File file = new File(context.getCacheDir().getPath()+"/thumbcache-app/", redditid+".png");
                    if (! file.getParentFile().exists()){
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
            View infview;
        }

        class ImageLoader extends AsyncTask<Void, Integer, Bitmap> {
            int itempos;
            String urlstr;
            String redditid;
            ImageLoader(int position, String url, String id){
                itempos = position;
                urlstr = url;
                redditid = id;
            }
            @Override
            protected Bitmap doInBackground(Void... voids) {
                URL url = null;
                try {
                    url = new URL(urlstr);
                    URLConnection con = url.openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(8000);
                    final Bitmap bmp = BitmapFactory.decodeStream(con.getInputStream());
                    return bmp;
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    return null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap result){
                View v = listView.getChildAt(itempos - listView.getFirstVisiblePosition());
                if (v==null){
                    return;
                }
                // save bitmap to cache, the item name will be the reddit id
                saveImageToStorage(result, redditid);
                // update view if it's being shown
                ImageView img = ((ImageView) v.findViewById(R.id.thumbnail));
                if (img!=null){
                    if (result!=null){
                        img.setImageBitmap(result);
                    } else {
                        listView.getChildAt(itempos - listView.getFirstVisiblePosition()).findViewById(R.id.thumbnail).setVisibility(View.GONE);
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
            //System.out.println("loadMoreReddits();");
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

            public  FeedLoader(Boolean loadmore){
                loadMore = loadmore;
            }

            @Override
            protected Long doInBackground(Void... none) {
                String curFeed = mSharedPreferences.getString("currentfeed-app", "technology");
                System.out.println(curFeed);
                String sort = mSharedPreferences.getString("sort-app", "hot");

                if (loadMore) {
                    // fetch 25 more after current last item and append to the list
                    JSONArray tempData = global.mRedditData.getRedditFeed(curFeed, sort, 25, lastItemId);
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
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                                i++;
                            }
                            mEditor.putString("feeddata-app", data.toString());
                            mEditor.commit();
                        }
                    } else {
                        return Long.valueOf(0);
                    }
                } else {
                    endOfFeed = false;
                    // reloading
                    int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));
                    JSONArray tempArray = global.mRedditData.getRedditFeed(curFeed, sort, limit, "0");
                    // check if data is valid; if the getredditfeed function fails to create a connection it returns -1 in the first value of the array
                    if (!isError(tempArray)) {
                        data = tempArray;
                        if (data.length() == 0) {
                            endOfFeed = true;
                        }
                        mEditor.putString("feeddata-app", data.toString());
                        mEditor.commit();

                    } else {
                        return Long.valueOf(0);
                    }
                }

                try {
                    lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name"); // name is actually the unique id we want
                } catch (JSONException e) {
                    lastItemId = "0"; // Could not get last item ID; perform a reload next time and show error view :(
                    e.printStackTrace();
                }
                return Long.valueOf(1);
            }

            @Override
            protected void onPostExecute(Long result){
                if (result>0){
                    // hide loader
                    if (loadMore) {
                        hideAppLoader(false, false); // don't go to top of list
                    } else {
                        hideAppLoader(true, false); // go to top
                    }
                    listAdapter.notifyDataSetChanged();
                } else {
                    hideAppLoader(false, true); // don't go to top of list and show error icon
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

        // hide loader
        private void hideAppLoader(boolean goToTopOfList, boolean showError) {
            // get theme layout id
            int layout = 1;
            loader.setVisibility(View.GONE);
            // go to the top of the list view
            if (goToTopOfList) {
                listView.smoothScrollToPosition(0);
            }
            if (showError) {
                errorIcon.setVisibility(View.VISIBLE);
            }
        }

        private void showAppLoader(){
            errorIcon.setVisibility(View.GONE);
            loader.setVisibility(View.VISIBLE);
        }
    }
}


