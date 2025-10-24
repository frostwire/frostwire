package net.miginfocom.layout;

import javax.swing.*;
import java.util.HashMap;
/*
 * License (BSD):
 * ==============
 *
 * Copyright (c) 2004, Mikael Grev, MiG InfoCom AB. (miglayout (at) miginfocom (dot) com)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * Neither the name of the MiG InfoCom AB nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific
 * prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 * @version 1.0
 * @author Mikael Grev, MiG InfoCom AB
 *         Date: 2006-sep-08
 * @author Xxxx Xxxx, Xxxx  - Gnome support
 *         Date: 2008-jan-16
 */

/**
 * Currently handles Windows, Mac OS X, and GNOME spacing.
 */
public final class PlatformDefaults {
    /**
     * I value indicating that the size of the font for the container of the component
     * will be used as a base for calculating the logical pixel size. This is much as how
     * Windows calculated DLU (dialog units).
     *
     * @see net.miginfocom.layout.UnitValue#LPX
     * @see net.miginfocom.layout.UnitValue#LPY
     * @see #setLogicalPixelBase(int)
     */
    public static final int BASE_FONT_SIZE = 100;
    /**
     * I value indicating that the screen DPI will be used as a base for calculating the
     * logical pixel size.
     * <p>
     * This is the default value.
     *
     * @see net.miginfocom.layout.UnitValue#LPX
     * @see net.miginfocom.layout.UnitValue#LPY
     * @see #setLogicalPixelBase(int)
     * @see #setVerticalScaleFactor(Float)
     * @see #setHorizontalScaleFactor(Float)
     */
    public static final int BASE_SCALE_FACTOR = 101;
    private static final UnitValue LPX4 = new UnitValue(4, UnitValue.LPX, null);
    private static final UnitValue LPX6 = new UnitValue(6, UnitValue.LPX, null);
    private static final UnitValue LPX7 = new UnitValue(7, UnitValue.LPX, null);
    private static final UnitValue LPX9 = new UnitValue(9, UnitValue.LPX, null);
    private static final UnitValue LPX10 = new UnitValue(10, UnitValue.LPX, null);
    private static final UnitValue LPX11 = new UnitValue(11, UnitValue.LPX, null);
    private static final UnitValue LPX12 = new UnitValue(12, UnitValue.LPX, null);
    private static final UnitValue LPX14 = new UnitValue(14, UnitValue.LPX, null);
    private static final UnitValue LPX16 = new UnitValue(16, UnitValue.LPX, null);
    private static final UnitValue LPX18 = new UnitValue(18, UnitValue.LPX, null);
    private static final UnitValue LPX20 = new UnitValue(20, UnitValue.LPX, null);
    private static final UnitValue LPY4 = new UnitValue(4, UnitValue.LPY, null);
    private static final UnitValue LPY6 = new UnitValue(6, UnitValue.LPY, null);
    private static final UnitValue LPY7 = new UnitValue(7, UnitValue.LPY, null);
    private static final UnitValue LPY9 = new UnitValue(9, UnitValue.LPY, null);
    private static final UnitValue LPY10 = new UnitValue(10, UnitValue.LPY, null);
    private static final UnitValue LPY11 = new UnitValue(11, UnitValue.LPY, null);
    private static final UnitValue LPY12 = new UnitValue(12, UnitValue.LPY, null);
    private static final UnitValue LPY14 = new UnitValue(14, UnitValue.LPY, null);
    private static final UnitValue LPY16 = new UnitValue(16, UnitValue.LPY, null);
    private static final UnitValue LPY18 = new UnitValue(18, UnitValue.LPY, null);
    private static final UnitValue LPY20 = new UnitValue(20, UnitValue.LPY, null);
    private static final int WINDOWS_XP = 0;
    private static final int MAC_OSX = 1;
    private static final int GNOME = 2;
    // Used for holding values.
    private final static UnitValue[] PANEL_INS = new UnitValue[4];
    private final static UnitValue[] DIALOG_INS = new UnitValue[4];
    private static final HashMap<String, UnitValue> HOR_DEFS = new HashMap<>(32);
    private static final HashMap<String, UnitValue> VER_DEFS = new HashMap<>(32);
    private static int DEF_H_UNIT = UnitValue.LPX;
    private static int DEF_V_UNIT = UnitValue.LPY;
    private static volatile int MOD_COUNT;
    private static final Object MOD_COUNT_LOCK = new Object();
    private static int CUR_PLAF = WINDOWS_XP;
    private static String BUTTON_FORMAT = null;
    private static BoundSize DEF_VGAP = null, DEF_HGAP = null;
    private static BoundSize RELATED_X = null;
    private static BoundSize RELATED_Y = null;
    private static UnitValue BUTT_WIDTH = null;
    private static Float horScale = null, verScale = null;
    private static int LP_BASE = BASE_SCALE_FACTOR;
    private static final Integer BASE_DPI_FORCED = null;
    private static int BASE_DPI = 96;

