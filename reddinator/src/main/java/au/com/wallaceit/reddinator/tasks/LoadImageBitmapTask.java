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
 * Created by michael on 9/08/16.
 */
package au.com.wallaceit.reddinator.tasks;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class LoadImageBitmapTask extends AsyncTask<Void, Integer, Bitmap> {
    private String url;
    private ImageCallback callback;

    public LoadImageBitmapTask(String url, ImageCallback callback) {
        this.url = url;
        this.callback = callback;
    }

    public static abstract class ImageCallback implements Runnable {
        public Bitmap image = null;
        @Override
        public void run(){
        }
    }

    @Override
    protected Bitmap doInBackground(Void... voids) {
        URL url;
        try {
            url = new URL(this.url);
            URLConnection con = url.openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            return BitmapFactory.decodeStream(con.getInputStream());
        } catch (Exception e) {
            Log.d("Reddinator", "Count not load image with URL: "+this.url, e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        if (callback!=null){
            callback.image = result;
            callback.run();
        }
    }
}
