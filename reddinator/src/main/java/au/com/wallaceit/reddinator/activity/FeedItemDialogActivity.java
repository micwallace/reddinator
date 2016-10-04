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
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.IconTextView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.service.WidgetProvider;
import au.com.wallaceit.reddinator.tasks.HidePostTask;
import au.com.wallaceit.reddinator.tasks.SavePostTask;
import au.com.wallaceit.reddinator.tasks.WidgetVoteTask;

import static android.content.Intent.ACTION_VIEW;

public class FeedItemDialogActivity extends Activity {
    public static final String EXTRA_CURRENT_FEED_PATH = "feedPath";

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
                        redditId = getIntent().getStringExtra(WidgetProvider.ITEM_ID);
                        int feedPos = getIntent().getIntExtra(WidgetProvider.ITEM_FEED_POSITION, 0);
                        if (global.mRedditData.isLoggedIn()){
                            new HidePostTask(FeedItemDialogActivity.this, false, null).execute(redditId);
                        } else {
                            global.getSubredditManager().addPostFilter(widgetId, redditId);
                        }
                        global.removePostFromFeed(widgetId, feedPos, redditId);
                        if (widgetId>0) {
                            WidgetProvider.hideLoaderAndRefreshViews(FeedItemDialogActivity.this, widgetId, false);
                        } else {
                            close(5); // tell main activity to refresh views
                            return;
                        }
                        break;
                    case "save_post":
                        redditId = getIntent().getStringExtra(WidgetProvider.ITEM_ID);
                        (new SavePostTask(FeedItemDialogActivity.this, widgetId>0, null)).execute("link", redditId);
                        break;
                    case "share_post":
                        Utilities.showPostShareDialog(
                                FeedItemDialogActivity.this,
                                getIntent().getStringExtra(WidgetProvider.ITEM_URL),
                                getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK))
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
                        Utilities.intentActionView(FeedItemDialogActivity.this, getIntent().getStringExtra(WidgetProvider.ITEM_URL));
                        break;
                    case "open_comments":
                        // open reddit comments page in browser
                        Utilities.intentActionView(FeedItemDialogActivity.this, "https://www.reddit.com" + getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK));
                        break;
                    case "view_subreddit":
                        // view subreddit of this item
                        String subreddit = getIntent().getStringExtra(WidgetProvider.ITEM_SUBREDDIT);
                        if (widgetId < 0) {
                            String feedPath = "/r/" + subreddit;
                            if (widgetId == -2) {
                                // If currently in search activity, open a temp feed on the main activity
                                openRedditFeed(Reddinator.REDDIT_BASE_URL + feedPath);
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
                                WidgetProvider.showLoaderAndUpdate(FeedItemDialogActivity.this, widgetId, false);
                            } else {
                                close(2); // tell main activity to update
                                return;
                            }
                        }
                        break;
                    case "view_domain":
                        // view listings for the domain of this item
                        String domain = getIntent().getStringExtra(WidgetProvider.ITEM_DOMAIN);
                        if (widgetId < 0) {
                            String feedPath = "/domain/" + domain;
                            if (widgetId == -2) {
                                openRedditFeed(Reddinator.REDDIT_BASE_URL + feedPath);
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
                                WidgetProvider.showLoaderAndUpdate(FeedItemDialogActivity.this, widgetId, false);
                            } else {
                                close(2);
                                return;
                            }
                        }
                        break;
                }
                close(0);
            }
        });
        // setup voting buttons
        String userLikes = getIntent().getStringExtra(WidgetProvider.ITEM_USERLIKES);
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
                            getIntent().getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1),
                            getIntent().getStringExtra(WidgetProvider.ITEM_ID)
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
                            getIntent().getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1),
                            getIntent().getStringExtra(WidgetProvider.ITEM_ID)
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

    private void openRedditFeed(String url){
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
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
            String domain = getIntent().getStringExtra(WidgetProvider.ITEM_DOMAIN);
            // determine whether subreddit and domain options are shown
            // (ie. subreddit domain option shouldn't be shown if the user is currently viewing the feed)
            if (widgetId<0){
                // for temp feeds and searches this needs to be calculated
                String feedPath = getIntent().getStringExtra(EXTRA_CURRENT_FEED_PATH);
                if (feedPath!=null && !feedPath.equals("") && !feedPath.equals("/r/all") && feedPath.contains("/r/")) {
                        canViewSubreddit = false;
                }
                if (domain.indexOf("self.")==0 || (feedPath!=null && feedPath.contains("/domain/"))){
                    canViewDomain = false;
                }
            } else {
                // for the widget and app feeds this data is available readily
                canViewSubreddit = global.getSubredditManager().isFeedMulti(widgetId);
                canViewDomain = (domain.indexOf("self.")!=0 && !global.getSubredditManager().getCurrentFeedName(widgetId).equals(domain));
            }

            if (canViewSubreddit)
                options.add(new String[]{"view_subreddit", getString(R.string.item_option_view_subreddit, getIntent().getStringExtra(WidgetProvider.ITEM_SUBREDDIT))});

            if (canViewDomain)
                options.add(new String[]{"view_domain", getString(R.string.item_option_view_domain, domain)});
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
        if (result==3 || result==4 || (widgetId<0 && result==5)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(WidgetProvider.ITEM_FEED_POSITION, getIntent().getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1));
            setResult(result, intent);
        } else {
            if (sintent!=null) {
                setResult(result, sintent);
            } else {
                setResult(result);
            }
        }
        dialog.dismiss();
        finish();
    }
}
