package com.example.reddinator;

import android.app.Activity;
import android.os.Bundle;
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
	private Bundle WVState;
	private String url;
	public void onCreated(Bundle savedInstanceState){
		this.setRetainInstance(true);
		
	}
	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
       super.onActivityCreated(savedInstanceState);
       wv.restoreState(savedInstanceState);
       
    }
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
    	url = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_URL);
    	
		System.out.println("URL is: "+url);
    		WVState = savedInstanceState;	
        if (firsttime){
        	final Activity act = this.getActivity();
        	act.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
        	ll = (LinearLayout)inflater.inflate(R.layout.tab1, container, false);
        	wv = (WebView) ll.findViewById(R.id.webView1);
        	//wv = new WebView(this.getActivity().getApplicationContext());
        	wv.getSettings().setJavaScriptEnabled(true);
        	wv.getSettings().setSupportZoom(true);
        	wv.getSettings().setBuiltInZoomControls(true);
        	wv.getSettings().setDisplayZoomControls(true);
        	wv.setWebChromeClient(new WebChromeClient(){
                public void onProgressChanged(WebView view, int progress)   
                {
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
        if (container == null) {
            // We have different layouts, and in one of them this
            // fragment's containing frame doesn't exist.  The fragment
            // may still be created from its saved state, but there is
            // no reason to try to create its view hierarchy because it
            // won't be displayed.  Note this is not needed -- we could
            // just run the code below, where we would create and return
            // the view hierarchy; it would just never be used.
            return null;
        }
        
        System.out.println("Created fragment");
        return ll;
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
       super.onSaveInstanceState(outState);
       wv.saveState(outState);
    }
    @Override
    public void onPause(){
    	super.onPause();
    	wv.saveState(WVState);
    }
}
