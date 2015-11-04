/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 
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

package com.frostwire.search;

import rx.Observable;

import java.util.concurrent.TimeUnit;

/**
 * @author gubatron
 * @author aldenml
 */
public interface SearchManager {

    // IMPORTANT: This is a multiplexer observable. You don't want to make this guy call onComplete() as it is reused,
    // observed for example by LocalSearchEngine on Android and by SearchEngine on Desktop.
    Observable<SearchManagerSignal> observable();

    void perform(SearchPerformer performer);

    void stop();

    void stop(long token);

    boolean shutdown(long timeout, TimeUnit unit);
}
