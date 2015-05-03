package au.com.wallaceit.reddinator;

import android.app.ActionBar;
import android.app.ListActivity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.UUID;


public class ThemeEditorActivity extends ListActivity {
    private GlobalObjects global;
    private String themeId = "";
    private ThemeManager.Theme theme;
    private ThemeSettingsAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        global = ((GlobalObjects) ThemeEditorActivity.this.getApplicationContext());
        if (getIntent().hasExtra("themeId")) {
            // edit existing theme
            themeId = getIntent().getStringExtra("themeId");
            theme = global.mThemeManager.getTheme(themeId);
        } else if (getIntent().hasExtra("templateId")){
            // creating new theme from template
            theme = global.mThemeManager.getTheme(getIntent().getStringExtra("templateId"));
            // set a unique id & default name for the theme
            themeId = "theme-"+ UUID.randomUUID();
            theme.setName("My Awesome Theme");
        }

        setContentView(R.layout.activity_theme_editor);

        listAdapter = new ThemeSettingsAdapter();
        setListAdapter(listAdapter);

        // get actionbar and set home button, pad the icon
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ImageView view = (ImageView) findViewById(android.R.id.home);
        if (view != null) {
            view.setPadding(5, 0, 5, 0);
        }
    }

    @Override
    public void onBackPressed(){
        super.onBackPressed();

        global.mThemeManager.saveCustomTheme(themeId, theme);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ThemeSettingsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return theme.getValues().size()+1; // +1 for theme name
        }

        @Override
        public Object getItem(int position) {
            String key = (String) theme.getValues().keySet().toArray()[position];
            return theme.getValues().get(key);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            convertView = getLayoutInflater().inflate(R.layout.myredditlistitem, parent, false);
            // setup the row
            TextView settingName = (TextView) convertView.findViewById(R.id.subreddit_name);

            if (position==0){
                settingName.setText("Name");

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                    /*String sreddit = ((TextView) ((View) v.getParent()).findViewById(R.id.subreddit_name)).getText().toString();
                    personalList.remove(sreddit);
                    global.removeFromPersonalList(sreddit);
                    mListAdapter.notifyDataSetChanged();*/
                        System.out.println("Set name...");
                    }
                });
            } else {
                final String key = (String) theme.getValues().keySet().toArray()[position-1];
                settingName.setText(global.mThemeManager.getThemePrefLabel(key));

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                    /*String sreddit = ((TextView) ((View) v.getParent()).findViewById(R.id.subreddit_name)).getText().toString();
                    personalList.remove(sreddit);
                    global.removeFromPersonalList(sreddit);
                    mListAdapter.notifyDataSetChanged();*/
                        System.out.println(key);
                    }
                });
            }
            return convertView;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return theme.getValues().size()==0;
        }
    }
}