    static {
        setPlatform(getCurrentPlatform());
        MOD_COUNT = 0;
    }

    private PlatformDefaults() {
    }

    private static void incModCount() {
        synchronized (MOD_COUNT_LOCK) {
            MOD_COUNT++;
        }
    }

    /**
     * Returns the platform that the JRE is running on currently.
     *
     * @return The platform that the JRE is running on currently. E.g. {@link #MAC_OSX}, {@link #WINDOWS_XP}, or {@link #GNOME}.
     */
    private static int getCurrentPlatform() {
        final String os = System.getProperty("os.name");
        if (os.startsWith("Mac OS")) {
            return MAC_OSX;
        } else if (os.startsWith("Linux")) {
            return GNOME;
        } else {
            return WINDOWS_XP;
        }
    }

    private static int getPlatformDPI(int plaf) {
        switch (plaf) {
            case WINDOWS_XP:
            case GNOME:
                return 96;
            case MAC_OSX:
                try {
                    return System.getProperty("java.version").compareTo("1.6") < 0 ? 72 : 96; // Default DPI was 72 prior to JSE 1.6
                } catch (Throwable t) {
                    return 72;
                }
            default:
                throw new IllegalArgumentException("Unknown platform: " + plaf);
        }
    }

    /**
     * Returns the current platform
     *
     * @return <code>PlatformDefaults.WINDOWS</code> or <code>PlatformDefaults.MAC_OSX</code>
     */
    public static int getPlatform() {
        return CUR_PLAF;
    }

