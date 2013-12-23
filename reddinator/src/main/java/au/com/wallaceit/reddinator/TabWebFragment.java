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

import android.annotation.SuppressLint;
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

public class TabWebFragment extends Fragment {
    /** (non-Javadoc)
     * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
     */
	private Context mContext;
	public WebView mWebView;
	private boolean mFirstTime = true;
	private LinearLayout ll;
    //private Bundle WVState;
    public View mFullSView;
    private LinearLayout mTabcontainer;
    private FrameLayout mVideoFrame;
    private WebChromeClient.CustomViewCallback mFullSCallback;
    public WebChromeClient mChromeClient;
    private Activity mActivity;

	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
       	//mWebView.restoreState(savedInstanceState);
    }

    @SuppressLint("SetJavaScriptEnabled")
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	mContext = this.getActivity();
    	if (container == null) {
            return null;
        }
        if (mFirstTime){
        	// get shared preferences
        	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity().getApplicationContext());
        	// work out the url this instance should load
        	boolean commentswv = false;
        	if (this.getArguments() != null){
        		commentswv = this.getArguments().getBoolean("loadcom", false);
        	}

            int fontsize;
            String url;
        	if (commentswv){
        		url = "http://reddit.com"+getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK)+".compact";
        		fontsize = Integer.parseInt(prefs.getString("commentfontpref", "22"));
        	} else {
        		url = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_URL);
        		fontsize = Integer.parseInt(prefs.getString("contentfontpref", "18"));
        	}
        	// setup progressbar
        	mActivity = this.getActivity();
        	mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
        	ll = (LinearLayout)inflater.inflate(R.layout.tab1, container, false);
        	mWebView = (WebView) ll.findViewById(R.id.webView1);
        	// fixes for webview not taking keyboard input on some devices
        	mWebView.requestFocus(View.FOCUS_DOWN);
        	mWebView.setOnTouchListener(new View.OnTouchListener() {
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
        	mWebView.getSettings().setJavaScriptEnabled(true); // enable ecmascript
        	mWebView.getSettings().setSupportZoom(true);
        	mWebView.getSettings().setUseWideViewPort(true);
        	mWebView.getSettings().setBuiltInZoomControls(true);
        	mWebView.getSettings().setDisplayZoomControls(true);
        	mWebView.getSettings().setDefaultFontSize(fontsize);
        	mChromeClient = newchromeclient;
        	mWebView.setWebChromeClient(mChromeClient);
        	mWebView.setWebViewClient(new WebViewClient());
        	mWebView.loadUrl(url);
        	mFirstTime = false;
        	//System.out.println("Created fragment");
        } else {
        	((ViewGroup) ll.getParent()).removeView(ll);
        }
        
        return ll;
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
       super.onSaveInstanceState(outState);
       //mWebView.saveState(outState);
    }
    @Override
    public void onPause(){
    	super.onPause();
    	//mWebView.saveState(WVState);
    }
    // web chrome client
    WebChromeClient newchromeclient = new WebChromeClient(){
        public void onProgressChanged(WebView view, int progress){
        	//Make the bar disappear after URL is loaded, and changes string to Loading...
        	mActivity.setTitle("Loading...");
        	mActivity.setProgress(progress * 100); //Make the bar disappear after URL is loaded
        	// Return the app name after finish loading
            if(progress == 100){
            	mActivity.setTitle(R.string.app_name);
          	}
        }
        FrameLayout.LayoutParams LayoutParameters = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            // if a view already exists then immediately terminate the new one
            if (mFullSView != null) {
                callback.onCustomViewHidden();
                return;
            }
            // get main view and hide
            mTabcontainer = (LinearLayout) ((Activity) mContext).findViewById(R.id.redditview);
            mTabcontainer.setVisibility(View.GONE);
            // create custom view to show
            mVideoFrame = new FrameLayout(mContext);
            mVideoFrame.setLayoutParams(LayoutParameters);
            mVideoFrame.setBackgroundResource(android.R.color.black);
            mVideoFrame.addView(view);
            view.setLayoutParams(LayoutParameters);
            mFullSView = view;
            mFullSCallback = callback;
            // hide actionbar
            mActivity.getActionBar().hide();
            // set fullscreen
            ((Activity) mContext).getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            mVideoFrame.setVisibility(View.VISIBLE);
            ((Activity) mContext).setContentView(mVideoFrame);
        }

        @Override
        public void onHideCustomView() {
            if (mFullSView != null) {
                // Hide the custom view.
                mFullSView.setVisibility(View.GONE);
                // Remove the custom view from its container.  
                mVideoFrame.removeView(mFullSView);
                mFullSView = null;
                mVideoFrame.setVisibility(View.GONE);
                mFullSCallback.onCustomViewHidden();
                // Show the content view.
                mActivity.getActionBar().show();
                // remove fullscreen
                ((Activity) mContext).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                mTabcontainer.setVisibility(View.VISIBLE);
                ((Activity) mContext).setContentView(mTabcontainer);
            }
        }
    };
}
