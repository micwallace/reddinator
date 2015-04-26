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

package au.com.wallaceit.reddinator;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

public class MailCheckReceiver extends BroadcastReceiver {

    public static String CHECK_ACTION = "reddinator.background.mailcheck";

    public static void setAlarm(Context context){

        Intent intent = new Intent(context, MailCheckReceiver.class);
        intent.setPackage(context.getPackageName());
        intent.setAction(CHECK_ACTION);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        PendingIntent updateIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, intent, 0);

        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int refreshRate = Integer.valueOf(prefs.getString(context.getString(R.string.background_mail_pref), "43200000"));

        if (refreshRate == 0 || prefs.getString("oauthtoken", "").equals("")) {
            alarmManager.cancel(updateIntent); // cancel if disabled or not logged in
        } else {
            alarmManager.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + refreshRate, refreshRate, updateIntent);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        MailCheckService.checkMail(context, MailCheckService.NOTIFY_CHECK_ACTION);
    }
}