    /**
     * Set the defaults to the default for the platform
     *
     * @param plaf The platform. <code>PlatformDefaults.WINDOWS_XP</code>,
     *             <code>PlatformDefaults.MAC_OSX</code>, or
     *             <code>PlatformDefaults.GNOME</code>.
     */
    private static void setPlatform(int plaf) {
        switch (plaf) {
            case WINDOWS_XP:
                setRelatedGap(LPX4, LPY4);
                setUnrelatedGap(LPX7, LPY9);
                setParagraphGap(LPX14, LPY14);
                setIndentGap(LPX9, LPY9);
                setGridCellGap(LPX4, LPY4);
                setMinimumButtonWidth(new UnitValue(75, UnitValue.LPX, null));
                setButtonOrder("L_E+U+YNBXOCAH_R");
                setDialogInsets(LPY11, LPX11, LPY11, LPX11);
                setPanelInsets(LPY7, LPX7, LPY7, LPX7);
                break;
            case MAC_OSX:
                setRelatedGap(LPX4, LPY4);
                setUnrelatedGap(LPX7, LPY9);
                setParagraphGap(LPX14, LPY14);
                setIndentGap(LPX10, LPY10);
                setGridCellGap(LPX4, LPY4);
                setMinimumButtonWidth(new UnitValue(68, UnitValue.LPX, null));
                setButtonOrder("L_HE+U+NYBXCOA_R");
                setDialogInsets(LPY14, LPX20, LPY20, LPX20);
                setPanelInsets(LPY16, LPX16, LPY16, LPX16);
//				setRelatedGap(LPX8, LPY8);
//				setUnrelatedGap(LPX12, LPY12);
//				setParagraphGap(LPX16, LPY16);
//				setIndentGap(LPX10, LPY10);
//				setGridCellGap(LPX8, LPY8);
//
//				setMinimumButtonWidth(new UnitValue(68, UnitValue.LPX, null));
//				setButtonOrder("L_HE+U+NYBXCOA_R");
//				setDialogInsets(LPY14, LPX20, LPY20, LPX20);
//				setPanelInsets(LPY16, LPX16, LPY16, LPX16);
                break;
            case GNOME:
                setRelatedGap(LPX6, LPY6);                    // GNOME HIG 8.2.3
                setUnrelatedGap(LPX12, LPY12);                // GNOME HIG 8.2.3
                setParagraphGap(LPX18, LPY18);                // GNOME HIG 8.2.3
                setIndentGap(LPX12, LPY12);                   // GNOME HIG 8.2.3
                setGridCellGap(LPX6, LPY6);                   // GNOME HIG 8.2.3
                // GtkButtonBox, child-min-width property default value
                setMinimumButtonWidth(new UnitValue(85, UnitValue.LPX, null));
                setButtonOrder("L_HE+UNYACBXIO_R");           // GNOME HIG 3.4.2, 3.7.1
                setDialogInsets(LPY12, LPX12, LPY12, LPX12);  // GNOME HIG 3.4.3
                setPanelInsets(LPY6, LPX6, LPY6, LPX6);       // ???
                break;
            default:
                throw new IllegalArgumentException("Unknown platform: " + plaf);
        }
        CUR_PLAF = plaf;
        BASE_DPI = BASE_DPI_FORCED != null ? BASE_DPI_FORCED : getPlatformDPI(plaf);
    }

    public static int getDefaultDPI() {
        return BASE_DPI;
    }

    /**
     * The forced scale factor that all screen relative units (e.g. millimeters, inches and logical pixels) will be multiplied
     * with. If <code>null</code> this will default to a scale that will scale the current screen to the default screen resolution
     * (72 DPI for Mac and 92 DPI for Windows).
     *
     * @return The forced scale or <code>null</code> for default scaling.
     * @see #getHorizontalScaleFactor()
     * @see ComponentWrapper#getHorizontalScreenDPI()
     */
    public static Float getHorizontalScaleFactor() {
        return horScale;
    }

    /**
     * The forced scale factor that all screen relative units (e.g. millimeters, inches and logical pixels) will be multiplied
     * with. If <code>null</code> this will default to a scale that will scale the current screen to the default screen resolution
     * (72 DPI for Mac and 92 DPI for Windows).
     *
     * @param f The forced scale or <code>null</code> for default scaling.
     * @see #getHorizontalScaleFactor()
     * @see ComponentWrapper#getHorizontalScreenDPI()
     */
    @SuppressWarnings("unused")
    private static void setHorizontalScaleFactor(Float f) {
        if (!LayoutUtil.equals(horScale, f)) {
            horScale = f;
            incModCount();
        }
    }

    /**
     * The forced scale factor that all screen relative units (e.g. millimeters, inches and logical pixels) will be multiplied
     * with. If <code>null</code> this will default to a scale that will scale the current screen to the default screen resolution
     * (72 DPI for Mac and 92 DPI for Windows).
     *
     * @return The forced scale or <code>null</code> for default scaling.
     * @see #getHorizontalScaleFactor()
     * @see ComponentWrapper#getVerticalScreenDPI()
     */
    public static Float getVerticalScaleFactor() {
        return verScale;
    }

