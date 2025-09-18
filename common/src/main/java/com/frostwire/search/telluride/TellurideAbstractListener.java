/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.search.telluride;

/**
 * An abstract listener so we can implement succinct TellurideListeners
 * with only the methods we care about
 */
public class TellurideAbstractListener implements TellurideListener {
    @Override
    public void onProgress(float completionPercentage, float fileSize, String fileSizeUnits, float downloadSpeed, String downloadSpeedUnits, String ETA) {

    }

    @Override
    public void onError(String errorMessage) {

    }

    @Override
    public void onFinished(int exitCode) {

    }

    @Override
    public void onDestination(String outputFilename) {

    }

    @Override
    public boolean aborted() {
        return false;
    }

    @Override
    public void onMeta(String json) {

    }
}
