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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import org.json.JSONArray;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.activity.MessagesActivity;
import au.com.wallaceit.reddinator.core.RedditData;

public class MailCheckService extends Service {
    public static String MAIL_CHECK_COMPLETE = "reddinator.mail.check.complete";
    public static String ACTIVITY_CHECK_ACTION = "reddinator.mail.check";
    public static String NOTIFY_CHECK_ACTION = "reddinator.mail.check.notify";
    private Reddinator global;
    private String action;

    public static void checkMail(Context context, String action){
        Intent intent = new Intent(context, MailCheckService.class);
        intent.setAction(action);
        context.startService(intent);
    }

    @Override
    public void onCreate(){
        global = ((Reddinator) getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        action = intent.getAction();
        if (global.mRedditData.isLoggedIn())
            if (action.equals(ACTIVITY_CHECK_ACTION) || action.equals(NOTIFY_CHECK_ACTION)) {
                (new MailCheckTask()).execute();
            }
        return START_NOT_STICKY;
    }

    private class MailCheckTask extends AsyncTask<String, Void, Boolean> {

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
                sendBroadcast(bIntent);
            } else {
                // show notification
                if (global.mRedditData.getInboxCount()>0) setNotification();
            }
        }
    }

    private void setNotification(){
        int nummessages = global.mRedditData.getInboxCount();
        Intent notifyIntent = new Intent(this, MessagesActivity.class);
        notifyIntent.setAction(MessagesActivity.ACTION_UNREAD);
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getResources().getQuantityString(R.plurals.new_messages, nummessages, nummessages))
                .setContentText(getResources().getString(R.string.new_messages_text))
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.reddinator_logo))
                .setSmallIcon(R.drawable.ic_notify)
                .setContentIntent(PendingIntent.getActivity(this, 0 ,notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(1, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