    /**
     * The forced scale factor that all screen relative units (e.g. millimeters, inches and logical pixels) will be multiplied
     * with. If <code>null</code> this will default to a scale that will scale the current screen to the default screen resolution
     * (72 DPI for Mac and 92 DPI for Windows).
     *
     * @param f The forced scale or <code>null</code> for default scaling.
     * @see #getHorizontalScaleFactor()
     * @see ComponentWrapper#getVerticalScreenDPI()
     */
    @SuppressWarnings("unused")
    private static void setVerticalScaleFactor(Float f) {
        if (!LayoutUtil.equals(verScale, f)) {
            verScale = f;
            incModCount();
        }
    }

    /**
     * What base value should be used to calculate logical pixel sizes.
     *
     * @return The current base. Default is {@link #BASE_SCALE_FACTOR}
     * @see #BASE_FONT_SIZE
     * @see #BASE_SCALE_FACTOR
     */
    public static int getLogicalPixelBase() {
        return LP_BASE;
    }

    /**
     * What base value should be used to calculate logical pixel sizes.
     *
     * @param base The new base. Default is {@link #BASE_SCALE_FACTOR}
     * @see #BASE_FONT_SIZE
     * @see #BASE_SCALE_FACTOR
     */
    @SuppressWarnings("unused")
    private static void setLogicalPixelBase(int base) {
        if (LP_BASE != base) {
            if (base < BASE_FONT_SIZE || base > BASE_SCALE_FACTOR)
                throw new IllegalArgumentException("Unrecognized base: " + base);
            LP_BASE = base;
            incModCount();
        }
    }

    /**
     * Sets gap value for components that are "related".
     *
     * @param x The value that will be transformed to pixels. If <code>null</code> the current value will not change.
     * @param y The value that will be transformed to pixels. If <code>null</code> the current value will not change.
     */
    private static void setRelatedGap(UnitValue x, UnitValue y) {
        setUnitValue(new String[]{"r", "rel", "related"}, x, y);
        RELATED_X = new BoundSize(x, x, null, "rel:rel");
        RELATED_Y = new BoundSize(y, y, null, "rel:rel");
    }

    /**
     * Sets gap value for components that are "unrelated".
     *
     * @param x The value that will be transformed to pixels. If <code>null</code> the current value will not change.
     * @param y The value that will be transformed to pixels. If <code>null</code> the current value will not change.
     */
    private static void setUnrelatedGap(UnitValue x, UnitValue y) {
        setUnitValue(new String[]{"u", "unrel", "unrelated"}, x, y);
    }

    /**
     * Sets paragraph gap value for components.
     *
     * @param x The value that will be transformed to pixels. If <code>null</code> the current value will not change.
     * @param y The value that will be transformed to pixels. If <code>null</code> the current value will not change.
     */
    private static void setParagraphGap(UnitValue x, UnitValue y) {
        setUnitValue(new String[]{"p", "para", "paragraph"}, x, y);
    }

    /**
     * Sets gap value for components that are "intended".
     *
     * @param x The value that will be transformed to pixels. If <code>null</code> the current value will not change.
     * @param y The value that will be transformed to pixels. If <code>null</code> the current value will not change.
     */
    private static void setIndentGap(UnitValue x, UnitValue y) {
        setUnitValue(new String[]{"i", "ind", "indent"}, x, y);
    }

    /**
     * Sets gap between two cells in the grid. Note that this is not a gap between component IN a cell, that has to be set
     * on the component constraints. The value will be the min and preferred size of the gap.
     *
     * @param x The value that will be transformed to pixels. If <code>null</code> the current value will not change.
     * @param y The value that will be transformed to pixels. If <code>null</code> the current value will not change.
     */
    private static void setGridCellGap(UnitValue x, UnitValue y) {
        if (x != null)
            DEF_HGAP = new BoundSize(x, x, null, null);
        if (y != null)
            DEF_VGAP = new BoundSize(y, y, null, null);
        incModCount();
    }

    /**
     * Returns the recommended minimum button width depending on the current set platform.
     *
     * @return The recommended minimum button width depending on the current set platform.
     */
    static UnitValue getMinimumButtonWidth() {
        return BUTT_WIDTH;
    }

