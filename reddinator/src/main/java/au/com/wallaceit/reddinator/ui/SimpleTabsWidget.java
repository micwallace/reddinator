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
 * Created by michael on 12/05/15.
 */
package au.com.wallaceit.reddinator.ui;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import au.com.wallaceit.reddinator.R;

public class SimpleTabsWidget {
    LayoutInflater inflater;
    LinearLayout tabWidget;
    ViewPager viewPager;
    //TableRow tabs;
    //TableRow indicators;
    ArrayList<LinearLayout> indicatorItems = new ArrayList<>();
    ArrayList<TextView> tabItems = new ArrayList<>();
    int[] colors = new int[]{Color.WHITE, Color.BLACK};

    public SimpleTabsWidget(Context context, LinearLayout tabView) {
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        tabWidget = tabView;
        //tabs = (TableRow) tabWidget.findViewById(R.id.tabs);
        //indicators = (TableRow) tabWidget.findViewById(R.id.indicators);
    }

    public void setViewPager(ViewPager viewPager){
        this.viewPager = viewPager;
        initTabs();
        viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                setTab(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
        setTab(viewPager.getCurrentItem());
    }

    public void initTabs(){
        PagerAdapter adapter = viewPager.getAdapter();
        int tabCount = adapter.getCount();
        for (int i=0; i<tabCount; i++){
            String title = adapter.getPageTitle(i).toString();
            insertTab(i, title);
        }
    }

    private void insertTab(int index, String text){

        TabClickListener clickListener = new TabClickListener(index);
        LinearLayout tabContainer = (LinearLayout) inflater.inflate(R.layout.tab, tabWidget, false);
        tabContainer.setOnClickListener(clickListener);

        TextView tabText = (TextView) tabContainer.findViewById(R.id.tab_text);
        tabText.setText(text);
        tabText.setTextColor(colors[0]);
        tabItems.add(tabText);

        LinearLayout indicator = (LinearLayout) tabContainer.findViewById(R.id.tab_indicator);
        indicator.setBackgroundColor(colors[1]);
        indicatorItems.add(indicator);

        tabContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        tabWidget.addView(tabContainer);
    }

    public void setTab(int position){
        for (int i=0; i<indicatorItems.size(); i++){
            if (i==position){
                indicatorItems.get(i).setVisibility(View.VISIBLE);
            } else {
                indicatorItems.get(i).setVisibility(View.INVISIBLE);
            }
        }
    }

    class TabClickListener implements View.OnClickListener {
        private int index;
        public TabClickListener(int index){
            this.index = index;
        }
        @Override
        public void onClick(View view){
            setTab(index);
            if (viewPager!=null)
                viewPager.setCurrentItem(index);
        }
    }

    public void setBackgroundColor(int color){
        tabWidget.setBackgroundColor(color);
    }
    public void setTextColor(int color){
        colors[0] = color;
        for (int i=0; i<tabItems.size(); i++){
            tabItems.get(i).setTextColor(color);
        }
    }
    public void setInidicatorColor(int color){
        colors[1] = color;
        for (int i=0; i<indicatorItems.size(); i++){
            indicatorItems.get(i).setBackgroundColor(color);
        }
    }
}
