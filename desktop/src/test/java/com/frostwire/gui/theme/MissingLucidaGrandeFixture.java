/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import javax.swing.JLabel;

/**
 * Runs in its own JVM (macOS, {@code --add-opens=java.desktop/sun.font=ALL-UNNAMED}) and puts the
 * JDK's font manager in the state of a Mac whose Lucida Grande font is missing or disabled: the
 * Lucida Grande family and the logical fonts the JDK builds from it (Dialog, SansSerif,
 * DialogInput) are not registered. {@code CFontManager.loadFonts} leaves exactly that state when
 * the family is absent, because {@code cloneStyledFont} skips a null family, and it never loads the
 * native fonts twice.
 *
 * <p>Then it measures text the way FrostWire's splash screen does, and prints one line:
 *
 * <ul>
 *   <li>{@code CRASH <exception> <top frame>} when measuring throws;
 *   <li>{@code OK} when every font measured.
 * </ul>
 *
 * <p>Argument {@code unrepaired} measures right away; {@code repaired} runs {@link
 * MacLogicalFonts#repairIfMissing()} first. With {@code unlisted-unrepaired} and {@code
 * unlisted-repaired} the Mac is in the state a native font listing that ended early leaves (macOS
 * 27, frostwire/frostwire#1484): Lucida Grande's substitutes and the Serif and Monospaced fonts
 * with the families they are built from are not registered either.
 */
public final class MissingLucidaGrandeFixture {

  public static void main(String[] args) throws Exception {
    String mode = args.length > 0 ? args[0] : "";
    boolean unlisted = mode.startsWith("unlisted-");
    boolean repair = mode.endsWith("repaired") && !mode.endsWith("unrepaired");
    // Load the native fonts and build the logical fonts, as the first lookup at startup does.
    Object fontManager =
        Class.forName("sun.font.FontManagerFactory").getMethod("getInstance").invoke(null);
    Method findFont2D =
        Class.forName("sun.font.FontManager")
            .getMethod("findFont2D", String.class, int.class, int.class);
    findFont2D.invoke(fontManager, "Helvetica", Font.PLAIN, 0);

    hideFamilies(fontManager, unlisted ? UNLISTED : LUCIDA_GRANDE);
    if (findFont2D.invoke(fontManager, Font.DIALOG, Font.PLAIN, 0) != null) {
      System.out.println("SETUP the logical Dialog font is still registered");
      return;
    }

    if (args.length > 0 && args[0].equals("fallback")) {
      // A launcher without access to sun.font: no repair, only FrostWire's own fonts.
      try {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.getFontMetrics(PlatformFonts.dialog(Font.PLAIN, 13)).stringWidth("FrostWire");
        g.getFontMetrics(PlatformFonts.DIALOG_BOLD).stringWidth("FrostWire");
        g.dispose();
        System.out.println("OK " + PlatformFonts.dialog(Font.PLAIN, 13).getFamily());
      } catch (RuntimeException e) {
        System.out.println("CRASH " + e.getClass().getName());
      }
      return;
    }
    if (repair && !MacLogicalFonts.repairIfMissing()) {
      System.out.println("REPAIR returned false");
      return;
    }
    try {
      BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      // The splash label's font on macOS (AquaFonts' control font), then the logical fonts.
      for (String family :
          new String[] {
            "Lucida Grande",
            // FrostWire's sans serif font on macOS (PlatformFonts.sansSerif).
            "Helvetica",
            Font.DIALOG,
            Font.SANS_SERIF,
            Font.DIALOG_INPUT,
            Font.SERIF,
            Font.MONOSPACED
          }) {
        for (int style : new int[] {Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC}) {
          if (g.getFontMetrics(new Font(family, style, 13)).stringWidth("FrostWire") <= 0) {
            throw new IllegalStateException("no width: " + family + " " + style);
          }
        }
      }
      g.getFontMetrics(PlatformFonts.dialog(Font.BOLD, 12)).stringWidth("FrostWire");
      // An HTML label: its default style sheet asks for the SansSerif family.
      new JLabel("<html><b>Frost</b>Wire</html>").getPreferredSize();
      g.dispose();
      System.out.println("OK");
    } catch (RuntimeException e) {
      StackTraceElement top = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
      System.out.println(
          "CRASH "
              + e.getClass().getName()
              + " "
              + (top == null ? "?" : top.getClassName() + "." + top.getMethodName()));
    }
  }

  /** Lucida Grande and the logical fonts the JDK builds from it. */
  private static final java.util.Set<String> LUCIDA_GRANDE =
      java.util.Set.of("lucida grande", "dialog", "sansserif", "dialoginput");

  /**
   * What a native font listing that ended early leaves unregistered: also the families FrostWire
   * builds the logical fonts from instead, and Serif and Monospaced with theirs.
   */
  private static final java.util.Set<String> UNLISTED =
      java.util.Set.of(
          "lucida grande",
          "dialog",
          "sansserif",
          "dialoginput",
          "helvetica neue",
          "helvetica",
          "arial",
          "serif",
          "times",
          "times new roman",
          "monospaced",
          "menlo",
          "courier");

  /** Unregisters the families (lower case) and their fonts, and empties the lookup cache. */
  private static void hideFamilies(Object fontManager, java.util.Set<String> hidden)
      throws Exception {
    Class<?> fontFamily = Class.forName("sun.font.FontFamily");
    Map<?, ?> families = (Map<?, ?>) staticField(fontFamily, "familyNameMap");
    families.keySet().removeIf(hidden::contains);
    Class<?> font2D = Class.forName("sun.font.Font2D");
    Method familyName = font2D.getMethod("getFamilyName", Locale.class);
    removeFamilies((Map<?, ?>) field(fontManager, "genericFonts"), familyName, hidden);
    removeFamilies((Map<?, ?>) field(fontManager, "fullNameToFont"), familyName, hidden);
    ((Map<?, ?>) field(fontManager, "fontNameCache")).clear();
  }

  private static void removeFamilies(
      Map<?, ?> fonts, Method familyName, java.util.Set<String> hidden) {
    fonts
        .values()
        .removeIf(
            font -> {
              try {
                return hidden.contains(
                    ((String) familyName.invoke(font, Locale.ENGLISH)).toLowerCase(Locale.ENGLISH));
              } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
              }
            });
  }

  private static Object staticField(Class<?> type, String name) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(null);
  }

  /** The named field, declared by the object's class or one of its superclasses. */
  private static Object field(Object target, String name) throws Exception {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
      } catch (NoSuchFieldException e) {
        // Declared further up.
      }
    }
    throw new NoSuchFieldException(name);
  }
}