    /**
     * Sets the recommended minimum button width.
     *
     * @param width The recommended minimum button width.
     */
    private static void setMinimumButtonWidth(UnitValue width) {
        BUTT_WIDTH = width;
        incModCount();
    }

    /**
     * Sets the unit value associated with a unit string. This may be used to store values for new unit strings
     * or modify old. Note that if a built in unit (such as "related") is modified all versions of it must be
     * set (I.e. "r", "rel" and "related"). The build in values will be reset to the default ones if the platform
     * is re-set.
     *
     * @param unitStrings The unit strings. E.g. "mu", "myunit". Will be converted to lower case and trimmed. Not <code>null</code>.
     * @param x           The value for the horizontal dimension. If <code>null</code> the value is not changed.
     * @param y           The value for the vertical dimension. Might be same object as for <code>x</code>. If <code>null</code> the value is not changed.
     */
    private static void setUnitValue(String[] unitStrings, UnitValue x, UnitValue y) {
        for (String unitString : unitStrings) {
            String s = unitString.toLowerCase().trim();
            if (x != null)
                HOR_DEFS.put(s, x);
            if (y != null)
                VER_DEFS.put(s, y);
        }
        incModCount();
    }

    /**
     * Understands ("r", "rel", "related") OR ("u", "unrel", "unrelated") OR ("i", "ind", "indent") OR ("p", "para", "paragraph").
     */
    static int convertToPixels(float value, String unit, boolean isHor, float ref, ContainerWrapper parent, ComponentWrapper comp) {
        UnitValue uv = (isHor ? HOR_DEFS : VER_DEFS).get(unit);
        return uv != null ? Math.round(value * uv.getPixels(ref, parent, comp)) : UnitConverter.UNABLE;
    }

    /**
     * Returns the order for the typical buttons in a standard button bar. It is one letter per button type.
     *
     * @return The button order.
     * @see #setButtonOrder(String)
     */
    static String getButtonOrder() {
        return BUTTON_FORMAT;
    }

    /**
     * Sets the order for the typical buttons in a standard button bar. It is one letter per button type.
     * <p>
     * Letter in upper case will get the minimum button width that the {@link #getMinimumButtonWidth()} specifies
     * and letters in lower case will get the width the current look&feel specifies.
     * <p>
     * Gaps will never be added to before the first component or after the last component. However, '+' (push) will be
     * applied before and after as well, but with a minimum size of 0 if first/last so there will not be a gap
     * before or after.
     * <p>
     * If gaps are explicitly set on buttons they will never be reduced, but they may be increased.
     * <p>
     * These are the characters that can be used:
     * <ul>
     * <li><code>'L'</code> - Buttons with this style tag will staticall end up on the left end of the bar.
     * <li><code>'R'</code> - Buttons with this style tag will staticall end up on the right end of the bar.
     * <li><code>'H'</code> - A tag for the "help" button that normally is supposed to be on the right.
     * <li><code>'E'</code> - A tag for the "help2" button that normally is supposed to be on the left.
     * <li><code>'Y'</code> - A tag for the "yes" button.
     * <li><code>'N'</code> - A tag for the "no" button.
     * <li><code>'X'</code> - A tag for the "next >" or "forward >" button.
     * <li><code>'B'</code> - A tag for the "< back>" or "< previous" button.
     * <li><code>'I'</code> - A tag for the "finish".
     * <li><code>'A'</code> - A tag for the "apply" button.
     * <li><code>'C'</code> - A tag for the "cancel" or "close" button.
     * <li><code>'O'</code> - A tag for the "ok" or "done" button.
     * <li><code>'U'</code> - All Uncategorized, Other, or "Unknown" buttons. Tag will be "other".
     * <li><code>'+'</code> - A glue push gap that will take as much space as it can and at least an "unrelated" gap. (Platform dependant)
     * <li><code>'_'</code> - (underscore) An "unrelated" gap. (Platform dependant)
     * </ul>
     * <p>
     * Even though the style tags are normally applied to buttons this works with all components.
     * <p>
     * The normal style for MAC OS X is <code>"L_HE+U+FBI_NYCOA_R"</code>,
     * for Windows is <code>"L_E+U+FBI_YNOCAH_R"</code>, and for GNOME is
     * <code>"L_HE+UNYACBXIO_R"</code>.
     *
     * @param order The new button order for the current platform.
     */
    private static void setButtonOrder(String order) {
        BUTTON_FORMAT = order;
        incModCount();
    }

