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

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by michael on 10/05/15.
 *
 * Subreddit Manager Provides a global interface for modifying the user subreddit & multi lists.
 */
public class SubredditManager {
    private SharedPreferences prefs;
    private ArrayList<String> subreddits;
    private JSONObject multis;

    public SubredditManager(SharedPreferences prefs){
        // load subreddits & multis
        this.prefs = prefs;
        Set<String> feeds = prefs.getStringSet("personalsr", new HashSet<String>());
        if (feeds==null || feeds.isEmpty()) {
            // first time setup
            subreddits = new ArrayList<>(Arrays.asList("Front Page", "all", "arduino", "AskReddit", "pics", "technology", "science", "videos", "worldnews"));
            saveSubs();
        } else {
            subreddits = new ArrayList<>(feeds);
        }
        try {
            multis = new JSONObject(prefs.getString("userMultis", "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveSubs(){
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> set = new HashSet<>();
        set.addAll(subreddits);
        editor.putStringSet("personalsr", set);
        editor.apply();
    }

    public ArrayList<String> getSubreddits() {
        return subreddits;
    }

    public void addSubreddit(String subredditName){
        subreddits.add(subredditName);
    }

    public void addSubreddits(ArrayList<String> subNames, boolean clearCurrent){
        if (clearCurrent)
            subreddits.clear();

        subreddits.addAll(subNames);
    }

    public void removeSubreddit(String subredditName){
        subreddits.remove(subredditName);
    }

    public void addMulti(JSONObject multiObj){

    }

    public void addMultis(JSONArray subsArray, boolean clearCurrent){

    }

    public void removeMulti(String key){

    }

    private void saveMultis(){
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("userMultis", multis.toString());
        editor.apply();
    }
}
