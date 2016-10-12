package au.com.wallaceit.reddinator.core;

/*
 * Copyright 2016 Michael Boyde Wallace (http://wallaceit.com.au)
 * This file is part of reddinator.
 *
 * reddinator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reddinator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reddinator (COPYING). If not, see <http://www.gnu.org/licenses/>.
 *
 * Created by michael on 12/10/16.
 */

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.ContextThemeWrapper;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.service.WidgetCommon;

public class ThemeHelper {

    public interface ThemeInstallInterface {
        void onThemeResult(boolean updateTheme);
    }

    public static void handleThemeInstall(final Context context, final Reddinator global, final ThemeInstallInterface callback, JSONObject postData, final Runnable openPostRunnable){
        // extract and parse json from theme
        try {
            String postText = postData.getString("selftext");
            Pattern pattern = Pattern.compile("reddinator_theme=(.*\\}\\})");
            Matcher matcher = pattern.matcher(postText);

            if (matcher.find()){
                final JSONObject themeJson = new JSONObject(matcher.group(1));

                AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogStyle);
                builder.setTitle(R.string.install_theme_title)
                        .setMessage(R.string.install_theme_message)
                        .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (global.mThemeManager.importTheme(themeJson)){
                                    Toast.makeText(context, R.string.theme_install_success, Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(context, R.string.theme_load_error, Toast.LENGTH_LONG).show();
                                }
                                callback.onThemeResult(false);
                            }
                        })
                        .setNeutralButton(R.string.preview, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                if (global.mThemeManager.setPreviewTheme(themeJson)){
                                    //refreshTheme();
                                    WidgetCommon.refreshAllWidgetViews(global);
                                    new AlertDialog.Builder(context, R.style.AlertDialogStyle)
                                            .setTitle(R.string.theme_preview)
                                            .setMessage(R.string.theme_preview_applied_message)
                                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                                @Override
                                                public void onCancel(DialogInterface dialog) {
                                                    callback.onThemeResult(true);
                                                }
                                            })
                                            .show().setCanceledOnTouchOutside(true);
                                } else {
                                    Toast.makeText(context, R.string.theme_load_error, Toast.LENGTH_LONG).show();
                                    callback.onThemeResult(false);
                                }
                            }
                        });
                if (openPostRunnable!=null)
                    builder.setNegativeButton(R.string.view_comments_noicon, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    openPostRunnable.run();
                                }
                            });
                builder.show().setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        callback.onThemeResult(false);
                    }
                });
                return;
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        Toast.makeText(context, R.string.theme_load_error, Toast.LENGTH_LONG).show();
    }
}
