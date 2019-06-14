/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.theme;

import com.apple.laf.AquaFonts;
import com.frostwire.gui.tabs.SearchTab;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIMediator.Tabs;
import com.limegroup.gnutella.gui.search.SearchMediator;
import com.limegroup.gnutella.gui.tables.LimeJTable;
import com.limegroup.gnutella.settings.ApplicationSettings;
import org.limewire.util.OSUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.table.TableCellEditor;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Class that mediates between themes and FrostWire.
 *
 * @author gubatron
 * @author aldenml
 */
public final class ThemeMediator {
    public static final Font DIALOG_FONT = new Font(Font.DIALOG, Font.PLAIN, 12);
    public static final Color LIGHT_BORDER_COLOR = SkinColors.GENERAL_BORDER_COLOR;
    public static final Color TABLE_ALTERNATE_ROW_COLOR = SkinColors.TABLE_ALTERNATE_ROW_COLOR;
    public static final Color TABLE_SELECTED_BACKGROUND_ROW_COLOR = SkinColors.TABLE_SELECTED_BACKGROUND_ROW_COLOR;
    public static final Color TAB_BUTTON_FOREGROUND_COLOR = new Color(0xFFFFFF);
    public static final String SKIN_PROPERTY_DARK_BOX_BACKGROUND = "skin_property_dark_box_background";
    private static final Logger LOG = Logger.getLogger(ThemeMediator.class);
    private static final int TABLE_FONT_SIZE_MIN = 10;
    private static final int TABLE_FONT_SIZE_MAX = 20;
    private static final Color APPLICATION_HEADER_SEPARATOR_COLOR = new Color(0x295164);

    private ThemeMediator() {
    }

