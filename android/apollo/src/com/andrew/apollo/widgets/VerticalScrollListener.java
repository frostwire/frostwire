/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.widgets;

import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

public class VerticalScrollListener implements OnScrollListener {

    /* Used to determine the off set to scroll the header */
    private final ScrollableHeader mHeader;

    private final ProfileTabCarousel mTabCarousel;

    private final int mPageIndex;

    public VerticalScrollListener(final ScrollableHeader header, final ProfileTabCarousel carousel,
            final int pageIndex) {
        mHeader = header;
        mTabCarousel = carousel;
        mPageIndex = pageIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScroll(final AbsListView view, final int firstVisibleItem,
            final int visibleItemCount, final int totalItemCount) {

        view.setVerticalScrollBarEnabled(firstVisibleItem > 0);

        if (mTabCarousel == null || mTabCarousel.isTabCarouselIsAnimating()) {
            return;
        }

        final View top = view.getChildAt(firstVisibleItem);
        if (top == null) {
            return;
        }

        if (firstVisibleItem > 0) {
            mTabCarousel.moveToYCoordinate(mPageIndex, -mTabCarousel.getAllowedVerticalScrollLength());
            return;
        }

        float y = view.getChildAt(firstVisibleItem).getY();
        final float amtToScroll = Math.max(y, -mTabCarousel.getAllowedVerticalScrollLength());
        mTabCarousel.moveToYCoordinate(mPageIndex, amtToScroll);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
        if (mHeader != null) {
            mHeader.onScrollStateChanged(view, scrollState);
        }
    }

    /** Defines the header to be scrolled. */
    public interface ScrollableHeader {

        /* Used the pause the disk cache while scrolling */
        void onScrollStateChanged(AbsListView view, int scrollState);
    }
}
