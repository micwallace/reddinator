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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.JsonReader;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by michael on 2/05/15.
 */
public class ThemeManager {
    Context context;
    SharedPreferences prefs;
    JSONObject valueLabels;
    JSONObject themes;
    JSONObject customThemes;

    public ThemeManager(Context context, SharedPreferences preferences){
        this.prefs = preferences;
        this.context = context;
        loadThemes();
    }

    public HashMap<String, String> getThemeList(){
        HashMap<String, String> themeList = new HashMap<>();
        String key;
        Iterator iterator = themes.keys();
        while (iterator.hasNext()){
            key = (String) iterator.next();
            try {
                themeList.put(key, themes.getJSONObject(key).getString("name"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        iterator = customThemes.keys();
        while (iterator.hasNext()){
            key = (String) iterator.next();
            try {
                themeList.put(key, customThemes.getJSONObject(key).getString("name"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return themeList;
    }

    public String getThemePrefLabel(String key){
        try {
            return valueLabels.getString(key);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    public JSONObject getThemes(){
        return new JSONObject();
    }

    public JSONObject getCustomThemes(){
        return new JSONObject();
    }

    public Theme getTheme(String key){
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

        return new Theme(theme);
    }

    public Theme getActiveTheme(String themePrefKey){
        if (themePrefKey==null)
            return getTheme(prefs.getString("appthemepref", "reddit_classic"));

        return getTheme(prefs.getString(themePrefKey, "reddit_classic"));
    }

    private void loadThemes(){
        String json = "{}";
        // load static themes
        try {
            InputStream is = context.getAssets().open("themes.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
            JSONObject themeData = new JSONObject(json);
            themes = themeData.getJSONObject("themes");
            valueLabels = themeData.getJSONObject("labels");
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        // load custom themes
        try {
            customThemes = new JSONObject(prefs.getString("userThemes", "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void saveCustomTheme(String name, Theme theme){
        try {
            customThemes.put(name, theme.getTheme());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        prefs.edit().putString("userThemes", customThemes.toString()).apply();
    }

    public class Theme {
        JSONObject theme;
        JSONObject jsonValues;
        HashMap<String, String> values = null;

        public Theme(JSONObject theme){
            this.theme = theme;
            try {
                jsonValues = theme.getJSONObject("values");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        public JSONObject getTheme(){
            return theme;
        }

        public String getValuesString(){
            return jsonValues.toString();
        }

        public String getName(){
            try {
                return theme.getString("name");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return "";
        }

        private void loadValues(){
            values = new HashMap<>();
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

            return null; // TODO: return default color
        }

        public void setValue(String key, String newValue){
            // update in index if loaded
            if (values!=null)
                values.put(key, newValue);
            // update in json source
            try {
                jsonValues.put(key, newValue);
                theme.put("values", jsonValues);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        public HashMap<String, Integer> getIntColors(){
            HashMap<String, String> srcColors = getValues();
            HashMap<String, Integer>  themeColors = new HashMap<>();
            Iterator iterator = srcColors.keySet().iterator();
            String key;
            while (iterator.hasNext()){
                key = (String) iterator.next();
                themeColors.put(key, Color.parseColor(srcColors.get(key)));
            }
            return themeColors;
        }
    }
}
