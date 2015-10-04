package au.com.wallaceit.reddinator.activity;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.IconTextView;
import android.widget.ImageView;
import android.widget.TextView;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import java.util.HashMap;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.ThemeManager;


public class ThemesActivity extends ListActivity {
    Reddinator global;
    HashMap<String, String> themesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        global = ((Reddinator) ThemesActivity.this.getApplicationContext());
        themesList = global.mThemeManager.getThemeList(ThemeManager.LISTMODE_CUSTOM);

        setContentView(R.layout.activity_themes);

        setListAdapter(new ThemesListAdapter());

        // get actionbar and set home button, pad the icon
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ImageView view = (ImageView) findViewById(android.R.id.home);
        if (view != null) {
            view.setPadding(5, 0, 5, 0);
        }
        setResult(0);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if (resultCode==3) {
            refreshList();
            setResult(3); // indicate theme edit
        }
    }

    public void refreshList(){
        themesList = global.mThemeManager.getThemeList(ThemeManager.LISTMODE_CUSTOM);
        ((BaseAdapter) getListView().getAdapter()).notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_themes, menu);
        int iconColor = Color.parseColor("#25C48F");
        (menu.findItem(R.id.action_add)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_plus).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_about)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_info_circle).color(iconColor).actionBarSize());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        switch (id){
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_add:
                // open template picker dialog
                final HashMap<String,String> themesList = global.mThemeManager.getThemeList(ThemeManager.LISTMODE_ALL);
                AlertDialog.Builder builder = new AlertDialog.Builder(ThemesActivity.this);
                builder.setTitle("Choose A Template to Start")
                        .setItems(themesList.values().toArray(new CharSequence[themesList.values().size()]), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                String themeId = (String) themesList.keySet().toArray()[i];
                                Intent intent = new Intent(ThemesActivity.this, ThemeEditorActivity.class);
                                intent.putExtra("templateId", themeId);
                                startActivityForResult(intent, 1);
                            }
                        });
                builder.show();
                break;
            case R.id.menu_about:
                Reddinator.showInfoDialog(this, true);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private class ThemesListAdapter extends BaseAdapter {

        public ThemesListAdapter(){

        }

        @Override
        public int getCount() {
            return themesList.size();
        }

        @Override
        public Object getItem(int position) {
            String key = (String) themesList.keySet().toArray()[position];
            return themesList.get(key);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView==null || convertView.getTag()==null) {
                convertView = getLayoutInflater().inflate(R.layout.myredditlistitem, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(R.id.subreddit_name);
                viewHolder.delete = (IconTextView) convertView.findViewById(R.id.subreddit_delete_btn);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            // setup the row
            final String themeId = (String) themesList.keySet().toArray()[position];
            viewHolder.name.setText(themesList.get(themeId));
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                Intent intent = new Intent(ThemesActivity.this, ThemeEditorActivity.class);
                intent.putExtra("themeId", themeId);
                startActivityForResult(intent, 0);
                }
            });
            viewHolder.delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(ThemesActivity.this);
                    builder.setTitle("Delete Theme").setMessage("Are you sure you want to delete this theme?")
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    global.mThemeManager.deleteCustomTheme(themeId);
                                    refreshList();
                                }
                            }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).show();
                }
            });
            return convertView;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return themesList.size()==0;
        }
    }

    class ViewHolder {
        TextView name;
        IconTextView delete;
    }
}
