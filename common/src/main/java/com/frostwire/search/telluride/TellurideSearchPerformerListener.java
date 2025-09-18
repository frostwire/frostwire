/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.search.telluride;

import java.util.List;

public interface TellurideSearchPerformerListener {
    void onTellurideBinaryNotFound(IllegalArgumentException e);

    void onTellurideJSONResult(final long token, final TellurideSearchPerformer.TellurideJSONResult result);

    void onError(final long token, final String errorMessage);

    void onSearchResults(final long token, final List<TellurideSearchResult> results);
}
