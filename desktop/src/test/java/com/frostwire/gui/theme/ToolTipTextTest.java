/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolTipTextTest {
  @Test
  void theRealHtmlTooltipsBecomeReadablePlainText() {
    assertEquals(
        "FrostWire has detected a VPN connection, your privacy is safe from prying eyes.",
        ToolTipText.toPlain(
            "<html><p width=\"260\">FrostWire has detected a VPN connection, your privacy is safe"
                + " from prying eyes.</p></html>"));
    assertEquals(
        "Seeding\ncompleted torrent downloads.",
        ToolTipText.toPlain("<html><b>Seeding</b><p>completed torrent downloads.</p></html>"));
    assertEquals(
        "Click to copy Info hash\nabc&def<1>",
        ToolTipText.toPlain("<html>Click to copy Info hash<br>abc&amp;def&lt;1&gt;</html>"));
  }

  @Test
  void plainStripsMarkupFromAStringThatIsNotAnHtmlDocument() {
    assertEquals(
        "Seeding\ncompleted torrent downloads.",
        ToolTipText.plain("<b>Seeding</b><p>completed torrent downloads.</p>"));
    assertEquals("a\nb", ToolTipText.plain("<html>a<br>b</html>"));
    assertEquals("no markup", ToolTipText.plain("no markup"));
    assertEquals("a\nb", ToolTipText.plain("a\nb"), "plain line breaks are kept");
    assertNull(ToolTipText.plain(null));
  }

  @Test
  void blocksListsAndEntitiesAreHandled() {
    assertEquals(
        "Title\n• one\n• two",
        ToolTipText.toPlain("<HTML><h3>Title</h3><ul><li>one</li><li>two</li></ul></HTML>"));
    assertEquals("a  b", ToolTipText.toPlain("<html>a&nbsp;  \n\t b</html>"));
    assertEquals("Aé", ToolTipText.toPlain("<html>A&#233;</html>"));
    assertEquals("A&unknown;", ToolTipText.toPlain("<html>A&unknown;</html>"));
    assertEquals("keep", ToolTipText.toPlain("<html><style>p{x}</style><script>x<y</script>keep"));
  }

  @Test
  void aTagThatNeverClosesAndHostileMarkupAreHarmless() {
    assertEquals("a", ToolTipText.toPlain("<html>a<b"), "an open tag at the end is dropped");
    assertEquals("a <b", ToolTipText.toPlain("<html>a &lt;b"));
    assertEquals("a < b", ToolTipText.toPlain("<html>a < b</html>"), "a lone < is text");
    assertEquals(
        "name",
        ToolTipText.toPlain("<html><img src=\"http://example.com/x.png\">name</html>"),
        "no image, no remote load, only the text");
    StringBuilder deep = new StringBuilder("<html>");
    for (int i = 0; i < 100_000; i++) {
      deep.append("<b>");
    }
    assertEquals("", ToolTipText.toPlain(deep.toString()), "linear, no recursion");
    StringBuilder styles = new StringBuilder("<html>");
    for (int i = 0; i < 20_000; i++) {
      styles.append("<style>x</style>");
    }
    assertEquals("", ToolTipText.toPlain(styles.toString()), "many <style> elements stay linear");
  }

  @Test
  void plainTextIsReturnedAsItIs() {
    String plain = "Copy Magnet <b>not html</b>";
    assertSame(plain, ToolTipText.toPlain(plain));
    assertNull(ToolTipText.toPlain(null));
    assertFalse(ToolTipText.needsMultiline("Copy Magnet"));
    assertFalse(ToolTipText.needsMultiline(""));
    assertFalse(ToolTipText.needsMultiline(null));
    assertTrue(ToolTipText.needsMultiline("a\nb"));
    assertTrue(ToolTipText.needsMultiline("<html>a</html>"));
  }
}
