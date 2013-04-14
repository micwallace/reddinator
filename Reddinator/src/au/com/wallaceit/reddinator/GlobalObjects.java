package au.com.wallaceit.reddinator;

import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;

import android.app.Application;

public class GlobalObjects extends Application {
	private ArrayList<String> srlist;
	static int LOADTYPE_LOAD = 0;
	static int LOADTYPE_LOADMORE = 1;
	private int loadtype = 0; // tells the service what to do when notifyAppDataChanged is fired
	private boolean bypasscache = false; // tells the factory to bypass the cache when creating a new remoteviewsfacotry
	public RedditData rdata;
	//public RedditData redditdata;
	public GlobalObjects(){
		if (srlist == null){
			srlist = new ArrayList<String>();
		}
		rdata = new RedditData();
	}
	// cached data
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
		//System.out.println("Using cached subreddits");
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
