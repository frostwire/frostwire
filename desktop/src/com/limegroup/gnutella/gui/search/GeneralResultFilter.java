/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.filters.TableLineFilter;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.LabeledTextField;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public final class GeneralResultFilter implements TableLineFilter<SearchResultDataLine> {

    private SearchResultMediator _rp;
    private LabeledRangeSlider _rangeSliderSeeds;
    private LabeledRangeSlider _rangeSliderSize;

    private int _minResultsSeeds;
    private int _maxResultsSeeds;
    private long _minResultsSize;
    private long _maxResultsSize;

    private int _minSeeds;
    private int _maxSeeds;
    private int _minSize;
    private int _maxSize;

    private String _keywords;

    public GeneralResultFilter(SearchResultMediator rp, LabeledRangeSlider rangeSliderSeeds, LabeledRangeSlider rangeSliderSize, LabeledTextField keywordTextField) {
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
            _minResultsSeeds = seeds;
            seedsNeedUpdate = true;
        }
        if (seeds > _maxResultsSeeds) {
            _maxResultsSeeds = seeds;
            seedsNeedUpdate = true;
        }
        boolean sizeNeedUpdate = false;
        long size = node.getSize();
        if (size < _minResultsSize) {
            _minResultsSize = size;
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
            _rangeSliderSize.getMinimumValueLabel().setText(GUIUtils.toUnitbytes(_minResultsSize));
            _rangeSliderSize.getMaximumValueLabel().setText(GUIUtils.toUnitbytes(_maxResultsSize));
        }

        boolean inSeedRange = false;

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

        boolean inSizeRange = false;

        if (_maxResultsSize > _minResultsSize) {
            long sizeNorm = ((size - _minResultsSize) * 1000) / (_maxResultsSize - _minResultsSize);

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

    public int getMinResultsSeeds() {
        return _minResultsSeeds;
    }

    public int getMaxResultsSeeds() {
        return _maxResultsSeeds;
    }

    public long getMinResultsSize() {
        return _minResultsSize;
    }

    public long getMaxResultsSize() {
        return _maxResultsSize;
    }

    public int getMinSeeds() {
        return _minSeeds;
    }

    public int getMaxSeeds() {
        return _maxSeeds;
    }

    public int getMinSize() {
        return _minSize;
    }

    public int getMaxSize() {
        return _maxSize;
    }

    public void setRangeSeeds(int min, int max) {
        _minSeeds = min;
        _maxSeeds = max;
        _rp.filterChanged(this, 1);
    }

    public void setRangeSize(int min, int max) {
        _minSize = min;
        _maxSize = max;
        _rp.filterChanged(this, 1);
    }

    public void updateKeywordFiltering(String text) {
        _keywords = new String(text);
        _rp.filterChanged(this, 1);
    }

    public String getKeywordFilterText() {
        return _keywords;
    }
}
