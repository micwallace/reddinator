package com.example.reddinator;

import java.util.ArrayList;

import android.app.Application;

public class GlobalObjects extends Application {
	private ArrayList<String> srlist;
	//public RedditData redditdata;
	public GlobalObjects(){
		srlist = new ArrayList<String>();
		//redditdata = new RedditData();
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
}
