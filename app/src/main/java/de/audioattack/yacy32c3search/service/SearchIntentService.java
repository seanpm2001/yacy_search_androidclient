/*
 * Copyright 2014 Marc Nause <marc.nause@gmx.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see  http:// www.gnu.org/licenses/.
 */
package de.audioattack.yacy32c3search.service;

import android.app.IntentService;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.audioattack.yacy32c3search.activity.SettingsDialog;

/**
 * @author Marc Nause <marc.nause@gmx.de>
 */
public class SearchIntentService extends IntentService {

    private static final String TAG = SearchIntentService.class.getSimpleName();

    public static String lastSearch;

    public static final List<SearchItem> SEARCH_RESULT = new ArrayList<>();

    private static SearchListener searchListener;
    public static boolean isLoading;

    private static volatile long id = Long.MIN_VALUE;

    /**
     * Creates an IntentService.
     */
    public SearchIntentService() {
        super(TAG);
    }

    public static void addSearchListener(final SearchListener listener) {
        searchListener = listener;
    }

    @Override
    protected void onHandleIntent(final Intent intent) {

        String searchString = intent.getStringExtra(SearchManager.QUERY);
        lastSearch = searchString;
        if (searchString != null) {

            while (searchString.matches(".*\\s\\s.*")) {
                searchString = searchString.replaceAll("\\s\\s", " ");
            }

            searchString = searchString.trim().replaceAll("\\s", "+");


            search(getNewId(), searchString);
        }
    }

    private synchronized long getNewId() {

        if (id < Long.MAX_VALUE) {
            id++;
        } else {
            id = Long.MIN_VALUE;
        }

        return id;
    }

    static long getCurrentId() {

        return id;
    }

    public static void clearList() {

        final int numberOfItems = SEARCH_RESULT.size();

        searchListener.onOldResultCleared(numberOfItems);

        if (numberOfItems > 0) {
            SEARCH_RESULT.clear();
        }
    }

    private void search(final long myId, final String searchString) {

        if (searchString.length() > 0) {

            isLoading = true;
            searchListener.onLoadingData();

            InputStream is = null;

            try {

                final ConnectivityManager connMgr = (ConnectivityManager)
                        getSystemService(Context.CONNECTIVITY_SERVICE);
                final NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.isConnected()) {


                    final ISearchResultParser parser = new XmlSearchResultParser(SEARCH_RESULT, searchListener, myId);

                    final String host = SettingsDialog.load(getApplicationContext(), SettingsDialog.KEY_HOST, SettingsDialog.DEFAULT_HOST);

                    final URL url = new URL("http://" + host + "/" + String.format(Locale.US, parser.getSearchUrlParameter(), searchString));
                    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(10000 /* milliseconds */);
                    conn.setConnectTimeout(15000 /* milliseconds */);
                    conn.setRequestMethod("GET");
                    conn.setDoInput(true);

                    if (myId == getCurrentId()) {

                        conn.connect();

                        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            is = conn.getInputStream();
                        } else {
                            throw new IOException("Server returned HTTP code " + conn.getResponseCode() + ".");
                        }

                        if (myId == getCurrentId()) {

                            parser.parse(is);

                            searchListener.onFinishedData();
                            isLoading = false;
                        }
                    }

                } else {

                    searchListener.onNetworkUnavailable();
                    isLoading = false;
                }

            } catch (Exception e) {

                searchListener.onError(e);
                isLoading = false;
            } finally {

                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {

                        // we don't care anymore
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}