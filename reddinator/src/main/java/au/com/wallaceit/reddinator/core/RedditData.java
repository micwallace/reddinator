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
package au.com.wallaceit.reddinator.core;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Base64;

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.wallaceit.reddinator.activity.OAuthView;

public class RedditData {
    private SharedPreferences sharedPrefs;
    private OkHttpClient httpClient;
    private static final String STANDARD_ENDPOINT = "https://www.reddit.com";
    private static final String OAUTH_ENDPOINT = "https://oauth.reddit.com";
    public static final String OAUTH_CLIENTID = "wY63YAHgSPSh5w";
    public static final String OAUTH_SCOPES = "mysubreddits,vote,read,submit,edit,identity,subscribe,save";
    public static final String OAUTH_REDIRECT = "oauth://reddinator.wallaceit.com.au";
    private String userAgent;
    private JSONObject oauthToken = null;
    private String oauthstate = null; // random string for secure oauth flow
    private String username;
    private int inboxCount = 0;
    private long lastUpdateTime = 0;

    public RedditData(Context context) {
        // set user agent
        userAgent = "android:au.com.wallaceit.reddinator:v";
        try {
            PackageInfo manager = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            userAgent += manager.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        userAgent += " (by /u/micwallace)";
        // load account
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String tokenStr = sharedPrefs.getString("oauthtoken", "");
        username = sharedPrefs.getString("username", "");
        inboxCount = sharedPrefs.getInt("inbox_count", 0);
        lastUpdateTime = sharedPrefs.getLong("last_info_update", 0);
        if (!tokenStr.equals("")) {
            try {
                oauthToken = new JSONObject(tokenStr);
            } catch (JSONException e) {
                e.printStackTrace();
                oauthToken = null;
            }
        }
    }

    // ACCOUNT CONTROL
    public void initiateLogin(Context context) {
        Intent loginintent = new Intent(context, OAuthView.class);
        oauthstate = UUID.randomUUID().toString();
        loginintent.putExtra("oauthstate", oauthstate);
        context.startActivity(loginintent);
    }

    public void purgeAccountData() {
        oauthToken = null;
        username = null;
        saveUserData();
    }

    // NON-AUTHED REQUESTS
    public JSONArray getSubreddits() throws RedditApiException {
        JSONArray subreddits;
        String url = STANDARD_ENDPOINT + "/subreddits/popular.json?limit=50";
        try {
            subreddits = redditApiGet(url, false).getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        }
        return subreddits;
    }

    public JSONArray getSubredditSearch(String query) throws RedditApiException {
        JSONArray subreddits;
        String url = STANDARD_ENDPOINT + "/subreddits/search.json?q=" + Uri.encode(query);
        try {
            subreddits = redditApiGet(url, false).getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        }
        return subreddits;
    }

    public JSONArray searchRedditNames(String query) throws RedditApiException {
        JSONArray names;
        String url = (isLoggedIn() ? OAUTH_ENDPOINT : STANDARD_ENDPOINT) + "/api/search_reddit_names.json?include_over_18=true&query=" + Uri.encode(query);
        try {
            names = redditApiPost(url).getJSONArray("names");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        }
        return names;
    }

    public JSONObject getSubmitText(String subreddit) throws RedditApiException {

        String url = STANDARD_ENDPOINT + "/r/"+subreddit+"/api/submit_text/.json";
        return redditApiGet(url, false);
    }

    public JSONArray getRedditFeed(String feedPath, String sort, int limit, String afterid) throws RedditApiException {
        boolean loggedIn = isLoggedIn();
        String url = (loggedIn ? OAUTH_ENDPOINT : STANDARD_ENDPOINT) + feedPath + "/" + sort + ".json?limit=" + String.valueOf(limit) + (!afterid.equals("0") ? "&after=" + afterid : "");
        JSONObject result;
        JSONArray feed;

        result = redditApiGet(url, true); // use oauth if logged in
        try {
            feed = result.getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        }
        return feed;
    }

    public JSONArray getCommentsFeed(String permalink, String sort, int limit) throws RedditApiException {
        boolean loggedIn = isLoggedIn();
        String url = (loggedIn ? OAUTH_ENDPOINT : STANDARD_ENDPOINT) + permalink + ".json?api_type=json&sort=" + sort + "&limit=" + String.valueOf(limit);

        return redditApiGetArray(url, loggedIn);
    }

    public JSONArray getChildComments(String moreId, String articleId, String children, String sort) throws RedditApiException {
        boolean loggedIn = isLoggedIn();
        String url = (loggedIn ? OAUTH_ENDPOINT : STANDARD_ENDPOINT) + "/api/morechildren.json?api_type=json&sort=" + sort + "&id=" + moreId + "&link_id=" + articleId + "&children=" + children;

        JSONArray feed = new JSONArray();

        try {
            JSONObject result = redditApiGet(url, true); // use oauth if logged in
            if (result != null) {
                feed = result.getJSONObject("json").getJSONObject("data").getJSONArray("things");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        }
        return feed;
    }

    // AUTHED CALLS
    public String getUsername(){
        return username;
    }

    public int getInboxCount(){
        return inboxCount;
    }

    public long getLastUserUpdateTime(){ return lastUpdateTime; }

    public void clearStoredInboxCount(){
        inboxCount = 0;
        SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putInt("inbox_count", inboxCount);
        edit.apply();
    }

    // updates internally tracked user info and saves it to preference. This is also used for saving oauth token for the first time.
    public void updateUserInfo() throws RedditApiException {
        JSONObject userInfo = getUserInfo();
        try {
            username = userInfo.getString("name");
            inboxCount = userInfo.getInt("inbox_count");
            lastUpdateTime = (new Date()).getTime();
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        saveUserData();
    }

    private JSONObject getUserInfo() throws RedditApiException {

        checkLogin();

        JSONObject resultjson;
        String url = OAUTH_ENDPOINT + "/api/v1/me";
        try {
            resultjson = redditApiGet(url, true);

            if (resultjson.has("errors") && resultjson.getJSONArray("errors").length()>0) {
                throw new RedditApiException("API error: "+resultjson.getJSONArray("errors").getJSONArray(0).getString(1));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        }

        return resultjson;
    }

    public boolean vote(String id, int direction) throws RedditApiException {

        checkLogin();

        JSONObject resultjson;
        String url = OAUTH_ENDPOINT + "/api/vote?id=" + id + "&dir=" + String.valueOf(direction) + "&api_type=json";
        try {
            resultjson = redditApiPost(url);

            if (resultjson.has("errors") && resultjson.getJSONArray("errors").length()>0) {
                JSONArray errors = resultjson.getJSONArray("errors");
                JSONArray firsterror = (JSONArray) errors.get(0);
                if (firsterror.get(0).equals("USER_REQUIRED")) {
                    oauthToken = null; // bearer token invalid, nullify
                    throw new RedditApiException("Authentication Error, Reddit Login Required", true); // creds invalid re-authenticate.
                }
                return false;
            } else {
                return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        }
    }

    public JSONObject postComment(String parentId, String text) throws RedditApiException {

        checkLogin();

        JSONObject resultjson;

        try {
            String url = OAUTH_ENDPOINT + "/api/comment?thing_id=" + parentId + "&text=" + URLEncoder.encode(text, "UTF-8") + "&api_type=json";

            resultjson = redditApiPost(url).getJSONObject("json");
            System.out.println(resultjson.toString());
            if (resultjson.has("errors") && resultjson.getJSONArray("errors").length()>0) {
                JSONArray errors = resultjson.getJSONArray("errors");
                JSONArray firsterror = (JSONArray) errors.get(0);
                if (firsterror.get(0).equals("USER_REQUIRED")) {
                    oauthToken = null; // bearer token invalid, nullify
                    throw new RedditApiException("Authentication Error, Reddit Login Required", true); // creds invalid re-authenticate.
                }
                throw new RedditApiException("API Error: "+firsterror.get(1), true);
            } else {
                return resultjson.getJSONObject("data").getJSONArray("things").getJSONObject(0).getJSONObject("data");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RedditApiException("Encoding error: "+e.getMessage());
        }
    }

    public JSONObject editComment(String thingId, String text) throws RedditApiException {

        checkLogin();

        JSONObject resultjson;

        try {
            String url = OAUTH_ENDPOINT + "/api/editusertext?thing_id=" + thingId + "&text=" + URLEncoder.encode(text, "UTF-8") + "&api_type=json";

            resultjson = redditApiPost(url).getJSONObject("json");

            if (resultjson.has("errors") && resultjson.getJSONArray("errors").length()>0) {
                JSONArray errors = resultjson.getJSONArray("errors");
                JSONArray firsterror = (JSONArray) errors.get(0);
                if (firsterror.get(0).equals("USER_REQUIRED")) {
                    oauthToken = null; // bearer token invalid, nullify
                    throw new RedditApiException("Authentication Error, Reddit Login Required", true); // creds invalid re-authenticate.
                }
                throw new RedditApiException("API Error: "+firsterror.get(1), true);
            } else {
                return resultjson.getJSONObject("data").getJSONArray("things").getJSONObject(0).getJSONObject("data");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RedditApiException("Encoding error: "+e.getMessage());
        }
    }

    public boolean deleteComment(String thingId) throws RedditApiException {
        checkLogin();

        JSONObject resultjson;

        try {
            String url = OAUTH_ENDPOINT + "/api/del?id=" + thingId;

            resultjson = redditApiPost(url);

            if (resultjson.has("errors") && resultjson.getJSONArray("errors").length()>0) {
                JSONArray errors = resultjson.getJSONArray("errors");
                JSONArray firsterror = (JSONArray) errors.get(0);
                if (firsterror.get(0).equals("USER_REQUIRED")) {
                    oauthToken = null; // bearer token invalid, nullify
                    throw new RedditApiException("Authentication Error, Reddit Login Required", true); // creds invalid re-authenticate.
                }
                throw new RedditApiException("API Error: "+firsterror.get(1), true);
            } else {
                return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        }
    }

    public JSONArray getMySubreddits() throws RedditApiException {
        checkLogin();

        String url = OAUTH_ENDPOINT + "/subreddits/mine/subscriber.json?limit=100&show=all";
        JSONArray resultjson;
        try {
            resultjson = redditApiGet(url, true).getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Parsing error: "+e.getMessage());
        }
        return resultjson;
    }

    public JSONObject subscribe(String subId, boolean subscribe) throws RedditApiException {
        checkLogin();

        String url = OAUTH_ENDPOINT + "/api/subscribe?sr="+ subId +"&action="+(subscribe?"sub":"unsub");

        return redditApiPost(url);
    }

    public JSONArray getMyMultis() throws RedditApiException {
        checkLogin();

        String url = OAUTH_ENDPOINT + "/api/multi/mine";

        return redditApiGetArray(url, true);
    }

    public JSONObject copyMulti(String name, String fromPath) throws RedditApiException {
        checkLogin();

        String url;
        try {
            url = OAUTH_ENDPOINT + "/api/multi/copy?display_name="+ URLEncoder.encode(name, "UTF-8")+"&from="+fromPath+"&to=/user/"+username+"/m/"+URLEncoder.encode(name.toLowerCase().replaceAll("\\s+", ""), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RedditApiException("Encoding error: "+e.getMessage());
        }

        return redditApiPost(url);
    }

    public JSONObject createMulti(String name, JSONObject multiObj) throws RedditApiException {
        checkLogin();

        String url;
        try {
            url = OAUTH_ENDPOINT + "/api/multi/user/"+username+"/m/"+name+"?model="+URLEncoder.encode(multiObj.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RedditApiException("Encoding error: "+e.getMessage());
        }

        return redditApiPost(url);
    }

    public JSONObject editMulti(String multiPath, JSONObject multiObj) throws RedditApiException {
        checkLogin();

        String url;
        try {
            url = OAUTH_ENDPOINT + "/api/multi"+multiPath+"?model="+URLEncoder.encode(multiObj.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RedditApiException("Encoding error: "+e.getMessage());
        }

        return redditApiPut(url);
    }

    public void deleteMulti(String multiPath) throws RedditApiException {
        checkLogin();

        String url = OAUTH_ENDPOINT + "/api/multi"+multiPath;

        redditApiDelete(url);
    }

    public JSONObject renameMulti(String multiPath, String newName) throws RedditApiException {
        checkLogin();

        String url = OAUTH_ENDPOINT + "/api/multi/rename/?from="+multiPath+"&to=/user/"+username+"/m/"+newName;

        return redditApiPost(url);
    }

    public JSONObject addMultiSubreddit(String multiPath, String subredditName) throws RedditApiException {
        checkLogin();

        String url;
        try {
            url = OAUTH_ENDPOINT + "/api/multi"+multiPath+"/r/"+subredditName+"?srname="+URLEncoder.encode(subredditName, "UTF-8")+"&model="+URLEncoder.encode("{\"name\":\""+subredditName+"\"}", "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RedditApiException("Encoding error: "+e.getMessage());
        }

        return redditApiPut(url);
    }

    public void removeMultiSubreddit(String multiPath, String subredditName) throws RedditApiException {
        checkLogin();

        String url;
        try {
            url = OAUTH_ENDPOINT + "/api/multi"+multiPath+"/r/"+subredditName+"?srname="+URLEncoder.encode(subredditName, "UTF-8")+"&model="+URLEncoder.encode("{\"name\":\""+subredditName+"\"}", "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RedditApiException("Encoding error: "+e.getMessage());
        }

        redditApiDelete(url);
    }

    public JSONObject submit(String subreddit, boolean isLink, String title, String content) throws RedditApiException {
        checkLogin();

        try {
            content = URLEncoder.encode(content,"UTF-8");
            String url = OAUTH_ENDPOINT + "/api/submit?api_type=json&extension=json&then=comments&sr=" + URLEncoder.encode(subreddit, "UTF-8") + "&kind=" + (isLink?"link":"self") + "&title=" + URLEncoder.encode(title, "UTF-8") + "&" + (isLink?"url="+content:"text="+content);

            return redditApiPost(url).getJSONObject("json");

        } catch (JSONException | UnsupportedEncodingException e) {
            throw new RedditApiException(e.getMessage());
        }
    }

    public void save(String category, String name) throws RedditApiException {
        checkLogin();

        String url = OAUTH_ENDPOINT + "/api/save?category="+category+"&id="+name;

        redditApiPost(url);
    }

    // COMM FUNCTIONS
    // Create Http/s client
    private boolean createHttpClient() {
        httpClient = new OkHttpClient();
        httpClient.setConnectTimeout(10, TimeUnit.SECONDS);
        httpClient.setReadTimeout(10, TimeUnit.SECONDS);
        httpClient.networkInterceptors().add(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request originalRequest = chain.request();
                Request requestWithUserAgent = originalRequest.newBuilder()
                        .removeHeader("User-Agent")
                        .addHeader("User-Agent", userAgent)
                        .build();
                return chain.proceed(requestWithUserAgent);
            }
        });

        return true;
    }

    private JSONArray redditApiGetArray(String url, boolean useAuth) throws RedditApiException {
        JSONArray jArr;
        try {
            String json = redditApiRequest(url, "GET", useAuth ? REQUEST_MODE_AUTHED : REQUEST_MODE_UNAUTHED, null);
            jArr = new JSONArray(json);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Error: "+e.getMessage());
        }
        return jArr;
    }

    private JSONObject redditApiGet(String url, boolean useAuth) throws RedditApiException {
        JSONObject jObj;
        try {
            String json = redditApiRequest(url, "GET", useAuth?REQUEST_MODE_AUTHED:REQUEST_MODE_UNAUTHED, null);
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Error: "+e.getMessage());
        }
        return jObj;
    }

    private JSONObject redditApiPost(String url) throws RedditApiException {
        JSONObject jObj;
        try {
            String json = redditApiRequest(url, "POST", REQUEST_MODE_AUTHED, null);
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Error: "+e.getMessage());
        }
        return jObj;
    }

    private JSONObject redditApiPut(String url) throws RedditApiException {
        JSONObject jObj;
        try {
            String json = redditApiRequest(url, "PUT", REQUEST_MODE_AUTHED, null);
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Error: "+e.getMessage());
        }
        return jObj;
    }

    private void redditApiDelete(String url) throws RedditApiException {

        redditApiRequest(url, "DELETE", REQUEST_MODE_AUTHED, null);
    }

    private JSONObject redditApiOauthRequest(String url, HashMap<String, String> data) throws RedditApiException {
        JSONObject jObj;
        try {
            String json = redditApiRequest(url, "POST", REQUEST_MODE_OAUTHREQ, data);
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RedditApiException("Error: "+e.getMessage());
        }
        return jObj;
    }

    private static final int REQUEST_MODE_UNAUTHED = 0;
    private static final int REQUEST_MODE_AUTHED = 1;
    private static final int REQUEST_MODE_OAUTHREQ = 2;
    private static final MediaType POST_ENCODED = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8");
    private String redditApiRequest(String urlStr, String method, int oauthMode, HashMap<String, String> formData) throws RedditApiException {
        String json;
        // create client if null
        if (httpClient == null) {
            createHttpClient();
        }
        try {
            Request.Builder httpRequest = new Request.Builder().url(urlStr);
            RequestBody httpRequestBody;
            String requestStr = "";
            if (formData!=null) {
                FormEncodingBuilder formBuilder = new FormEncodingBuilder();
                Iterator iterator = formData.keySet().iterator();
                String key;
                while (iterator.hasNext()){
                    key = (String) iterator.next();
                    formBuilder.add(key, formData.get(key));
                }
                httpRequestBody = formBuilder.build();
            } else {
                if (!method.equals("GET")) {
                    int queryIndex = urlStr.indexOf("?");
                    if (queryIndex!=-1)
                        urlStr = urlStr.substring(queryIndex);
                    requestStr = URLEncoder.encode(urlStr, "UTF-8");
                }
                httpRequestBody = RequestBody.create(POST_ENCODED, requestStr);
            }

            switch (method){
                case "POST":
                    httpRequest.post(httpRequestBody);
                    break;
                case "PUT":
                    httpRequest.put(httpRequestBody);
                    break;
                case "DELETE":
                    httpRequest.delete(httpRequestBody);
                    break;
                case "GET":
                default:
                    httpRequest.get();
                    break;
            }
            if (oauthMode==REQUEST_MODE_OAUTHREQ) {
                // For oauth token retrieval and refresh
                httpRequest.addHeader("Authorization", "Basic " + Base64.encodeToString((OAUTH_CLIENTID + ":").getBytes(), Base64.URL_SAFE | Base64.NO_WRAP));
            } else if (isLoggedIn() && oauthMode==REQUEST_MODE_AUTHED) {
                if (isTokenExpired()) {
                    refreshToken();
                }
                // add auth headers
                String tokenStr = getTokenValue("token_type") + " " + getTokenValue("access_token");
                httpRequest.addHeader("Authorization", tokenStr);
            }

            Response response = httpClient.newCall(httpRequest.build()).execute();
            json = response.body().string();
            int errorCode = response.code();
            if (errorCode<200 || errorCode>202) {
                String errorMsg = getErrorText(json);
                throw new RedditApiException("Error "+String.valueOf(errorCode)+": "+(errorMsg.equals("")?response.message():errorMsg)+(errorCode==403?" (Authorization with Reddit required)":""), errorCode==403, errorCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RedditApiException("Error: "+e.getMessage());
        }

        return json;
    }

    private String getErrorText(String response){
        String errorMsg = "";
        if (response!=null) {
            if (response.indexOf("{")==0)
            try {
                JSONObject errorJson = new JSONObject(response);
                if (errorJson.has("errors")) {
                    JSONArray errorArr = errorJson.getJSONArray("errors");
                    if (errorArr.length()>0)
                        errorArr.getJSONArray(0).getString(1);
                }
                return errorMsg;
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // attempt to get html error message (often returned by 403/500)
            //System.err.println(response);
            final Pattern patternh2 = Pattern.compile("<h2>(.+?)</h2>");
            Matcher matcher = patternh2.matcher(response);
            if (matcher.matches()) {
                errorMsg = matcher.group(1);
            }
        }
        return errorMsg;
    }

    private void checkLogin() throws RedditApiException {
        if (!isLoggedIn()) {
            throw new RedditApiException("Reddit Login Required", true);
        }
    }

    public class RedditApiException extends Exception {
        private boolean isLoginError = false;
        private int httpErrorCode = 200;
        //Constructor that accepts a message
        public RedditApiException(String message) {
            super(message);
        }

        public RedditApiException(String message, boolean isLoginError) {
            super(message);
            this.isLoginError = isLoginError;
        }

        public RedditApiException(String message, boolean isLoginError, int httpErrorCode) {
            super(message);
            this.isLoginError = isLoginError;
            this.httpErrorCode = httpErrorCode;
        }

        public int getHttpErrorCode() { return httpErrorCode; }

        public boolean isAuthError(){
            return isLoginError;
        }
    }

    // OAUTH FUNCTIONS
    public boolean isLoggedIn() {
        return oauthToken != null;
    }

    private boolean isTokenExpired() {
        Long now = (System.currentTimeMillis() / 1000L);
        Long expiry = (long) 0;
        try {
            expiry = oauthToken.getLong("expires_at");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return expiry < now;
    }

    private String getTokenValue(String key) {
        String token = "";
        try {
            token = oauthToken.getString(key);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return token;
    }

    public void retrieveToken(String code, String state) throws RedditApiException {
        if (!state.equals(oauthstate)) {
            throw new RedditApiException("OAuth Error: Invalid state");
        }
        String url = "https://www.reddit.com/api/v1/access_token";
        JSONObject resultjson;
        HashMap<String, String> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", OAUTH_REDIRECT);
        resultjson = redditApiOauthRequest(url, params);
        if (resultjson.has("access_token")) {
            // login successful, set new token and save
            oauthToken = resultjson;
            try {
                Long epoch = (System.currentTimeMillis() / 1000L);
                Long expires_at = epoch + Integer.parseInt(oauthToken.getString("expires_in"));
                oauthToken.put("expires_at", expires_at);
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RedditApiException("OAuth Error: "+e.getMessage());
            }
            // try to retrieve user info & save, if exception thrown, just make sure we save token
            try {
                updateUserInfo();
            } catch (RedditApiException e) {
                e.printStackTrace();
                saveUserData();
            }
            //System.out.println("oauth request result: OK");
            return;
        }
        // throw error
        throwOAuthError(resultjson);
    }

    private void refreshToken() throws RedditApiException {
        String url = "https://www.reddit.com/api/v1/access_token";
        JSONObject resultjson;
        HashMap<String, String> params = new HashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", getTokenValue("refresh_token"));
        resultjson = redditApiOauthRequest(url, params);
        if (resultjson.has("access_token")) {
            // login successful, update token and save
            try {
                oauthToken.put("access_token", resultjson.get("access_token"));
                oauthToken.put("token_type", resultjson.get("token_type"));
                Long expires_in = resultjson.getLong("expires_in") - 30;
                Long epoch = (System.currentTimeMillis() / 1000L);
                oauthToken.put("expires_in", expires_in);
                oauthToken.put("expires_at", (epoch + expires_in));
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RedditApiException("OAuth Error: "+e.getMessage());
            }
            // save oauth token
            saveUserData();
            //System.out.println("oauth refresh result: OK");
            return;
        }
        // set error result
        throwOAuthError(resultjson);
    }

    private void throwOAuthError(JSONObject resultjson) throws RedditApiException {
        String error;
        if (resultjson.has("error")){
            try {
                error = resultjson.getString("error");
            } catch (JSONException e) {
                e.printStackTrace();
                error = "Unknown Error D-:";
            }
        } else {
            error = "Unknown Error D-:";
        }
        throw new RedditApiException("OAuth Error: "+error);
    }

    public void saveUserData() {
        SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putString("oauthtoken", oauthToken == null ? "" : oauthToken.toString());
        edit.putString("username", username);
        edit.putInt("inbox_count", inboxCount);
        edit.putLong("last_info_update", lastUpdateTime);
        edit.apply();
    }

}
