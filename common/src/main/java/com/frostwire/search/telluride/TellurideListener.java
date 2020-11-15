/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2007-2020, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
