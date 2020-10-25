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

/**
 * An abstract listener so we can implement succint TellurideListeners
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
