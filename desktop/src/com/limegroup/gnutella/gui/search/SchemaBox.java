/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.theme.AbstractSkinPainter;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.ImageManipulator;
import com.limegroup.gnutella.settings.SearchSettings;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthRadioButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * A group of radio buttons for each schema.
 */
final class SchemaBox extends JPanel {
    private final SearchResultMediator resultPanel;
    private final ButtonGroup buttonGroup;
    private final Map<NamedMediaType, JToggleButton> buttonsMap;
    private final Map<NamedMediaType, String> tooltipPlaceHolders;

    /**
     * Constructs the SchemaBox.
     */
    SchemaBox(SearchResultMediator resultPanel) {
        this.resultPanel = resultPanel;
        this.buttonGroup = new ButtonGroup();
        this.buttonsMap = new HashMap<>();
        this.tooltipPlaceHolders = new HashMap<>();
        setLayout(new BorderLayout());
        addSchemas();
        setBorder(BorderFactory.createEmptyBorder(3, 4, 0, 0));
        Dimension dim = new Dimension(10, 30);
        setPreferredSize(dim);
        setMinimumSize(dim);
        setMaximumSize(new Dimension(100000, 30));
    }

    private static String safeTooltipFormat(String str, String val) {
        try {
            return String.format(str, val);
        } catch (Throwable e) {
            // ignore error, possible due to a bad rtl language translation
            e.printStackTrace();
            return str;
        }
    }

    void applyFilters() {
        AbstractButton button = getSelectedButton();
        if (button != null) {
            button.doClick();
        }
    }

    void updateCounters(UISearchResult sr) {
        NamedMediaType nmt = NamedMediaType.getFromExtension(sr.getExtension());
        if (nmt != null && buttonsMap.containsKey(nmt)) {
            JToggleButton button = buttonsMap.get(nmt);
            incrementText(button, nmt);
        }
    }

    void resetCounters() {
        Collection<JToggleButton> values = buttonsMap.values();
        for (JToggleButton button : values) {
            button.setText("0");
            button.setToolTipText(I18n.tr("No results so far..."));
        }
    }

    private void incrementText(JToggleButton button, NamedMediaType nmt) {
        String text = button.getText();
        int n = 0;
        try { // only justified situation of using try-catch for logic flow, since regex is slower
            n = Integer.parseInt(text);
        } catch (Throwable e) {
            // no an integer
        }
        String incrementedCounterValue = String.valueOf(n + 1);
        button.setText(incrementedCounterValue);
        button.setToolTipText(safeTooltipFormat(tooltipPlaceHolders.get(nmt), incrementedCounterValue));
    }

    /**
     * Adds the given schemas as radio buttons.
     */
    private void addSchemas() {
        NamedMediaType nmt;
        JPanel panel = new JPanel(new MigLayout("insets 0, fillx"));
//        panel.setBackground(Color.BLUE);
        //panel.setOpaque(true);
        //panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        Dimension dim = new Dimension(400, 30);
        panel.setPreferredSize(dim);
        panel.setMinimumSize(dim);
        panel.setMaximumSize(new Dimension(100000, 30));
        // Then add 'Audio'
        nmt = NamedMediaType.getFromDescription(MediaType.SCHEMA_AUDIO);
        tooltipPlaceHolders.put(nmt, I18n.tr("%s Audio files found (including .mp3, .wav, .ogg, and more)"));
        addMediaType(panel, nmt, safeTooltipFormat(tooltipPlaceHolders.get(nmt), "0"));
        // Then add 'Video'
        nmt = NamedMediaType.getFromDescription(MediaType.SCHEMA_VIDEO);
        tooltipPlaceHolders.put(nmt, I18n.tr("%s Video files found (including .avi, .mpg, .wmv, and more)"));
        addMediaType(panel, nmt, safeTooltipFormat(tooltipPlaceHolders.get(nmt), "0"));
        // Then add 'Images'
        nmt = NamedMediaType.getFromDescription(MediaType.SCHEMA_IMAGES);
        tooltipPlaceHolders.put(nmt, I18n.tr("%s Image files found (including .jpg, .gif, .png and more)"));
        addMediaType(panel, nmt, safeTooltipFormat(tooltipPlaceHolders.get(nmt), "0"));
        // Then add 'Documents'
        nmt = NamedMediaType.getFromDescription(MediaType.SCHEMA_DOCUMENTS);
        tooltipPlaceHolders.put(nmt, I18n.tr("%s Document files found (including .html, .txt, .pdf, and more)"));
        addMediaType(panel, nmt, safeTooltipFormat(tooltipPlaceHolders.get(nmt), "0"));
        // Then add 'Programs'
        nmt = NamedMediaType.getFromDescription(MediaType.SCHEMA_PROGRAMS);
        tooltipPlaceHolders.put(nmt, I18n.tr("%s Program files found (including .exe, .zip, .gz, and more)"));
        addMediaType(panel, nmt, safeTooltipFormat(tooltipPlaceHolders.get(nmt), "0"));
        // Then add 'Torrents'
        nmt = NamedMediaType.getFromDescription(MediaType.SCHEMA_TORRENTS);
        tooltipPlaceHolders.put(nmt, I18n.tr("%s Torrent files found (includes only .torrent files. Torrent files point to collections of files shared on the BitTorrent network.)"));
        addMediaType(panel, nmt, safeTooltipFormat(tooltipPlaceHolders.get(nmt), "0"));
        add(panel, BorderLayout.LINE_START);
    }

