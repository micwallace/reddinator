package au.com.wallaceit.reddinator.activity;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.IconTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.OpacityBar;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

import org.json.JSONException;

import java.util.UUID;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.core.ThemeManager;


public class ThemeEditorActivity extends ListActivity {
    private Reddinator global;
    private String themeId = "";
    private ThemeManager.Theme theme;
    private boolean themeChanged = false;
    private Resources resources;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        global = ((Reddinator) ThemeEditorActivity.this.getApplicationContext());
        resources = getResources();

        if (getIntent().hasExtra("themeId")) {
            // edit existing theme
            themeId = getIntent().getStringExtra("themeId");
            theme = global.mThemeManager.getTheme(themeId);
        } else if (getIntent().hasExtra("templateId")){
            // creating new theme from template
            theme = global.mThemeManager.cloneTheme(getIntent().getStringExtra("templateId"));
            // set a unique id & default name for the theme
            themeId = "theme-"+ UUID.randomUUID();
            theme.setName(resources.getString(R.string.my_awesome_theme));
            themeChanged = true;
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
        if (themeChanged)
            global.mThemeManager.saveCustomTheme(themeId, theme);

        setResult((themeChanged?6:0));

        super.onBackPressed();
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
            return global.mThemeManager.getPreferenceOrder().length()+1; // +1 for theme name
        }

        @Override
        public Object getItem(int position) {
            String key;
            if (position==0){
                return theme.getName();
            }
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
            ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                convertView = getLayoutInflater().inflate(R.layout.theme_editor_row, parent, false);
                // setup the row
                viewHolder = new ViewHolder();
                viewHolder.settingName = (TextView) convertView.findViewById(R.id.theme_value_name);
                viewHolder.settingValue = (TextView) convertView.findViewById(R.id.theme_value);
                viewHolder.colorPreview = (ImageView) convertView.findViewById(R.id.color_preview);
                viewHolder.colorOptions = (LinearLayout) convertView.findViewById(R.id.color_options);
                viewHolder.simplePickBtn = (IconTextView) convertView.findViewById(R.id.simple_color_btn);
                viewHolder.customPickBtn = (IconTextView) convertView.findViewById(R.id.custom_color_btn);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            if (position==0){
                viewHolder.settingName.setText(resources.getString(R.string.name));
                viewHolder.settingValue.setText(theme.getName());
                viewHolder.colorPreview.setVisibility(View.GONE);
                viewHolder.colorOptions.setVisibility(View.GONE);
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(ThemeEditorActivity.this);
                        final EditText input = new EditText(ThemeEditorActivity.this);
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        input.setText(theme.getName());
                        input.selectAll();
                        builder.setTitle(resources.getString(R.string.theme_name))
                        .setView(input)
                        .setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                theme.setName(input.getText().toString());
                                themeChanged = true;
                                refreshList();
                            }
                        })
                        .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
                    }
                });
            } else {
                String key;
                try {
                    key = (String) global.mThemeManager.getPreferenceOrder().get(position-1);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return convertView;
                }
                final String value = theme.getValue(key);
                viewHolder.settingName.setText(global.mThemeManager.getThemePrefLabel(key));
                viewHolder.settingValue.setText(value);
                int color;
                try {
                    color = Color.parseColor(value);
                } catch (IllegalArgumentException e){
                    e.printStackTrace();
                    color = Color.WHITE;
                }
                viewHolder.colorPreview.setBackgroundColor(color);
                viewHolder.colorPreview.setVisibility(View.VISIBLE);
                viewHolder.colorOptions.setVisibility(View.VISIBLE);

                final String finalKey = key;
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final Dialog dialog = new Dialog(ThemeEditorActivity.this);
                        dialog.setTitle(resources.getString(R.string.select_color));
                        dialog.setContentView(R.layout.color_picker_dialog);
                        // Initialise the color picker
                        final ColorPicker picker = (ColorPicker) dialog.findViewById(R.id.picker);
                        SaturationBar sBar = (SaturationBar) dialog.findViewById(R.id.saturationbar);
                        ValueBar vBar = (ValueBar) dialog.findViewById(R.id.valuebar);
                        OpacityBar opacityBar = (OpacityBar) dialog.findViewById(R.id.opacitybar);
                        picker.addSaturationBar(sBar);
                        picker.addValueBar(vBar);
                        // is opacity needed?
                        final boolean useAlpha = theme.getValue(finalKey).length()>7;
                        if (useAlpha) {
                            picker.addOpacityBar(opacityBar);
                        } else {
                            opacityBar.setVisibility(View.GONE);
                        }
                        // set current color
                        int curColor;
                        try {
                            curColor = Color.parseColor(theme.getValue(finalKey));
                        } catch (IllegalArgumentException iae) {
                            curColor = Color.WHITE;
                        }
                        picker.setColor(curColor);
                        picker.setOldCenterColor(curColor);

                        Button okButton = (Button) dialog.findViewById(R.id.button_ok);
                        okButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String hexColor = useAlpha?("#"+Integer.toHexString(picker.getColor())):String.format("#%06X", (0xFFFFFF & picker.getColor()));
                                theme.setValue(finalKey, hexColor.toUpperCase());
                                themeChanged = true;
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

                viewHolder.simplePickBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(ThemeEditorActivity.this);
                        builder.setTitle(resources.getString(R.string.pick_basic_color))
                        .setItems(R.array.fontcolor_names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                String hexColor = getResources().getStringArray(R.array.fontcolor_values)[i];
                                if (theme.getValue(finalKey).length()>7){
                                    // Add alpha values if needed
                                    hexColor = "#FF"+hexColor.substring(1);
                                }
                                theme.setValue(finalKey, hexColor);
                                themeChanged = true;
                                refreshList();
                                dialogInterface.dismiss();
                            }
                        })
                        .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        }).show();
                    }
                });
                viewHolder.customPickBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(ThemeEditorActivity.this);
                        final EditText input = new EditText(ThemeEditorActivity.this);
                        input.setText(value);
                        builder.setView(input);
                        builder.setTitle(resources.getString(R.string.custom_hex_color))
                        .setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // validate input
                                String hexCode = input.getText().toString();
                                dialogInterface.dismiss();
                                try {
                                    Color.parseColor(hexCode);
                                } catch (IllegalArgumentException iae) {
                                    // This color string is not valid
                                    Toast.makeText(ThemeEditorActivity.this, "Please enter a valid hex color code", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                theme.setValue(finalKey, hexCode);
                                themeChanged = true;
                                refreshList();
                            }
                        })
                        .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        }).show();
                    }
                });
            }

            convertView.setTag(viewHolder);

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

        class ViewHolder {
            TextView settingName;
            TextView settingValue;
            ImageView colorPreview;
            LinearLayout colorOptions;
            IconTextView simplePickBtn;
            IconTextView customPickBtn;
        }
    }
}
