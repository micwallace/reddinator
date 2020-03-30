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

package au.com.wallaceit.reddinator.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.JobIntentService;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.json.JSONArray;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.activity.MessagesActivity;
import au.com.wallaceit.reddinator.core.RedditData;

public class MailCheckService extends JobIntentService {
    public static String MAIL_CHECK_COMPLETE = "reddinator.mail.check.complete";
    public static String ACTIVITY_CHECK_ACTION = "reddinator.mail.check";
    public static String NOTIFY_CHECK_ACTION = "reddinator.mail.check.notify";
    private Reddinator global;
    public static final int JOB_ID = 1;

    public static void checkMail(Context context, String action){
        Intent intent = new Intent(context, MailCheckService.class);
        intent.setAction(action);

        try {
            MailCheckService.enqueueWork(context, MailCheckService.class, JOB_ID, intent);
        } catch (Exception e){
            Log.e("reddinator", e.getMessage());
        }
    }

    @Override
    public void onCreate(){
        global = ((Reddinator) getApplicationContext());
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        String action = intent.getAction();
        if (global.mRedditData.isLoggedIn())
            if (ACTIVITY_CHECK_ACTION.equals(action) || NOTIFY_CHECK_ACTION.equals(action)) {
                (new MailCheckTask(global, action)).execute();
            }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    private static class MailCheckTask extends AsyncTask<String, Void, Boolean> {

        Reddinator global;
        String action;

        MailCheckTask(Reddinator global, String action){
            super();

            this.global = global;
            this.action = action;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            int oldCount = global.mRedditData.getInboxCount();
            try {
                global.mRedditData.updateUserInfo();
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                return false;
            }
            // update stored unread messages if the count has changed
            int newCount = global.mRedditData.getInboxCount();
            if (newCount>0 && newCount!=oldCount){
                try {
                    JSONArray messages = global.mRedditData.getMessageFeed("unread", 25, null);
                    global.setUnreadMessages(messages);
                } catch (RedditData.RedditApiException e) {
                    // user may not be authorised for the new api scope, ignore
                    e.printStackTrace();
                }
            } else {
                if (oldCount>0) global.clearUnreadMessages();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result){
            if (result)
            if (action.equals(ACTIVITY_CHECK_ACTION)) {
                // notify activity
                Intent bIntent = new Intent(MAIL_CHECK_COMPLETE);
                global.sendBroadcast(bIntent);
            } else {
                // show notification
                if (global.mRedditData.getInboxCount()>0) setNotification();
            }
        }

        private void setNotification(){
            int nummessages = global.mRedditData.getInboxCount();
            Intent notifyIntent = new Intent(global, MessagesActivity.class);
            notifyIntent.setAction(MessagesActivity.ACTION_UNREAD);
            Notification notification = new NotificationCompat.Builder(global)
                    .setContentTitle(global.getResources().getQuantityString(R.plurals.new_messages, nummessages, nummessages))
                    .setContentText(global.getResources().getString(R.string.new_messages_text))
                    .setLargeIcon(BitmapFactory.decodeResource(global.getResources(), R.drawable.reddinator_logo))
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentIntent(PendingIntent.getActivity(global, 0 ,notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                    .build();

            NotificationManager notificationManager = (NotificationManager) global.getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(1, notification);
        }
    }

}
