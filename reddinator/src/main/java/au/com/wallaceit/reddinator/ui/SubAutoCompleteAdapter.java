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
package au.com.wallaceit.reddinator.ui;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;

public class SubAutoCompleteAdapter extends ArrayAdapter<String> implements Filterable {
    private Reddinator global;
    private JSONArray suggestions = new JSONArray();

    public SubAutoCompleteAdapter(Context context, int resource) {
        super(context, resource);
        global = (Reddinator) context.getApplicationContext();
    }

    @Override
    public int getCount() {
        if (suggestions==null)
            return 0;

        return suggestions.length();
    }

    @Override
    public String getItem(int index) {
        try {
            return suggestions.getString(index);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            private Exception exception;
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults filterResults = new FilterResults();
                if (constraint != null) {
                    // Retrieve the autocomplete results.
                    try {
                        JSONArray suggestions = global.mRedditData.searchRedditNames(constraint.toString());
                        // Assign the data to the FilterResults
                        filterResults.values = suggestions;
                        filterResults.count = suggestions.length();
                    } catch (RedditData.RedditApiException e) {
                        e.printStackTrace();
                        exception = e;
                        return null;
                    }
                }
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null) {
                    suggestions = (JSONArray) results.values;
                    if (results.count > 0) {
                        notifyDataSetChanged();
                        return;
                    }
                } else {
                    Toast.makeText(getContext(), exception.getMessage(), Toast.LENGTH_LONG).show();
                }
                notifyDataSetInvalidated();
            }};
    }
}
