/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.search.telluride;

public interface TellurideListener {
    // [download]  30.5% of 277.93MiB at 507.50KiB/s ETA 06:29
    void onProgress(float completionPercentage,
                    float fileSize,
                    String fileSizeUnits,
                    float downloadSpeed,
                    String downloadSpeedUnits,
                    String ETA);

    void onError(String errorMessage);

    void onFinished(int exitCode);

    void onDestination(String outputFilename);

    /** Call this to kill telluride process */
    boolean aborted();

    /**
     * Called by the output parser when the process ends and it has JSON for us
     */
    void onMeta(String json);
}
