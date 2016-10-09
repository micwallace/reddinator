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

package au.com.wallaceit.reddinator.activity;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.CompoundButtonCompat;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.IconTextView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.ui.SubAutoCompleteAdapter;
import au.com.wallaceit.reddinator.ui.SubredditFeedAdapter;

public class SearchActivity extends Activity implements SubredditFeedAdapter.ActivityInterface {

    private Reddinator global;
    private SubredditFeedAdapter listAdapter;
    private CheckBox subredditLimitCb;
    private AutoCompleteTextView subredditLimitText;
    private AbsListView listView;
    private EditText searchbox;
    private IconTextView searchbtn;
    private View appView;
    private ThemeManager.Theme theme;

    private String query = "";
    private String feedPath = "";
    private String sort = "relevance";
    private String time = "week";
    private boolean restrictSub = false;

    private String lastItemId = "0";
    private boolean endOfFeed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        global = (Reddinator) getApplicationContext();
        setContentView(R.layout.activity_search);
        // Setup actionbar
        appView = findViewById(R.id.appview);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        searchbox = (EditText) this.findViewById(R.id.query);
        searchbox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    onSearchQueryEnter();
                }
                return true;
            }

        });

        searchbtn = (IconTextView) this.findViewById(R.id.searchbutton);
        searchbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSearchQueryEnter();
            }
        });

        feedPath = getIntent().getStringExtra("feed_path");
        if (feedPath==null) feedPath = ""; // default to front page

        subredditLimitCb = (CheckBox) findViewById(R.id.limit_sr);
        final SubAutoCompleteAdapter subredditAdapter = new SubAutoCompleteAdapter(this, R.layout.autocomplete_list_item);
        subredditLimitText = (AutoCompleteTextView) findViewById(R.id.limit_sr_subreddit);

        subredditLimitText.setAdapter(subredditAdapter);
        subredditLimitText.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                restrictSub = true;
                subredditLimitCb.setChecked(true);
                feedPath = "/r/" + subredditAdapter.getItem(position);
            }
        });
        subredditLimitText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    subredditLimitCb.setChecked(true);
                    onSearchQueryEnter();
                }
                return true;
            }
        });

        subredditLimitCb.setText(getString(R.string.limit_to));
        subredditLimitCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                restrictSub = isChecked;
                if (!query.equals("") && !subredditLimitText.getText().toString().equals(""))
                    onSearchQueryEnter();
            }
        });

        if (!feedPath.equals("")){
            restrictSub = true;
            subredditLimitCb.setChecked(true);
            subredditLimitText.setText(feedPath.replace("/r/", ""));
        }

        // set theme colors
        setThemeColors();

        Spinner sortselect = (Spinner) findViewById(R.id.sort);
        sortselect.getBackground().setColorFilter(buttonfilter);
        sortselect.setAdapter(new SearchSpinnerAdapter(SearchActivity.this, android.R.layout.simple_spinner_dropdown_item, android.R.id.text1, getResources().getStringArray(R.array.reddit_search_sorts)));
        sortselect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sort = "relevance"; // default if fails
                // find index
                switch (position) {
                    case 0:
                        sort = "relevance";
                        break;
                    case 1:
                        sort = "hot";
                        break;
                    case 2:
                        sort = "new";
                        break;
                    case 3:
                        sort = "top";
                        break;
                }
                if (!query.equals("")) search();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        Spinner timeselect = (Spinner) findViewById(R.id.time);
        timeselect.getBackground().setColorFilter(buttonfilter);
        timeselect.setAdapter(new SearchSpinnerAdapter(SearchActivity.this, android.R.layout.simple_spinner_dropdown_item, android.R.id.text1, getResources().getStringArray(R.array.reddit_search_times)));
        timeselect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // find index
                switch (position) {
                    case 0:
                        time = "all";
                        break;
                    case 1:
                        time = "hour";
                        break;
                    case 2:
                        time = "day";
                        break;
                    case 3:
                        time = "week";
                        break;
                    case 4:
                        time = "month";
                        break;
                    case 5:
                        time = "year";
                        break;
                    default:
                        time = "all";
                }
                if (!query.equals("")) search();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // Setup list adapter
        listView = (ListView) findViewById(R.id.applistview);
        listAdapter = new SubredditFeedAdapter(this, this, global, theme, -2, null, true, true);
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // open in the reddinator view
                Intent clickIntent1 = new Intent(SearchActivity.this, ViewRedditActivity.class);
                clickIntent1.putExtras(listAdapter.getItemExtras(position));
                SearchActivity.this.startActivity(clickIntent1);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
                Intent ointent = new Intent(SearchActivity.this, FeedItemDialogActivity.class);
                ointent.putExtras(listAdapter.getItemExtras(position));
                SearchActivity.this.startActivityForResult(ointent, 1);
                return true;
            }
        });

        if (Intent.ACTION_SEARCH.equals(getIntent().getAction())){
            query = getIntent().getStringExtra("query");
            sort = getIntent().getStringExtra("sort");
            time = getIntent().getStringExtra("time");
            restrictSub = getIntent().getBooleanExtra("restrict_sub", true);

            search();
        }
    }

    private void onSearchQueryEnter(){
        restrictSub = subredditLimitCb.isChecked();
        feedPath = (restrictSub  ? "/r/" + subredditLimitText.getText().toString() : "");
        if (restrictSub && feedPath.equals("/r/")){
            subredditLimitCb.setChecked(false);
            feedPath = "";
            restrictSub = false;
        }
        query = searchbox.getText().toString();
        if (!query.equals("")) {
            search();
        } else {
            Toast.makeText(SearchActivity.this, getString(R.string.no_query_message), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Bundle update = global.getItemUpdate();
        if (update != null) {
            listAdapter.updateUiVote(update.getInt("position", 0), update.getString("id"), update.getString("val"), update.getInt("netvote"));
            // refresh view; unfortunately we have to refresh them all :( invalidateViewAtPosition(); please android?
            //listView.invalidateViews();
        }
    }

    private int headerText = Color.BLACK;
    private ColorMatrixColorFilter buttonfilter;
    private void setThemeColors() {
        theme = global.mThemeManager.getActiveTheme("appthemepref");
        appView.setBackgroundColor(Color.parseColor(theme.getValue("background_color")));
        headerText = Color.parseColor(theme.getValue("header_text"));
        int headerColor = Color.parseColor(theme.getValue("header_color"));
        int iconColor = Color.parseColor(theme.getValue("default_icon"));
        findViewById(R.id.searchbar).setBackgroundColor(headerColor);
        ColorMatrixColorFilter searchFilter = Utilities.getColorFilterFromColor(iconColor, -50);
        searchbox.setHintTextColor(headerText);
        searchbox.setTextColor(headerText);
        searchbox.getBackground().setColorFilter(searchFilter);
        searchbtn.setTextColor(iconColor);
        subredditLimitText.setHintTextColor(headerText);
        subredditLimitText.setTextColor(headerText);
        subredditLimitCb.setTextColor(headerText);
        int states[][] = {{android.R.attr.state_checked}, {}};
        int colors[] = {headerText, headerText};
        CompoundButtonCompat.setButtonTintList(subredditLimitCb, new ColorStateList(states, colors));
        subredditLimitText.getBackground().setColorFilter(searchFilter);
        buttonfilter = Utilities.getColorFilterFromColor(iconColor, 250);
    }

    private class SearchSpinnerAdapter extends ArrayAdapter<String>{

        SearchSpinnerAdapter(Context context, int resource, int textViewResourceId, String[] objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @NonNull
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View v = super.getView(position, convertView, parent);
            ((TextView) v).setTextColor(headerText);
            return v;
        }
    }

    @Override
    protected void onActivityResult(int reqcode, int resultcode, Intent data) {
        switch (resultcode) {
            // reload feed prefs
            case 1:
                listAdapter.loadFeedPrefs();
                listView.invalidateViews();
                break;
            // initiate vote
            case 3:
            case 4:
                int position = data.getIntExtra(Reddinator.ITEM_FEED_POSITION, -1);
                listAdapter.initialiseVote(position, (resultcode==3?1:-1));
                break;
            // reload feed data from cache
            case 5:
                listAdapter.removePostAtPosition(data.getIntExtra(Reddinator.ITEM_FEED_POSITION, -1));
                //listView.invalidateViews();
                break;
        }
        if (data!=null && data.getBooleanExtra("themeupdate", true)){
            setThemeColors();
            listAdapter.setTheme(theme);
            //listView.invalidateViews();
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return false;
    }

    private JSONArray data;

    public void search() {
        global.triggerThunbnailCacheClean();
        showLoader();
        new SearchFeedLoader(false).execute();
    }

    public void hideLoader(boolean goToTopOfList) {
        hideLoader();
        // go to the top of the list view
        if (goToTopOfList) {
            listView.smoothScrollToPosition(0);
        }
    }

    public void hideLoader() {
        setProgressBarIndeterminateVisibility(false);
    }

    public void showLoader() {
        setProgressBarIndeterminateVisibility(true);
    }

    public void loadMore(){
        showLoader();
        new SearchFeedLoader(true).execute();
    }

    class SearchFeedLoader extends AsyncTask<Void, Integer, Long> {

        private Boolean loadMore;
        private RedditData.RedditApiException exception;

        SearchFeedLoader(Boolean loadmore) {
            loadMore = loadmore;
        }

        @Override
        protected Long doInBackground(Void... none) {
            JSONArray tempArray;
            endOfFeed = false;
            if (loadMore) {
                // fetch 25 more after current last item and append to the list
                try {
                    tempArray = global.mRedditData.searchRedditPosts(query, feedPath, restrictSub, sort, time, 25, lastItemId);
                } catch (RedditData.RedditApiException e) {
                    e.printStackTrace();
                    exception = e;
                    return (long) 0;
                }
                if (tempArray.length() == 0) {
                    endOfFeed = true;
                } else {
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
                //int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));
                try {
                    tempArray = global.mRedditData.searchRedditPosts(query, feedPath, restrictSub, sort, time, 25, "0");
                } catch (RedditData.RedditApiException e) {
                    e.printStackTrace();
                    exception = e;
                    return (long) 0;
                }
                // check if end of feed, if not process & set feed data
                if (tempArray.length() == 0) {
                    endOfFeed = true;
                }
                data = tempArray;
            }
            // save feed
            if (endOfFeed){
                lastItemId = "0";
            } else {
                try {
                    lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name"); // name is actually the unique id we want
                } catch (JSONException e) {
                    lastItemId = "0"; // Could not get last item ID; perform a reload next time
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
                    hideLoader(false); // don't go to top of list
                } else {
                    hideLoader(true); // go to top
                }
                listAdapter.setFeed(data, !endOfFeed, true);
                //listAdapter.notifyDataSetChanged();
            } else {
                Toast.makeText(SearchActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
                hideLoader(false); // don't go to top of list and show error icon
            }
        }
    }

}


