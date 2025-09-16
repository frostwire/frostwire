/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.limegroup.gnutella;

/**
 * The list of all downloads in progress.  DownloadManager has a fixed number
 * of download slots given by the MAX_SIM_DOWNLOADS property.  It is
 * responsible for starting downloads and scheduling and queueing them as
 * needed.  This class is thread safe.<p>
 * <p>
 * As with other classes in this package, a DownloadManager instance may not be
 * used until initialize(..) is called.  The arguments to this are not passed
 * in to the constructor in case there are circular dependencies.<p>
 * <p>
 * DownloadManager provides ways to serialize download state to disk.  Reads
 * are initiated by RouterService, since we have to wait until the GUI is
 * initiated.  Writes are initiated by this, since we need to be notified of
 * completed downloads.  Downloads in the COULDNT_DOWNLOAD state are not
 * serialized.
 */
public interface DownloadManager {
    /**
     * Performs the slow, low-priority initialization tasks: reading in
     * snapshots and scheduling snapshot checkpointing.
     */
    void loadSavedDownloadsAndScheduleWriting();
}
