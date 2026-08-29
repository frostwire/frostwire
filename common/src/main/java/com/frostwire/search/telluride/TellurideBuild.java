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

import com.frostwire.util.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TellurideBuild {
    private static final Logger LOG = Logger.getLogger(TellurideBuild.class);
    private static final Pattern BANNER_BUILD = Pattern.compile("Build\\s+(\\d+)");
    private static final Pattern PYTHON_BUILD = Pattern.compile("(?m)^BUILD\\s*=\\s*(\\d+)\\s*$");
    private static volatile Integer cached;

    private TellurideBuild() {
    }

    public static Integer parseBanner(String text) {
        return firstInt(BANNER_BUILD, text);
    }

    public static Integer parsePythonSource(String text) {
        return firstInt(PYTHON_BUILD, text);
    }

    public static Integer detect(File executable) {
        if (cached != null) {
            return cached;
        }
        Integer build = readFromPythonSource(executable);
        if (build == null) {
            build = readFromProcess(executable);
        }
        if (build != null) {
            cached = build;
        }
        return build;
    }

    private static Integer firstInt(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer readFromPythonSource(File executable) {
        if (executable == null || executable.getParentFile() == null) {
            return null;
        }
        File python = new File(executable.getParentFile(), "telluride.py");
        if (!python.isFile()) {
            return null;
        }
        try {
            return parsePythonSource(
                    new String(Files.readAllBytes(python.toPath()), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            LOG.warn("TellurideBuild.readFromPythonSource failed", t);
            return null;
        }
    }

    private static Integer readFromProcess(File executable) {
        if (executable == null || !executable.isFile() || !executable.canExecute()) {
            return null;
        }
        Process process = null;
        try {
            process = new ProcessBuilder(executable.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Integer build = parseBanner(line);
                    if (build != null) {
                        return build;
                    }
                }
            }
        } catch (Throwable t) {
            LOG.warn("TellurideBuild.readFromProcess failed", t);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
        return null;
    }
}
