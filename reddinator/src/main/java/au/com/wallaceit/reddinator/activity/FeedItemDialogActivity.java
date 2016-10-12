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
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.IconTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.ThemeHelper;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.service.WidgetCommon;
import au.com.wallaceit.reddinator.tasks.HidePostTask;
import au.com.wallaceit.reddinator.tasks.SavePostTask;
import au.com.wallaceit.reddinator.tasks.SubscriptionEditTask;
import au.com.wallaceit.reddinator.tasks.WidgetVoteTask;

public class FeedItemDialogActivity extends Activity implements SubscriptionEditTask.Callback, ThemeHelper.ThemeInstallInterface {
    public static final String EXTRA_CURRENT_FEED_PATH = "feedPath";
    public static final String EXTRA_IS_THEME = "isTheme";
    public static final String EXTRA_POST_DATA = "postData";

    private Reddinator global;
    private Dialog dialog;
    private int widgetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        global = (Reddinator) getApplicationContext();
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.activity_item_dialog);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                close(0);
            }
        });
        // check if it is a self post and remove view domain option

        final ItemOptionsAdapter adapter = new ItemOptionsAdapter();
        ListView listview = (ListView) dialog.findViewById(R.id.opt_list);
        listview.setAdapter(adapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String key = adapter.getItemKey(position);
                String redditId;
                switch (key){
                    case "hide_post":
                        redditId = getIntent().getStringExtra(Reddinator.ITEM_ID);
                        int feedPos = getIntent().getIntExtra(Reddinator.ITEM_FEED_POSITION, 0);
                        if (global.mRedditData.isLoggedIn()){
                            new HidePostTask(FeedItemDialogActivity.this, false, null).execute(redditId);
                        } else {
                            global.getSubredditManager().addPostFilter(widgetId, redditId);
                        }
                        global.removePostFromFeed(widgetId, feedPos, redditId);
                        if (widgetId>0) {
                            WidgetCommon.hideLoaderAndRefreshViews(FeedItemDialogActivity.this, widgetId, false);
                        } else {
                            close(5); // tell main activity to refresh views
                            return;
                        }
                        break;
                    case "save_post":
                        redditId = getIntent().getStringExtra(Reddinator.ITEM_ID);
                        (new SavePostTask(FeedItemDialogActivity.this, widgetId>0, null)).execute("link", redditId);
                        break;
                    case "share_post":
                        Utilities.showPostShareDialog(
                                FeedItemDialogActivity.this,
                                getIntent().getStringExtra(Reddinator.ITEM_URL),
                                getIntent().getStringExtra(Reddinator.ITEM_PERMALINK))
                                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                    @Override
                                    public void onDismiss(DialogInterface dialog) {
                                        close(0);
                                    }
                                });
                        dialog.dismiss();
                        return;
                    case "open_post":
                        // open link in browser
                        Utilities.intentActionView(FeedItemDialogActivity.this, getIntent().getStringExtra(Reddinator.ITEM_URL));
                        break;
                    case "open_comments":
                        // open reddit comments page in browser
                        Utilities.intentActionView(FeedItemDialogActivity.this, "https://www.reddit.com" + getIntent().getStringExtra(Reddinator.ITEM_PERMALINK));
                        break;
                    case "view_subreddit":
                        // view subreddit of this item
                        String subreddit = getIntent().getStringExtra(Reddinator.ITEM_SUBREDDIT);
                        if (widgetId < 0) {
                            String feedPath = "/r/" + subreddit;
                            if (widgetId == -2) {
                                // If currently in search activity, open a temp feed on the main activity
                                global.openSubredditFeed(FeedItemDialogActivity.this, Reddinator.REDDIT_BASE_URL + feedPath);
                                close(0);
                            } else {
                                // replace current temporary feed in main activity
                                Intent intent = new Intent(FeedItemDialogActivity.this, MainActivity.class);
                                intent.putExtra(MainActivity.EXTRA_FEED_PATH, feedPath);
                                intent.putExtra(MainActivity.EXTRA_FEED_NAME, subreddit);
                                close(2, intent);
                            }
                        } else {
                            global.getSubredditManager().setFeedSubreddit(widgetId, subreddit, null);
                            if (widgetId > 0) {
                                WidgetCommon.showLoaderAndUpdate(FeedItemDialogActivity.this, widgetId, false);
                            } else {
                                close(2); // tell main activity to update
                                return;
                            }
                        }
                        break;
                    case "view_domain":
                        // view listings for the domain of this item
                        String domain = getIntent().getStringExtra(Reddinator.ITEM_DOMAIN);
                        if (widgetId < 0) {
                            String feedPath = "/domain/" + domain;
                            if (widgetId == -2) {
                                global.openSubredditFeed(FeedItemDialogActivity.this, Reddinator.REDDIT_BASE_URL + feedPath);
                                close(0);
                            } else {
                                Intent intent = new Intent(FeedItemDialogActivity.this, MainActivity.class);
                                intent.putExtra(MainActivity.EXTRA_FEED_PATH, feedPath);
                                intent.putExtra(MainActivity.EXTRA_FEED_NAME, domain);
                                close(2, intent);
                            }
                        } else {
                            global.getSubredditManager().setFeedDomain(widgetId, domain);
                            if (widgetId > 0) {
                                WidgetCommon.showLoaderAndUpdate(FeedItemDialogActivity.this, widgetId, false);
                            } else {
                                close(2);
                                return;
                            }
                        }
                        break;
                    case "copy_multi":
                        dialog.dismiss();
                        LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_multi_add, null, false);
                        final EditText name = (EditText) layout.findViewById(R.id.new_multi_name);
                        name.setTextSize(20);
                        final String multiPath = getIntent().getStringExtra(Reddinator.ITEM_URL);
                        name.setText(multiPath.substring(multiPath.lastIndexOf("/")+1));
                        name.selectAll();
                        AlertDialog.Builder builder = new AlertDialog.Builder(FeedItemDialogActivity.this, R.style.AlertDialogStyle);
                        builder.setView(layout).setTitle(getString(R.string.copy_multi))
                                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();
                                    }
                                })
                                .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        if (name.getText().toString().equals("")) {
                                            Toast.makeText(FeedItemDialogActivity.this, getString(R.string.enter_multi_name_error), Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        new SubscriptionEditTask(global, FeedItemDialogActivity.this, FeedItemDialogActivity.this, SubscriptionEditTask.ACTION_MULTI_COPY)
                                                .execute(name.getText().toString(), multiPath.replaceFirst(".*reddit.com", ""));
                                                dialogInterface.dismiss();
                                            }
                                })
                                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        close(0);
                                    }
                                })
                                .show().setCanceledOnTouchOutside(true);
                        return;
                    case "open_theme":
                        try {
                            dialog.dismiss();
                            ThemeHelper.handleThemeInstall(FeedItemDialogActivity.this, global, FeedItemDialogActivity.this, new JSONObject(getIntent().getStringExtra(EXTRA_POST_DATA)), null);
                            return;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                }
                close(0);
            }
        });
        // setup voting buttons
        String userLikes = getIntent().getStringExtra(Reddinator.ITEM_USERLIKES);
        IconTextView upvote = (IconTextView) dialog.findViewById(R.id.opt_upvote);
        IconTextView downvote = (IconTextView) dialog.findViewById(R.id.opt_downvote);
        if (!userLikes.equals("null")) {
            if (userLikes.equals("true")) {
                upvote.setTextColor(Color.parseColor(Reddinator.COLOR_UPVOTE_ACTIVE));
            } else {
                downvote.setTextColor(Color.parseColor(Reddinator.COLOR_DOWNVOTE_ACTIVE));
            }
        }
        upvote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (widgetId>0) {
                    new WidgetVoteTask(
                            FeedItemDialogActivity.this,
                            getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0),
                            1,
                            getIntent().getIntExtra(Reddinator.ITEM_FEED_POSITION, -1),
                            getIntent().getStringExtra(Reddinator.ITEM_ID)
                    ).execute();
                }
                close(3);
            }
        });
        downvote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (widgetId>0) {
                    new WidgetVoteTask(
                            FeedItemDialogActivity.this,
                            getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0),
                            -1,
                            getIntent().getIntExtra(Reddinator.ITEM_FEED_POSITION, -1),
                            getIntent().getStringExtra(Reddinator.ITEM_ID)
                    ).execute();
                }
                close(4);
            }
        });
        // setup theme, use widget theme if coming from a widget
        ThemeManager.Theme theme = global.mThemeManager.getActiveTheme((widgetId>0 ? "widgettheme-"+widgetId : "appthemepref"));
        dialog.findViewById(R.id.dialog).setBackgroundColor(Color.parseColor(theme.getValue("header_color")));
        ((TextView) dialog.findViewById(R.id.title)).setTextColor(Color.parseColor(theme.getValue("header_text")));
        // show the dialog
        dialog.show();
    }

    @Override
    public void onSubscriptionEditComplete(boolean result, RedditData.RedditApiException exception, int action, Object[] params, JSONObject data) {
        if (result) {
            //if (this.data!=null)
            //System.out.println("resultData: "+this.data.toString());
            switch (action) {
                case SubscriptionEditTask.ACTION_MULTI_COPY:
                    try {
                        if (data ==null) return;
                        JSONObject multiObj = data.getJSONObject("data");
                        String path = multiObj.getString("path");
                        global.getSubredditManager().setMultiData(path, multiObj);
                        Toast.makeText(FeedItemDialogActivity.this, R.string.copy_multi_success, Toast.LENGTH_LONG).show();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        } else {
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(FeedItemDialogActivity.this, false);
            // show error
            Utilities.showApiErrorToastOrDialog(FeedItemDialogActivity.this, exception);
        }
        close(0);
    }

    @Override
    public void onThemeResult(boolean updateTheme) {
        close(updateTheme?6:0);
    }

    private class ItemOptionsAdapter extends BaseAdapter {
        LayoutInflater inflater;
        ArrayList<String[]> options;

        ItemOptionsAdapter(){
            inflater = LayoutInflater.from(FeedItemDialogActivity.this);

            options = new ArrayList<>();
            options.add(new String[]{"hide_post", getString(R.string.item_option_hide_post)});
            options.add(new String[]{"save_post", getString(R.string.item_option_save_post)});
            options.add(new String[]{"share_post", getString(R.string.item_option_share_post)});
            options.add(new String[]{"open_post", getString(R.string.item_option_open_post)});
            options.add(new String[]{"open_comments", getString(R.string.item_option_open_comments)});

            boolean canViewSubreddit = true;
            boolean canViewDomain = true;
            String domain = getIntent().getStringExtra(Reddinator.ITEM_DOMAIN);
            // determine whether subreddit and domain options are shown
            // (ie. subreddit domain option shouldn't be shown if the user is currently viewing the feed)
            if (widgetId<0){
                // for temp feeds and searches this needs to be calculated
                String feedPath = getIntent().getStringExtra(EXTRA_CURRENT_FEED_PATH);
                if (feedPath!=null && !feedPath.equals("") && !feedPath.equals("/r/all") && feedPath.contains("/r/")) {
                        canViewSubreddit = false;
                }
                if (domain.indexOf("self.")==0 || domain.indexOf("reddit.com")==0 || (feedPath!=null && Utilities.isFeedPathDomain(feedPath))){
                    canViewDomain = false;
                }
            } else {
                // for the widget and app feeds this data is available readily
                canViewSubreddit = global.getSubredditManager().isFeedMulti(widgetId);
                canViewDomain = (domain.indexOf("self.")!=0 && !global.getSubredditManager().getCurrentFeedName(widgetId).equals(domain));
            }

            if (canViewSubreddit)
                options.add(new String[]{"view_subreddit", getString(R.string.item_option_view_subreddit, getIntent().getStringExtra(Reddinator.ITEM_SUBREDDIT))});

            if (canViewDomain)
                options.add(new String[]{"view_domain", getString(R.string.item_option_view_domain, domain)});

            if (Utilities.isFeedPathMulti(getIntent().getStringExtra(Reddinator.ITEM_URL)))
                options.add(new String[]{"copy_multi", getString(R.string.copy_multi)});

            if (getIntent().getBooleanExtra(EXTRA_IS_THEME, false))
                options.add(new String[]{"open_theme", getString(R.string.open_theme)});
        }

        @Override
        public int getCount() {
            return options.size();
        }

        @Override
        public Object getItem(int position) {
            return options.get(position)[1];
        }

        String getItemKey(int position) {
            return options.get(position)[0];
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            TextView text;

            if (convertView == null) {
                view = inflater.inflate(R.layout.item_dialog_row, parent, false);
            } else {
                view = convertView;
            }

            text = (TextView) view.findViewById(R.id.item_text);
            text.setText((String) getItem(position));

            return view;
        }
    }

    private void close(int result){
        close(result, null);
    }

    private void close(int result, Intent sintent){
        if (dialog.isShowing())
            dialog.dismiss();

        if (result==3 || result==4 || (widgetId<0 && result==5)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(Reddinator.ITEM_FEED_POSITION, getIntent().getIntExtra(Reddinator.ITEM_FEED_POSITION, -1));
            setResult(result, intent);
        } else {
            if (sintent!=null) {
                setResult(result, sintent);
            } else {
                setResult(result);
            }
        }
        finish();
    }
}
