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
 * Created by michael on 14/05/15.
 */
package au.com.wallaceit.reddinator;

import android.app.Activity;
import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.view.View;

class SimpleTabsAdapter extends PagerAdapter {

    private View layout = null;
    private String[] labels;
    private int[] layoutIds;
    private Activity context;

    public SimpleTabsAdapter(String[] labels, int[] layoutIds, Context context, View layout){
        this.context = (Activity) context;
        this.layout = layout;
        this.labels = labels;
        this.layoutIds = layoutIds;
    }

    public Object instantiateItem(View collection, int position) {
        if (position>labels.length)
            return null;
        if (layout==null)
            return context.findViewById(layoutIds[position]);
        return layout.findViewById(layoutIds[position]);
    }

    @Override
    public int getCount() {
        return labels.length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (position>labels.length)
            return null;
        return labels[position];
    }

    @Override
    public boolean isViewFromObject(View arg0, Object arg1) {
        return arg0 == arg1;
    }
}
