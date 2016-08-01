/* Copyright 2013 Michael Boyde Wallace (http://wallaceit.com.au)
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
 *
 * Created by michael on 27/03/16.
 */

package au.com.wallaceit.reddinator.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;

public class HtmlDialog extends AlertDialog {
    public static HtmlDialog init(Context context, String title, String html){
        HtmlDialog dialog = new HtmlDialog(context, title, html);
        dialog.show();
        return dialog;
    }

    public HtmlDialog(Context context, String title, String html) {
        super(context, R.style.HtmlDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_html, null);
        WebView wv = (WebView) view.findViewById(R.id.webView);
        setTitle(title);
        setView(view);
        setCancelable(true);
        wv.setWebViewClient(new NoNavClient());
        wv.loadData(html, "text/html", "UTF-8");
        setCanceledOnTouchOutside(true);
    }

    class NoNavClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.indexOf("file://") == 0) { // fix for relative links
                url = url.replace("file://", "https://www.reddit.com");
            }
            ((Reddinator) getContext().getApplicationContext()).handleLink(getContext(), url);
            return true;
        }
    }
}
