/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import javax.swing.plaf.basic.BasicHTML;

/**
 * Turns tooltip text into plain text. A tooltip whose text starts with {@code <html>} goes through
 * {@code BasicHTML}, which parses it and lays out a CSS view tree on the EDT every time the tooltip
 * is shown (frostwire/frostwire#1331, #1580), and would load remote images from text that came from
 * a peer. Tooltips show text, not layout, so {@link SafeToolTipUI} shows the plain text of such a
 * string instead.
 *
 * <p>Thread-safe: stateless.
 */
public final class ToolTipText {
  private ToolTipText() {}

  /** True when Swing would render {@code text} as HTML: it starts with {@code <html>}. */
  public static boolean isHtml(String text) {
    return BasicHTML.isHTMLString(text);
  }

  /** True when {@code text} cannot be painted as one plain line: HTML, or with a line break. */
  public static boolean needsMultiline(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    return isHtml(text) || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
  }

  /**
   * Like {@link #toPlain} for a string that carries markup without being an {@code <html>}
   * document, such as a translation with {@code <b>} or {@code <p>} in it. Text with no markup, or
   * null, is returned as it is.
   */
  public static String plain(String text) {
    if (text == null || text.indexOf('<') < 0) {
      return text;
    }
    return toPlain(isHtml(text) ? text : "<html>" + text);
  }

  /**
   * The text of {@code text} without markup: {@code <br>} and the end of a block ({@code </p>},
   * {@code </div>}, {@code </li>}, {@code </tr>}, headings) become line breaks, list items get a
   * bullet, other tags are dropped, entities are decoded, whitespace inside a paragraph collapses
   * like HTML does, and blank lines never repeat. Text that is not HTML is returned as it is.
   * Linear: one pass, no regular expressions.
   */
  public static String toPlain(String text) {
    if (text == null || !isHtml(text)) {
      return text;
    }
    StringBuilder out = new StringBuilder(text.length());
    int len = text.length();
    int i = 0;
    String skipUntil = null; // closing tag of an element whose content is not text
    String lower = null; // lower case copy of text, made once, for finding closing tags
    while (i < len) {
      if (skipUntil != null) {
        // the content of <style> and <script> is not text: jump to its closing tag
        if (lower == null) {
          lower = text.toLowerCase(java.util.Locale.ROOT);
        }
        int end = lower.indexOf("</" + skipUntil, i);
        if (end < 0) {
          break;
        }
        i = end;
        skipUntil = null;
      }
      char c = text.charAt(i);
      if (c == '<' && startsTag(text, i)) {
        int close = text.indexOf('>', i + 1);
        if (close < 0) {
          break; // a tag cut off at the end of the text
        }
        String tag = tagName(text, i + 1, close);
        boolean closing = i + 1 < len && text.charAt(i + 1) == '/';
        if (!closing && (tag.equals("style") || tag.equals("script"))) {
          skipUntil = tag;
        } else if (tag.equals("br")) {
          newline(out, false);
        } else if (closing && isBlock(tag)) {
          newline(out, false);
        } else if (!closing && tag.equals("li")) {
          newline(out, false);
          out.append("\u2022 ");
        } else if (!closing && isBlock(tag)) {
          newline(out, false);
        }
        i = close + 1;
      } else {
        int next = i + 1;
        while (next < len && !(text.charAt(next) == '<' && startsTag(text, next))) {
          next++;
        }
        appendText(out, text.substring(i, next), true);
        i = next;
      }
    }
    return trim(out);
  }

  /** A '<' starts a tag when a letter, '/' or '!' follows it, as in HTML; "a < b" is text. */
  private static boolean startsTag(String text, int lt) {
    if (lt + 1 >= text.length()) {
      return false;
    }
    char next = text.charAt(lt + 1);
    return Character.isLetter(next) || next == '/' || next == '!';
  }

  private static String tagName(String text, int from, int close) {
    int start = from;
    if (start < close && text.charAt(start) == '/') {
      start++;
    }
    int end = start;
    while (end < close && Character.isLetterOrDigit(text.charAt(end))) {
      end++;
    }
    return text.substring(start, end).toLowerCase(java.util.Locale.ROOT);
  }

  private static boolean isBlock(String tag) {
    switch (tag) {
      case "p":
      case "div":
      case "li":
      case "tr":
      case "ul":
      case "ol":
      case "table":
      case "h1":
      case "h2":
      case "h3":
      case "h4":
      case "h5":
      case "h6":
      case "blockquote":
      case "pre":
        return true;
      default:
        return false;
    }
  }

  /** A line break unless the output is empty or already ends with one. */
  private static void newline(StringBuilder out, boolean force) {
    int n = out.length();
    if (n == 0) {
      return;
    }
    while (n > 0 && out.charAt(n - 1) == ' ') {
      out.setLength(--n);
    }
    if (n > 0 && out.charAt(n - 1) != '\n') {
      out.append('\n');
    }
  }

  private static void appendText(StringBuilder out, String raw, boolean decode) {
    String text = decode ? decodeEntities(raw) : raw;
    boolean lastSpace = out.length() == 0 || " \n".indexOf(out.charAt(out.length() - 1)) >= 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\u00a0') {
        out.append(' ');
        lastSpace = false;
      } else if (Character.isWhitespace(c)) {
        if (!lastSpace) {
          out.append(' ');
          lastSpace = true;
        }
      } else {
        out.append(c);
        lastSpace = false;
      }
    }
  }

  private static String decodeEntities(String s) {
    if (s.indexOf('&') < 0) {
      return s;
    }
    StringBuilder sb = new StringBuilder(s.length());
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      int semi = c == '&' ? s.indexOf(';', i + 1) : -1;
      if (semi > i + 1 && semi - i <= 10) {
        String name = s.substring(i + 1, semi);
        String decoded = entity(name);
        if (decoded != null) {
          sb.append(decoded);
          i = semi + 1;
          continue;
        }
      }
      sb.append(c);
      i++;
    }
    return sb.toString();
  }

  private static String entity(String name) {
    switch (name) {
      case "amp":
        return "&";
      case "lt":
        return "<";
      case "gt":
        return ">";
      case "quot":
        return "\"";
      case "apos":
        return "'";
      case "nbsp":
        return "\u00a0";
      default:
        break;
    }
    if (name.length() > 1 && name.charAt(0) == '#') {
      try {
        int cp =
            name.charAt(1) == 'x' || name.charAt(1) == 'X'
                ? Integer.parseInt(name.substring(2), 16)
                : Integer.parseInt(name.substring(1));
        if (Character.isValidCodePoint(cp) && cp != 0) {
          return new String(Character.toChars(cp));
        }
      } catch (NumberFormatException ignored) {
        // not an entity: shown as typed
      }
    }
    return null;
  }

  private static String trim(StringBuilder out) {
    int start = 0;
    int end = out.length();
    while (start < end && Character.isWhitespace(out.charAt(start))) {
      start++;
    }
    while (end > start && Character.isWhitespace(out.charAt(end - 1))) {
      end--;
    }
    return out.substring(start, end);
  }
}
