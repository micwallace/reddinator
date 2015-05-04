package au.com.wallaceit.reddinator;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.OpacityBar;
import com.larswerkman.holocolorpicker.SVBar;

import org.json.JSONException;

import java.util.UUID;


public class ThemeEditorActivity extends ListActivity {
    private GlobalObjects global;
    private String themeId = "";
    private ThemeManager.Theme theme;

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
            theme = global.mThemeManager.cloneTheme(getIntent().getStringExtra("templateId"));
            // set a unique id & default name for the theme
            themeId = "theme-"+ UUID.randomUUID();
            theme.setName("My Awesome Theme");
        }

        setContentView(R.layout.activity_theme_editor);

        setListAdapter(new ThemeSettingsAdapter());

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

    public void refreshList(){
        ((BaseAdapter) getListView().getAdapter()).notifyDataSetChanged();
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
            String key = null;
            try {
                key = (String) global.mThemeManager.getPreferenceOrder().get(position);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
            return theme.getValues().get(key);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            convertView = getLayoutInflater().inflate(R.layout.theme_editor_row, parent, false);
            // setup the row
            TextView settingName = (TextView) convertView.findViewById(R.id.theme_value_name);
            TextView settingValue = (TextView) convertView.findViewById(R.id.theme_value);

            if (position==0){
                settingName.setText("Name");
                settingValue.setText(theme.getName());

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(ThemeEditorActivity.this);
                        final EditText input = new EditText(ThemeEditorActivity.this);
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        input.setText(theme.getName());
                        input.selectAll();
                        builder.setTitle("Theme Name")
                        .setView(input)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                theme.setName(input.getText().toString());
                                refreshList();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
                    }
                });
            } else {
                String key = null;
                try {
                    key = (String) global.mThemeManager.getPreferenceOrder().get(position-1);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
                ImageView colorPreview = (ImageView) convertView.findViewById(R.id.color_preview);
                String value = theme.getValue(key);
                settingName.setText(global.mThemeManager.getThemePrefLabel(key));
                settingValue.setText(value);
                if (!value.equals("")) {
                    colorPreview.setBackgroundColor(Color.parseColor(value));
                    colorPreview.setVisibility(View.VISIBLE);
                }

                final String finalKey = key;
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final Dialog dialog = new Dialog(ThemeEditorActivity.this);
                        dialog.setTitle("Select Color");
                        dialog.setContentView(R.layout.color_picker_dialog);
                        // Initialise the color picker
                        final ColorPicker picker = (ColorPicker) dialog.findViewById(R.id.picker);
                        SVBar svBar = (SVBar) dialog.findViewById(R.id.svbar);
                        OpacityBar opacityBar = (OpacityBar) dialog.findViewById(R.id.opacitybar);
                        picker.addSVBar(svBar);
                        // is opacity needed?
                        final boolean useAlpha = theme.getValue(finalKey).length()>7;
                        if (useAlpha) {
                            picker.addOpacityBar(opacityBar);
                        } else {
                            opacityBar.setVisibility(View.GONE);
                        }
                        // set current color
                        int curColor = Color.parseColor(theme.getValue(finalKey));
                        picker.setColor(curColor);
                        picker.setOldCenterColor(curColor);

                        Button okButton = (Button) dialog.findViewById(R.id.button_ok);
                        okButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String hexColor = useAlpha?("#"+Integer.toHexString(picker.getColor())):String.format("#%06X", (0xFFFFFF & picker.getColor()));
                                System.out.println(hexColor);
                                theme.setValue(finalKey, hexColor.toUpperCase());
                                refreshList();
                                dialog.dismiss();
                            }
                        });

                        Button cancelButton = (Button) dialog.findViewById(R.id.button_cancel);
                        cancelButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dialog.dismiss();
                            }
                        });

                        dialog.show();
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
