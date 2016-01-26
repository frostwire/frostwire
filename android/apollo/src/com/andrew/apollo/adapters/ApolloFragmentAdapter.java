/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.adapters;

import android.content.Context;
import android.widget.ArrayAdapter;
import com.andrew.apollo.utils.Lists;

import java.util.List;

/**
 * Created by gubatron on 1/26/16 on a plane.
 *
 * @author gubatron
 * @author aldenml
 */
public abstract class ApolloFragmentAdapter<I> extends ArrayAdapter<I> {

    /**
     * Used to set the size of the data in the adapter
     */
    protected List<I> mCount = Lists.newArrayList();

    public ApolloFragmentAdapter(Context context, int i) {
        super(context, i);
    }

    /**
     * Method that unloads and clears the items in the adapter
     */
    public void unload() {
        mCount.clear();
        clear();
    }

    /**
     * @param data The {@link List} used to return the count for the adapter.
     */
    public void setCount(final List<I> data) {
        mCount = data;
    }
}
