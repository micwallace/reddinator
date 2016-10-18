package au.com.wallaceit.reddinator.activity;
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
 * Created by michael on 23/08/16.
 */
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.ui.SimpleTabsAdapter;
import au.com.wallaceit.reddinator.ui.SimpleTabsWidget;
import de.cketti.library.changelog.ChangeLog;

public class AboutDialog extends Dialog {

    private Context context;
    private boolean isUserInitiated = true;

    public static Dialog show(Context context, boolean isUserInitiated){
        Dialog dialog =  new AboutDialog(context, isUserInitiated);
        dialog.show();
        return dialog;
    }

    protected AboutDialog(Context context, boolean isUserInitiated) {
        this(context);
        this.isUserInitiated = isUserInitiated;
    }

    protected AboutDialog(final Context context) {
        super(context);
        this.context = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Resources resources = context.getResources();
        setContentView(R.layout.dialog_info);
        // setup view pager
        final ViewPager pager = (ViewPager) findViewById(R.id.pager);
        pager.setOffscreenPageLimit(3);
        pager.setAdapter(new SimpleTabsAdapter(
                new String[]{resources.getString(R.string.about), resources.getString(R.string.credits), resources.getString(R.string.changelog)},
                new int[]{R.id.info_about, R.id.info_credits, R.id.info_changelog}, context, findViewById(R.id.info_dialog)));
        LinearLayout tabsLayout = (LinearLayout) findViewById(R.id.tab_widget);
        SimpleTabsWidget tabs = new SimpleTabsWidget(context, tabsLayout);
        tabs.setViewPager(pager);
        ThemeManager.Theme theme = ((Reddinator) context.getApplicationContext()).mThemeManager.getActiveTheme("appthemepref");
        int headerColor = Color.parseColor(theme.getValue("header_color"));
        int headerColor2 = Color.parseColor(theme.getValue("header_color_2"));
        int headerText2 = Color.parseColor(theme.getValue("header_text_2"));
        findViewById(R.id.info_header).setBackgroundColor(headerColor2);
        ((TextView) findViewById(R.id.title)).setTextColor(headerText2);
        ((TextView) findViewById(R.id.subtitle)).setTextColor(headerText2);
        tabs.setBackgroundColor(headerColor);
        tabs.setInidicatorColor(Color.parseColor(theme.getValue("tab_indicator")));
        tabs.setTextColor(Color.parseColor(theme.getValue("header_text")));
        // do install/upgrade dialog specific stuff
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!isUserInitiated) {
            if (prefs.getBoolean("welcomeDialogShown", false)){
                pager.setCurrentItem(2); // show changelog view on upgrade
            } else {
                prefs.edit().putBoolean("welcomeDialogShown", true).apply(); // show details view on first run
            }
            prefs.edit().putString("changelogLastVersion", Utilities.getPackageInfo(context).versionName).apply();
        }
        // setup about view
        TextView version = ((TextView) findViewById(R.id.version));
        version.setText(context.getResources().getString(R.string.version_label, Utilities.getPackageInfo(context).versionName));
        version.setTextColor(headerText2);
        findViewById(R.id.github).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/micwallace/reddinator"));
                context.startActivity(intent);
            }
        });
        findViewById(R.id.donate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=RFUQJ6EP5FLD2"));
                context.startActivity(intent);
            }
        });
        findViewById(R.id.gold).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/gold?goldtype=gift&months=1&thing=t3_4e10jl"));
                context.startActivity(intent);
            }
        });
        // setup credits
        WebView cwv = (WebView) findViewById(R.id.info_credits);
        cwv.loadUrl("file:///android_asset/credits.html");
        // setup changelog_master
        ChangeLog cl = new ChangeLog(context);
        WebView wv = (WebView) findViewById(R.id.info_changelog);
        wv.loadData(cl.getLog(), "text/html", "UTF-8");
    }
}
