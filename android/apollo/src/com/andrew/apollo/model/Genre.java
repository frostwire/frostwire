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

package com.andrew.apollo.model;

import android.text.TextUtils;

/**
 * A class that represents a genre.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class Genre {

    /**
     * The unique Id of the genre
     */
    public long mGenreId;

    /**
     * The genre name
     */
    public String mGenreName;

    /**
     * Constructor of <code>Genre</code>
     * 
     * @param genreId The Id of the genre
     * @param genreName The genre name
     */
    public Genre(final long genreId, final String genreName) {
        super();
        mGenreId = genreId;
        mGenreName = genreName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) mGenreId;
        result = prime * result + (mGenreName == null ? 0 : mGenreName.hashCode());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Genre other = (Genre)obj;
        if (mGenreId != other.mGenreId) {
            return false;
        }
        return TextUtils.equals(mGenreName, other.mGenreName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return mGenreName;
    }

}
