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