    /**
     * Adds the given NamedMediaType.
     * <p>
     * Marks the 'Any Type' as selected.
     */
    private void addMediaType(JPanel panel, NamedMediaType type, String toolTip) {
        Icon icon = type.getIcon();
        Icon disabledIcon = null;
        Icon rolloverIcon = null;
        JToggleButton button = new JRadioButton("0");
        if (icon != null) {
            disabledIcon = ImageManipulator.darken(icon);
            rolloverIcon = ImageManipulator.brighten(icon);
        }
        button.setIcon(disabledIcon);
        button.setRolloverIcon(rolloverIcon);
        button.setRolloverSelectedIcon(rolloverIcon);
        button.setPressedIcon(rolloverIcon);
        button.setSelectedIcon(rolloverIcon);// use the right icon here
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setMargin(new Insets(4, 6, 4, 6));
        //Dimension d = new Dimension(60, 20);
        //button.setPreferredSize(d);
        //button.setMinimumSize(d);
        button.setOpaque(false);
        if (toolTip != null) {
            button.setToolTipText(toolTip);
        }
        buttonGroup.add(button);
        button.setUI(new SchemaButtonUI(button));
        panel.add(button);
        button.addActionListener(new SchemaButtonActionListener(type));
        button.setSelected(isMediaTypeSelected(type));
        buttonsMap.put(type, button);
    }

    /**
     * Use this if you want to programmatically change the current file type being displayed for a search.
     *
     */
    void selectMediaType(NamedMediaType type) {
        JToggleButton mediaTypeButton = buttonsMap.get(type);
        if (mediaTypeButton != null) {
            mediaTypeButton.doClick(); //setSelected doesn't fire the proper events.
        }
    }

    private boolean isMediaTypeSelected(NamedMediaType type) {
        boolean result = false;
        if (SearchSettings.LAST_MEDIA_TYPE_USED.getValue().contains(type.getMediaType().getMimeType())) {
            result = true;
        }
        if (SearchSettings.LAST_MEDIA_TYPE_USED.getValue().isEmpty() && type.getMediaType().equals(MediaType.getAudioMediaType())) {
            result = true;
        }
        return result;
    }

    private AbstractButton getSelectedButton() {
        AbstractButton selectedButton = null;
        for (Enumeration<AbstractButton> buttons = buttonGroup.getElements(); buttons.hasMoreElements(); ) {
            AbstractButton button = buttons.nextElement();
            if (button.isSelected()) {
                selectedButton = button;
            }
        }
        return selectedButton;
    }

    private static final class SchemaButtonBackgroundPainter extends AbstractSkinPainter {
        private static final Color STROKE = new Color(161, 195, 214);
        private static final Color LIGHT = new Color(203, 224, 236);
        private static final Color DARK = new Color(182, 206, 220);
        private static final Color[] BACKGROUND = new Color[]{LIGHT, DARK};

        @Override
        protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
            int w = width - 2;
            int h = height - 1;
            if (testValid(0, 0, w, h)) {
                Shape s = shapeGenerator.createRectangle(0, 0, w, h);
                Paint background = createVerticalGradient(s, BACKGROUND);
                g.setPaint(background);
                g.fill(s);
                g.setPaint(STROKE);
                g.draw(s);
            }
        }
    }

    private static final class SchemaButtonUI extends SynthRadioButtonUI {
        final SchemaButtonBackgroundPainter backgroundPainter;
        private final JToggleButton button;

        SchemaButtonUI(JToggleButton button) {
            this.button = button;
            this.backgroundPainter = new SchemaButtonBackgroundPainter();
        }

        @Override
        protected void paint(SynthContext context, Graphics g) {
            if (button.isSelected()) {
                backgroundPainter.doPaint((Graphics2D) g, button, button.getWidth(), button.getHeight(), null);
            }
            super.paint(context, g);
        }
    }

    private final class SchemaButtonActionListener implements ActionListener {
        private final NamedMediaType nmt;
        private final MediaTypeFilter filter;

        SchemaButtonActionListener(NamedMediaType nmt) {
            this.nmt = nmt;
            this.filter = new MediaTypeFilter(nmt);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String mimeType = nmt.getMediaType().getMimeType();
            SearchSettings.LAST_MEDIA_TYPE_USED.setValue(mimeType);
            if (resultPanel != null) {
                resultPanel.filterChanged(filter, 2);
            }
        }
    }
}
