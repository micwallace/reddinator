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
import android.net.Uri;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;

public class RedditData {
    private SharedPreferences sharedPrefs;
    private DefaultHttpClient httpclient;
    private CookieStore cookieStore;
    static JSONObject jObj = null;
    static String json = "";
    private String uname;
    private String pword;
    private String modhash = "";
    private String cookieStr = "";

    RedditData() {

    }

    public void loadAccn(SharedPreferences prefs) {
        uname = prefs.getString("uname", "");
        pword = prefs.getString("pword", "");
        cookieStr = prefs.getString("cook", "");
        modhash = prefs.getString("modhash", "");
        setClientCookie();
        // store shared prefs editor to commit any new cookies & modhashes
        sharedPrefs = prefs;
    }

    public void loadTempAccn(String user, String pass) {
        // load temp user and pass
        uname = user;
        pword = pass;
    }

    public void saveCookieData() {
        SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putString("cook", cookieStr);
        edit.putString("modhash", modhash);
        edit.commit();
    }

    public void purgeAccountData() {
        modhash = "";
        cookieStr = "";
        uname = "";
        pword = "";
        cookieStore.clear();
    }

    // data fetch calls
    public JSONArray getSubreddits() {
        JSONArray subreddits = new JSONArray();
        String url = "http://www.reddit.com/subreddits/popular.json?limit=50";
        try {
            subreddits = getJSONFromUrl(url).getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return subreddits;
    }

    public JSONArray getSubredditSearch(String query) {
        JSONArray subreddits = new JSONArray();
        String url = "http://www.reddit.com/subreddits/search.json?q=" + Uri.encode(query);
        try {
            subreddits = getJSONFromUrl(url).getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return subreddits;
    }

    public JSONArray getRedditFeed(String subreddit, String sort, int limit, String afterid) {
        String url = "http://www.reddit.com" + (subreddit.equals("Front Page") ? "" : "/r/" + subreddit) + "/" + sort + ".json?limit=" + String.valueOf(limit) + (!afterid.equals("0") ? "&after=" + afterid : "");
        JSONArray feed = new JSONArray();
        try {
            feed = getJSONFromUrl(url).getJSONObject("data").getJSONArray("children"); // get the feed items
        } catch (JSONException e) {
            feed.put("-1"); // error indicator
            e.printStackTrace();
        }
        return feed;
    }

    // GLOBAL FUNCTIONS
    // Create Http/s client
    private boolean createHttpClient() {
        // Initialize client
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, HTTP.DEFAULT_CONTENT_CHARSET);
        HttpProtocolParams.setUseExpectContinue(params, true);
        HttpConnectionParams.setConnectionTimeout(params, 12000);
        HttpConnectionParams.setSoTimeout(params, 10000);
        SchemeRegistry schReg = new SchemeRegistry();
        schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schReg.register(new Scheme("https", org.apache.http.conn.ssl.SSLSocketFactory.getSocketFactory(), 443));
        ClientConnectionManager conMgr = new ThreadSafeClientConnManager(params, schReg);
        cookieStore = new BasicCookieStore();
        httpclient = new DefaultHttpClient(conMgr, params);
        httpclient.setCookieStore(cookieStore);
        return true;
    }

    private void setClientCookie() {
        if (httpclient == null) {
            createHttpClient();
        }
        BasicClientCookie cookieobj = new BasicClientCookie("reddit_session", Uri.encode(cookieStr));
        cookieobj.setDomain(".reddit.com");
        cookieobj.setPath("/");
        cookieStore.addCookie(cookieobj);
    }

    // HTTP Get Request
    private JSONObject getJSONFromUrl(String url) {
        // create null object to return on errors
        jObj = new JSONObject();
        // create client if null
        if (httpclient == null) {
            createHttpClient();
        }
        InputStream is;
        // Making HTTP request
        try {
            HttpGet httpget = new HttpGet(url);
            HttpResponse httpResponse = httpclient.execute(httpget);
            HttpEntity httpEntity = httpResponse.getEntity();
            is = httpEntity.getContent();

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return jObj;
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            return jObj;
        } catch (IOException e) {
            e.printStackTrace();
            return jObj;
        }

        // read data
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    is, "iso-8859-1"), 8);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            is.close();
            json = sb.toString();
        } catch (Exception e) {
            //Log.e("Buffer Error", "Error converting result " + e.toString());
            e.printStackTrace();
            return jObj;
        }

        // try parse the string to a JSON object
        try {
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            //Log.e("JSON Parser", "Error parsing data " + e.toString());
            e.printStackTrace();
            return jObj;
        }
        // return JSON String
        return jObj;

    }

    public String getSessionCookie() {
        return cookieStr;
    }

    // Post calls

    public String vote(String id, int direction) {
        String result = "";
        JSONObject resultjson;
        // if modhash is blank, try to login
        if (modhash.equals("")) {
            if (!uname.equals("") || !pword.equals("")) {
                // Get account and authenticate
                result = login(true);
            }
        }
        if (!modhash.equals("")) {
            resultjson = new JSONObject();
            String url = "https://ssl.reddit.com/api/vote?id=" + id + "&dir=" + String.valueOf(direction) + "&uh=" + modhash + "&api_type=json";
            try {
                resultjson = getJSONFromPost(url).getJSONObject("json");
                JSONArray errors = resultjson.getJSONArray("errors");
                if (resultjson.getJSONArray("errors").get(0) != null) {
                    JSONArray firsterror = (JSONArray) errors.get(0);
                    if (firsterror.get(0).equals("USER_REQUIRED")) {
                        // check if creds exist to reauthenticate
                        if (!uname.equals("") && !pword.equals("")) {
                            if (login(true).equals("1")) { // try getting new session, if it fails show login prompt otherwise try voting again.
                                return vote(id, direction);
                            }
                        }
                        result = "LOGIN"; // no creds stored or login failed.
                    }
                    result = errors.toString();
                } else {
                    result = "OK";
                }
            } catch (JSONException e) {
                if (resultjson.toString().equals("{}")) {
                    result = "OK";
                } else {
                    e.printStackTrace();
                }
            }
        } else {
            result = "LOGIN";
        }

        return result;
    }

    public ArrayList<String> getMySubreddits() {
        ArrayList<String> mysrlist = new ArrayList<String>();
        String logresult = "1";
        if (modhash.equals("")) {
            if (!uname.equals("") || !pword.equals("")) {
                logresult = login(false);
            } else {
                logresult = "Stored credentials have not been loaded";
            }
        }

        if (logresult.equals("1")) {
            String url = "https://ssl.reddit.com/subreddits/mine.json";
            JSONArray resultjson = new JSONArray();
            try {
                resultjson = getJSONFromPost(url).getJSONObject("data").getJSONArray("children");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            int i = 0;
            while (i < resultjson.length() - 1) {
                try {
                    mysrlist.add(resultjson.getJSONObject(i).getJSONObject("data").getString("display_name"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                i++;
            }
            // System.out.println("Import Subreddit Output: " + mysrlist.toString());
        } else {
            mysrlist.add("Error: " + logresult);
        }
        return mysrlist;
    }

    public String checkLogin(SharedPreferences prefs, String user, String pass, boolean remember) {
        // set prefs for cookie storage
        sharedPrefs = prefs;
        // set creds
        uname = user;
        pword = pass;
        // try to authenticate, return error and clear account vars on failure
        return login(remember);
    }

    // authenticate with the given credentials and sa
    private String login(boolean remember) {
        String result;
        String url = "https://ssl.reddit.com/api/login.json?user=" + Uri.encode(uname) + "&passwd=" + Uri.encode(pword) + "&rem=" + String.valueOf(remember) + "&api_type=json";
        JSONObject resultjson = new JSONObject();
        try {
            resultjson = getJSONFromPost(url).getJSONObject("json");
            if (resultjson.getJSONArray("errors").isNull(0)) {
                // login successful, set session vars
                modhash = resultjson.getJSONObject("data").getString("modhash");
                cookieStr = resultjson.getJSONObject("data").getString("cookie");
                // save session data and add cookie to client
                saveCookieData();
                setClientCookie();
                result = "1";
            } else {
                // set error result
                result = resultjson.getJSONArray("errors").getJSONArray(0).getString(1);
                // unset account to prompt login
                uname = "";
                pword = "";
            }
        } catch (JSONException e) {
            e.printStackTrace();
            result = "JSON Parse exception";
        }
        System.out.println("login result: " + resultjson.toString());
        return result;
    }

    // HTTPS POST Request
    private JSONObject getJSONFromPost(String url) {
        InputStream is = null;
        // create client if null
        if (httpclient == null) {
            createHttpClient();
        }
        try {
            HttpPost httppost = new HttpPost(url);
            HttpResponse httpResponse = httpclient.execute(httppost);
            HttpEntity httpEntity = httpResponse.getEntity();
            is = httpEntity.getContent();

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            if (is != null) is.close();
            json = sb.toString();
        } catch (Exception e) {
            System.out.println("Error converting result " + e.toString());
        }
        // System.out.println("HTTP response:"+json);
        // try parse the string to a JSON object
        try {
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            System.out.println("Error parsing data " + e.toString());
        }
        // return json response
        return jObj;
    }
}
