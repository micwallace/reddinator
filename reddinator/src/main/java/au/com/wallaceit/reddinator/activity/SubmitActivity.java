package au.com.wallaceit.reddinator.activity;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.tasks.SubmitTask;
import au.com.wallaceit.reddinator.ui.SimpleTabsAdapter;
import au.com.wallaceit.reddinator.ui.SimpleTabsWidget;
import au.com.wallaceit.reddinator.ui.SubAutoCompleteAdapter;
import au.com.wallaceit.reddinator.core.ThemeManager;


public class SubmitActivity extends Activity implements SubmitTask.Callback {

    private Reddinator global;
    private AutoCompleteTextView subreddit;
    private TextView charsLeft;
    private TextView submitText;
    private EditText title;
    private EditText link;
    private EditText text;
    private ViewPager pager;
    private Resources resources;
    private ProgressDialog progressDialog;

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submit);
        global = (Reddinator) getApplicationContext();
        resources = getResources();

        subreddit = (AutoCompleteTextView) findViewById(R.id.subreddit);
        subreddit.setAdapter(new SubAutoCompleteAdapter(this, R.layout.autocomplete_list_item));
        subreddit.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (!subreddit.getText().toString().equals(""))
                    new SubmitTextTask().execute(subreddit.getText().toString());
            }
        });
        subreddit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b && !subreddit.getText().toString().equals(""))
                    new SubmitTextTask().execute(subreddit.getText().toString());
            }
        });
        submitText = (TextView) findViewById(R.id.submission_text);
        submitText.setMovementMethod(new SafeLinkMethod());
        charsLeft= (TextView) findViewById(R.id.title_chars_left);
        title = (EditText) findViewById(R.id.title);
        link = (EditText) findViewById(R.id.link);
        text = (EditText) findViewById(R.id.text);

        title.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                charsLeft.setText(resources.getString(R.string.characters_left, (300 - title.getText().toString().length())));
            }
        });

        String action = getIntent().getAction();
        if (action!=null && (action.equals(Intent.ACTION_SEND) && getIntent().getType().equals("text/plain"))) {
            link.setText(getIntent().getStringExtra(Intent.EXTRA_TEXT));
        }

        // get actionbar and set home button, pad the icon
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ImageView view = (ImageView) findViewById(android.R.id.home);
        if (view != null) {
            view.setPadding(5, 0, 5, 0);
        }

        pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(new SimpleTabsAdapter(new String[]{resources.getString(R.string.link), resources.getString(R.string.text)}, new int[]{R.id.link, R.id.text}, SubmitActivity.this, null));
        LinearLayout tabsLayout = (LinearLayout) findViewById(R.id.tab_widget);
        SimpleTabsWidget tabs = new SimpleTabsWidget(SubmitActivity.this, tabsLayout);
        tabs.setViewPager(pager);

        ThemeManager.Theme theme = global.mThemeManager.getActiveTheme("appthemepref");
        int headerColor = Color.parseColor(theme.getValue("header_color"));
        int headerText = Color.parseColor(theme.getValue("header_text"));
        tabs.setBackgroundColor(headerColor);
        tabs.setInidicatorColor(Color.parseColor(theme.getValue("tab_indicator")));
        tabs.setTextColor(headerText);

        Button submitButton = (Button) findViewById(R.id.submit_button);
        submitButton.getBackground().setColorFilter(headerColor, PorterDuff.Mode.MULTIPLY);
        submitButton.setTextColor(headerText);
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(SubmitActivity.this, false);
                } else {
                    if (validateInput()) {
                        boolean isLink = pager.getCurrentItem()==0;
                        String data = isLink ? link.getText().toString() : text.getText().toString();
                        progressDialog = ProgressDialog.show(SubmitActivity.this, "", resources.getString(R.string.submitting), true);
                        new SubmitTask(global, subreddit.getText().toString(), title.getText().toString(), data, isLink, SubmitActivity.this).execute();
                    }
                }
            }
        });
    }

    @Override
    public void onSubmitted(JSONObject result, RedditData.RedditApiException exception, boolean isLink) {
        progressDialog.cancel();
        if (result!=null){
            try {
                if (result.has("errors")) {
                    JSONArray errors = result.getJSONArray("errors");
                    if (errors.length()>0) {
                        submitText.setText(Utilities.fromHtml("<strong><font color=\"red\">" + errors.getJSONArray(0).getString(1) + "</font></strong>"));
                        return;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            String id;
            String permalink;
            try {
                JSONObject data = result.getJSONObject("data");
                id = data.getString("name");
                permalink = StringEscapeUtils.unescapeJava(data.getString("url").replace(".json", ""));
                String url = isLink?link.getText().toString():permalink+".compact";

                if (permalink != null)
                    permalink = permalink.substring(permalink.indexOf("/r/")); // trim domain to get real permalink

                Intent intent = new Intent(SubmitActivity.this, ViewRedditActivity.class);
                intent.putExtra(Reddinator.ITEM_ID, id);
                intent.putExtra(Reddinator.ITEM_PERMALINK, permalink);
                intent.putExtra(Reddinator.ITEM_URL, url);
                intent.putExtra("submitted", true); // tells the view reddit activity that this is liked & that no stored feed update is needed.
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } catch (JSONException e) {
                e.printStackTrace();
                // show api error
                Toast.makeText(SubmitActivity.this, resources.getString(R.string.cannot_open_post_error)+" "+e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            // show api error
            Toast.makeText(SubmitActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private class SafeLinkMethod extends LinkMovementMethod {

        @Override
        public boolean onTouchEvent( @NonNull TextView widget, @NonNull Spannable buffer, @NonNull MotionEvent event ) {
            try {
                return super.onTouchEvent( widget, buffer, event ) ;
            } catch( Exception ex ) {
                Toast.makeText( SubmitActivity.this, resources.getString(R.string.load_link_error), Toast.LENGTH_LONG ).show();
                return true;
            }
        }

    }

    private boolean validateInput(){
        String subText = title.getText().toString();
        if (subText.equals("")){
            global.showAlertDialog(SubmitActivity.this, resources.getString(R.string.whoa), resources.getString(R.string.no_subreddit_error));
            return false;
        }
        String titleText = title.getText().toString();
        if (titleText.equals("")){
            global.showAlertDialog(SubmitActivity.this, resources.getString(R.string.whoa), resources.getString(R.string.no_title_error));
            return false;
        } else if (titleText.length()>300){
            global.showAlertDialog(SubmitActivity.this, resources.getString(R.string.whoa), resources.getString(R.string.title_too_long_error));
            return false;
        }
        String content;
        if (pager.getCurrentItem()==0){
            content = link.getText().toString();
        } else {
            content = text.getText().toString();
        }
        if (content.equals("")){
            global.showAlertDialog(SubmitActivity.this, resources.getString(R.string.whoa), resources.getString(R.string.no_content_error));
            return false;
        }

        return true;
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

    class SubmitTextTask extends AsyncTask<String, Long, Boolean> {
        String submitHtml;

        @Override
        protected Boolean doInBackground(String... strings) {
            try {
                submitHtml = global.mRedditData.getSubmitText(strings[0]).getString("submit_text_html");
                if (submitHtml.equals("null"))
                    submitHtml = "";
                return true;
            } catch (RedditData.RedditApiException | JSONException e) {
                e.printStackTrace();
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            submitText.setText(Utilities.fromHtml(result?Utilities.fromHtml(submitHtml).toString():"<strong><font color=\"red\">"+resources.getString(R.string.sub_doesnt_look_valid)+"</font></strong>"));
        }
    }

}
