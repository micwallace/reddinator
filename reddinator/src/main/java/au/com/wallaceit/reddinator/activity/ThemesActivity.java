package au.com.wallaceit.reddinator.activity;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.IconTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.service.WidgetProvider;
import au.com.wallaceit.reddinator.tasks.SubmitTask;


public class ThemesActivity extends ListActivity implements SubmitTask.Callback {
    private Reddinator global;
    private Resources resources;
    private HashMap<String, String> themesList;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        global = ((Reddinator) ThemesActivity.this.getApplicationContext());
        resources = getResources();
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

        // check if preview theme is applied
        String previewName = global.mThemeManager.getPreviewName();
        if (previewName!=null){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.theme_preview)
                .setMessage(getString(R.string.theme_preview_clear_message, previewName))
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        global.mThemeManager.clearPreviewTheme();
                        setResult(6); // indicate theme edit
                    }
                })
                .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        global.mThemeManager.savePreviewTheme();
                        setResult(6); // indicate theme edit
                        refreshList();
                    }
                });
            builder.show().setCanceledOnTouchOutside(true);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if (resultCode==3) {
            refreshList();
            setResult(6); // indicate theme edit
        }
    }

    public void refreshList(){
        themesList = global.mThemeManager.getThemeList(ThemeManager.LISTMODE_CUSTOM);
        ((BaseAdapter) getListView().getAdapter()).notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.themes_menu, menu);
        int iconColor = Color.parseColor("#25C48F");
        (menu.findItem(R.id.action_add)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_plus).color(iconColor).actionBarSize());
        (menu.findItem(R.id.action_sharedthemes)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_globe).color(iconColor).actionBarSize());
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
                builder.setTitle(resources.getString(R.string.choose_template))
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
            case R.id.action_sharedthemes:
                Intent intent = new Intent(ThemesActivity.this, MainActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://reddit.com/r/reddinator"));
                intent.putExtra("view_themes", true);
                startActivity(intent);
                break;
            case R.id.menu_about:
                Reddinator.showInfoDialog(this, true);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onSubmitted(JSONObject result, RedditData.RedditApiException exception, boolean isLink) {
        progressDialog.cancel();
        if (result!=null){
            try {
                if (result.has("errors")) {
                    JSONArray errors = result.getJSONArray("errors");
                    if (errors.length()>0) {
                        Toast.makeText(ThemesActivity.this, errors.getJSONArray(0).getString(1), Toast.LENGTH_LONG).show();
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
                String url = permalink+".compact";

                if (permalink != null)
                    permalink = permalink.substring(permalink.indexOf("/r/")); // trim domain to get real permalink

                Intent intent = new Intent(ThemesActivity.this, ViewRedditActivity.class);
                intent.putExtra(WidgetProvider.ITEM_ID, id);
                intent.putExtra(WidgetProvider.ITEM_PERMALINK, permalink);
                intent.putExtra(WidgetProvider.ITEM_URL, url);
                intent.putExtra("submitted", true); // tells the view reddit activity that this is liked & that no stored feed update is needed.
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } catch (JSONException e) {
                e.printStackTrace();
                // show api error
                Toast.makeText(ThemesActivity.this, resources.getString(R.string.cannot_open_post_error)+" "+e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            // show api error
            Toast.makeText(ThemesActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private class ThemesListAdapter extends BaseAdapter {

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
                convertView = getLayoutInflater().inflate(R.layout.themes_list_item, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(R.id.theme_name);
                viewHolder.delete = (IconTextView) convertView.findViewById(R.id.theme_delete_btn);
                viewHolder.share = (IconTextView) convertView.findViewById(R.id.theme_share_btn);
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
                    builder.setTitle(resources.getString(R.string.delete_theme)).setMessage(resources.getString(R.string.delete_theme_message))
                            .setPositiveButton(resources.getString(R.string.yes), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    global.mThemeManager.deleteCustomTheme(themeId);
                                    refreshList();
                                }
                            }).setNegativeButton(resources.getString(R.string.no), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).show();
                }
            });
            viewHolder.share.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!global.mRedditData.isLoggedIn()){
                        Toast.makeText(ThemesActivity.this, "Reddit Login Required", Toast.LENGTH_SHORT).show();
                    }
                    final EditText input = new EditText(ThemesActivity.this);
                    input.setHint(R.string.title);
                    AlertDialog.Builder builder = new AlertDialog.Builder(ThemesActivity.this);
                    builder.setTitle(resources.getString(R.string.share_theme)).setMessage(resources.getString(R.string.share_theme_message))
                        .setView(input)
                        .setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String title = input.getText().toString();
                                if (title.length()>292){
                                    Toast.makeText(ThemesActivity.this, getText(R.string.title_too_long_error), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (title.length()==0){
                                    Toast.makeText(ThemesActivity.this, getText(R.string.no_title_error), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                String theme = global.mThemeManager.getThemeJSON(themeId).toString();
                                progressDialog = ProgressDialog.show(ThemesActivity.this, "", resources.getString(R.string.submitting), true);
                                new SubmitTask(global, "reddinator", "[Theme] "+title, "This theme was shared through Reddinator\n\r    reddinator_theme="+theme, false, ThemesActivity.this).execute();
                            }
                        }).setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
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
        IconTextView share;
        IconTextView delete;
    }
}
