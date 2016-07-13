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
 *
 * Created by michael on 13/07/16.
 */
package au.com.wallaceit.reddinator.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.widget.Toast;

import au.com.wallaceit.reddinator.R;

public class RWebView extends android.webkit.WebView {

    private Context context;
    private static final int ID_SHARELINK = 1;
    private static final int ID_COPYLINK = 2;
    private static final int ID_OPENLINK = 3;
    //private static final int ID_SHAREIMAGE = 4;
    private static final int ID_SAVEIMAGE = 5;

    public RWebView(Context context) {
        this(context, null);
    }

    public RWebView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.context = context;
    }

    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        super.onCreateContextMenu(menu);

        final HitTestResult result = getHitTestResult();

        MenuItem.OnMenuItemClickListener handler = new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {

                Intent intent;

                switch (item.getItemId()){
                    case ID_COPYLINK:
                        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText(result.getExtra(), result.getExtra());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                        return true;

                    case ID_OPENLINK:
                        intent = new Intent(Intent.ACTION_VIEW, Uri.parse(result.getExtra()));
                        context.startActivity(intent);
                        return true;

                    case ID_SHARELINK:
                        intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_SUBJECT, "Shared from Reddinator");
                        intent.putExtra(Intent.EXTRA_TEXT, result.getExtra());
                        context.startActivity(intent);
                        return true;

                }
                return false;
            }
        };

        if (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            // Menu options for an image.
            //set the header title to the image url
            menu.setHeaderTitle(result.getExtra());
            menu.add(0, ID_SAVEIMAGE, 0, "Save Image").setOnMenuItemClickListener(handler);
            //menu.add(0, ID_SHAREIMAGE, 0, "Share Image").setOnMenuItemClickListener(handler);
            menu.add(0, ID_OPENLINK, 0, "Open Link").setOnMenuItemClickListener(handler);
            menu.add(0, ID_SHARELINK, 0, "Share Link").setOnMenuItemClickListener(handler);
            menu.add(0, ID_COPYLINK, 0, "Copy Link").setOnMenuItemClickListener(handler);

        } else if (result.getType() == HitTestResult.SRC_ANCHOR_TYPE) {
            // Menu options for a hyperlink.
            //set the header title to the link url
            menu.setHeaderTitle(result.getExtra());
            menu.add(0, ID_OPENLINK, 0, "Open Link").setOnMenuItemClickListener(handler);
            menu.add(0, ID_SHARELINK, 0, "Share Link").setOnMenuItemClickListener(handler);
            menu.add(0, ID_COPYLINK, 0, "Copy Link").setOnMenuItemClickListener(handler);
        }
    }
}
