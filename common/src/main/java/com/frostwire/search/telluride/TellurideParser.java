/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2007-2021, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.search.telluride;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;

public final class TellurideParser {
    final static String DECIMAL_GROUP_FORMAT = "(?<%s>\\d{1,3}\\.\\d{1,3})";
    final static String ETA_GROUP = "(?<eta>\\d{1,3}\\:\\d{1,3})";
    // [download]  30.5% of 277.93MiB at 507.50KiB/s ETA 06:29
    final static String REGEX_PROGRESS = "(?is)" +
            String.format(DECIMAL_GROUP_FORMAT, "percentage") +
            "\\% of " +
            String.format(DECIMAL_GROUP_FORMAT, "size") +
            "(?<unitSize>[KMGTP]iB) at.*?" +
            String.format(DECIMAL_GROUP_FORMAT, "rate") +
            "(?<unitRate>[KMGTP]iB/s) ETA " + ETA_GROUP;
    final static Pattern downloadPattern = Pattern.compile(REGEX_PROGRESS);

    final TellurideListener processListener;
    final boolean metaOnly;
    boolean pageUrlRead;
    final StringBuilder sb;

    TellurideParser(TellurideListener listener, boolean pMetaOnly) {
        processListener = listener;
        metaOnly = pMetaOnly;
        sb = new StringBuilder();
    }

    public void parse(String line) {
        if (!pageUrlRead && line.contains("PAGE_URL:")) {
            pageUrlRead = true;
            return;
        }
        if (!pageUrlRead) {
            return;
        }
        if (line.contains("] ERROR:")) {
            processListener.onError(line);
            return;
        }
        if (metaOnly) {
            sb.append(line);
        } else if (!line.isEmpty()) {
            // reports on the file name we're about to download - onDestination
            if (line.startsWith("[download] Destination: ")) {
                processListener.onDestination(line.substring("[download] Destination: ".length()));
                return;
            }

            // reports on progress
            Matcher progressMatcher = downloadPattern.matcher(line);
            if (progressMatcher.find()) {
                String percentage = progressMatcher.group("percentage");
                String size = progressMatcher.group("size");
                String unitSize = progressMatcher.group("unitSize");
                String rate = progressMatcher.group("rate");
                String unitRate = progressMatcher.group("unitRate");
                String eta = progressMatcher.group("eta");

                processListener.onProgress(
                        Float.parseFloat(percentage),
                        Float.parseFloat(size),
                        unitSize,
                        Float.parseFloat(rate),
                        // hardcoded for now, we should parse this, we'll see how it integrates with SearchResults
                        unitRate,
                        eta
                );
                return;
            }

            // reports on errors
        }
    }

    public void done() {
        if (metaOnly) {
            String JSON = sb.toString();
            if (JSON != null && JSON.length() > 0) {
                processListener.onMeta(JSON);
            } else {
                processListener.onError("No metadata returned by telluride");
            }
        }
    }
}
