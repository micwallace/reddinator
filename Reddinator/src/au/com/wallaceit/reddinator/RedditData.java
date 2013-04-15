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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.net.Uri;

public class RedditData {
	private DefaultHttpClient httpclient;
    static JSONObject jObj = null;
    static String json = "";
	RedditData(){
		// Create the HTTP Client
		// httpclient = createHttpClient(); // only create when called
		// System.out.println("HTTPClient created");
	}
	// data fetch calls
	public JSONArray getSubreddits(){
		JSONArray sreddits = new JSONArray();
		String url = "http://www.reddit.com/subreddits/popular.json?limit=50";
		try {
			sreddits = getJSONFromUrl(url).getJSONObject("data").getJSONArray("children");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return sreddits;
	}
	public JSONArray getSubredditSearch(String query){
		JSONArray sreddits = new JSONArray();
		String url = "http://www.reddit.com/subreddits/search.json?q="+Uri.encode(query);
		try {
			sreddits = getJSONFromUrl(url).getJSONObject("data").getJSONArray("children");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return sreddits;
	}
	public JSONArray getRedditFeed(String subreddit, String sort, int limit, String afterid){
		String url = "http://www.reddit.com/r/"+subreddit+"/"+sort+".json?limit="+String.valueOf(limit)+(!afterid.equals("0")?"&after="+afterid:"");
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
	private DefaultHttpClient createHttpClient(){
	    HttpParams params = new BasicHttpParams();
	    HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
	    HttpProtocolParams.setContentCharset(params, HTTP.DEFAULT_CONTENT_CHARSET);
	    HttpProtocolParams.setUseExpectContinue(params, true);
	    SchemeRegistry schReg = new SchemeRegistry();
	    schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	    schReg.register(new Scheme("https", org.apache.http.conn.ssl.SSLSocketFactory.getSocketFactory(), 443));
	    ClientConnectionManager conMgr = new ThreadSafeClientConnManager(params, schReg);

	    return new DefaultHttpClient(conMgr, params);
	}
	// HTTP Get Request
	private JSONObject getJSONFromUrl(String url) {
		// create null object to return on errors
		jObj = new JSONObject();
		// create client if null
		if (httpclient == null){
			httpclient = createHttpClient();
		}
		InputStream is;
        // Making HTTP request
        try {
            // defaultHttpClient
            //DefaultHttpClient httpClient = new DefaultHttpClient();
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
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
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
        //System.out.println("Download complete");
        // return JSON String
        return jObj;
 
    }
	// UNUSED CODE BELOW
	/*// Post calls
		public String vote(String id, String direction){
			String result="";
			String url="https://ssl.reddit.com/api/vote.json?id="+id+"&dir="+direction+"&uh="+modhash+"&api_type=json";		
			JSONObject resultjson = new JSONObject();
			// if modhash is blank, try to login
			String logresult = login("micwallace", "#Gromit11", false);
			if (logresult.equals("1")){
				try {
				resultjson = getJSONFromPost(url).getJSONObject("json");
				JSONArray errors = resultjson.getJSONArray("errors");
				if (resultjson.getJSONArray("errors").get(0) != null){
					JSONArray firsterror = (JSONArray) errors.get(0);
					System.out.println();
					if (firsterror.get(0).equals("USER_REQUIRED")){
						// check for details and login or prompt user
						System.out.println("Vote failed: USER_REQUIRED");
					}
				} else {
					
				}
				} catch (JSONException e) {
					e.printStackTrace();	
				}
			} else {
				
			}
			System.out.println("vote result: "+resultjson.toString());
			return result;
		}
		public String login(String username, String passwd, boolean remember){
			String result="";
			String url="https://ssl.reddit.com/api/login.json?user="+username+"&passwd="+passwd+"&rem="+String.valueOf(remember)+"&api_type=json";		
			JSONObject resultjson = new JSONObject();
			try {
				resultjson = getJSONFromPost(url).getJSONObject("json");
				if (resultjson.getJSONArray("errors").get(0) != null){
					modhash = resultjson.getJSONObject("data").getString("modhash");
					result = "1";
				} else {
					//JSONArray error1 = (JSONArray) resultjson.getJSONArray("errors").get(0);
					result = "0";
				}
			} catch (JSONException e) {
				e.printStackTrace();
				result = "0";
			}
			System.out.println("login result: "+resultjson.toString());
			return result;
		}
		// HTTPS POST Request
		private JSONObject getJSONFromPost(String url){
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
	            String line = null;
	            while ((line = reader.readLine()) != null) {
	                sb.append(line + "\n");
	            }
	            is.close();
	            json = sb.toString();
	        } catch (Exception e) {
	            Log.e("Buffer Error", "Error converting result " + e.toString());
	        }
	        // try parse the string to a JSON object
	        try {
	            jObj = new JSONObject(json);
	        } catch (JSONException e) {
	            Log.e("JSON Parser", "Error parsing data " + e.toString());
	        }
	        System.out.println("POST complete");
	        // return json response
	        return jObj;
		}*/
}
