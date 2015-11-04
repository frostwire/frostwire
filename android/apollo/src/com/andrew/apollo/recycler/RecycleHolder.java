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

import com.andrew.apollo.ui.MusicHolder;

/**
 * A @ {@link RecyclerListener} for {@link MusicHolder}'s views.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class RecycleHolder implements RecyclerListener {

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMovedToScrapHeap(final View view) {
        MusicHolder holder = (MusicHolder)view.getTag();
        if (holder == null) {
            holder = new MusicHolder(view);
            view.setTag(holder);
        }

        // Release mBackground's reference
        if (holder.mBackground.get() != null) {
            holder.mBackground.get().setImageDrawable(null);
            holder.mBackground.get().setImageBitmap(null);
        }

        // Release mImage's reference
        if (holder.mImage.get() != null) {
            holder.mImage.get().setImageDrawable(null);
            holder.mImage.get().setImageBitmap(null);
        }

        // Release mLineOne's reference
        if (holder.mLineOne.get() != null) {
            holder.mLineOne.get().setText(null);
        }

        // Release mLineTwo's reference
        if (holder.mLineTwo.get() != null) {
            holder.mLineTwo.get().setText(null);
        }

        // Release mLineThree's reference
        if (holder.mLineThree.get() != null) {
            holder.mLineThree.get().setText(null);
        }
    }

}
