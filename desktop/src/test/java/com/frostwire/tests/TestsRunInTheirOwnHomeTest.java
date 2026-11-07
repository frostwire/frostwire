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

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.limewire.util.CommonUtils;

/**
 * Tests write FrostWire settings and crash reports under the settings folder, which lives under
 * {@code user.home}. In a test JVM that folder must be inside the project's build directory, never
 * the developer's own FrostWire profile: crash reports tests spooled there were uploaded to Icebase
 * by the next FrostWire started on the machine (frostwire/frostwire#1493).
 */
class TestsRunInTheirOwnHomeTest {
  private static final File BUILD_DIRECTORY = new File("build").getAbsoluteFile();

  @Test
  void theHomeFolderIsInsideTheBuildDirectory() throws IOException {
    assertInsideBuildDirectory(new File(System.getProperty("user.home")));
  }

  @Test
  void theFrostWireSettingsFolderIsInsideTheBuildDirectory() throws IOException {
    assertInsideBuildDirectory(CommonUtils.getUserSettingsDir());
  }

  private static void assertInsideBuildDirectory(File file) throws IOException {
    String path = file.getCanonicalPath() + File.separator;
    String build = BUILD_DIRECTORY.getCanonicalPath() + File.separator;
    assertTrue(path.startsWith(build), path + " is outside " + build);
  }
}
