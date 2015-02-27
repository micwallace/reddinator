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

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class ViewAllSubredditsActivity extends ListActivity {
    private GlobalObjects global;
    private ArrayList<String> sreddits;
    private JSONArray srjson;
    private ArrayAdapter<String> listadapter;
    private EditText searchbox;
    private ListView listview;
    private TextView emptyview;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        global = ((GlobalObjects) getApplicationContext());
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        setContentView(R.layout.viewallsubreddit);
        // setup list view
        listview = getListView();
        listview.setTextFilterEnabled(true);
        listview.setEmptyView(findViewById(R.id.subredditload));
        listview.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                String sreddit = ((TextView) view).getText().toString();
                Intent intent = new Intent();
                intent.putExtra("subreddit", sreddit);
                setResult(1, intent);
                finish();
                //System.out.println(sreddit+" selected");
            }
        });
        // get empty view text for easy access later
        emptyview = (TextView) findViewById(R.id.poploadtxt);
        // setup search buttons
        searchbox = (EditText) this.findViewById(R.id.searchbox);
        searchbox.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId,
                                          KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                    search(v.getText().toString());
                }
                return true;
            }

        });
        ImageView searchbtn = (ImageView) this.findViewById(R.id.searchbutton);
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
    }

    private boolean cancelrevert = false;

    public void onBackPressed() {
        // System.out.println("onBackPressed()");
        if (searchbox.getText().toString().equals("")) {
            this.finish();
        } else {
            if (global.isSrlistCached()) {
                sreddits.clear();
                sreddits.addAll(global.getSrList());
                updateAdapter();
            } else {
                emptyview.setText("Loading popular...");
                sreddits.clear();
                updateAdapter();
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
        // get list data
        if (global.isSrlistCached()) {
            sreddits = global.getSrList();
            setListAdaptor();
        } else {
            sreddits = new ArrayList<String>();
            setListAdaptor();
            loadPopularSubreddits();
        }
        super.onResume();
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
                srjson = global.mRedditData.getSubredditSearch(query);
                // put into arraylist
                sreddits = new ArrayList<String>();
                int i = 0;
                while (i < srjson.length()) {
                    try {
                        sreddits.add(srjson.getJSONObject(i).getJSONObject("data").getString("display_name"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    i++;
                }
                //System.out.println("search complete");
                runOnUiThread(new Runnable() {
                    public void run() {
                        setListAdaptor();
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

    private void setListAdaptor() {
        listadapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sreddits);
        listview.setAdapter(listadapter);
    }

    private void updateAdapter() {
        listadapter.notifyDataSetChanged();
    }

    private DLTask dlpopulartask;

    private class DLTask extends AsyncTask<String, Integer, ArrayList<String>> {
        @Override
        protected ArrayList<String> doInBackground(String... string) {
            // load popular subreddits
            srjson = global.mRedditData.getSubreddits();
            // put into arraylist
            ArrayList<String> popreddits = new ArrayList<String>();
            int i = 0;
            popreddits.add("Front Page"); // slap the front page on there
            popreddits.add("all"); // and an all
            while (i < srjson.length()) {
                try {
                    popreddits.add(srjson.getJSONObject(i).getJSONObject("data").getString("display_name"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                i++;
            }
            global.putSrList(popreddits);
            return popreddits;
        }

        protected void onPostExecute(ArrayList<String> resultlist) {
            if (!this.isCancelled() || cancelrevert) {
                sreddits.addAll(resultlist);
                updateAdapter();
            }
        }
    }
}
