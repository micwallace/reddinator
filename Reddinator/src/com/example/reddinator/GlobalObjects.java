package com.example.reddinator;

import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;

import android.app.Application;

public class GlobalObjects extends Application {
	private ArrayList<String> srlist;
	static int LOADTYPE_LOAD = 0;
	static int LOADTYPE_LOADMORE = 1;
	private int loadtype = 0; // tells the service what to do when notifyAppDataChanged is fired
	private boolean bypasscache = false; // tells the service to bypass the cache
	public RedditData rdata;
	private HashMap<Integer, JSONArray> datastore;
	//public RedditData redditdata;
	public GlobalObjects(){
		srlist = new ArrayList<String>();
		rdata = new RedditData();
		datastore = new HashMap<Integer, JSONArray>();
	}
	// data
	public JSONArray getFeedData(Integer widgetid){
		if (datastore.containsKey(widgetid)){
			return datastore.get(widgetid);
		} else {
			return (new JSONArray());
		}
	}
	public void putFeedData(Integer widgetid, JSONArray data){
		datastore.put(widgetid, data);
	}
	public boolean isSrlistCached(){
		if (!srlist.isEmpty()){
			return true;
		}
		return false;
	}
	public void putSrList(ArrayList<String> list){
		srlist.clear();
		srlist.addAll(list);
	}
	public ArrayList<String> getSrList(){
		System.out.println("Using cached subreddits");
		return srlist;
	}
	// data loadtype functions
	public int getLoadType(){
		return loadtype;
	}
	public void setLoadMore(){
		loadtype = 1;
	}
	public void SetLoad(){
		loadtype = 0;
	}
	// data cache functions
	public boolean getBypassCache(){
		return bypasscache;
	}
	public void setBypassCache(boolean bypassed){
		bypasscache = bypassed;
	}
}
