package au.com.wallaceit.reddinator;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;


public class SubmitActivity extends Activity implements ActionBar.TabListener {

    private GlobalObjects global;
    private AutoCompleteTextView subreddit;
    private TextView submitText;
    private EditText link;
    private EditText text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submit);
        global = (GlobalObjects) getApplicationContext();
        // get actionbar and set home button, pad the icon
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ImageView view = (ImageView) findViewById(android.R.id.home);
        if (view != null) {
            view.setPadding(5, 0, 5, 0);
        }
        subreddit = (AutoCompleteTextView) findViewById(R.id.subreddit);
        subreddit.setAdapter(new SubredditSelectAdaptor(this, R.layout.autocomplete_list_item));
        subreddit.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                new SubmitTextTask().execute(subreddit.getText().toString());
            }
        });
        subreddit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) new SubmitTextTask().execute(subreddit.getText().toString());
            }
        });
        submitText = (TextView) findViewById(R.id.submission_text);
        link = (EditText) findViewById(R.id.link);
        text = (EditText) findViewById(R.id.text);
        // add tab buttons
        actionBar.addTab(actionBar.newTab().setText("Link").setTabListener(SubmitActivity.this));
        actionBar.addTab(actionBar.newTab().setText("Text").setTabListener(SubmitActivity.this));

        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.selectTab(actionBar.getTabAt(0));
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        if (tab.getText().equals("Link")){
            text.setVisibility(View.GONE);
            link.setVisibility(View.VISIBLE);
        } else {
            link.setVisibility(View.GONE);
            text.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }
    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    class SubredditSelectAdaptor extends ArrayAdapter<String> implements Filterable {

        JSONArray suggestions;

        public SubredditSelectAdaptor(Context context, int resource) {
            super(context, resource);
        }

        @Override
        public int getCount() {
            return suggestions.length();
        }

        @Override
        public String getItem(int index) {
            try {
                return suggestions.getString(index);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public Filter getFilter() {
            Filter filter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();
                    if (constraint != null) {
                        // Retrieve the autocomplete results.
                        try {
                            suggestions = global.mRedditData.searchRedditNames(constraint.toString());
                            // Assign the data to the FilterResults
                            filterResults.values = suggestions;
                            filterResults.count = suggestions.length();
                        } catch (RedditData.RedditApiException e) {
                            e.printStackTrace();
                        }
                    }
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    if (results != null && results.count > 0) {
                        notifyDataSetChanged();
                    }
                    else {
                        notifyDataSetInvalidated();
                    }
                }};
            return filter;
        }
    }

    class SubmitTextTask extends AsyncTask<String, Long, Boolean> {
        String submitHtml = "";

        @Override
        protected Boolean doInBackground(String... strings) {
            try {
                submitHtml = global.mRedditData.getSubmitText(strings[0]).getString("submit_text_html");

                return true;
            } catch (RedditData.RedditApiException | JSONException e) {
                e.printStackTrace();
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            submitText.setText(Html.fromHtml(result?Html.fromHtml(submitHtml).toString():"<strong style=\"color:red;\">That subreddit does not look valid</strong>"));
        }
    }
}
