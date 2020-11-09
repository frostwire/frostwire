/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

import java.util.List;

public interface TellurideSearchPerformerListener {
    void onTellurideBinaryNotFound(IllegalArgumentException e);

    void onTellurideJSONResult(final long token, final TellurideSearchPerformer.TellurideJSONResult result);

    void onError(final long token, final String errorMessage);

    void onSearchResults(final long token, final List<TellurideSearchResult> results);
}
