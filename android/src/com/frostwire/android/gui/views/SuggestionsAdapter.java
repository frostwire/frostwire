/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.database.Cursor;

import com.frostwire.android.R;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.StringUtils;
import com.frostwire.util.http.HttpClient;

import org.json.JSONArray;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import androidx.cursoradapter.widget.SimpleCursorAdapter;

/**
 * @author gubatron
 * @author aldenml
 */
class SuggestionsAdapter extends SimpleCursorAdapter {

    private static final String SUGGESTIONS_URL = buildSuggestionsUrl();
    private static final int HTTP_QUERY_TIMEOUT = 1000;

    private final HttpClient client;

    private boolean discardLastResult;

    public SuggestionsAdapter(Context context) {
        super(context, R.layout.view_suggestion_item, null, new String[]{SuggestionsCursor.COLUMN_SUGGESTION}, new int[]{R.id.view_suggestion_item_text}, 0);
        this.client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        try {
            String url = String.format(SUGGESTIONS_URL, URLEncoder.encode(constraint.toString(), StandardCharsets.UTF_8.name()));

            String js = client.get(url, HTTP_QUERY_TIMEOUT);
            String json = stripJs(js);

            if (!discardLastResult) {
                return new SuggestionsCursor(new JSONArray(json).getJSONArray(1));
            }
        } catch (Throwable e) {
            // ignore
        } finally {
            discardLastResult = false;
        }

        return null;
    }

    @Override
    public CharSequence convertToString(Cursor cursor) {
        if (cursor != null) {
            return cursor.getString(1);
        }
        return null;
    }

    public void discardLastResult() {
        discardLastResult = true;
    }

    private static String buildSuggestionsUrl() {
        String lang = Locale.getDefault().getLanguage();
        if (StringUtils.isNullOrEmpty(lang)) {
            lang = "en";
        }

        return "https://clients1.google.com/complete/search?client=youtube&q=%s&hl=" + lang + "&gl=us&gs_rn=23&gs_ri=youtube&ds=yt&cp=2&gs_id=8&callback=google.sbox.p50";
    }

    private String stripJs(String js) {
        js = js.replace("google.sbox.p50 && google.sbox.p50(", "");
        js = js.replace("}])", "}]");

        return js;
    }
}
