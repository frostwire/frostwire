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

package com.limegroup.gnutella.gui.options.panes.ipfilter;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DatFilterInputStreamReader implements IPFilterInputStreamReader {
  private static final Pattern P2P_LINE_PATTERN =
      Pattern.compile("(.*)\\:(.*)\\-(.*)$", java.util.regex.Pattern.COMMENTS);

  /** eMule ipfilter.dat line: `001.002.003.004 - 001.002.003.255 , 000 , description`. */
  private static final Pattern EMULE_LINE_PATTERN =
      Pattern.compile(
          "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\s*-\\s*(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\s*(?:,\\s*\\d*\\s*(?:,(.*))?)?$");

  private final BufferedReader br;
  private InputStream is;
  private int bytesRead;

  public DatFilterInputStreamReader(File uncompressedFile) {
    try {
      this.is = new FileInputStream(uncompressedFile);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    br = new BufferedReader(new InputStreamReader(is));
    bytesRead = 0;
  }

  @Override
  public IPRange readLine() {
    try {
      String line;
      while ((line = br.readLine()) != null) {
        bytesRead += line.length();
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        Matcher emule = EMULE_LINE_PATTERN.matcher(line);
        if (emule.find()) {
          String description = emule.group(3) == null ? "" : emule.group(3).trim();
          return new IPRange(description, stripZeros(emule.group(1)), stripZeros(emule.group(2)));
        }
        Matcher matcher = P2P_LINE_PATTERN.matcher(line);
        if (matcher.find()) {
          return new IPRange(matcher.group(1), matcher.group(2), matcher.group(3));
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  /** `001.002.000.010` to `1.2.0.10`. */
  private static String stripZeros(String address) {
    String[] octets = address.split("\\.");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < octets.length; i++) {
      if (i > 0) {
        sb.append('.');
      }
      sb.append(Integer.parseInt(octets[i]));
    }
    return sb.toString();
  }

  @Override
  public int bytesRead() {
    return bytesRead;
  }

  @Override
  public int available() {
    try {
      return is.available();
    } catch (IOException e) {
      e.printStackTrace();
      return -1;
    }
  }

  @Override
  public void close() {
    try {
      is.close();
      br.close();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
}
