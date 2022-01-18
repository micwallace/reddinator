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
 * Created by michael on 9/08/16.
 */
package au.com.wallaceit.reddinator.core;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.Toast;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;

public class Utilities {

    public static Bitmap getFontBitmap(Context context, String text, int color, int fontSize, int[] shadow) {
        fontSize = convertDiptoPix(context, fontSize);
        int pad = (fontSize / 9);
        Paint paint = new Paint();
        Typeface typeface = Typeface.createFromAsset(context.getAssets(), "css/fonts/fontawesome-webfont.ttf");
        paint.setAntiAlias(true);
        paint.setTypeface(typeface);
        paint.setColor(color);
        paint.setTextSize(fontSize);
        paint.setShadowLayer(shadow[0], shadow[1], shadow[2], shadow[3]);

        int textWidth = (int) (paint.measureText(text) + pad * 2);
        int height = (int) (fontSize / 0.75);
        Bitmap bitmap = Bitmap.createBitmap(textWidth, height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(text, (float) pad, fontSize, paint);
        return bitmap;
    }

    public static int getActionbarIconColor(){
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Color.parseColor("#A5A5A5");
        }
        return Color.parseColor("#DBDBDB");
    }

    public static String getScoreText(int score){
        // Since reddit changes their scoring system, we need to abbreviate high scores. eg. 17.3k
        if (score>10000)
            return new BigDecimal((score/1000)).setScale(1, BigDecimal.ROUND_HALF_UP).toString()+"k";
        return String.valueOf(score);
    }

