package com.example.reddinator;

import java.util.ArrayList;

import android.app.Activity;
import android.app.ListActivity;

public class SubredditSelect extends ListActivity {
	private ArrayList sreddits;
	void onCreate(){
		setContentView(R.layout.selectsubreddit);
		// add predefined subreddits to arraylist (will load saved/personal subreddits later)
		sreddits = new ArrayList();
		this.findViewById(R.id.srlist);
	}
	
}
