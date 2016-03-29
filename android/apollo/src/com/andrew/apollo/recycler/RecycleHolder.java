/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
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