    /**
     * Returns the tag (used in the {@link CC}) for a char. The char is same as used in {@link #getButtonOrder()}.
     *
     * @param c The char. Must be lower case!
     * @return The tag that corresponds to the char or <code>null</code> if the char is unrecognized.
     */
    static String getTagForChar(char c) {
        switch (c) {
            case 'o':
                return "ok";
            case 'c':
                return "cancel";
            case 'h':
                return "help";
            case 'e':
                return "help2";
            case 'y':
                return "yes";
            case 'n':
                return "no";
            case 'a':
                return "apply";
            case 'x':
                return "next";  // a.k.a forward
            case 'b':
                return "back";  // a.k.a. previous
            case 'i':
                return "finish";
            case 'l':
                return "left";
            case 'r':
                return "right";
            case 'u':
                return "other";
            default:
                return null;
        }
    }

    /**
     * Returns the platform recommended inter-cell gap in the horizontal (x) dimension..
     *
     * @return The platform recommended inter-cell gap in the horizontal (x) dimension..
     */
    static BoundSize getGridGapX() {
        return DEF_HGAP;
    }

    /**
     * Returns the platform recommended inter-cell gap in the vertical (x) dimension..
     *
     * @return The platform recommended inter-cell gap in the vertical (x) dimension..
     */
    static BoundSize getGridGapY() {
        return DEF_VGAP;
    }

    /**
     * Returns the default dialog inset depending of the current platform.
     *
     * @param side top == 0, left == 1, bottom = 2, right = 3.
     * @return The inset. Never <code>null</code>.
     */
    static UnitValue getDialogInsets(int side) {
        return DIALOG_INS[side];
    }

    /**
     * Sets the default insets for a dialog. Values that are null will not be changed.
     *
     * @param top    The top inset. May be <code>null</code>.
     * @param left   The left inset. May be <code>null</code>.
     * @param bottom The bottom inset. May be <code>null</code>.
     * @param right  The right inset. May be <code>null</code>.
     */
    private static void setDialogInsets(UnitValue top, UnitValue left, UnitValue bottom, UnitValue right) {
        if (top != null)
            DIALOG_INS[0] = top;
        if (left != null)
            DIALOG_INS[1] = left;
        if (bottom != null)
            DIALOG_INS[2] = bottom;
        if (right != null)
            DIALOG_INS[3] = right;
        incModCount();
    }

    /**
     * Returns the default panel inset depending of the current platform.
     *
     * @param side top == 0, left == 1, bottom = 2, right = 3.
     * @return The inset. Never <code>null</code>.
     */
    static UnitValue getPanelInsets(int side) {
        return PANEL_INS[side];
    }

    /**
     * Sets the default insets for a dialog. Values that are null will not be changed.
     *
     * @param top    The top inset. May be <code>null</code>.
     * @param left   The left inset. May be <code>null</code>.
     * @param bottom The bottom inset. May be <code>null</code>.
     * @param right  The right inset. May be <code>null</code>.
     */
    private static void setPanelInsets(UnitValue top, UnitValue left, UnitValue bottom, UnitValue right) {
        if (top != null)
            PANEL_INS[0] = top;
        if (left != null)
            PANEL_INS[1] = left;
        if (bottom != null)
            PANEL_INS[2] = bottom;
        if (right != null)
            PANEL_INS[3] = right;
        incModCount();
    }

