package au.com.wallaceit.reddinator;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class MainActivity extends Activity {

    private Context context;
    private ReddinatorListAdapter listAdapter;
    private AbsListView listView;
    private ProgressBar loader;
    private TextView srtext;
    private ImageView errorIcon;
    private ImageButton refreshbutton;
    private ImageButton configbutton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = MainActivity.this;
        setContentView(R.layout.activity_main);

        // Setup actionbar, set theme colors
        View appView = findViewById(R.id.appview);
        ActionBar actionBar = getActionBar();
        View actionView = this.getLayoutInflater().inflate(R.layout.appheader, null);
        loader = (ProgressBar) actionView.findViewById(R.id.appsrloader);
        errorIcon = (ImageView) actionView.findViewById(R.id.apperroricon);
        refreshbutton = (ImageButton) actionView.findViewById(R.id.apprefreshbutton);
        configbutton = (ImageButton) actionView.findViewById(R.id.appprefsbutton);
        srtext = (TextView) actionView.findViewById(R.id.appsubreddittxt);

        int themenum = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.widget_theme_pref), "1"));
        switch (themenum) {
            case 1:
                actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#CEE3F8")));
                configbutton.setBackgroundColor(Color.parseColor("#CEE3F8"));
                refreshbutton.setBackgroundColor(Color.parseColor("#CEE3F8"));
                errorIcon.setBackgroundColor(Color.parseColor("#CEE3F8"));
                break;
            case 3:
                Drawable header = getResources().getDrawable(android.R.drawable.dark_header);
                actionBar.setBackgroundDrawable(header);
                appView.setBackgroundColor(Color.BLACK);
                configbutton.setBackgroundDrawable(header);
                refreshbutton.setBackgroundDrawable(header);
                errorIcon.setBackgroundDrawable(header);
                break;
            case 4:
            case 2:
                actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#5F99CF")));
                appView.setBackgroundColor(Color.BLACK);
                configbutton.setBackgroundColor(Color.parseColor("#5F99CF"));
                refreshbutton.setBackgroundColor(Color.parseColor("#5F99CF"));
                errorIcon.setBackgroundColor(Color.parseColor("#5F99CF"));
                break;
        }
        actionBar.setCustomView(actionView);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);

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

        srtext.setOnClickListener(srclick);
        findViewById(R.id.app_logo).setOnClickListener(srclick);

        // Setup list adapter
        listView = (ListView) findViewById(R.id.applistview);
        listAdapter = new ReddinatorListAdapter(context);
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                openLink(position);
            }
        });

        // set the current subreddit
        srtext.setText(PreferenceManager.getDefaultSharedPreferences(context).getString("currentfeed-app", "technology"));

        // Trigger reload?
        /*Thread t = new Thread(){
            public void run(){
                listAdapter.reloadReddits();
            }
        };
        t.run();*/

    }

    @Override
    protected void onActivityResult(int reqcode, int resultcode, Intent data){
        if (resultcode==1){
            srtext.setText(PreferenceManager.getDefaultSharedPreferences(context).getString("currentfeed-app", "technology"));
            listAdapter.reloadReddits();
        }
    }

    public void openLink(int position){
        // get the item
        JSONObject item = listAdapter.getItem(position);

        // NORMAL FEED ITEM CLICK
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String clickPrefString = prefs.getString(context.getString(R.string.on_click_pref), "1");
        int clickPref = Integer.valueOf(clickPrefString);
        switch (clickPref){
            case 1:
                // open in the reddinator view
                Intent clickIntent1 = new Intent(context, ViewRedditActivity.class);
                Bundle extras = new Bundle();
                try {
                    extras.putString(WidgetProvider.ITEM_ID, item.getString("id"));
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
            getThemeColors();
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

            // get thumbnail load preference for the widget
            loadThumbnails = mSharedPreferences.getBoolean("thumbnails-app", true);
            bigThumbs = mSharedPreferences.getBoolean("bigthumbs-app", false);
            hideInf = mSharedPreferences.getBoolean("hideinf-app", false);
            titleFontSize = mSharedPreferences.getString("titlefontpref", "16");
        }

        @Override
        public int getCount() {
            return (data.length() + 1); // plus 1 advertises the "load more" item to the listview without having to add it to the data source
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            View row;
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
                // build normal item
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
                    domain = tempobj.getString("domain");
                    thumbnail = (String) tempobj.get("thumbnail"); // we have to call get and cast cause its not in quotes
                    score = tempobj.getInt("score");
                    numcomments = tempobj.getInt("num_comments");
                } catch (JSONException e) {
                    e.printStackTrace();
                    // return null; // The view is invalid;
                }
                // create remote view from specified layout
                if (bigThumbs) {
                    row = getLayoutInflater().inflate(R.layout.listrowbigthumb, parent, false);
                } else {
                    row = getLayoutInflater().inflate(R.layout.listrow, parent, false);
                }
                TextView listheading = (TextView) row.findViewById(R.id.listheading);
                TextView sourcetxt = (TextView) row.findViewById(R.id.sourcetxt);
                TextView votestxt = (TextView) row.findViewById(R.id.votestxt);
                TextView commentstxt = (TextView) row.findViewById(R.id.commentstxt);
                // build view
                listheading.setText(Html.fromHtml(name).toString());
                listheading.setTextSize(Integer.valueOf(titleFontSize)); // use for compatibility setTextViewTextSize only introduced in API 16
                listheading.setTextColor(themeColors[0]);
                sourcetxt.setText(domain);
                sourcetxt.setTextColor(themeColors[3]);
                votestxt.setText(String.valueOf(score));
                votestxt.setTextColor(themeColors[4]);
                commentstxt.setText(String.valueOf(numcomments));
                commentstxt.setTextColor(themeColors[4]);
                row.findViewById(R.id.listdivider).setBackgroundColor(themeColors[2]);

                // load thumbnail if they are enabled for this widget
                ImageView thumbview = (ImageView) row.findViewById(R.id.thumbnail);
                if (loadThumbnails) {
                    // load big image if preference is set
                    if (!thumbnail.equals("") && !thumbnail.equals("self")) { // check for thumbnail; self is used to display the thinking logo on the reddit site, we'll just show nothing for now
                        // start the image load
                        loadImage(position, thumbnail);
                        thumbview.setVisibility(View.VISIBLE);
                    } else {
                        thumbview.setVisibility(View.GONE);
                    }
                } else {
                    thumbview.setVisibility(View.GONE);
                }
                // hide info bar if options set
                if (hideInf) {
                    thumbview.setVisibility(View.GONE);
                } else {
                    row.setVisibility(View.VISIBLE);
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

        private void loadImage(final int itempos, final String urlstr) {
            new ImageLoader(itempos, urlstr).execute();
        }

        class ImageLoader extends AsyncTask<Void, Integer, Bitmap> {
            int itempos;
            String urlstr;
            ImageLoader(int position, String url){
                itempos = position;
                urlstr = url;
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

        private void getThemeColors() {
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
        }
    }
}


