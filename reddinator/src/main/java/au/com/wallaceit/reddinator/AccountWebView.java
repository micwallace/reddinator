package au.com.wallaceit.reddinator;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Created by michael on 6/02/14.
 */
public class AccountWebView extends Activity {
    WebView wv;
    WebViewClient wvclient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.webview);
        // set and load webview
        wv = (WebView) findViewById(R.id.webView);
        wvclient = new WebViewClient();
        wv.setWebViewClient(wvclient);
        wv.loadUrl("http://www.reddit.com/message/inbox.compact");
    }
}
