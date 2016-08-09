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
package au.com.wallaceit.reddinator.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.service.WidgetProvider;
import au.com.wallaceit.reddinator.tasks.LoadSubredditInfoTask;
import au.com.wallaceit.reddinator.ui.HtmlDialog;

public class WidgetMenuDialogActivity extends Activity implements PopupMenu.OnMenuItemClickListener, View.OnClickListener, LoadSubredditInfoTask.Callback {
    Reddinator global;
    SharedPreferences prefs;
    int widgetId;
    boolean menuShown = false;
    boolean menuSelected = false;
    PopupMenu popupMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        global = (Reddinator) getApplicationContext();
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.activity_transparent);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.width  = WindowManager.LayoutParams.MATCH_PARENT;
        getWindow().setAttributes(params);

        popupMenu = new PopupMenu(new ContextThemeWrapper(this, R.style.PopupMenuStyle), findViewById(R.id.menu_anchor));
        popupMenu.getMenuInflater().inflate(R.menu.feed_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this);

        Menu menu = popupMenu.getMenu();

        try {
            Field[] fields = popupMenu.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popupMenu);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper
                            .getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod(
                            "setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            popupMenu.setOnDismissListener(new PopupMenu.OnDismissListener() {
                @Override
                public void onDismiss(PopupMenu menu) {
                   if (!menuSelected){
                       WidgetMenuDialogActivity.this.finish();
                   }
                }
            });
        }

        int iconColor = Utilities.getActionbarIconColor();
        int inboxColor = global.mRedditData.getInboxCount()>0? Color.parseColor("#E06B6C"): iconColor;
        MenuItem messageIcon = (menu.findItem(R.id.menu_inbox));
        messageIcon.setIcon(new IconDrawable(this, Iconify.IconValue.fa_envelope).color(inboxColor).actionBarSize());
        MenuItem sortItem = (menu.findItem(R.id.menu_sort));
        sortItem.setIcon(new IconDrawable(this, Iconify.IconValue.fa_sort).color(iconColor).actionBarSize());
        sortItem.setTitle(getString(R.string.sort_label) + " " + prefs.getString("sort-"+widgetId, "hot"));
        MenuItem sidebarIcon = menu.findItem(R.id.menu_sidebar);
        sidebarIcon.setIcon(new IconDrawable(this, Iconify.IconValue.fa_book).color(iconColor).actionBarSize());
        if (!global.getSubredditManager().isFeedMulti(widgetId)) {
            sidebarIcon.setVisible(true);
        }
        (menu.findItem(R.id.menu_submit)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_pencil).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_feedprefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_list_alt).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_thememanager)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_cogs).color(iconColor).actionBarSize());
        MenuItem accountItem = (menu.findItem(R.id.menu_account));
        if (global.mRedditData.isLoggedIn())
            accountItem.setTitle(global.mRedditData.getUsername());
        accountItem.setIcon(new IconDrawable(this, Iconify.IconValue.fa_reddit_square).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_search)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_search).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_prefs)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_wrench).color(iconColor).actionBarSize());
        (menu.findItem(R.id.menu_about)).setIcon(new IconDrawable(this, Iconify.IconValue.fa_info_circle).color(iconColor).actionBarSize());

        findViewById(R.id.activity_transparent).setOnClickListener(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && !menuShown) {
            menuShown = true;
            popupMenu.show();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        menuSelected = true;
        switch (item.getItemId()) {

            case R.id.menu_sort:
                showSortDialog();
                break;

            case R.id.menu_sidebar:
                openSidebar();
                break;

            case R.id.menu_inbox:
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(this, false);
                    Toast.makeText(this, "Reddit login required", Toast.LENGTH_LONG).show();
                    this.finish();
                } else {
                    Intent inboxIntent = new Intent(this, MessagesActivity.class);
                    if (global.mRedditData.getInboxCount() > 0) {
                        inboxIntent.setAction(MessagesActivity.ACTION_UNREAD);
                    }
                    startActivityAndFinish(inboxIntent);
                }
                break;

            case R.id.menu_account:
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(this, false);
                    Toast.makeText(this, "Reddit login required", Toast.LENGTH_LONG).show();
                    this.finish();
                } else {
                    Intent accnIntent = new Intent(this, AccountActivity.class);
                    startActivityAndFinish(accnIntent);
                }
                break;

            case R.id.menu_search:
                Intent searchIntent = new Intent(this, SearchActivity.class);
                if (!global.getSubredditManager().isFeedMulti(widgetId))
                    searchIntent.putExtra("feed_path", global.getSubredditManager().getCurrentFeedPath(widgetId));
                startActivityAndFinish(searchIntent);
                break;

            case R.id.menu_submit:
                Intent submitIntent = new Intent(this, SubmitActivity.class);
                startActivityAndFinish(submitIntent);
                break;

            case R.id.menu_feedprefs:
                showFeedPrefsDialog();
                break;

            case R.id.menu_thememanager:
                Intent intent = new Intent(this, ThemesActivity.class);
                startActivityAndFinish(intent);
                break;

            case R.id.menu_prefs:
                Intent intent2 = new Intent(this, PrefsActivity.class);
                intent2.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                startActivityAndFinish(intent2);
                break;

            case R.id.menu_about:
                Reddinator.showInfoDialog(this, true).setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        WidgetMenuDialogActivity.this.finish();
                    }
                });
                break;
        }
        return false;
    }

    private void showSortDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(WidgetMenuDialogActivity.this);
        builder.setTitle(getString(R.string.select_sort));
        builder.setItems(R.array.reddit_sorts, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor prefsedit = prefs.edit();
                String sort = "hot"; // default if fails
                // find index
                switch (which) {
                    case 0:
                        sort = "hot";
                        break;
                    case 1:
                        sort = "new";
                        break;
                    case 2:
                        sort = "rising";
                        break;
                    case 3:
                        sort = "controversial";
                        break;
                    case 4:
                        sort = "top";
                        break;
                }
                prefsedit.putString("sort-" + widgetId, sort);
                prefsedit.apply();
                // set new text in button
                WidgetProvider.showLoaderAndUpdate(WidgetMenuDialogActivity.this, widgetId, false);
                dialog.dismiss();
                WidgetMenuDialogActivity.this.finish();
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                WidgetMenuDialogActivity.this.finish();
            }
        });
        builder.show().setCanceledOnTouchOutside(true);
    }

    private boolean needsUpdate = false;
    private void showFeedPrefsDialog(){
        needsUpdate = false;
        final CharSequence[] names = {getString(R.string.image_previews), getString(R.string.thumbnails), getString(R.string.thumbnails_on_top), getString(R.string.hide_post_info)};
        final String widgetIdStr = String.valueOf(widgetId);
        // previews disabled by default in widgets due to listview dynamic height issue (causes views to jump around when scrolling up)
        final boolean[] initvalue = {prefs.getBoolean("imagepreviews-" + widgetIdStr, widgetId == 0), prefs.getBoolean("thumbnails-" + widgetIdStr, true), prefs.getBoolean("bigthumbs-" + widgetIdStr, false), prefs.getBoolean("hideinf-" + widgetIdStr, false)};
        AlertDialog.Builder builder = new AlertDialog.Builder(WidgetMenuDialogActivity.this);
        builder.setTitle(getString(R.string.widget_feed_prefs));
        builder.setMultiChoiceItems(names, initvalue, new DialogInterface.OnMultiChoiceClickListener() {
            public void onClick(DialogInterface dialogInterface, int item, boolean state) {
                SharedPreferences.Editor prefsedit = prefs.edit();
                switch (item) {
                    case 0:
                        prefsedit.putBoolean("imagepreviews-" + widgetId, state);
                        break;
                    case 1:
                        prefsedit.putBoolean("thumbnails-" + widgetId, state);
                        break;
                    case 2:
                        prefsedit.putBoolean("bigthumbs-" + widgetId, state);
                        break;
                    case 3:
                        prefsedit.putBoolean("hideinf-" + widgetId, state);
                        break;
                }
                prefsedit.apply();
                needsUpdate = true;
            }
        });
        builder.setPositiveButton(getString(R.string.close), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                if (needsUpdate)
                    WidgetProvider.showLoaderAndUpdate(WidgetMenuDialogActivity.this, widgetId, false);
                WidgetMenuDialogActivity.this.finish();
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                WidgetMenuDialogActivity.this.finish();
            }
        });
        builder.show().setCanceledOnTouchOutside(true);
    }

    private ProgressDialog sidebarProg;
    private void openSidebar(){
        sidebarProg = new ProgressDialog(this);
        sidebarProg.setIndeterminate(true);
        sidebarProg.setTitle(R.string.loading);
        sidebarProg.setMessage(getString(R.string.loading_sidebar));
        sidebarProg.show();
        new LoadSubredditInfoTask(global, this).execute(global.getSubredditManager().getCurrentFeedName(widgetId));
    }

    @Override
    public void onSubredditInfoLoaded(JSONObject result, RedditData.RedditApiException exception) {
        if (result!=null){
            try {
                String html = "&lt;p&gt;"+result.getString("subscribers")+" readers&lt;br/&gt;"+result.getString("accounts_active")+" users here now&lt;/p&gt;";
                html += result.getString("description_html");
                HtmlDialog.init(this, global.getSubredditManager().getCurrentFeedPath(widgetId), Utilities.fromHtml(html).toString())
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            WidgetMenuDialogActivity.this.finish();
                        }
                    });
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Error loading sidebar: "+exception.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (sidebarProg!=null)
            sidebarProg.dismiss();
    }

    private void startActivityAndFinish(Intent intent){
        startActivity(intent);
        this.finish();
    }

    // click outside popupMenu, close activity
    @Override
    public void onClick(View v) {
        this.finish();
    }
}
