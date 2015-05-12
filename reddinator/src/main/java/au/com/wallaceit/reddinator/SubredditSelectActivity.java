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

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.*;

public class SubredditSelectActivity extends Activity {
    ArrayAdapter<String> mListAdapter;
    ArrayAdapter<String> mMultiAdapter;
    private ArrayList<String> personalList;
    SharedPreferences mSharedPreferences;
    GlobalObjects global;
    String curSort;
    Button sortBtn;
    boolean needsThemeUpdate = false;
    boolean curThumbPref;
    boolean curBigThumbPref;
    boolean curHideInfPref;
    private int mAppWidgetId;
    private ListView subList;
    private ListView multiList;
    private SimpleTabsWidget tabs;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.subredditselect);

        // load personal list from saved prefereces, if null use default and save
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(SubredditSelectActivity.this);
        global = ((GlobalObjects) SubredditSelectActivity.this.getApplicationContext());

        // get subreddit list and set adapter
        personalList = global.getSubredditManager().getSubreddits();
        mListAdapter = new MySubredditsAdapter(this, personalList);
        subList = (ListView) findViewById(R.id.sublist);
        subList.setAdapter(mListAdapter);
        subList.setTextFilterEnabled(true);
        subList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                String subreddit = ((TextView) view.findViewById(R.id.subreddit_name)).getText().toString();
                global.getSubredditManager().setFeedSubreddit(mAppWidgetId, subreddit);
                updateFeedAndFinish();
                //System.out.println(sreddit+" selected");
            }
        });
        mListAdapter.sort(new Comparator<String>() {
            @Override
            public int compare(String s, String t1) {
                return s.compareToIgnoreCase(t1);
            }
        });
        // get multi list and set adapter
        //personalList = global.getPersonalList();
        mMultiAdapter = new MyMultisAdapter(this, global.getSubredditManager().getMultiNames());
        multiList = (ListView) findViewById(R.id.multilist);
        multiList.setAdapter(mMultiAdapter);
        multiList.setTextFilterEnabled(true);
        multiList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                String multiname = ((TextView) view.findViewById(R.id.multireddit_name)).getText().toString();
                JSONObject multiObj = global.getSubredditManager().getMultiData(multiname);
                try {
                    String name = multiObj.getString("display_name");
                    String path = multiObj.getString("path");
                    global.getSubredditManager().setFeed(mAppWidgetId, name, path, true);
                    updateFeedAndFinish();
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(SubredditSelectActivity.this, "Error setting multi.", Toast.LENGTH_LONG).show();
                }
            }
        });
        mMultiAdapter.sort(new Comparator<String>() {
            @Override
            public int compare(String s, String t1) {
                return s.compareToIgnoreCase(t1);
            }
        });

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                mAppWidgetId = 0; // Id of 4 zeros indicates its the app view, not a widget, that is being updated
            }
        } else {
            mAppWidgetId = 0;
        }

        final ViewPager pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(new SubredditsPagerAdapter());

        LinearLayout tabsLayout = (LinearLayout) findViewById(R.id.tab_widget);
        tabs = new SimpleTabsWidget(SubredditSelectActivity.this, tabsLayout);
        tabs.setViewPager(pager);

        Button addBtn = (Button) findViewById(R.id.addsrbutton);
        addBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent intent = new Intent(SubredditSelectActivity.this, ViewAllSubredditsActivity.class);
                startActivityForResult(intent, 0);
            }
        });
        Button importBtn = (Button) findViewById(R.id.importsrbutton);
        importBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (global.mRedditData.isLoggedIn()) {
                    if (pager.getCurrentItem()==1) {
                        showImportMultiDialog();
                    } else {
                        showImportDialog();
                    }
                } else {
                    global.mRedditData.initiateLogin(SubredditSelectActivity.this);
                }
            }
        });
        // sort button
        sortBtn = (Button) findViewById(R.id.sortselect);
        curSort = mSharedPreferences.getString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), "hot");
        String sortTxt = "Sort:  " + curSort;
        sortBtn.setText(sortTxt);
        sortBtn.setCompoundDrawables(new IconDrawable(this, Iconify.IconValue.fa_sort).color(Color.parseColor("#22000000")).actionBarSize(), null, null, null);
        sortBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                showSortDialog();
            }
        });

        // load initial values for comparison
        curThumbPref = mSharedPreferences.getBoolean("thumbnails-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), true);
        curBigThumbPref = mSharedPreferences.getBoolean("bigthumbs-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false);
        curHideInfPref = mSharedPreferences.getBoolean("hideinf-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false);



        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // set theme colors
        setThemeColors();
    }

    class SubredditsPagerAdapter extends PagerAdapter {

        public Object instantiateItem(View collection, int position) {

            int resId = 0;
            switch (position) {
                case 0:
                    resId = R.id.sublist;
                    break;
                case 1:
                    resId = R.id.multilist;
                    break;
            }
            return findViewById(resId);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "My Subreddits";
                case 1:
                    return "My Multis";
            }

            return null;
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0 == ((View) arg1);
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setThemeColors(){
        ThemeManager.Theme theme = global.mThemeManager.getActiveTheme("appthemepref");
        int headerColor = Color.parseColor(theme.getValue("header_color"));
        //findViewById(R.id.srtoolbar).setBackgroundColor(headerColor);
        tabs.setBackgroundColor(headerColor);
        tabs.setInidicatorColor(Color.parseColor("#FF4500"));
        tabs.setTextColor(Color.parseColor(theme.getValue("header_text")));
        /*if (actionBar!=null)
            actionBar.setStackedBackgroundDrawable(new ColorDrawable(headerColor));*/
    }

    @Override
    protected void onStart() {
        super.onStart();
        Toast.makeText(this, "Press back to save changes", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == ViewAllSubredditsActivity.RESULT_REFRESH_LIST) {
            personalList = global.getSubredditManager().getSubreddits();
            mListAdapter.notifyDataSetChanged();
        } else if (resultCode == ViewAllSubredditsActivity.RESULT_SET_SUBREDDIT) {
            global.getSubredditManager().setFeedSubreddit(mAppWidgetId, data.getStringExtra("subreddit"));
            updateFeedAndFinish();
        }
    }

    private void updateFeedAndFinish() {

        if (mAppWidgetId != 0) {
            // refresh widget and close activity (NOTE: put in function)
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelectActivity.this);
            RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);
            views.setTextViewText(R.id.subreddittxt, global.getSubredditManager().getCurrentFeedName(mAppWidgetId));
            views.setViewVisibility(R.id.srloader, View.VISIBLE);
            views.setViewVisibility(R.id.erroricon, View.INVISIBLE);
            // bypass cache if service not loaded
            global.setBypassCache(true);
            appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, views);
            appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
        } else {
            setResult(2); // update feed prefs + reload feed
        }
        finish();
    }

    // save changes on back press
    public void onBackPressed() {
        // check if sort has changed
        if (!curSort.equals(mSharedPreferences.getString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), "hot")) || curThumbPref != mSharedPreferences.getBoolean("thumbnails-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), true) || curBigThumbPref != mSharedPreferences.getBoolean("bigthumbs-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false) || curHideInfPref != mSharedPreferences.getBoolean("hideinf-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false) || needsThemeUpdate) {
            if (mAppWidgetId != 0) {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelectActivity.this);
                RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);
                views.setViewVisibility(R.id.srloader, View.VISIBLE);
                views.setViewVisibility(R.id.erroricon, View.INVISIBLE);
                // bypass the cached entrys only if the sorting preference has changed
                if (!curSort.equals(mSharedPreferences.getString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), "hot"))) {
                    global.setBypassCache(true);
                } else {
                    global.setRefreshView();
                }
                if (needsThemeUpdate){
                    WidgetProvider.updateAppWidgets(SubredditSelectActivity.this, appWidgetManager, new int[]{mAppWidgetId}, false);
                } else {
                    appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, views);
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
            } else {
                if (!curSort.equals(mSharedPreferences.getString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), "hot"))) {
                    setResult(2); // reload feed and prefs
                } else {
                    setResult(1); // tells main activity to update feed prefs
                }
            }
        } else {
            setResult(0); // feed doesn't need updating
        }
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.subreddit_select_menu, menu);
        // set options menu view
        int iconColor = Color.parseColor("#DBDBDB");
        (menu.findItem(R.id.menu_submit)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_pencil).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_feedprefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_list_alt).color(iconColor).actionBarSize());
        if (mAppWidgetId==0) {
            (menu.findItem(R.id.menu_widgettheme)).setEnabled(false);
        }
        (menu.findItem(R.id.menu_widgettheme)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_paint_brush).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_thememanager)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_cogs).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_prefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_wrench).color(iconColor).actionBarSize());

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu)
    {
        if(featureId == Window.FEATURE_ACTION_BAR && menu != null){
            if(menu.getClass().getSimpleName().equals("MenuBuilder")){
                try{
                    Method m = menu.getClass().getDeclaredMethod(
                            "setOptionalIconsVisible", Boolean.TYPE);
                    m.setAccessible(true);
                    m.invoke(menu, true);
                }
                catch(NoSuchMethodException e){
                    System.out.println("Could not display action icons in menu");
                }
                catch(Exception e){
                    throw new RuntimeException(e);
                }
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;

            case R.id.menu_submit:
                Intent submitIntent = new Intent(SubredditSelectActivity.this, SubmitActivity.class);
                startActivity(submitIntent);
                break;

            case R.id.menu_feedprefs:
                showFeedPrefsDialog();
                return true;

            case R.id.menu_widgettheme:
                showWidgetThemeDialog();
                return true;

            case R.id.menu_thememanager:
                Intent intent = new Intent(SubredditSelectActivity.this, ThemesActivity.class);
                startActivityForResult(intent, 0);
                return true;

            case R.id.menu_prefs:
                Intent intent2 = new Intent(SubredditSelectActivity.this, PrefsActivity.class);
                startActivityForResult(intent2, 0);
                return true;
        }
        return false;
    }

    // show sort select dialog
    private void showSortDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        builder.setTitle("Pick a sort, any sort");
        builder.setItems(R.array.reddit_sorts, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Editor prefsedit = mSharedPreferences.edit();
                String sort = "hot"; // default if fails
                // find index
                switch (which) {
                    case 0:
                        sort = "hot";
                        break;
                    case 1:
                        sort = "new";
                        break;
                    case 2:
                        sort = "rising";
                        break;
                    case 3:
                        sort = "controversial";
                        break;
                    case 4:
                        sort = "top";
                        break;
                }
                prefsedit.putString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), sort);
                prefsedit.apply();
                // set new text in button
                String sorttxt = "Sort:  " + sort;
                sortBtn.setText(sorttxt);
                //System.out.println("Sort set: "+sort);
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void showFeedPrefsDialog(){
        final CharSequence[] names = {"Thumbnails", "Thumbs On Top", "Hide Post Info"};
        final boolean[] initvalue = {mSharedPreferences.getBoolean("thumbnails-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), true), mSharedPreferences.getBoolean("bigthumbs-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false), mSharedPreferences.getBoolean("hideinf-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false)};
        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        builder.setTitle("Feed Options");
        builder.setMultiChoiceItems(names, initvalue, new DialogInterface.OnMultiChoiceClickListener() {
            public void onClick(DialogInterface dialogInterface, int item, boolean state) {
                Editor prefsedit = mSharedPreferences.edit();
                switch (item) {
                    case 0:
                        prefsedit.putBoolean("thumbnails-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), state);
                        break;
                    case 1:
                        prefsedit.putBoolean("bigthumbs-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), state);
                        break;
                    case 2:
                        prefsedit.putBoolean("hideinf-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), state);
                        break;
                }
                prefsedit.apply();
            }
        });
        builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }

    private void showWidgetThemeDialog(){

        // set themes list
        LinkedHashMap<String, String> themeList = global.mThemeManager.getThemeList(ThemeManager.LISTMODE_ALL);
        themeList.put("app_select", "Use App theme");
        final String[] keys = themeList.keySet().toArray(new String[themeList.keySet().size()]);
        String curTheme = mSharedPreferences.getString("widgettheme-"+mAppWidgetId, "app_select");
        int curIndex = 0;
        for (int i=0; i<keys.length; i++){
            if (keys[i].equals(curTheme)) {
                curIndex = i;
                break;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Widget Theme")
        .setSingleChoiceItems(themeList.values().toArray(new String[themeList.values().size()]), curIndex,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    needsThemeUpdate = true;
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    editor.putString("widgettheme-" + mAppWidgetId, keys[i]);
                    System.out.println(keys[i]);
                    editor.apply();
                    dialogInterface.cancel();
                }
            }
        ).setPositiveButton("Close", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        }).show();
    }

    private void showImportDialog() {
        AlertDialog importDialog = new AlertDialog.Builder(SubredditSelectActivity.this).create(); //Read Update
        importDialog.setTitle("Replace current list?");

        importDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                importSubreddits(true);
            }
        });

        importDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                importSubreddits(false);
            }
        });

        importDialog.show();
    }

    // import personal subreddits
    private void importSubreddits(final boolean clearlist) {
        // use a thread for searching; show dialog
        // Set thread network policy to prevent network on main thread exceptions.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        final ProgressDialog sdialog = ProgressDialog.show(SubredditSelectActivity.this, "", ("Importing..."), true);
        Thread t = new Thread() {
            public void run() {

                final JSONArray list;
                try {
                    list = global.mRedditData.getMySubreddits();
                } catch (final RedditData.RedditApiException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            sdialog.dismiss();
                            // check login required
                            if (e.isAuthError())
                                global.mRedditData.initiateLogin(SubredditSelectActivity.this);
                            // show error
                            Toast.makeText(SubredditSelectActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                if (list == null)
                    return;

                // copy into current personal list if not empty or error
                if (list.length()>0) {
                    ArrayList<String> mysrlist = new ArrayList<>();
                    // Add Front Page & all
                    mysrlist.add(0, "Front Page");
                    mysrlist.add(1, "all");

                    int i = 0;
                    while (i < list.length()) {
                        try {
                            mysrlist.add(list.getJSONObject(i).getJSONObject("data").getString("display_name"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        i++;
                    }
                    global.getSubredditManager().addSubreddits(mysrlist, clearlist);
                    personalList = global.getSubredditManager().getSubreddits();
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        sdialog.dismiss();
                        if (list.length()==0) {
                            new AlertDialog.Builder(SubredditSelectActivity.this).setMessage("No subreddits to import!")
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                        }
                                    }).show();
                        } else {
                            mListAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        };
        t.start();
    }

    private void showImportMultiDialog() {
        AlertDialog importDialog = new AlertDialog.Builder(SubredditSelectActivity.this).create(); //Read Update
        importDialog.setTitle("Importing Multi-reddits, Replace current list?");

        importDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                importMultireddits(true);
            }
        });

        importDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                importMultireddits(false);
            }
        });

        importDialog.show();
    }

    private void importMultireddits(final boolean clearlist) {
        // use a thread for searching; show dialog
        // Set thread network policy to prevent network on main thread exceptions.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        final ProgressDialog sdialog = ProgressDialog.show(SubredditSelectActivity.this, "", ("Importing Multis..."), true);
        Thread t = new Thread() {
            public void run() {

                final JSONArray list;
                try {
                    list = global.mRedditData.getMyMultis();
                } catch (final RedditData.RedditApiException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            sdialog.dismiss();
                            // check login required
                            if (e.isAuthError())
                                global.mRedditData.initiateLogin(SubredditSelectActivity.this);
                            // show error
                            Toast.makeText(SubredditSelectActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                if (list == null)
                    return;

                // copy into current personal list if not empty or error
                System.out.println(list);
                runOnUiThread(new Runnable() {
                    public void run() {
                        sdialog.dismiss();
                        if (list.length() == 0) {
                            new AlertDialog.Builder(SubredditSelectActivity.this).setMessage("No multis to import!")
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                        }
                                    }).show();
                        } else {
                            global.mSubManager.addMultis(list, clearlist);
                            mListAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        };
        t.start();
    }

    // list adapter
    class MySubredditsAdapter extends ArrayAdapter<String> {
        private LayoutInflater inflater;

        public MySubredditsAdapter(Context context, ArrayList<String> objects) {
            super(context, R.layout.myredditlistitem, R.id.subreddit_name, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            super.getView(position, convertView, parent);
            ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                // inflate new view
                convertView = inflater.inflate(R.layout.myredditlistitem, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(R.id.subreddit_name);
                viewHolder.deleteIcon = (IconTextView) convertView.findViewById(R.id.subreddit_delete_btn);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            // setup the row
            viewHolder.name.setText(getItem(position));
            viewHolder.deleteIcon.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String sreddit = ((TextView) ((View) v.getParent()).findViewById(R.id.subreddit_name)).getText().toString();
                    personalList.remove(sreddit);
                    global.getSubredditManager().removeSubreddit(sreddit);
                    mListAdapter.notifyDataSetChanged();
                }
            });
            convertView.setTag(viewHolder);

            return convertView;
        }

        class ViewHolder {
            TextView name;
            IconTextView deleteIcon;
        }
    }

    class MyMultisAdapter extends ArrayAdapter<String> {
        private LayoutInflater inflater;

        public MyMultisAdapter(Context context, ArrayList<String> objects) {
            super(context, R.layout.myredditlistitem, R.id.subreddit_name, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            super.getView(position, convertView, parent);
            ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                // inflate new view
                convertView = inflater.inflate(R.layout.mymultilistitem, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(R.id.multireddit_name);
                viewHolder.deleteIcon = (IconTextView) convertView.findViewById(R.id.multi_delete_btn);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            JSONObject multiObj = global.getSubredditManager().getMultiData(getItem(position));
            String displayName;
            try {
                displayName = multiObj.getString("display_name");
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
            // setup the row
            viewHolder.name.setText(displayName);
            viewHolder.deleteIcon.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String mreddit = ((TextView) ((View) v.getParent()).findViewById(R.id.multireddit_name)).getText().toString();
                    //personalList.remove(mreddit);
                    global.getSubredditManager().removeMulti(mreddit);
                    mListAdapter.notifyDataSetChanged();
                }
            });

            convertView.setTag(viewHolder);

            return convertView;
        }

        class ViewHolder {
            TextView name;
            IconTextView deleteIcon;
        }
    }
}
