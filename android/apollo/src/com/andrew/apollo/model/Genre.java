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
