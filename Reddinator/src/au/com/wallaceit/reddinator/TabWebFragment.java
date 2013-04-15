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

import au.com.wallaceit.reddinator.R;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

public class TabWebFragment extends Fragment {
    /** (non-Javadoc)
     * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
     */
	private WebView wv;
	private boolean firsttime = true;
	private LinearLayout ll;
	private int fontsize;
	//private Bundle WVState;
	private String url;
	public void onCreated(Bundle savedInstanceState){
		//this.setRetainInstance(true);
	}
	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
       super.onActivityCreated(savedInstanceState);
       //wv.restoreState(savedInstanceState);
    }
	
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	if (container == null) {
            return null;
        }
        if (firsttime){
        	// get shared preferences
        	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity().getApplicationContext());
        	// work out the url this instance should load
        	boolean commentswv = false;
        	if (this.getArguments() != null){
        		commentswv = this.getArguments().getBoolean("loadcom", false);
        	}
        	if (commentswv){
        		url = "http://reddit.com"+getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK)+".compact";
        		fontsize = Integer.parseInt(prefs.getString("commentfontpref", "22"));
        	} else {
        		url = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_URL);
        		fontsize = Integer.parseInt(prefs.getString("contentfontpref", "18"));
        	}
        	// setup progressbar
        	final Activity act = this.getActivity();
        	act.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
        	ll = (LinearLayout)inflater.inflate(R.layout.tab1, container, false);
        	wv = (WebView) ll.findViewById(R.id.webView1);
        	wv.requestFocus(View.FOCUS_DOWN);
        	wv.getSettings().setJavaScriptEnabled(true);
        	//wv.getSettings().setDefaultZoom(ZoomDensity.FAR);
        	wv.getSettings().setSupportZoom(true);
        	wv.getSettings().setBuiltInZoomControls(true);
        	wv.getSettings().setDisplayZoomControls(true);
        	wv.getSettings().setDefaultFontSize(fontsize);
        	wv.setWebChromeClient(new WebChromeClient(){
                public void onProgressChanged(WebView view, int progress){
                	//Make the bar disappear after URL is loaded, and changes string to Loading...
                	act.setTitle("Loading...");
                	act.setProgress(progress * 100); //Make the bar disappear after URL is loaded
                	// Return the app name after finish loading
                    if(progress == 100){
                    	act.setTitle(R.string.app_name);
                  	}
                }
        	});
        	wv.setWebViewClient(new WebViewClient());
        	wv.loadUrl(url);
        	firsttime = false;
        } else {
        	//wv.restoreState(savedInstanceState);
        	((ViewGroup) ll.getParent()).removeView(ll);
        }
        System.out.println("Created fragment");
        return ll;
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
       super.onSaveInstanceState(outState);
       //wv.saveState(outState);
    }
    @Override
    public void onPause(){
    	super.onPause();
    	//wv.saveState(WVState);
    }
}
