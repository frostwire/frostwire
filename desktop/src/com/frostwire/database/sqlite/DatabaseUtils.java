/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.database.sqlite;

import com.frostwire.database.Cursor;

/**
 * Static utility methods for dealing with databases and {@link Cursor}s.
 */
class DatabaseUtils {
    /**
     * Appends an SQL string to the given StringBuilder, including the opening
     * and closing single quotes. Any single quotes internal to sqlString will
     * be escaped.
     * <p>
     * This method is deprecated because we want to encourage everyone
     * to use the "?" binding form.  However, when implementing a
     * ContentProvider, one may want to add WHERE clauses that were
     * not provided by the caller.  Since "?" is a positional form,
     * using it in this case could break the caller because the
     * indexes would be shifted to accomodate the ContentProvider's
     * internal bindings.  In that case, it may be necessary to
     * construct a WHERE clause manually.  This method is useful for
     * those cases.
     *
     * @param sb        the StringBuilder that the SQL string will be appended to
     * @param sqlString the raw string to be appended, which may contain single
     *                  quotes
     */
    public static void appendEscapedSQLString(StringBuilder sb, String sqlString) {
        sb.append('\'');
        if (sqlString.indexOf('\'') != -1) {
            int length = sqlString.length();
            for (int i = 0; i < length; i++) {
                char c = sqlString.charAt(i);
                if (c == '\'') {
                    sb.append('\'');
                }
                sb.append(c);
            }
        } else
            sb.append(sqlString);
        sb.append('\'');
    }
}
