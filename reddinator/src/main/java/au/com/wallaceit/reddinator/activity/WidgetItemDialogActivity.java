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

import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.IconTextView;
import android.widget.ListView;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.service.WidgetProvider;
import au.com.wallaceit.reddinator.service.WidgetVoteTask;

public class WidgetItemDialogActivity extends Activity {
    Reddinator global;
    Dialog dialog;
    int widgetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        global = (Reddinator) getApplicationContext();
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.activity_widget_item_dialog);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                close(0);
            }
        });
        // check if it is a self post and remove view domain option
        String[] options = getResources().getStringArray(R.array.post_option_items);
        options[4] = String.format(options[4], getIntent().getStringExtra(WidgetProvider.ITEM_SUBREDDIT));
        String domain = getIntent().getStringExtra(WidgetProvider.ITEM_DOMAIN);
        if (domain.indexOf("self.")==0){
            options = new String[]{options[0],options[1],options[2],options[3],options[4]};
        } else {
            options[5] = String.format(options[5], domain);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.widget_item_dialog_row, R.id.item_text, options);
        ListView listview = (ListView) dialog.findViewById(R.id.opt_list);
        listview.setAdapter(adapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 0:

                        break;
                    case 1:

                        break;
                    case 2:
                        // open link in browser
                        String url = getIntent().getStringExtra(WidgetProvider.ITEM_URL);
                        Intent clickIntent2 = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        clickIntent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        WidgetItemDialogActivity.this.startActivity(clickIntent2);
                        break;
                    case 3:
                        // open reddit comments page in browser
                        String permalink = getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK);
                        Intent clickIntent3 = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com" + permalink));
                        clickIntent3.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        WidgetItemDialogActivity.this.startActivity(clickIntent3);
                        break;
                    case 4:
                        // view subreddit of this item
                        String subreddit = getIntent().getStringExtra(WidgetProvider.ITEM_SUBREDDIT);
                        global.getSubredditManager().setFeedSubreddit(widgetId, subreddit);
                        if (widgetId!=0) {
                            WidgetProvider.showLoaderAndUpdate(WidgetItemDialogActivity.this, widgetId, false);
                        } else {
                            close(2); // tell main activity to update
                            return;
                        }
                        break;
                    case 5:
                        // view listings for the domain of this item
                        String domain = getIntent().getStringExtra(WidgetProvider.ITEM_DOMAIN);
                        global.getSubredditManager().setFeedDomain(widgetId, domain);
                        if (widgetId!=0) {
                            WidgetProvider.showLoaderAndUpdate(WidgetItemDialogActivity.this, widgetId, false);
                        } else {
                            close(2);
                            return;
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
                upvote.setTextColor(Color.parseColor("#FF8B60"));
            } else {
                downvote.setTextColor(Color.parseColor("#9494FF"));
            }
        }
        upvote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (widgetId!=0) {
                    new WidgetVoteTask(
                            WidgetItemDialogActivity.this,
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
                if (widgetId!=0) {
                    new WidgetVoteTask(
                            WidgetItemDialogActivity.this,
                            getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0),
                            -1,
                            getIntent().getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1),
                            getIntent().getStringExtra(WidgetProvider.ITEM_ID)
                    ).execute();
                }
                close(4);
            }
        });
        // show the dialog
        dialog.show();
    }

    private void close(int result){
        if (result==3 || result==4) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(WidgetProvider.ITEM_FEED_POSITION, getIntent().getIntExtra(WidgetProvider.ITEM_FEED_POSITION, -1));
            setResult(result, intent);
        } else {
            setResult(result);
        }
        dialog.dismiss();
        finish();
    }
}
