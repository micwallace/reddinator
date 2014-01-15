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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import java.util.*;

public class SubredditSelectActivity extends ListActivity {
    ArrayAdapter<String> mListAdapter;
    private ArrayList<String> personalList;
    SharedPreferences mSharedPreferences;
    GlobalObjects global;
    String curSort;
    Button sortBtn;
    boolean curThumbPref;
    boolean curBigThumbPref;
    boolean curHideInfPref;
    TextView widgetPrefBtn;
    private int mAppWidgetId;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.subredditselect);
        // load personal list from saved prefereces, if null use default and save
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(SubredditSelectActivity.this);
        global = ((GlobalObjects) SubredditSelectActivity.this.getApplicationContext());
        global.loadSavedAccn(mSharedPreferences);
        Set<String> feeds = mSharedPreferences.getStringSet("personalsr", new HashSet<String>());
        if (feeds.isEmpty()) {
            // first time setup
            personalList = new ArrayList<String>(Arrays.asList("Front Page", "all", "arduino", "AskReddit", "pics", "technology", "science", "videos", "worldnews"));
            savePersonalList();
        } else {
            personalList = new ArrayList<String>(feeds);
        }
        mListAdapter = new MyRedditsAdapter(this, personalList);
        setListAdapter(mListAdapter);
        ListView listView = getListView();
        listView.setTextFilterEnabled(true);
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
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                String subreddit = ((TextView) view.findViewById(R.id.subreddit_name)).getText().toString();
                // save list
                savePersonalList();
                // save preference
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SubredditSelectActivity.this);
                Editor editor = prefs.edit();

                if (mAppWidgetId != 0) {
                    editor.putString("currentfeed-" + mAppWidgetId, subreddit);
                    // refresh widget and close activity (NOTE: put in function)
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelectActivity.this);
                    RemoteViews views = new RemoteViews(getPackageName(), getThemeLayoutId());
                    views.setTextViewText(R.id.subreddittxt, subreddit);
                    views.setViewVisibility(R.id.srloader, View.VISIBLE);
                    views.setViewVisibility(R.id.erroricon, View.INVISIBLE);
                    // bypass cache if service not loaded
                    global.setBypassCache(true);
                    appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, views);
                    appWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.listview);
                } else {
                    editor.putString("currentfeed-app", subreddit);
                    setResult(2); // update feed prefs + reload feed
                }
                // save the preference
                editor.commit();
                finish();
                //System.out.println(sreddit+" selected");
            }
        });
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
                if (mSharedPreferences.getString("uname", "").equals("") || mSharedPreferences.getString("pword", "").equals("")) {
                    showLoginDialog();
                } else {
                    showImportDialog();
                }
            }
        });
        // sort button
        sortBtn = (Button) findViewById(R.id.sortselect);
        curSort = mSharedPreferences.getString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), "hot");
        String sortTxt = "Sort:  " + curSort;
        sortBtn.setText(sortTxt);
        sortBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                showSortDialog();
            }
        });
        // set options dialog click listener
        widgetPrefBtn = (TextView) findViewById(R.id.widgetprefbtn);
        widgetPrefBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
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
                        prefsedit.commit();
                    }
                });
                builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                builder.create().show();
            }
        });
        // load initial values for comparison
        curThumbPref = mSharedPreferences.getBoolean("thumbnails-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), true);
        curBigThumbPref = mSharedPreferences.getBoolean("bigthumbs-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false);
        curHideInfPref = mSharedPreferences.getBoolean("hideinf-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Toast.makeText(this, "Press back to save changes", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == 1) {
            String subreddit = data.getStringExtra("subreddit");
            personalList.add(subreddit);
            mListAdapter.notifyDataSetChanged();
        }
    }

    // save changes on back press
    public void onBackPressed() {
        savePersonalList();
        // check if sort has changed
        if (!curSort.equals(mSharedPreferences.getString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), "hot")) || curThumbPref != mSharedPreferences.getBoolean("thumbnails-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), true) || curBigThumbPref != mSharedPreferences.getBoolean("bigthumbs-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false) || curHideInfPref != mSharedPreferences.getBoolean("hideinf-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), false)) {
            if (mAppWidgetId != 0) {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(SubredditSelectActivity.this);
                RemoteViews views = new RemoteViews(getPackageName(), getThemeLayoutId());
                views.setViewVisibility(R.id.srloader, View.VISIBLE);
                views.setViewVisibility(R.id.erroricon, View.INVISIBLE);
                // bypass the cached entrys only if the sorting preference has changed
                if (!curSort.equals(mSharedPreferences.getString("sort-" + (mAppWidgetId == 0 ? "app" : mAppWidgetId), "hot"))) {
                    global.setBypassCache(true);
                } else {
                    global.setRefreshView();
                }
                appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, views);
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

    // save/restore personal list
    private void savePersonalList() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SubredditSelectActivity.this);
        Editor editor = prefs.edit();
        Set<String> set = new HashSet<String>();
        set.addAll(personalList);
        editor.putStringSet("personalsr", set);
        editor.commit();
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
                prefsedit.commit();
                // set new text in button
                String sorttxt = "Sort:  " + sort;
                sortBtn.setText(sorttxt);
                //System.out.println("Sort set: "+sort);
                dialog.dismiss();
            }
        });
        builder.show();
    }

    // show the import/login dialog
    private void showLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(SubredditSelectActivity.this);
        // Get the layout inflater
        LayoutInflater inflater = getLayoutInflater();
        final View v = inflater.inflate(R.layout.logindialog, null);
        v.findViewById(R.id.clearlist).setVisibility(View.VISIBLE);
        builder.setView(v)
                // Add action buttons
                .setPositiveButton("Import", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String username = ((EditText) v.findViewById(R.id.username)).getText().toString();
                        String password = ((EditText) v.findViewById(R.id.password)).getText().toString();
                        boolean clearlist = ((CheckBox) v.findViewById(R.id.clearlist)).isChecked();
                        boolean rememberaccn = ((CheckBox) v.findViewById(R.id.rememberaccn)).isChecked();
                        global.setAccount(mSharedPreferences, username, password, false); // load temp account
                        dialog.cancel();
                        // import
                        importSubreddits(clearlist, rememberaccn, username, password); // remember accn for first time import
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .setTitle("Login to Reddit");
        builder.create().show();
    }

    private void showImportDialog() {
        AlertDialog importDialog = new AlertDialog.Builder(SubredditSelectActivity.this).create(); //Read Update
        importDialog.setTitle("Replace current list?");

        importDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                importSubreddits(true, false, null, null);
            }
        });

        importDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                importSubreddits(false, false, null, null);
            }
        });

        importDialog.show();
    }

    // import personal subreddits
    private void importSubreddits(final boolean clearlist, final boolean rememberaccn, final String username, final String password) {
        // use a thread for searching
        final ProgressDialog sdialog = ProgressDialog.show(SubredditSelectActivity.this, "", ("Importing..."), true);
        Thread t = new Thread() {
            public void run() {

                final ArrayList<String> list = global.mRedditData.getMySubreddits();
                // copy into current personal list if not empty or error
                if (!list.isEmpty() && !list.get(0).contains("Error:")) {
                    if (clearlist) {
                        personalList.clear();
                    }
                    personalList.addAll(list);
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        sdialog.dismiss();
                        if (list.isEmpty() || list.get(0).contains("Error:")) {
                            new AlertDialog.Builder(SubredditSelectActivity.this).setMessage(list.isEmpty() ? "No subreddits to import!" : list.get(0))
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                        }
                                    }).show();
                        } else {
                            // save account to prefs if selected
                            if (rememberaccn) {
                                global.setAccount(mSharedPreferences, username, password, true); // true saves a persistent account
                            }
                            mListAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        };
        t.start();
    }

    // list adapter
    class MyRedditsAdapter extends ArrayAdapter<String> {
        private LayoutInflater inflater;

        public MyRedditsAdapter(Context context, List<String> objects) {
            super(context, R.layout.myredditlistitem, R.id.subreddit_name, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView = inflater.inflate(R.layout.myredditlistitem, parent,
                    false);
            super.getView(position, convertView, parent);
            // setup the row
            ((TextView) convertView.findViewById(R.id.subreddit_name)).setText(personalList.get(position).toString());
            convertView.findViewById(R.id.subreddit_delete_btn).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String sreddit = ((TextView) ((View) v.getParent()).findViewById(R.id.subreddit_name)).getText().toString();
                    personalList.remove(sreddit);
                    mListAdapter.notifyDataSetChanged();
                }
            });
            return convertView;
        }
    }

    private int getThemeLayoutId() {
        // get theme layout id
        int layoutid = 1;
        switch (Integer.valueOf(mSharedPreferences.getString("widgetthemepref", "1"))) {
            case 1:
                layoutid = R.layout.widgetmain;
                break;
            case 2:
                layoutid = R.layout.widgetdark;
                break;
            case 3:
                layoutid = R.layout.widgetholo;
                break;
            case 4:
                layoutid = R.layout.widgetdarkholo;
                break;
        }
        return layoutid;
    }
}
