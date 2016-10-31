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
 * Created by michael on 10/05/15.
 *
 * Subreddit Manager Provides a global interface for modifying the user subreddit & multi lists.
 */
package au.com.wallaceit.reddinator.core;

import android.content.SharedPreferences;
import android.os.AsyncTask;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class SubredditManager {
    private SharedPreferences prefs;
    private RedditData redditData;
    private JSONObject subreddits;
    private JSONObject multis;
    private JSONObject postFilters;
    public final static String defaultSubreddits = "{\"Front Page\":{\"display_name\"=\"Front Page\", \"public_description\"=\"Your reddit front page\",\"url\"=\"\"}, \"all\":{\"display_name\"=\"all\", \"public_description\"=\"The best of reddit\",\"url\"=\"/r/all\"}}";
    private final static String defaultFeed = "{\"name\":\"Front Page\",\"path\":\"\",\"is_multi\":\"true\"}"; // default subs are also "multi"

    public SubredditManager(RedditData redditData, SharedPreferences prefs){
        this.prefs = prefs;
        this.redditData = redditData;
        // load subreddits & multis
        try {
            subreddits = new JSONObject(prefs.getString("userSubreddits", "{}"));
            if (subreddits.length()==0){
                loadDefaultSubreddits();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            multis = new JSONObject(prefs.getString("userMultis", "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // load hidden post filters
        try {
            postFilters = new JSONObject(prefs.getString("postFilters", "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void loadDefaultSubreddits(){
        try {
            subreddits = new JSONObject(defaultSubreddits); // start with front page and all
        } catch (JSONException e) {
            e.printStackTrace();
        }
        new LoadDefaultSubredditsTask().execute();
    }

    public void clearMultis(){
        multis = new JSONObject();
        saveMultis();
    }

    private class LoadDefaultSubredditsTask extends AsyncTask<Void, Void, JSONArray>{

        @Override
        protected JSONArray doInBackground(Void... params) {
            try {
                return redditData.getDefaultSubreddits();
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONArray result) {
            if (result!=null){
                for (int i=0; i<result.length(); i++){
                    try {
                        JSONObject subSrc = result.getJSONObject(i).getJSONObject("data");
                        JSONObject sub = new JSONObject();
                        String name = subSrc.getString("display_name");
                        sub.put("display_name", name);
                        sub.put("public_description", subSrc.getString("public_description"));
                        subreddits.put(name, sub);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                saveSubs();
            }
        }
    }

    // CURRENT FEEDS
    public String getCurrentFeedName(int feedId){
        try {
            return getCurrentFeed(feedId).getString("name");
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    public String getCurrentFeedPath(int feedId){
        try {
            return getCurrentFeed(feedId).getString("path");
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    public boolean isFeedMulti(int feedId){
        try {
            return getCurrentFeed(feedId).getBoolean("is_multi");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }
    // this sets the current feed using the supplied name, reddit url path and isMulti value.
    // isMulti is used to determine whether the items are from a different subreddit, in order to show that value to the user
    public void setFeed(int feedId, String name, String path, boolean isMulti){
        if (path.length()>1 && '/' == (path.charAt(path.length()-1)))
            path = path.substring(0, path.length()-1);

        JSONObject data = new JSONObject();
        try {
            data.put("name", name);
            data.put("path", path);
            data.put("is_multi", isMulti);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("currentfeed-"+String.valueOf(feedId), data.toString());
        editor.apply();
    }
    // shortcut method for setting the feed to a subreddit
    public void setFeedSubreddit(int feedId, String subreddit, String path){
        boolean isMulti = (subreddit.equals("Front Page") || subreddit.equals("all"));
        // backwards compatibility; using name in url causes issues on subreddits with non-url-compatible characters
        if (path==null) {
            path = subreddit.equals("Front Page") ? "" : "/r/" + subreddit;
            // Strip last / from url if present
        } else if (path.length()>0 && path.charAt(path.length()-1)=='/')
            path = path.substring(0, path.length()-1);

        setFeed(feedId, subreddit, path, isMulti);
    }
    // shortcut method for setting the feed to a domain
    public void setFeedDomain(int feedId, String domain){
        setFeed(feedId, domain, "/domain/" + domain, true);
    }

    private JSONObject getCurrentFeed(int feedId) throws JSONException {
        return new JSONObject(prefs.getString("currentfeed-" + String.valueOf(feedId), defaultFeed));
    }

    // /r/all filtering
    public ArrayList<String> getAllFilter(){
        return new ArrayList<>(Arrays.asList(StringUtils.split(prefs.getString("allFilter", ""), ",")));
    }

    public void setAllFilter(ArrayList<String> filter){
        prefs.edit().putString("allFilter", StringUtils.join(filter.toArray(new String[filter.size()]), ",")).apply();
    }
    // TODO: per subreddit domain filtering
    // hidden post filters
    public void addPostFilter(int feedId, String redditId){
        String feedPath = getCurrentFeedPath(feedId);
        JSONObject arr;
        try {
            if (postFilters.has(feedPath)){
                arr = postFilters.getJSONObject(feedPath);
            } else {
                arr = new JSONObject();
            }
            arr.put(redditId, redditId);
            postFilters.put(feedPath, arr);
            savePostFilters();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private JSONObject getPostFilters(String feedPath){
        // return all for front page and all otherwise just return the path specific filters
        JSONObject finalarr = new JSONObject();
        if (feedPath.equals("") || feedPath.equals("/r/all")){
            Iterator it = postFilters.keys();
            while (it.hasNext()){
                try {
                    JSONObject arr = postFilters.getJSONObject((String) it.next());
                    Iterator it2 = arr.keys();
                    while (it2.hasNext()){
                        String id = arr.getString((String) it2.next());
                        finalarr.put(id, id);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return finalarr;
        } else {
            if (postFilters.has(feedPath)) {
                try {
                    finalarr = postFilters.getJSONObject(feedPath);
                    return finalarr;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return new JSONObject();
    }

    public int getPostFilterCount(){
        int count = 0;
        Iterator it = postFilters.keys();
        while (it.hasNext()){
            try {
                count += postFilters.getJSONObject((String) it.next()).length();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return count;
    }

    public void clearPostFilters(){
        postFilters = new JSONObject();
        savePostFilters();
    }

    private void savePostFilters(){
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("postFilters", postFilters.toString());
        editor.apply();
    }
    // apply filters to the new feed data
    public JSONArray filterFeed(int feedId, JSONArray feedArray, JSONArray currentFeed, boolean filterAll, boolean filterPosts){
        // determine filter requirements
        boolean filterDuplicates = prefs.getBoolean("filterduplicatespref", true) && currentFeed!=null;
        JSONObject postFilters = null;
        if (filterPosts) {
            postFilters = getPostFilters(getCurrentFeedPath(feedId));
            filterPosts = postFilters.length() > 0;
        }
        if (filterAll) {
            filterAll = !prefs.getString("allFilter", "").equals("");
        }
        if (!filterAll && !filterDuplicates && !filterPosts)
            return feedArray; // no filters applied
        // collect current ids
        ArrayList<String> ids = new ArrayList<>();
        if (filterDuplicates){
            for (int i=0; i<currentFeed.length(); i++){
                try {
                    ids.add(currentFeed.getJSONObject(i).getJSONObject("data").getString("name"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        // filter the new feed
        JSONArray filtered = new JSONArray();
        ArrayList<String> filter = null;
        if (filterAll)
            filter = getAllFilter();
        JSONObject feedObj;
        String subreddit;
        for (int i=0; i<feedArray.length(); i++){
            try {
                feedObj = feedArray.getJSONObject(i);
                if (filterDuplicates || filterPosts) {
                    String currentId = feedObj.getJSONObject("data").getString("name");
                    if (filterDuplicates) {
                        if (ids.contains(currentId)) {
                            continue;
                        }
                    }
                    if (filterPosts) {
                        if (postFilters.has(currentId)) {
                            continue;
                        }
                    }
                }
                if (filterAll) {
                    subreddit = feedObj.getJSONObject("data").getString("subreddit");
                    if (filter.contains(subreddit)) { // add item if not exlcuded (in filter)
                        continue;
                    }
                }
                filtered.put(feedObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return filtered;
    }

    // SUBREDDIT STORAGE
    private void saveSubs(){
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("userSubreddits", subreddits.toString());
        editor.apply();
    }

    public ArrayList<String> getSubredditNames() {
        ArrayList<String> subList = new ArrayList<>();
        Iterator iterator = subreddits.keys();
        while (iterator.hasNext()){
            subList.add(iterator.next().toString());
        }
        return subList;
    }

    public JSONObject getSubredditData(String name){
        try {
            return subreddits.getJSONObject(name);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void addSubreddit(JSONObject subObj){
        try {
            subreddits.put(subObj.getString("display_name"), subObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        saveSubs();
    }

    public void setSubreddits(JSONArray subsArray){
        try {
            subreddits = new JSONObject(defaultSubreddits); // start with front page and all
        } catch (JSONException e) {
            e.printStackTrace();
        }
        for (int i = 0; i<subsArray.length(); i++){
            try {
                addSubredditData(subsArray.getJSONObject(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        saveSubs();
    }

    private void addSubredditData(JSONObject subObj){
        try {
            JSONObject data =  subObj.getJSONObject("data");
            String name = data.getString("display_name");
            subreddits.put(name, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void removeSubreddit(String subredditName){
        subreddits.remove(subredditName);
        saveSubs();
    }

    // MULTI STORAGE
    public ArrayList<JSONObject> getMultiList(){
        ArrayList<JSONObject> multiList = new ArrayList<>();
        Iterator iterator = multis.keys();
        JSONObject multiObj;
        while(iterator.hasNext()){
            try {
                multiObj = multis.getJSONObject(iterator.next().toString());
                multiList.add(multiObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return multiList;
    }

    public ArrayList<String> getMultiSubreddits(String multiPath){

        ArrayList<String> multiList = new ArrayList<>();
        try {
            JSONArray multiSubs = multis.getJSONObject(multiPath).getJSONArray("subreddits");
            for (int i=0; i<multiSubs.length(); i++){
                multiList.add(multiSubs.getJSONObject(i).getString("name"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return multiList;
    }

    public void setMultiSubs(String multiPath, ArrayList<String> multiSubList){
        JSONArray multiSubs = new JSONArray();
        JSONObject subObj;
        for (int i=0; i<multiSubList.size(); i++){
            subObj = new JSONObject();
            try {
                subObj.put("name", multiSubList.get(i));
                multiSubs.put(subObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        try {
            JSONObject multiObj = multis.getJSONObject(multiPath);
            multiObj.put("subreddits", multiSubs);
            multis.put(multiPath, multiObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        saveMultis();
    }

    public JSONObject getMultiData(String multiPath){
        try {
            return multis.getJSONObject(multiPath);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setMultiData(String multiPath, JSONObject multiObj){

        try {
            multis.put(multiPath, multiObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        saveMultis();
    }

    public void addMultis(JSONArray subsArray, boolean clearCurrent){
        if (clearCurrent)
            multis = new JSONObject();

        for (int i = 0; i<subsArray.length(); i++){
            try {
                addMultiData(subsArray.getJSONObject(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        saveMultis();
    }

    public void removeMulti(String key){
        multis.remove(key);
        saveMultis();
    }

    private void addMultiData(JSONObject multiObj){
        try {
            JSONObject data =  multiObj.getJSONObject("data");
            String name = data.getString("path");
            multis.put(name, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveMultis(){
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("userMultis", multis.toString());
        editor.apply();
    }
}
