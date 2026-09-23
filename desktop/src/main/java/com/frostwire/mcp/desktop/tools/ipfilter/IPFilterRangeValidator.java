/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.mcp.desktop.tools.ipfilter;

import com.frostwire.jlibtorrent.swig.address;
import com.frostwire.jlibtorrent.swig.error_code;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;

/** Parse IP literals with the same libtorrent parser used when installing filter rules. */
final class IPFilterRangeValidator {

  private IPFilterRangeValidator() {}

  static ParsedRange parse(IPRange range) {
    error_code startError = new error_code();
    address start = address.from_string(range.startAddress(), startError);
    if (startError.failed()) {
      throw new IllegalArgumentException("Invalid start IP address: " + range.startAddress());
    }
    error_code endError = new error_code();
    address end = address.from_string(range.endAddress(), endError);
    if (endError.failed()) {
      throw new IllegalArgumentException("Invalid end IP address: " + range.endAddress());
    }
    return new ParsedRange(start, end);
  }

  record ParsedRange(address start, address end) {}
}