    private static int convertDiptoPix(Context context, float dip) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, context.getResources().getDisplayMetrics());
    }

    public static PackageInfo getPackageInfo(Context context){
        PackageInfo pInfo = null;
        try {
            pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return pInfo;
    }

    // compares a semantic version number without build
    public static boolean compareVersionWithoutBuild(String version1, String version2){
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");
        return parts1.length > 1 && parts2.length > 1 && parts1[0].equals(parts2[0]) && parts1[1].equals(parts2[1]);
    }

    public static String getImageCacheSize(Context context){
        File cacheDir = new File(context.getCacheDir().getPath() + Reddinator.IMAGE_CACHE_DIR);
        return Formatter.formatShortFileSize(context, dirSize(cacheDir));
    }

    public static String getFeedDataSize(Context context){
        File cacheDir = new File(context.getApplicationInfo().dataDir + Reddinator.FEED_DATA_DIR);
        return Formatter.formatShortFileSize(context, dirSize(cacheDir));
    }

    /**
     * Return the size of a directory in bytes
     */
    private static long dirSize(File dir) {
        if (dir.exists()) {
            long result = 0;
            File[] fileList = dir.listFiles();
            for (File aFileList : fileList) {
                // Recursive call if it's a directory
                if (aFileList.isDirectory()) {
                    result += dirSize(aFileList);
                } else {
                    // Sum the file size in bytes
                    result += aFileList.length();
                }
            }
            return result; // return the file size
        }
        return 0;
    }

    public static boolean isFeedPathMulti(String feedUrl){
        return feedUrl.matches("(.*reddit.com)?/user/[^/]*/m/[^/]*/?");
    }

    public static boolean isFeedPathDomain(String feedUrl){
        return feedUrl.matches("(.*reddit.com)?/domain/[^/]*/?");
    }

    public static boolean isImageUrl(String url) {
        if (url == null)
            return false;
        // Check image extension
        if (hasImageExtension(url))
            return true;
        // Check for i.reddituploads.com images
        return url.toLowerCase().matches("(https?://(i.reddituploads.com/.*)$)") || isImgurUrl(url) || isGfycatUrl(url);
    }

    public static boolean isImgurUrl(String url){
        if (url == null)
            return false;
        // Check for imgur url without file extension (should not be album)
        return url.toLowerCase().matches("(https?://.*(imgur.com/(?!gallery/|a/).*)$)");
    }

    public static boolean isGfycatUrl(String url){
        if (url == null)
            return false;
        // Check for imgur url without file extension (should not be album)
        return url.toLowerCase().matches("(https?://.*(gfycat.com/[^/]*)$)");
    }

    public static boolean hasImageExtension(String url){
        if (url == null)
            return false;
        return url.toLowerCase().matches("([^\\s]+(\\.(?i)(jpe?g|png|gif?v|bmp))$)");
    }

    public static void executeJavascriptInWebview(WebView webView, String javascript){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(javascript, null);
        } else {
            webView.loadUrl("javascript:"+javascript);
        }
    }

    public static ColorMatrixColorFilter getColorFilterFromColor(int color, int darken){
        float r = (Color.red(color)+darken) / 255f;
        float g = (Color.green(color)+darken) / 255f;
        float b = (Color.blue(color)+darken) / 255f;
        ColorMatrix cm = new ColorMatrix(new float[] {
                // Change red channel
                r, 0, 0, 0, 0,
                // Change green channel
                0, g, 0, 0, 0,
                // Change blue channel
                0, 0, b, 0, 0,
                // Keep alpha channel
                0, 0, 0, 1, 0,
        });
        return new ColorMatrixColorFilter(cm);
    }

    public static Spanned fromHtml(String html){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            //noinspection deprecation
            return Html.fromHtml(html);
        }
    }

    public static void updateActionbarOverflowIcon(final Activity context, final int iconColor){
        final ViewGroup decorView = (ViewGroup) context.getWindow().getDecorView();
        decorView.postDelayed(new Runnable() {
            @Override
            public void run() {
                final ArrayList<View> outViews = new ArrayList<>();
                @SuppressLint("PrivateResource")
                String overflowDescription = context.getString(R.string.abc_action_menu_overflow_description);
                findViewsWithText(outViews, decorView, overflowDescription);
                if (outViews.isEmpty()) {
                    return;
                }
                ImageView overflow = (ImageView) outViews.get(0);
                IconDrawable iconDrawable = new IconDrawable(context, Iconify.IconValue.fa_bars).color(iconColor).sizeDp(28);
                overflow.setImageDrawable(iconDrawable);
            }
            private void findViewsWithText(ArrayList<View> outViews, ViewGroup parent, String targetDescription) {
                if (parent == null || TextUtils.isEmpty(targetDescription)) {
                    return;
                }
                final int count = parent.getChildCount();
                for (int i = 0; i < count; i++) {
                    final View child = parent.getChildAt(i);
                    final CharSequence desc = child.getContentDescription();
                    if (!TextUtils.isEmpty(desc) && targetDescription.equals(desc.toString())) {
                        outViews.add(child);
                    } else if (child instanceof ViewGroup && child.getVisibility() == View.VISIBLE) {
                        findViewsWithText(outViews, (ViewGroup) child, targetDescription);
                    }
                }
            }
        }, 50);
    }

    public static int voteDirectionToInt(String vote){
        switch (vote) {
            case "null":
                return 0;
            case "true":
                return 1;
            case "false":
                return -1;
            default:
                return 0;
        }
    }

    public static String voteDirectionToString(int vote){
        switch (vote) {
            case 0:
                return "null";
            case 1:
                return "true";
            case -1:
                return "false";
            default:
                return "null";
        }
    }

    public static AlertDialog showPostShareDialog(final Context context, final String postUrl, final String postPermalink) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.share_url))
            .setNegativeButton(context.getString(R.string.content), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    intentActionShareText(context, postUrl);
                }
            }).setPositiveButton(context.getString(R.string.both), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    intentActionShareText(context, postUrl+"\nhttps://reddit.com" + postPermalink);
                }
            })
            .setNeutralButton(context.getString(R.string.reddit_page), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    intentActionShareText(context, "https://reddit.com" + postPermalink);
                }
            });
        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }

    public static void intentActionView(Context context, String url) {
        Intent openintent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        openintent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(openintent);
    }

    public static void intentActionShareText(Context context, String txt) {
        Intent sendintent = new Intent(Intent.ACTION_SEND);
        sendintent.setAction(Intent.ACTION_SEND);
        sendintent.putExtra(Intent.EXTRA_TEXT, txt);
        sendintent.setType("text/plain");
        context.startActivity(Intent.createChooser(sendintent, context.getString(R.string.share_with)));
    }

    public static void showApiErrorToastOrDialog(final Context context, Exception ex){
        int errorCode = (ex instanceof RedditData.RedditApiException) ? ((RedditData.RedditApiException) ex).getHttpErrorCode() : 0;
        if (errorCode >= 500 && errorCode < 600){
            new AlertDialog.Builder(context)
                .setTitle(R.string.error)
                .setMessage(ex.getMessage()+context.getString(R.string.reddit_server_error_message))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse("http://www.redditstatus.com/"));
                        context.startActivity(intent);
                    }
                }).show().setCanceledOnTouchOutside(true);
            return;
        }
        Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
    }

    /*
    * See https://developer.android.com/topic/performance/graphics/load-bitmap
    * */
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /*
     * See https://developer.android.com/topic/performance/graphics/load-bitmap
     * */
    public static Bitmap decodeSampledBitmapFromFile(String fileurl, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(fileurl, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(fileurl, options);
    }
}
