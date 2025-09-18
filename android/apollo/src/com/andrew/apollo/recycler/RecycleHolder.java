/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.andrew.apollo.recycler;

import android.view.View;
import android.widget.AbsListView.RecyclerListener;
import com.andrew.apollo.ui.MusicViewHolder;


/**
 * A @ {@link RecyclerListener} for {@link MusicViewHolder}'s views.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class RecycleHolder implements RecyclerListener {

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMovedToScrapHeap(final View view) {
        if (view == null) {
            return;
        }

        MusicViewHolder holder = (MusicViewHolder) view.getTag();
        if (holder == null) {
            holder = new MusicViewHolder(view);
            view.setTag(holder);
        }

        holder.reset();
    }
}
