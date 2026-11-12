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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.search.relay.icebridge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The IceBridge child is a second JVM next to FrostWire's own. On a computer with one or two CPUs,
 * where Icebase's build 337 freeze reports show the whole machine saturated with 50 to 160 MB of
 * free memory, it runs with a small footprint (frostwire/frostwire#1512).
 */
class IceBridgeChildJvmOptionsTest {
  @Test
  void aOneOrTwoCpuComputerGetsTheSmallFootprintOptions() {
    for (int cpus : new int[] {1, 2}) {
      List<String> options = IceBridgeProcessLauncher.jvmOptions(cpus);
      assertTrue(options.contains("-XX:+UseSerialGC"), cpus + " CPU(s): " + options);
      assertTrue(options.contains("-XX:TieredStopAtLevel=1"), options::toString);
      assertTrue(options.contains("-XX:CICompilerCount=1"), options::toString);
      assertTrue(options.contains("-Xmx256m"), options::toString);
      assertTrue(options.contains("-Xss512k"), options::toString);
    }
  }

  @Test
  void otherComputersKeepTheJvmDefaults() {
    assertEquals(List.of(), IceBridgeProcessLauncher.jvmOptions(3));
    assertEquals(List.of(), IceBridgeProcessLauncher.jvmOptions(16));
  }

  @Test
  void everyOptionIsAJvmOptionNotAProgramArgument() {
    for (String option : IceBridgeProcessLauncher.jvmOptions(1)) {
      assertTrue(option.startsWith("-X"), option);
    }
  }

  @Test
  void theOptionsComeBeforeTheJarInTheChildCommand() throws Exception {
    String source =
        Files.readString(
                Path.of(
                    "src/main/java/com/frostwire/search/relay/icebridge/client/"
                        + "IceBridgeProcessLauncher.java"))
            .replaceAll("\\s+", "");

    int options =
        source.indexOf("command.addAll(jvmOptions(Runtime.getRuntime().availableProcessors()));");
    int jar = source.indexOf("command.add(\"-jar\");");
    assertTrue(options > 0, "the launcher adds the options");
    assertTrue(options < jar, "JVM options after -jar would be program arguments");
  }
}
