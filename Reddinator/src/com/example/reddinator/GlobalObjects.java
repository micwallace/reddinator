package com.example.reddinator;

import java.util.ArrayList;

import android.app.Application;

public class GlobalObjects extends Application {
	private ArrayList<String> srlist;
	public GlobalObjects(){
		srlist = new ArrayList<String>();
	}
	public boolean isSrlistCached(){
		if (!srlist.isEmpty()){
			return true;
		}
		return false;
	}
	public void putSrList(ArrayList<String> list){
		srlist = list;
	}
	public ArrayList<String> getSrList(){
		return srlist;
	}
}
