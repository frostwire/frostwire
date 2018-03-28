/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.frostwire.light.ConfigurationManager;
import com.frostwire.util.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ConfigurationManagerTests {
    private static Logger LOG = Logger.getLogger(ConfigurationManager.class);

    private static void getConfigManager(ExecutorService executorService) {
        executorService.execute(() -> {
            LOG.info("Waiting to get configuration instance.", true);
            ConfigurationManager cm = ConfigurationManager.instance();
            LOG.info("Got configuration instance " + cm, true);
        });
    }

    public static void main(String[] args) {
        ExecutorService executorService = new ThreadPoolExecutor(4,4,0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        LOG.info("About to call create(), should not block", true);
        ConfigurationManager.create();
        LOG.info("create call sent", true);
        getConfigManager(executorService);
        getConfigManager(executorService);
        getConfigManager(executorService);
        getConfigManager(executorService);
        getConfigManager(executorService);
        getConfigManager(executorService);
        getConfigManager(executorService);
        getConfigManager(executorService);
        executorService.shutdown();
        LOG.info("Finished", true);
    }

}