    public static void changeTheme() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    try {
                        UIManager.setLookAndFeel(new NimbusLookAndFeel() {
                            @Override
                            public UIDefaults getDefaults() {
                                return modifyNimbusDefaults(super.getDefaults());
                            }
                        });
                        applySkinSettings();
                        setupGlobalKeyManager();
                    } catch (Throwable e) {
                        throw new RuntimeException("Unable to change the L&F", e);
                    }
                }
            });
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException) {
                e = ((InvocationTargetException) e).getTargetException();
            }
            throw new RuntimeException("Unable to change the L&F", e);
        }
    }

    public static Font fixLabelFont(JLabel label) {
        return fixComponentFont(label, label.getText());
    }

    static Font fixComponentFont(JComponent c, Object msg) {
        Font oldFont = null;
        if (c != null && OSUtils.isWindows()) {
            Font currentFont = c.getFont();
            if (currentFont != null && !canDisplayMessage(currentFont, msg)) {
                oldFont = currentFont;
                c.setFont(ThemeMediator.DIALOG_FONT);
            }
        }
        return oldFont;
    }

    public static TitledBorder createTitledBorder(String title) {
        return new SkinTitledBorder(title);
    }

    private static JSeparator createSeparator(int orientation, Color color) {
        JSeparator sep = new JSeparator(orientation);
        UIDefaults defaults = new UIDefaults();
        defaults.put("Separator[Enabled].backgroundPainter", new SkinSeparatorBackgroundPainter(SkinSeparatorBackgroundPainter.State.Enabled, color));
        defaults.put("Separator.thickness", 1);
        sep.putClientProperty("Nimbus.Overrides.InheritDefaults", Boolean.TRUE);
        sep.putClientProperty("Nimbus.Overrides", defaults);
        return sep;
    }

    private static JSeparator createVerticalSeparator(Color color) {
        return createSeparator(SwingConstants.VERTICAL, color);
    }

    public static JSeparator createAppHeaderSeparator() {
        return createVerticalSeparator(APPLICATION_HEADER_SEPARATOR_COLOR);
    }

    private static Font getDefaultFont() {
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        return defaults.getFont("defaultFont");
    }

    public static Font getCurrentTableFont() {
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        return defaults.getFont("Table.font");
    }

    public static void modifyTablesFont(int delta) {
        int currentSize = ApplicationSettings.GUI_TABLES_FONT_SIZE.getValue();
        if (currentSize == 0 || delta == 0) {
            currentSize = getDefaultFont().getSize();
        }
        int newSize = currentSize + delta;
        if (TABLE_FONT_SIZE_MIN <= newSize && newSize <= TABLE_FONT_SIZE_MAX) {
            ApplicationSettings.GUI_TABLES_FONT_SIZE.setValue(currentSize + delta);
            Font f = setupTableFont(UIManager.getLookAndFeelDefaults());
            changeTablesFont(f);
        }
    }

    public static void fixKeyStrokes(JTextComponent textField) {
        if (OSUtils.isMacOSX()) {
            fixKeyStroke(textField, "copy", KeyEvent.VK_C, 0);
            fixKeyStroke(textField, "paste", KeyEvent.VK_V, 0);
            fixKeyStroke(textField, "cut", KeyEvent.VK_X, 0);
            fixKeyStroke(textField, "caret-begin-line", KeyEvent.VK_LEFT, 0);
            fixKeyStroke(textField, "caret-end-line", KeyEvent.VK_RIGHT, 0);
            fixKeyStroke(textField, "selection-begin-line", KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK);
            fixKeyStroke(textField, "selection-end-line", KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK);
            fixKeyStroke(textField, "select-all", KeyEvent.VK_A, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        }
    }

    static void testComponentCreationThreadingViolation() {
        if (!SwingUtilities.isEventDispatchThread()) {
            UiThreadingViolationException uiThreadingViolationError = new UiThreadingViolationException("Component creation must be done on Event Dispatch Thread");
            uiThreadingViolationError.printStackTrace(System.err);
            throw uiThreadingViolationError;
        }
    }

    private static void fixKeyStroke(JTextComponent textField, String name, int vk, int mask) {
        Action action = textField.getActionMap().get(name);
        if (action != null) {
            InputMap[] inputMaps = new InputMap[]{textField.getInputMap(JComponent.WHEN_FOCUSED), textField.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT), textField.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)};
            for (InputMap i : inputMaps) {
                i.put(KeyStroke.getKeyStroke(vk, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | mask), action);
            }
        }
    }

    private static void changeTablesFont(Font f) {
        Frame[] frames = Frame.getFrames();
        for (Frame frame : frames) {
            changeTablesFont(frame, f);
        }
    }

    // Where Tab shortcuts, Font Size changes,
    private static void setupGlobalKeyManager() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            // handle Ctrl+- for font change in tables
            //System.out.println(e.getSource());
            if (e.getID() == KeyEvent.KEY_PRESSED && (e.isMetaDown() || e.isControlDown())) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_EQUALS:
                        if (OSUtils.isMacOSX()) {
                            if (e.getKeyChar() == '+') {
                                modifyTablesFont(1);
                            }
                        } else {
                            modifyTablesFont(1);
                        }
                        return true;
                    case KeyEvent.VK_MINUS:
                        modifyTablesFont(-1);
                        return true;
                    case KeyEvent.VK_0:
                        modifyTablesFont(0);
                        return true;
                    case KeyEvent.VK_W:
                        closeCurrentSearchTab(e);
                }
                //Ctrl+Tab, Ctrl+Shift Tab to switch search tabs (Windows/Firefox Style)
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_TAB) {
                    int offset = (e.isShiftDown()) ? -1 : 1;
                    SearchMediator.getSearchResultDisplayer().switchToTabByOffset(offset);
                    return true;
                }
                //Cmd+Shift+[, Cmd+Shift+] Chrome Style.
                if (e.isMetaDown() && e.isShiftDown()) {
                    int offset = 0;
                    if (e.getKeyCode() == KeyEvent.VK_OPEN_BRACKET) {
                        offset = -1;
                    } else if (e.getKeyCode() == KeyEvent.VK_CLOSE_BRACKET) {
                        offset = 1;
                    }
                    if (offset != 0) {
                        SearchMediator.getSearchResultDisplayer().switchToTabByOffset(offset);
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private static void closeCurrentSearchTab(KeyEvent e) {
        if (e.getSource() instanceof Component) {
            Window eventParentWindow = SwingUtilities.getWindowAncestor((Component) e.getSource());
            if (GUIMediator.getAppFrame().equals(eventParentWindow)) {
                SearchTab searchTab = (SearchTab) GUIMediator.instance().getTab(Tabs.SEARCH);
                if (searchTab.getComponent().isVisible()) {
                    SearchMediator.getSearchResultDisplayer().closeCurrentTab();
                }
            }
        }
    }

    private static void changeTablesFont(Container c, Font f) {
        Component[] comps = c.getComponents();
        for (Component comp : comps) {
            if (comp instanceof LimeJTable) {
                changeTableFont((JTable) comp, f);
            } else if (comp instanceof Container) {
                changeTablesFont((Container) comp, f);
            }
        }
    }

    private static void changeTableFont(JTable table, Font f) {
        UIDefaults nimbusOverrides = new UIDefaults();
        nimbusOverrides.put("Table.font", new FontUIResource(f));
        table.putClientProperty("Nimbus.Overrides", nimbusOverrides);
        TableCellEditor editor = table.getCellEditor();
        if (editor != null) {
            editor.cancelCellEditing();
        }
        FontMetrics fm = table.getFontMetrics(f);
        int h = fm.getHeight() + 8;
        table.setRowHeight(h);
        SwingUtilities.updateComponentTreeUI(table);
    }

    private static void applyCommonSkinUI() {
        UIManager.put("PopupMenuUI", "com.frostwire.gui.theme.SkinPopupMenuUI");
        UIManager.put("MenuItemUI", "com.frostwire.gui.theme.SkinMenuItemUI");
        UIManager.put("MenuUI", "com.frostwire.gui.theme.SkinMenuUI");
        UIManager.put("CheckBoxMenuItemUI", "com.frostwire.gui.theme.SkinCheckBoxMenuItemUI");
        UIManager.put("MenuBarUI", "com.frostwire.gui.theme.SkinMenuBarUI");
        UIManager.put("RadioButtonMenuItemUI", "com.frostwire.gui.theme.SkinRadioButtonMenuItemUI");
        UIManager.put("PopupMenuSeparatorUI", "com.frostwire.gui.theme.SkinPopupMenuSeparatorUI");
        UIManager.put("FileChooserUI", "com.frostwire.gui.theme.SkinFileChooserUI");
        //UIManager.put("FileChooserUI", "javax.swing.plaf.FileChooserUI");
        UIManager.put("TabbedPaneUI", "com.frostwire.gui.theme.SkinTabbedPaneUI");
        UIManager.put("OptionPaneUI", "com.frostwire.gui.theme.SkinOptionPaneUI");
        UIManager.put("LabelUI", "com.frostwire.gui.theme.SkinLabelUI");
        UIManager.put("ProgressBarUI", "com.frostwire.gui.theme.SkinProgressBarUI");
        UIManager.put("PanelUI", "com.frostwire.gui.theme.SkinPanelUI");
        UIManager.put("ScrollBarUI", "com.frostwire.gui.theme.SkinScrollBarUI");
        UIManager.put("ScrollPaneUI", "com.frostwire.gui.theme.SkinScrollPaneUI");
        UIManager.put("SplitPaneUI", "com.frostwire.gui.theme.SkinSplitPaneUI");
        UIManager.put("ApplicationHeaderUI", "com.frostwire.gui.theme.SkinApplicationHeaderUI");
        UIManager.put("MultilineToolTipUI", "com.frostwire.gui.theme.SkinMultilineToolTipUI");
        UIManager.put("TreeUI", "com.frostwire.gui.theme.SkinTreeUI");
        UIManager.put("TextFieldUI", "com.frostwire.gui.theme.SkinTextFieldUI");
        UIManager.put("RangeSliderUI", "com.frostwire.gui.theme.SkinRangeSliderUI");
        UIManager.put("TableUI", "com.frostwire.gui.theme.SkinTableUI");
        UIManager.put("RadioButtonUI", "com.frostwire.gui.theme.SkinRadioButtonUI");
    }

    private static FontUIResource getControlFont() {
        FontUIResource font = null;
        if (OSUtils.isWindows()) {
            Font recommendedFont = fixWindowsOSFont();
            if (recommendedFont != null) {
                font = new FontUIResource(recommendedFont);
            }
        } else if (OSUtils.isMacOSX()) {
            font = AquaFonts.getControlTextFont();
        } else if (OSUtils.isLinux()) {
            Font recommendedFont = fixLinuxOSFont();
            if (recommendedFont != null) {
                font = new FontUIResource(recommendedFont);
            }
        }
        return font;
    }

    private static String getRecommendedFontName() {
        String fontName = null;
        String language = ApplicationSettings.getLanguage();
        if (language != null) {
            if (language.startsWith("ja")) {
                //Meiryo for Japanese
                fontName = "Meiryo";
            } else if (language.startsWith("ko")) {
                //Malgun Gothic for Korean
                fontName = "Malgun Gothic";
            } else if (language.startsWith("zh")) {
                //Microsoft JhengHei for Chinese (Traditional)
                //Microsoft YaHei for Chinese (Simplified)
                fontName = "Microsoft JhengHei";
            } else if (language.startsWith("he")) {
                //Gisha for Hebrew
                fontName = "Gisha";
            } else if (language.startsWith("th")) {
                //Leelawadee for Thai
                fontName = "Leelawadee";
            }
        }
        return fontName;
    }

    private static boolean canDisplayMessage(Font f, Object msg) {
        boolean result = true;
        if (msg instanceof String) {
            String s = (String) msg;
            result = f.canDisplayUpTo(s) == -1;
        }
        return result;
    }

    private static void applySkinSettings() {
        applyCommonSkinUI();
        // NOTE: this was necessary in old versions of java 7, it was unmodified
        // when we migrated to java 8, but the classes has been removed in
        // java 9.
        //fixAAFontSettings();
        UIManager.put("Tree.leafIcon", UIManager.getIcon("Tree.closedIcon"));
        // remove split pane borders
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        if (!OSUtils.isMacOSX()) {
            UIManager.put("Table.focusRowHighlightBorder", UIManager.get("Table.focusCellHighlightBorder"));
        }
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder(1, 1, 1, 1));
        // Add a bold text version of simple text.
        Font normal = UIManager.getFont("Table.font");
        FontUIResource bold = new FontUIResource(normal.getName(), Font.BOLD, normal.getSize());
        UIManager.put("Table.font.bold", bold);
        UIManager.put("Tree.rowHeight", 0);
    }
//    private static void fixAAFontSettings() {
//        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
//        boolean lafCond = SwingUtilities2.isLocalDisplay();
//        Object aaTextInfo = SwingUtilities2.AATextInfo.getAATextInfo(lafCond);
//        defaults.put(SwingUtilities2.AA_TEXT_PROPERTY_KEY, aaTextInfo);
//    }

    // windows font policy http://msdn.microsoft.com/en-us/library/windows/desktop/aa511282.aspx
    // table of languages http://msdn.microsoft.com/en-us/library/ee825488(v=cs.20).aspx
    private static Font fixWindowsOSFont() {
        Font font = null;
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Method method = Toolkit.class.getDeclaredMethod("setDesktopProperty", String.class, Object.class);
            method.setAccessible(true);
            String fontName = ThemeMediator.getRecommendedFontName();
            if (fontName != null) {
                font = new Font(fontName, Font.PLAIN, 12);
                method.invoke(toolkit, "win.icon.font", font);
                //SubstanceLookAndFeel.setFontPolicy(SubstanceFontUtilities.getDefaultFontPolicy());
            }
        } catch (Throwable e) {
            LOG.error("Error fixing font", e);
        }
        return font;
    }

    private static Font fixLinuxOSFont() {
        Font font = null;
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Method method = Toolkit.class.getDeclaredMethod("setDesktopProperty", String.class, Object.class);
            method.setAccessible(true);
            String fontName = ThemeMediator.getRecommendedFontName();
            if (fontName != null) {
                // linux is hardcoded to Dialog
                fontName = "Dialog";
                font = new Font(fontName, Font.PLAIN, 12);
                method.invoke(toolkit, "gnome.Gtk/FontName", fontName);
                //SubstanceLookAndFeel.setFontPolicy(SubstanceFontUtilities.getDefaultFontPolicy());
            }
        } catch (Throwable e) {
            LOG.error("Error fixing font", e);
        }
        return font;
    }

    private static UIDefaults modifyNimbusDefaults(UIDefaults defaults) {
        defaults.put("control", SkinColors.LIGHT_BACKGROUND_COLOR);
        //defaults.put("nimbusBase", new Color(SkinColors.GENERAL_BORDER_COLOR.getRGB()));
        defaults.put("nimbusSelection", SkinColors.TABLE_SELECTED_BACKGROUND_ROW_COLOR);
        // font color
        defaults.put("text", SkinColors.TEXT_FONT_FOREGROUND_COLOR);
        defaults.put("controlText", SkinColors.TEXT_FONT_FOREGROUND_COLOR);
        defaults.put("infoText", SkinColors.TEXT_FONT_FOREGROUND_COLOR);
        defaults.put("menuText", SkinColors.TEXT_FONT_FOREGROUND_COLOR);
        defaults.put("textForeground", SkinColors.TEXT_FONT_FOREGROUND_COLOR);
        FontUIResource font = getControlFont();
        if (font != null) {
            defaults.put("defaultFont", font);
        }
        defaults.put("Panel.background", SkinColors.LIGHT_BACKGROUND_COLOR);
        // progressbar
        int paddingEnabled = defaults.getInt("ProgressBar[Enabled+Indeterminate].progressPadding");
        int paddingDisabled = defaults.getInt("ProgressBar[Disabled+Indeterminate].progressPadding");
        defaults.put("ProgressBar[Enabled].foregroundPainter", new SkinProgressBarPainter(SkinProgressBarPainter.State.Enabled, paddingEnabled));
        defaults.put("ProgressBar[Enabled+Finished].foregroundPainter", new SkinProgressBarPainter(SkinProgressBarPainter.State.Enabled, paddingEnabled));
        defaults.put("ProgressBar[Enabled+Indeterminate].foregroundPainter", new SkinProgressBarPainter(SkinProgressBarPainter.State.EnabledIndeterminate, paddingEnabled));
        defaults.put("ProgressBar[Disabled].foregroundPainter", new SkinProgressBarPainter(SkinProgressBarPainter.State.Disabled, paddingDisabled));
        defaults.put("ProgressBar[Disabled+Finished].foregroundPainter", new SkinProgressBarPainter(SkinProgressBarPainter.State.Disabled, paddingDisabled));
        defaults.put("ProgressBar[Disabled+Indeterminate].foregroundPainter", new SkinProgressBarPainter(SkinProgressBarPainter.State.DisabledIndeterminate, paddingDisabled));
        // scrollbar
        defaults.put("ScrollBar:\"ScrollBar.button\".size", 18);
        defaults.put("ScrollBar:\"ScrollBar.button\"[Disabled].foregroundPainter", new SkinScrollBarButtonPainter(SkinScrollBarButtonPainter.State.Disabled));
        defaults.put("ScrollBar:\"ScrollBar.button\"[Enabled].foregroundPainter", new SkinScrollBarButtonPainter(SkinScrollBarButtonPainter.State.Enabled));
        defaults.put("ScrollBar:\"ScrollBar.button\"[MouseOver].foregroundPainter", new SkinScrollBarButtonPainter(SkinScrollBarButtonPainter.State.MouseOver));
        defaults.put("ScrollBar:\"ScrollBar.button\"[Pressed].foregroundPainter", new SkinScrollBarButtonPainter(SkinScrollBarButtonPainter.State.Pressed));
        defaults.put("ScrollBar:ScrollBarTrack[Disabled].backgroundPainter", new SkinScrollBarTrackPainter(SkinScrollBarTrackPainter.State.Disabled));
        defaults.put("ScrollBar:ScrollBarTrack[Enabled].backgroundPainter", new SkinScrollBarTrackPainter(SkinScrollBarTrackPainter.State.Enabled));
        defaults.put("ScrollBar:ScrollBarThumb[Enabled].backgroundPainter", new SkinScrollBarThumbPainter(SkinScrollBarThumbPainter.State.Enabled));
        defaults.put("ScrollBar:ScrollBarThumb[MouseOver].backgroundPainter", new SkinScrollBarThumbPainter(SkinScrollBarThumbPainter.State.MouseOver));
        defaults.put("ScrollBar:ScrollBarThumb[Pressed].backgroundPainter", new SkinScrollBarThumbPainter(SkinScrollBarThumbPainter.State.Pressed));
        // table header
        defaults.put("TableHeader.background", SkinColors.LIGHT_BACKGROUND_COLOR);
        defaults.put("TableHeader:\"TableHeader.renderer\"[Enabled].backgroundPainter", new SkinTableHeaderPainter(SkinTableHeaderPainter.State.Enabled));
        defaults.put("TableHeader:\"TableHeader.renderer\"[MouseOver].backgroundPainter", new SkinTableHeaderPainter(SkinTableHeaderPainter.State.MouseOver));
        defaults.put("TableHeader:\"TableHeader.renderer\"[Pressed].backgroundPainter", new SkinTableHeaderPainter(SkinTableHeaderPainter.State.Pressed));
        // table
        defaults.put("Table.cellNoFocusBorder", new InsetsUIResource(0, 0, 0, 0));
        defaults.put("Table.focusCellHighlightBorder", new InsetsUIResource(0, 0, 0, 0));
        defaults.put("Table.alternateRowColor", new Color(SkinColors.TABLE_ALTERNATE_ROW_COLOR.getRGB()));
        defaults.put("Table[Enabled+Selected].textBackground", new Color(SkinColors.TABLE_SELECTED_BACKGROUND_ROW_COLOR.getRGB()));
        defaults.put("Table[Enabled+Selected].textForeground", SkinColors.TABLE_SELECTED_FOREGROUND_ROW_COLOR);
        // table row setting
        setupTableFont(defaults);
        // splitter
        defaults.put("SplitPane:SplitPaneDivider[Enabled].backgroundPainter", new SkinSplitPaneDividerBackgroundPainter(SkinSplitPaneDividerBackgroundPainter.State.Enabled));
        // tabbed pane tab
        defaults.put("TabbedPane:TabbedPaneTabArea.contentMargins", new InsetsUIResource(3, 4, 0, 4));
        defaults.put("TabbedPane:TabbedPaneTabArea[Disabled].backgroundPainter", new SkinTabbedPaneTabAreaBackgroundPainter(SkinTabbedPaneTabAreaBackgroundPainter.State.Disabled));
        defaults.put("TabbedPane:TabbedPaneTabArea[Enabled+MouseOver].backgroundPainter", new SkinTabbedPaneTabAreaBackgroundPainter(SkinTabbedPaneTabAreaBackgroundPainter.State.EnableMouseOver));
        defaults.put("TabbedPane:TabbedPaneTabArea[Enabled+Pressed].backgroundPainter", new SkinTabbedPaneTabAreaBackgroundPainter(SkinTabbedPaneTabAreaBackgroundPainter.State.EnablePressed));
        defaults.put("TabbedPane:TabbedPaneTabArea[Enabled].backgroundPainter", new SkinTabbedPaneTabAreaBackgroundPainter(SkinTabbedPaneTabAreaBackgroundPainter.State.Enable));
        defaults.put("TabbedPane:TabbedPaneTab.contentMargins", new InsetsUIResource(3, 4, 4, 8));
        defaults.put("TabbedPane:TabbedPaneTab[Disabled+Selected].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.DisabledSelected));
        defaults.put("TabbedPane:TabbedPaneTab[Disabled].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.Disabled));
        defaults.put("TabbedPane:TabbedPaneTab[Enabled+MouseOver].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.EnabledMouseOver));
        defaults.put("TabbedPane:TabbedPaneTab[Enabled+Pressed].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.EnabledPressed));
        defaults.put("TabbedPane:TabbedPaneTab[Enabled].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.Enabled));
        defaults.put("TabbedPane:TabbedPaneTab[Focused+MouseOver+Selected].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.FocusedMouseOverSelected));
        defaults.put("TabbedPane:TabbedPaneTab[Focused+Pressed+Selected].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.FocusedPressedSelected));
        defaults.put("TabbedPane:TabbedPaneTab[Focused+Selected].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.FocusedSelected));
        defaults.put("TabbedPane:TabbedPaneTab[MouseOver+Selected].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.MouseOverSelected));
        defaults.put("TabbedPane:TabbedPaneTab[Pressed+Selected].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.PressedSelected));
        defaults.put("TabbedPane:TabbedPaneTab[Selected].backgroundPainter", new SkinTabbedPaneTabBackgroundPainter(SkinTabbedPaneTabBackgroundPainter.State.Selected));
        // tree
        defaults.put("Tree.closedIcon", null);
        defaults.put("Tree.openIcon", null);
        defaults.put("Tree.leafIcon", null);
        defaults.put("Tree.selectionForeground", SkinColors.TEXT_FONT_FOREGROUND_COLOR);
        defaults.put("Tree:TreeCell[Enabled+Selected].textForeground", SkinColors.TEXT_FONT_FOREGROUND_COLOR);
        defaults.put("Tree:TreeCell[Focused+Selected].textForeground", SkinColors.TEXT_FONT_FOREGROUND_COLOR);
        //defaults.put("Tree.rendererFillBackground", Boolean.TRUE);
        // list
        defaults.put("List.cellNoFocusBorder", new InsetsUIResource(0, 0, 0, 0));
        defaults.put("List.focusCellHighlightBorder", new InsetsUIResource(0, 0, 0, 0));
        defaults.put("List[Selected].textBackground", new Color(SkinColors.TABLE_SELECTED_BACKGROUND_ROW_COLOR.getRGB()));
        defaults.put("List[Selected].textForeground", new Color(SkinColors.TEXT_FONT_FOREGROUND_COLOR.getRGB()));
        // popup
        defaults.put("PopupMenu[Disabled].backgroundPainter", new SkinPopupMenuBackgroundPainter(SkinPopupMenuBackgroundPainter.State.Disabled));
        defaults.put("PopupMenu[Enabled].backgroundPainter", new SkinPopupMenuBackgroundPainter(SkinPopupMenuBackgroundPainter.State.Enabled));
        // menuitem
        defaults.put("MenuItem[Enabled].textForeground", SkinColors.TEXT_FONT_FOREGROUND_COLOR);
        defaults.put("MenuItem[MouseOver].backgroundPainter", new SkinMenuItemBackgroundPainter(SkinMenuItemBackgroundPainter.State.MouseOver));
        // textfield
        //defaults.put("TextField.contentMargins", new InsetsUIResource(0, 0, 0, 0));
        defaults.put("TextField[Disabled].borderPainter", new SkinTextFieldBorderPainter(SkinTextFieldBorderPainter.State.Disabled));
        defaults.put("TextField[Enabled].borderPainter", new SkinTextFieldBorderPainter(SkinTextFieldBorderPainter.State.Enabled));
        defaults.put("TextField[Focused].borderPainter", new SkinTextFieldBorderPainter(SkinTextFieldBorderPainter.State.Focused));
        defaults.put("TextField[Disabled].backgroundPainter", new SkinTextFieldBackgroundPainter(SkinTextFieldBackgroundPainter.State.Disabled));
        defaults.put("TextField[Enabled].backgroundPainter", new SkinTextFieldBackgroundPainter(SkinTextFieldBackgroundPainter.State.Enabled));
        defaults.put("TextField[Focused].backgroundPainter", new SkinTextFieldBackgroundPainter(SkinTextFieldBackgroundPainter.State.Focused));
        // scrollpane
        defaults.put("ScrollPane.background", new ColorUIResource(Color.WHITE));
        // editorpane
        defaults.put("EditorPane[Enabled].backgroundPainter", SkinColors.LIGHT_BACKGROUND_COLOR);
        // radio buttons
        //defaults.put("RadioButton.icon", new IconUIResource()); 
        defaults.put("RadioButton[Disabled+Selected].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.DisabledSelected));
        defaults.put("RadioButton[Disabled].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.Disabled));
        defaults.put("RadioButton[Enabled].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.Enabled));
        defaults.put("RadioButton[Focused+MouseOver+Selected].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.FocusedMouseOverSelected));
        defaults.put("RadioButton[Focused+MouseOver].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.FocusedMouseOver));
        defaults.put("RadioButton[Focused+Pressed+Selected].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.FocusedPressedSelected));
        defaults.put("RadioButton[Focused+Pressed].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.FocusedPressed));
        defaults.put("RadioButton[Focused+Selected].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.FocusedSelected));
        defaults.put("RadioButton[Focused].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.Focused));
        defaults.put("RadioButton[MouseOver+Selected].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.MouseOverSelected));
        defaults.put("RadioButton[MouseOver].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.MouseOver));
        defaults.put("RadioButton[Pressed+Selected].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.PressedSelected));
        defaults.put("RadioButton[Pressed].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.Pressed));
        defaults.put("RadioButton[Selected].iconPainter", new SkinRadioButtonIconPainter(SkinRadioButtonIconPainter.State.Selected));
        // checkbox
        defaults.put("CheckBox[Disabled+Selected].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.DisabledSelected));
        defaults.put("CheckBox[Disabled].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.Disabled));
        defaults.put("CheckBox[Enabled].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.Enabled));
        defaults.put("CheckBox[Focused+MouseOver+Selected].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.FocusedMouseOverSelected));
        defaults.put("CheckBox[Focused+MouseOver].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.FocusedMouseOver));
        defaults.put("CheckBox[Focused+Pressed+Selected].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.FocusedPressedSelected));
        defaults.put("CheckBox[Focused+Pressed].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.FocusedPressed));
        defaults.put("CheckBox[Focused+Selected].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.FocusedSelected));
        defaults.put("CheckBox[Focused].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.Focused));
        defaults.put("CheckBox[MouseOver+Selected].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.MouseOverSelected));
        defaults.put("CheckBox[MouseOver].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.MouseOver));
        defaults.put("CheckBox[Pressed+Selected].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.PressedSelected));
        defaults.put("CheckBox[Pressed].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.Pressed));
        defaults.put("CheckBox[Selected].iconPainter", new SkinCheckBoxIconPainter(SkinCheckBoxIconPainter.State.Selected));
        // slider
        defaults.put("Slider:SliderThumb[Disabled].backgroundPainter", new SkinSliderThumbPainter(SkinSliderThumbPainter.State.Disabled));
        defaults.put("Slider:SliderThumb[Enabled].backgroundPainter", new SkinSliderThumbPainter(SkinSliderThumbPainter.State.Enabled));
        defaults.put("Slider:SliderThumb[Focused+MouseOver].backgroundPainter", new SkinSliderThumbPainter(SkinSliderThumbPainter.State.FocusedMouseOver));
        defaults.put("Slider:SliderThumb[Focused+Pressed].backgroundPainter", new SkinSliderThumbPainter(SkinSliderThumbPainter.State.FocusedPressed));
        defaults.put("Slider:SliderThumb[Focused].backgroundPainter", new SkinSliderThumbPainter(SkinSliderThumbPainter.State.Focused));
        defaults.put("Slider:SliderThumb[MouseOver].backgroundPainter", new SkinSliderThumbPainter(SkinSliderThumbPainter.State.MouseOver));
        defaults.put("Slider:SliderThumb[Pressed].backgroundPainter", new SkinSliderThumbPainter(SkinSliderThumbPainter.State.Pressed));
        // popupmenuseparator
        defaults.put("PopupMenuSeparator[Enabled].backgroundPainter", new SkinPopupMenuSeparatorPainter(SkinPopupMenuSeparatorPainter.State.Enabled));
        return defaults;
    }

    private static Font setupTableFont(UIDefaults defaults) {
        int sizeSetting = ApplicationSettings.GUI_TABLES_FONT_SIZE.getValue();
        if (sizeSetting != 0 && (sizeSetting < TABLE_FONT_SIZE_MIN || TABLE_FONT_SIZE_MAX < sizeSetting)) {
            ApplicationSettings.GUI_TABLES_FONT_SIZE.setValue(0);
            sizeSetting = 0;
        }
        Font f;
        if (sizeSetting != 0) {
            f = defaults.getFont("defaultFont").deriveFont((float) sizeSetting);
        } else {
            f = defaults.getFont("defaultFont");
        }
        defaults.put("Table.font", new FontUIResource(f));
        return f;
    }
}
