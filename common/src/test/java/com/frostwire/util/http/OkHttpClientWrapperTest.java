/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.util.http;

import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.ThreadPool;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OkHttpClientWrapperTest {

    @Test
    public void pingIntervalDetectsStaleHttp2Connections() {
        ThreadPool pool = new ThreadPool("okhttp-test", 1, new LinkedBlockingQueue<>(), true);
        OkHttpClient client = OkHttpClientWrapper.newOkHttpClient(pool).build();
        assertEquals(5000, client.pingIntervalMillis());
        pool.shutdownNow();
    }

    @Test
    public void miscClientFetchesSlideshowJson() {
        try {
            String json =
                    HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC)
                            .get(
                                    "https://update.frostwire.com/o2.php?from=desktop&version=7.0.4&build=331");
            assertNotNull(json);
            assertTrue(json.contains("\"slides\""));
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "update.frostwire.com unreachable: " + e.getMessage());
        }
    }
}
