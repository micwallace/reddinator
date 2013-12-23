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
import android.app.Application;

public class GlobalObjects extends Application {
	private ArrayList<String> mSubredditList;
	static int LOADTYPE_LOAD = 0;
	static int LOADTYPE_LOADMORE = 1;
	static int LOADTYPE_REFRESH_VIEW = 3;
	private int loadtype = 0; // tells the service what to do when notifyAppDataChanged is fired
	private boolean bypasscache = false; // tells the factory to bypass the cache when creating a new remoteviewsfacotry
	public RedditData mRedditData;
	//public RedditData redditdata;
	public GlobalObjects(){
		if (mSubredditList == null){
			mSubredditList = new ArrayList<String>();
		}
		mRedditData = new RedditData();
	}
	// cached data
	public boolean isSrlistCached(){
		if (!mSubredditList.isEmpty()){
			return true;
		}
		return false;
	}
	public void putSrList(ArrayList<String> list){
		mSubredditList.clear();
		mSubredditList.addAll(list);
	}
	public ArrayList<String> getSrList(){
		return mSubredditList;
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
	public void setRefreshView(){
		loadtype = 3;
	}
	// data cache functions
	public boolean getBypassCache(){
		return bypasscache;
	}
	public void setBypassCache(boolean bypassed){
		bypasscache = bypassed;
	}
}
