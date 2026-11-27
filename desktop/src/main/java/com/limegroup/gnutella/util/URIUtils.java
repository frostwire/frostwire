package com.limegroup.gnutella.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/** Utilities for URIs */
public class URIUtils {
  private static final String RESERVED = ";/?:@&=+$,";
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  /**
   * Creates a <code>URI</code> from the input string. The preferred way to invoke this method is
   * with an URL-encoded string.
   *
   * <p>However, if the string has not been encoded, this method will encode it. It is ambiguous
   * whether a string has been encoded or not, which is why it is preferred to pass in the string
   * pre-encoded.
   *
   * <p>This method is useful when manipulating a URI and you don't know if it is encoded or not.
   *
   * @param uriString the uri to be created
   * @return
   * @throws URISyntaxException
   */
  public static URI toURI(final String uriString) throws URISyntaxException {
    URI uri;
    try {
      uri = new URI(uriString);
    } catch (URISyntaxException e) {
      // the uriString was perhaps not encoded.
      // try to encode it.
      String encodedURIString = encodeURI(uriString);
      try {
        uri = new URI(encodedURIString);
      } catch (URISyntaxException e1) {
        // encoding the uriString didn't help.
        // this probably means there is something structuraly
        // wrong with it.
        // NOTE: throwing the original exception.
        // initing with second Exception.  Not the normal
        // use case for initCause(), but this will at least capture both
        // stack traces
        if (e.getCause() == null) {
          e.initCause(e1);
        }
        throw e;
      }
    }
    return uri;
  }

  /**
   * Percent-encodes (UTF-8) everything that is not allowed in a URI, leaving the reserved
   * delimiters, the unreserved characters and already valid %XX escapes as they are. A space
   * becomes %20 (not '+', which in a path means a plus).
   */
  private static String encodeURI(String url) {
    StringBuilder encodedURL = new StringBuilder();
    int i = 0;
    while (i < url.length()) {
      int cp = url.codePointAt(i);
      int len = Character.charCount(cp);
      if (cp < 128 && (RESERVED.indexOf(cp) >= 0 || isUnreserved(cp))) {
        encodedURL.append((char) cp);
      } else if (cp == '%' && isEscapeAt(url, i)) {
        encodedURL.append('%');
      } else {
        for (byte b : url.substring(i, i + len).getBytes(StandardCharsets.UTF_8)) {
          encodedURL.append('%');
          encodedURL.append(HEX[(b >> 4) & 0xF]);
          encodedURL.append(HEX[b & 0xF]);
        }
      }
      i += len;
    }
    return encodedURL.toString();
  }

  private static boolean isUnreserved(int c) {
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || "-_.!~*'()".indexOf(c) >= 0;
  }

  private static boolean isEscapeAt(String s, int i) {
    return i + 2 < s.length()
        && Character.digit(s.charAt(i + 1), 16) >= 0
        && Character.digit(s.charAt(i + 2), 16) >= 0;
  }
}
