/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Builds ID3 tags and frames byte by byte, as a file would hold them. */
final class Id3Bytes {

  private Id3Bytes() {}

  /** Concatenates bytes (Integer), ISO-8859-1 strings and byte arrays. */
  static byte[] bytes(Object... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Object p : parts) {
      if (p instanceof Integer i) {
        out.write(i);
      } else if (p instanceof String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
      } else {
        out.writeBytes((byte[]) p);
      }
    }
    return out.toByteArray();
  }

  static byte[] v22Frame(String id, byte[] data) {
    int n = data.length;
    return bytes(id, new byte[] {(byte) (n >>> 16), (byte) (n >>> 8), (byte) n}, data);
  }

  static byte[] v23Frame(String id, byte[] data) {
    return v23Frame(id, 0, 0, data);
  }

  static byte[] v23Frame(String id, int flags1, int flags2, byte[] data) {
    int n = data.length;
    byte[] size = {(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n};
    return bytes(id, size, flags1, flags2, data);
  }

  static byte[] v24Frame(String id, byte[] data) {
    return bytes(id, synchsafe(data.length), new byte[2], data);
  }

  /** A tag header (major version, revision 0, flags, synchsafe size of body) followed by body. */
  static byte[] tag(int majorVersion, int flags, byte[] body) {
    return bytes("ID3", majorVersion, 0, flags, synchsafe(body.length), body);
  }

  static byte[] synchsafe(int n) {
    return new byte[] {
      (byte) ((n >> 21) & 0x7f),
      (byte) ((n >> 14) & 0x7f),
      (byte) ((n >> 7) & 0x7f),
      (byte) (n & 0x7f)
    };
  }

  static byte[] utf16be(String s) {
    return s.getBytes(StandardCharsets.UTF_16BE);
  }
}
