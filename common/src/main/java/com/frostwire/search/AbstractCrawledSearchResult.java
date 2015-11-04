/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 
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

import com.frostwire.licences.License;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractCrawledSearchResult<T extends CrawlableSearchResult> extends AbstractSearchResult implements CrawledSearchResult {

    protected final T parent;

    public AbstractCrawledSearchResult(T parent) {
        this.parent = parent;
    }

    @Override
    public T getParent() {
        return parent;
    }

    @Override
    public String getDetailsUrl() {
        return parent.getDetailsUrl();
    }

    @Override
    public String getSource() {
        return parent.getSource();
    }

    @Override
    public License getLicense() {
        return parent.getLicense();
    }

    @Override
    public long getCreationTime() {
        return parent.getCreationTime();
    }
}
