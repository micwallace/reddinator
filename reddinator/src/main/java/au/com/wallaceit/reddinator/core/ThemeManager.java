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
 *
 * Created by michael on 2/05/15.
 */

package au.com.wallaceit.reddinator.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

public class ThemeManager {
    private Context context;
    private SharedPreferences prefs;
    private JSONArray valueOrder;
    private JSONObject themes;
    private JSONArray themeOrder;
    private JSONObject customThemes;
    public static final int LISTMODE_ALL= 0;
    public static final int LISTMODE_CUSTOM= 1;
    public static final int LISTMODE_DEFAULT= 2;
    private Theme defaultValues;

    public ThemeManager(Context context, SharedPreferences preferences){
        this.prefs = preferences;
        this.context = context;
        loadThemes();
    }

    public LinkedHashMap<String, String> getThemeList(int mode){
        LinkedHashMap<String, String> themeList = new LinkedHashMap<>();
        String key;
        if (mode==LISTMODE_ALL || mode==LISTMODE_DEFAULT) {
            for (int i=0; i<themeOrder.length(); i++) {
                try {
                    key = themeOrder.getString(i);
                    themeList.put(key, themes.getJSONObject(key).getString("name"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        if (mode==LISTMODE_ALL || mode==LISTMODE_CUSTOM) {
            Iterator iterator = customThemes.keys();
            while (iterator.hasNext()) {
                key = (String) iterator.next();
                try {
                    themeList.put(key, customThemes.getJSONObject(key).getString("name"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        return themeList;
    }

    public String getThemePrefLabel(String key) {
        int resId = context.getResources().getIdentifier("tl_"+key, "string", context.getPackageName());
        return context.getResources().getString(resId);
    }

    public JSONArray getPreferenceOrder(){
        return valueOrder;
    }

    private JSONObject getThemeJSON(String key){
        JSONObject theme = null;
        if (themes.has(key)){
            try {
                theme =  themes.getJSONObject(key);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (customThemes.has(key)){
            try {
                theme =  customThemes.getJSONObject(key);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            try {
                theme =  themes.getJSONObject("reddit_classic");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return theme;
    }

    public Theme cloneTheme(String key){
        JSONObject theme = getThemeJSON(key);
        try {
            theme = new JSONObject(theme.toString()); // so fucking ridiculous, only way to clone json
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new Theme(theme);
    }

    public Theme getTheme(String key){
        JSONObject theme = getThemeJSON(key);
        return new Theme(theme);
    }

    public Theme getActiveTheme(String themePrefKey){
        if (themePrefKey==null)
            return getTheme(prefs.getString("appthemepref", "reddit_classic"));

        String themeKey = prefs.getString(themePrefKey, "app_select");

        boolean appSelect = themeKey.equals("app_select");
        if (appSelect || (!themes.has(themeKey) && !customThemes.has(themeKey)))
            return getTheme(prefs.getString("appthemepref", "reddit_classic"));

        return getTheme(themeKey);
    }

    public boolean isThemeEditable(String key){
        return customThemes.has(key);
    }

    private void loadThemes(){
        String json;
        // load static themes
        try {
            InputStream is = context.getAssets().open("themes.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            //noinspection ResultOfMethodCallIgnored
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
            JSONObject themeData = new JSONObject(json);
            themes = themeData.getJSONObject("themes");
            themeOrder = themeData.getJSONArray("theme_order");
            valueOrder = themeData.getJSONArray("label_order");
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        // load custom themes
        try {
            customThemes = new JSONObject(prefs.getString("userThemes", "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // load failsafe values
        defaultValues = new Theme(getThemeJSON("reddit_classic"));
    }

    public void saveCustomTheme(String themeId, Theme theme){
        try {
            customThemes.put(themeId, theme.getTheme());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        prefs.edit().putString("userThemes", customThemes.toString()).apply();
    }

    public void deleteCustomTheme(String themeId){
        customThemes.remove(themeId);
        prefs.edit().putString("userThemes", customThemes.toString()).apply();
    }

    public class Theme {
        private JSONObject mTheme;
        private JSONObject jsonValues;
        private LinkedHashMap<String, String> values = null;

        public Theme(JSONObject theme) {
            mTheme = theme;
            try {
                jsonValues = theme.getJSONObject("values");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            //System.out.println(theme.toString());
        }

        public JSONObject getTheme(){
            return mTheme;
        }

        public JSONObject cloneJsonValues(){
            if (values==null)
                loadValues();
            try {
                return new JSONObject(jsonValues, values.keySet().toArray(new String[values.size()]));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return new JSONObject();
        }

        public String getValuesString(boolean includeLayoutOptions){
            if (includeLayoutOptions){
                JSONObject valuesObject = cloneJsonValues();
                try {
                    valuesObject.put("comments_layout", prefs.getString("commentslayoutpref", "1"));
                    valuesObject.put("comments_border_style", prefs.getString("commentsborderpref", "1"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return valuesObject.toString();
            } else {
                return jsonValues.toString();
            }
        }

        public String getName(){
            try {
                return mTheme.getString("name");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return "";
        }

        public void setName(String name){
            try {
                mTheme.put("name", name);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void loadValues(){
            values = new LinkedHashMap<>();
            Iterator iterator = jsonValues.keys();
            String key;
            while (iterator.hasNext()){
                key = (String) iterator.next();
                try {
                    values.put(key, jsonValues.getString(key));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        public HashMap<String, String> getValues(){
            if (values==null)
                loadValues();

            return values;
        }

        public String getValue(String key){
            if (values==null)
                loadValues();

            if (values.containsKey(key))
                return values.get(key);

            return defaultValues.getValue(key);
        }

        public void setValue(String key, String newValue){
            // update in index if loaded
            if (values!=null)
                values.put(key, newValue);
            // update in json source
            try {
                jsonValues.put(key, newValue);
                mTheme.put("values", jsonValues);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        public HashMap<String, Integer> getIntColors(){
            HashMap<String, String> srcColors = getValues();
            HashMap<String, Integer>  themeColors = new HashMap<>();
            Iterator iterator = srcColors.keySet().iterator();
            String key;
            int colorVal;
            Exception colorException;
            while (iterator.hasNext()){
                key = (String) iterator.next();
                try {
                    colorVal = Color.parseColor(srcColors.get(key));
                } catch (IllegalArgumentException e){
                    colorException = new Exception("Color value invalid: "+srcColors.get(key), e); // This will give us more info in g.play console.
                    colorException.printStackTrace();
                    colorVal = Color.GRAY;
                }
                themeColors.put(key, colorVal);
            }
            return themeColors;
        }
    }

}
