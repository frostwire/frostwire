/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.filters.TableLineFilter;
import com.limegroup.gnutella.gui.GUIUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public final class GeneralResultFilter implements TableLineFilter<SearchResultDataLine> {
    private final SearchResultMediator _rp;
    private final LabeledRangeSlider _rangeSliderSeeds;
    private final LabeledRangeSlider _rangeSliderSize;
    private int _minResultsSeeds;
    private int _maxResultsSeeds;
    private double _minResultsSize;
    private double _maxResultsSize;
    private int _minSeeds;
    private int _maxSeeds;
    private int _minSize;
    private int _maxSize;
    private String _keywords;

    GeneralResultFilter(SearchResultMediator rp, LabeledRangeSlider rangeSliderSeeds, LabeledRangeSlider rangeSliderSize) {
        _rp = rp;
        _rangeSliderSeeds = rangeSliderSeeds;
        _rangeSliderSize = rangeSliderSize;
        _minResultsSeeds = Integer.MAX_VALUE;
        _maxResultsSeeds = 0;
        _minResultsSize = Long.MAX_VALUE;
        _maxResultsSize = 0;
        _minSeeds = 0;
        _maxSeeds = Integer.MAX_VALUE;
        _minSize = 0;
        _maxSize = Integer.MAX_VALUE;
    }

    public boolean allow(SearchResultDataLine node) {
        boolean seedsNeedUpdate = false;
        int seeds = node.getSeeds();
        if (seeds < _minResultsSeeds) {
            _minResultsSeeds = Math.max(seeds, 0);
            seedsNeedUpdate = true;
        }
        if (seeds > _maxResultsSeeds) {
            _maxResultsSeeds = seeds;
            seedsNeedUpdate = true;
        }
        boolean sizeNeedUpdate = false;
        double size = node.getSize();
        if (size < _minResultsSize) {
            _minResultsSize = size >= 0 ? size : 0;
            sizeNeedUpdate = true;
        }
        if (size > _maxResultsSize) {
            _maxResultsSize = size;
            sizeNeedUpdate = true;
        }
        if (seedsNeedUpdate) {
            _rangeSliderSeeds.getMinimumValueLabel().setText(String.valueOf(_minResultsSeeds));
            _rangeSliderSeeds.getMaximumValueLabel().setText(String.valueOf(_maxResultsSeeds));
        }
        if (sizeNeedUpdate) {
            _rangeSliderSize.getMinimumValueLabel().setText(GUIUtils.getBytesInHuman(_minResultsSize));
            _rangeSliderSize.getMaximumValueLabel().setText(GUIUtils.getBytesInHuman(_maxResultsSize));
        }
        boolean inSeedRange;
        if (_maxResultsSeeds > _minResultsSeeds) {
            int seedNorm = ((seeds - _minResultsSeeds) * 1000) / (_maxResultsSeeds - _minResultsSeeds);
            if (_minSeeds == 0 && _maxSeeds == 1000) {
                inSeedRange = true;
            } else if (seeds == _minSeeds || seeds == _maxSeeds) {
                inSeedRange = true;
            } else if (_minSeeds == 0) {
                inSeedRange = seedNorm <= _maxSeeds;
            } else if (_maxSeeds == 1000) {
                inSeedRange = seedNorm >= _minSeeds;
            } else {
                inSeedRange = seedNorm >= _minSeeds && seedNorm <= _maxSeeds;
            }
        } else {
            inSeedRange = seeds == _maxResultsSeeds;
        }
        boolean inSizeRange;
        if (_maxResultsSize > _minResultsSize) {
            double sizeNorm = ((size - _minResultsSize) * 1000) / (_maxResultsSize - _minResultsSize);
            if (_minSize == 0 && _maxSize == 1000) {
                inSizeRange = true;
            } else if (_minSize == 0) {
                inSizeRange = sizeNorm <= _maxSize;
            } else if (_maxSize == 1000) {
                inSizeRange = sizeNorm >= _minSize;
            } else {
                inSizeRange = sizeNorm >= _minSize && sizeNorm <= _maxSize;
            }
        } else {
            inSizeRange = size == _maxResultsSize;
        }
        String sourceName = getSourceName(node);
        boolean hasKeywords = hasKeywords(node.getDisplayName() + " " + node.getExtension() + " " + sourceName);
        return inSeedRange && inSizeRange && hasKeywords;
    }

    private String getSourceName(SearchResultDataLine node) {
        SourceHolder sourceHolder = (SourceHolder) node.getValueAt(SearchTableColumns.SOURCE_IDX);
        String sourceName = "";
        if (sourceHolder != null) {
            sourceName = sourceHolder.getSourceName();
        }
        return sourceName;
    }

    private boolean hasKeywords(String filename) {
        String keywordText = _keywords;
        if (keywordText == null || keywordText.trim().length() == 0) {
            return true;
        }
        //if it's just one keyword.
        String[] keywords = keywordText.split(" ");
        if (keywords.length == 1) {
            return filename.toLowerCase().contains(keywordText.toLowerCase());
        } else {
            String fname = filename.toLowerCase();
            //all keywords must be in the file name.
            for (String k : keywords) {
                if (!fname.contains(k.toLowerCase())) {
                    return false;
                }
            }
        }
        return true;
    }

    void setRangeSeeds(int min, int max) {
        _minSeeds = min;
        _maxSeeds = max;
        _rp.filterChanged(this, 1);
    }

    void setRangeSize(int min, int max) {
        _minSize = min;
        _maxSize = max;
        _rp.filterChanged(this, 1);
    }

    void updateKeywordFiltering(String text) {
        _keywords = text;
        _rp.filterChanged(this, 1);
    }
}