    /**
     * Returns the percentage used for alignment for labels (0 is left, 50 is center and 100 is right).
     *
     * @return The percentage used for alignment for labels
     */
    static float getLabelAlignPercentage() {
        return CUR_PLAF == MAC_OSX ? 1f : 0f;
    }

    /**
     * Returns the default gap between two components that <b>are in the same cell</b>.
     *
     * @param adjacentComp The adjacent component if any. May be <code>null</code>.
     * @param adjacentSide What side the <code>adjacentComp</code> is on. {@link javax.swing.SwingUtilities#TOP} or
     *                     {@link javax.swing.SwingUtilities#LEFT} or {@link javax.swing.SwingUtilities#BOTTOM} or {@link javax.swing.SwingUtilities#RIGHT}.
     * @param tag          The tag string that the component might be tagged with in the component constraints. May be <code>null</code>.
     * @param isLTR        If it is left-to-right.
     * @return The default gap between two components or <code>null</code> if there should be no gap.
     */
    static BoundSize getDefaultComponentGap(ComponentWrapper adjacentComp, int adjacentSide, @SuppressWarnings("unused") String tag, @SuppressWarnings("unused") boolean isLTR) {
        if (adjacentComp == null)
            return null;
        return (adjacentSide == SwingConstants.LEFT || adjacentSide == SwingConstants.RIGHT) ? RELATED_X : RELATED_Y;
    }

    /**
     * Returns how many times the defaults has been changed. This can be used as a light weight check to
     * see if layout caches needs to be refreshed.
     *
     * @return How many times the defaults has been changed.
     */
    public static int getModCount() {
        return MOD_COUNT;
    }

    /**
     * Returns the current default unit. The default unit is the unit used if no unit is set. E.g. "width 10".
     *
     * @return The current default unit.
     * @see UnitValue#PIXEL
     * @see UnitValue#LPX
     */
    static int getDefaultHorizontalUnit() {
        return DEF_H_UNIT;
    }

    /**
     * Sets the default unit. The default unit is the unit used if no unit is set. E.g. "width 10".
     *
     * @param unit The new default unit.
     * @see UnitValue#PIXEL
     * @see UnitValue#LPX
     */
    static void setDefaultHorizontalUnit(int unit) {
        if (unit < UnitValue.PIXEL || unit > UnitValue.LABEL_ALIGN)
            throw new IllegalArgumentException("Illegal Unit: " + unit);
        if (DEF_H_UNIT != unit) {
            DEF_H_UNIT = unit;
            incModCount();
        }
    }

    /**
     * Returns the current default unit. The default unit is the unit used if no unit is set. E.g. "width 10".
     *
     * @return The current default unit.
     * @see UnitValue#PIXEL
     * @see UnitValue#LPY
     */
    static int getDefaultVerticalUnit() {
        return DEF_V_UNIT;
    }

    /**
     * Sets the default unit. The default unit is the unit used if no unit is set. E.g. "width 10".
     *
     * @param unit The new default unit.
     * @see UnitValue#PIXEL
     * @see UnitValue#LPY
     */
    static void setDefaultVerticalUnit(int unit) {
        if (unit < UnitValue.PIXEL || unit > UnitValue.LABEL_ALIGN)
            throw new IllegalArgumentException("Illegal Unit: " + unit);
        if (DEF_V_UNIT != unit) {
            DEF_V_UNIT = unit;
            incModCount();
        }
    }

    /**
     * The default alignment for rows. Pre v3.5 this was <code>false</code> but now it is
     * <code>true</code>.
     *
     * @return The current value. Default is <code>true</code>.
     * @since 3.5
     */
    static boolean getDefaultRowAlignmentBaseline() {
        return true;
    }

    /**
     * Tells all layout manager instances to revalidate and recalculated everything.
     */
    @SuppressWarnings("unused")
    public void invalidate() {
        incModCount();
    }
}
