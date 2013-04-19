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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

public class TabWebFragment extends Fragment {
    /** (non-Javadoc)
     * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
     */
	private Context context;
	public WebView wv;
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
	public View fullsview;
	private LinearLayout tabcontainer;
	private FrameLayout videoframe;
	private WebChromeClient.CustomViewCallback fullscallback;
	public WebChromeClient chromeclient;
	private Activity act;
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	context = this.getActivity();
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
        	act = this.getActivity();
        	act.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
        	ll = (LinearLayout)inflater.inflate(R.layout.tab1, container, false);
        	wv = (WebView) ll.findViewById(R.id.webView1);
        	// fixes for webview not taking keyboard input on some devices
        	wv.requestFocus(View.FOCUS_DOWN);
        	wv.setOnTouchListener(new View.OnTouchListener() { 
        		@Override
        		public boolean onTouch(View v, MotionEvent event) {
        		           switch (event.getAction()) { 
        		               case MotionEvent.ACTION_DOWN: 
        		               case MotionEvent.ACTION_UP: 
        		                   if (!v.hasFocus()) {
        		                       v.requestFocus(); 
        		                   } 
        		                   break;
        		           } 
        		           return false; 
        		}
        	});
        	wv.getSettings().setJavaScriptEnabled(true); // enable ecmascript
        	wv.getSettings().setSupportZoom(true);
        	wv.getSettings().setUseWideViewPort(true);
        	wv.getSettings().setBuiltInZoomControls(true);
        	wv.getSettings().setDisplayZoomControls(true);
        	wv.getSettings().setDefaultFontSize(fontsize);
        	chromeclient = newchromeclient;
        	wv.setWebChromeClient(chromeclient);
        	wv.setWebViewClient(new WebViewClient());
        	wv.loadUrl(url);
        	firsttime = false;
        	//System.out.println("Created fragment");
        } else {
        	((ViewGroup) ll.getParent()).removeView(ll);
        }
        
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
    // web chrome client
    WebChromeClient newchromeclient = new WebChromeClient(){
        public void onProgressChanged(WebView view, int progress){
        	//Make the bar disappear after URL is loaded, and changes string to Loading...
        	act.setTitle("Loading...");
        	act.setProgress(progress * 100); //Make the bar disappear after URL is loaded
        	// Return the app name after finish loading
            if(progress == 100){
            	act.setTitle(R.string.app_name);
          	}
        }
        FrameLayout.LayoutParams LayoutParameters = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            // if a view already exists then immediately terminate the new one
            if (fullsview != null) {
                callback.onCustomViewHidden();
                return;
            }
            // get main view and hide
            tabcontainer = (LinearLayout) ((Activity) context).findViewById(R.id.redditview);
            tabcontainer.setVisibility(View.GONE);
            // create custom view to show
            videoframe = new FrameLayout(context);
            videoframe.setLayoutParams(LayoutParameters);
            videoframe.setBackgroundResource(android.R.color.black);
            videoframe.addView(view);
            view.setLayoutParams(LayoutParameters);
            fullsview = view;
            fullscallback = callback;
            // hide actionbar
            act.getActionBar().hide();
            // set fullscreen
            ((Activity) context).getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            videoframe.setVisibility(View.VISIBLE);
            ((Activity) context).setContentView(videoframe);
        }

        @Override
        public void onHideCustomView() {
            if (fullsview == null) {
                return;
            } else {
                // Hide the custom view.  
                fullsview.setVisibility(View.GONE);
                // Remove the custom view from its container.  
                videoframe.removeView(fullsview);
                fullsview = null;
                videoframe.setVisibility(View.GONE);
                fullscallback.onCustomViewHidden();
                // Show the content view.
                act.getActionBar().show();
                // remove fullscreen
                ((Activity) context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                tabcontainer.setVisibility(View.VISIBLE);
                ((Activity) context).setContentView(tabcontainer);
            }
        }
    };
}
