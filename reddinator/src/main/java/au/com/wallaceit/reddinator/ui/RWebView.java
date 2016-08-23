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

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.tasks.LoadImageBitmapTask;

public class RWebView extends android.webkit.WebView {

    private static final int ID_SHARELINK = 1;
    private static final int ID_COPYLINK = 2;
    private static final int ID_OPENLINK = 3;
    private static final int ID_SHAREIMAGE = 4;
    private static final int ID_SAVEIMAGE = 5;

    public RWebView(Context context) {
        this(context, null);
    }

    public RWebView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.getSettings().setDefaultTextEncodingName("utf-8");
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
                        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText(result.getExtra(), result.getExtra());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(getContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                        return true;

                    case ID_OPENLINK:
                        intent = new Intent(Intent.ACTION_VIEW, Uri.parse(result.getExtra()));
                        getContext().startActivity(intent);
                        return true;

                    case ID_SHARELINK:
                        intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_SUBJECT, "Shared from Reddinator");
                        intent.putExtra(Intent.EXTRA_TEXT, result.getExtra());
                        getContext().startActivity(intent);
                        return true;

                    // This stuff needs additional permissions so saving it for next version
                    case ID_SAVEIMAGE:
                        downloadFile(result.getExtra());
                        return true;

                    case ID_SHAREIMAGE:
                        shareImage(result.getExtra());
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
            menu.add(0, ID_SHAREIMAGE, 0, "Share Image").setOnMenuItemClickListener(handler);
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

    private String callbackUrl = null;

    public void downloadFile(String url) {
        // Check permissions for android M
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) {
                callbackUrl = url;
                ((Activity) getContext()).requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                Toast.makeText(getContext(), "Storage permission is required to download files.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        DownloadManager mgr = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        Uri downloadUri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(downloadUri);
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverRoaming(false)
                .setTitle(appendImageExtensionIfNeeded(downloadUri.getLastPathSegment()))
                .setDescription("Reddinator image download")
                .setVisibleInDownloadsUi(true)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(), downloadUri.getLastPathSegment());
        mgr.enqueue(request);
    }

    public void onDownloadPermissionSuccess(){
        if (callbackUrl!=null){
            downloadFile(callbackUrl);
            callbackUrl = null;
        }
    }

    public void shareImage(final String url){
        final ProgressDialog dialog = ProgressDialog.show(getContext(), "Downloading", "Please wait...", true);
        new LoadImageBitmapTask(url, new LoadImageBitmapTask.ImageCallback() {
            @Override
            public void run() {
                dialog.dismiss();
                if (image!=null){
                    // save file
                    String filename = "share-"+appendImageExtensionIfNeeded(Uri.parse(url).getLastPathSegment());
                    File file = new File(getContext().getCacheDir().getPath() + Reddinator.THUMB_CACHE_DIR + filename);
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(file);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    image.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    // send share intent
                    Uri contentUri = FileProvider.getUriForFile(getContext(), "au.com.wallaceit.reddinator.fileprovider", file);
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // temp permission for receiving app to read this file
                    shareIntent.setType("image/*");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    getContext().startActivity(Intent.createChooser(shareIntent, "Choose an app"));
                } else {
                    Toast.makeText(getContext(), "Image failed to download", Toast.LENGTH_LONG).show();
                }
            }
        }).execute();
    }

    private String appendImageExtensionIfNeeded(String filename){
        if (filename.indexOf(".")>filename.length()-5){
            return filename+".jpg";
        }
        return filename;
    }
}
