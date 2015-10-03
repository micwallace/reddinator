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
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.IconTextView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ViewAllSubredditsActivity extends ListActivity {
    public static final int RESULT_ADD_TO_MULTI = 3;
    public static final int RESULT_SET_SUBREDDIT = 2;
    public static final int RESULT_ADD_SUBREDDIT = 1;
    public static final String ACTION_ADD_MULTI_SUB = "ADD_MULTI_SUBREDDIT";
    private String action;
    private GlobalObjects global;
    private ArrayList<JSONObject> sreddits = new ArrayList<>();
    private ArrayList<JSONObject> defaultsreddits;
    private JSONArray srjson;
    private SubredditsAdapter listadapter;
    private EditText searchbox;
    private TextView emptyview;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        action = getIntent().getAction();
        global = ((GlobalObjects) getApplicationContext());
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        setContentView(R.layout.viewallsubreddit);
        // setup list view
        ListView listview = getListView();
        listview.setTextFilterEnabled(true);
        listview.setEmptyView(findViewById(R.id.subredditload));
        listview.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                returnResult(sreddits.get(position), false);
            }
        });
        // get empty view text for easy access later
        emptyview = (TextView) findViewById(R.id.poploadtxt);
        // setup search buttons
        searchbox = (EditText) this.findViewById(R.id.searchbox);
        searchbox.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    search(v.getText().toString());
                }
                return true;
            }

        });
        IconTextView searchbtn = (IconTextView) this.findViewById(R.id.searchbutton);
        searchbtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = searchbox.getText().toString();
                if (!query.equals("")) {
                    search(query);
                } else {
                    new AlertDialog.Builder(ViewAllSubredditsActivity.this).setTitle("No Query").setMessage("Please enter something to search for").show();
                }
            }
        });
        loadDefaults();
        // get list data
        listadapter = new SubredditsAdapter(this);
        if (global.isSrlistCached()) {
            if (action==null || !action.equals(ACTION_ADD_MULTI_SUB)) {
                sreddits.addAll(defaultsreddits);
            }
            sreddits.addAll(global.getSrList());
        } else {
            loadPopularSubreddits();
        }
        listview.setAdapter(listadapter);
    }

    private boolean cancelrevert = false;

    public void onBackPressed() {
        // System.out.println("onBackPressed()");
        if (searchbox.getText().toString().equals("")) {
            this.finish();
        } else {
            if (global.isSrlistCached()) {
                sreddits.clear();
                if (action==null || !action.equals(ACTION_ADD_MULTI_SUB)) {
                    sreddits.addAll(defaultsreddits);
                }
                sreddits.addAll(global.getSrList());
                listadapter.notifyDataSetChanged();
            } else {
                emptyview.setText("Loading popular...");
                sreddits.clear();
                listadapter.notifyDataSetChanged();
                if (dlpopulartask == null) {
                    loadPopularSubreddits();
                } else {
                    cancelrevert = true;
                }
            }
            searchbox.setText("");
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

    protected void onResume() {
        //System.out.println("onResume()");
        super.onResume();
    }

    private void loadDefaults(){
        defaultsreddits = new ArrayList<>();
        try {
            defaultsreddits.add(new JSONObject("{\"display_name\"=\"Front Page\", \"public_description\"=\"Your reddit front page\"}")); // slap the front page on there
            defaultsreddits.add(new JSONObject("{\"display_name\"=\"all\", \"public_description\"=\"The best of reddit\"}")); // and an all
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void loadPopularSubreddits() {
        dlpopulartask = new DLTask();
        dlpopulartask.execute("");
    }

    private void search(final String query) {
        //System.out.println("Searching: " + query);
        if (dlpopulartask != null) {
            dlpopulartask.cancel(true);
        }
        // use a thread for searching
        final ProgressDialog sdialog = ProgressDialog.show(ViewAllSubredditsActivity.this, "", ("Searching..."), true);
        Thread t = new Thread() {
            public void run() {
                // get all popular subreddits
                try {
                    srjson = global.mRedditData.getSubredditSearch(query);
                } catch (final RedditData.RedditApiException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(ViewAllSubredditsActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                            sdialog.dismiss();
                        }
                    });
                    return;
                }
                //System.out.println("search complete");
                runOnUiThread(new Runnable() {
                    public void run() {
                        // put into arraylist
                        sreddits.clear();
                        int i = 0;
                        while (i < srjson.length()) {
                            try {
                                sreddits.add(srjson.getJSONObject(i).getJSONObject("data"));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            i++;
                        }
                        listadapter.notifyDataSetChanged();
                        if (sreddits.size() == 0) {
                            // set no result text in no items view
                            emptyview.setText("No subreddits found");
                        }
                        sdialog.dismiss();
                    }
                });

            }
        };
        t.start();
    }

    private void returnResult(JSONObject subObj, boolean addAction){
        Intent intent = new Intent();
        intent.putExtra("subredditObj", subObj.toString());
        if (action!=null && action.equals(ACTION_ADD_MULTI_SUB)){
            intent.putExtra("multipath", getIntent().getStringExtra("multipath"));
            setResult(RESULT_ADD_TO_MULTI, intent);
        } else {
            if (addAction) {
                setResult(RESULT_ADD_SUBREDDIT, intent);
            } else {
                setResult(RESULT_SET_SUBREDDIT, intent);
            }
        }
        finish();
    }

    class SubredditsAdapter extends BaseAdapter {
        private LayoutInflater inflater;

        public SubredditsAdapter(Context context) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return sreddits.size();
        }

        @Override
        public Object getItem(int i) {
            return sreddits.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(final int position, View row, ViewGroup parent) {
            ViewHolder viewHolder = new ViewHolder();
            if (row == null || row.getTag() == null) {
                // inflate new view
                row = inflater.inflate(R.layout.subreddititem, parent, false);
                viewHolder.name = (TextView) row.findViewById(R.id.subreddit_name);
                viewHolder.description = (TextView) row.findViewById(R.id.subreddit_description);
                viewHolder.addIcon = (IconTextView) row.findViewById(R.id.subreddit_add_btn);
            } else {
                viewHolder = (ViewHolder) row.getTag();
            }
            // setup the row
            final String name;
            String description;
            try {
                name = sreddits.get(position).getString("display_name");
                description = sreddits.get(position).getString("public_description");
            } catch (JSONException e) {
                e.printStackTrace();
                return row;
            }
            viewHolder.name.setText(name);
            viewHolder.description.setText(description);
            viewHolder.addIcon.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    returnResult(sreddits.get(position), true);
                }
            });
            row.setTag(viewHolder);

            return row;
        }

        class ViewHolder {
            TextView name;
            TextView description;
            IconTextView addIcon;
        }
    }

    private DLTask dlpopulartask;

    private class DLTask extends AsyncTask<String, Integer, ArrayList<JSONObject>> {
        RedditData.RedditApiException exception;
        @Override
        protected ArrayList<JSONObject> doInBackground(String... string) {
            // load popular subreddits
            try {
                srjson = global.mRedditData.getSubreddits();
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                exception = e;
                return null;
            }
            if (srjson == null) {
                return new ArrayList<>();
            }
            // put into arraylist
            ArrayList<JSONObject> popreddits = new ArrayList<>();
            int i = 0;
            while (i < srjson.length()) {
                try {
                    popreddits.add(srjson.getJSONObject(i).getJSONObject("data"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                i++;
            }
            global.putSrList(popreddits);
            return popreddits;
        }

        protected void onPostExecute(ArrayList<JSONObject> resultlist) {
            if (resultlist==null){
                Toast.makeText(ViewAllSubredditsActivity.this, "Error loading subreddits: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            if (!this.isCancelled() || cancelrevert) {
                if (action==null || !action.equals(ACTION_ADD_MULTI_SUB)) {
                    sreddits.addAll(defaultsreddits);
                }
                sreddits.addAll(resultlist);
                listadapter.notifyDataSetChanged();
            }
        }
    }
}
