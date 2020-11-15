package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.settings.ApplicationSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class LanguageButton extends JPanel {
    /**
     *
     */
    private static final long serialVersionUID = 1795381168007012403L;
    private final JButton bheader;

    LanguageButton() {
        bheader = new JButton();
        updateLanguageFlag();
        //when pressed displays a dialog that allows you to change the language.
        ActionListener languageButtonListener = e -> {
            LanguageWindow lw = new LanguageWindow();
            GUIUtils.centerOnScreen(lw);
            lw.setVisible(true);
        };
        bheader.addActionListener(languageButtonListener);
        MouseListener languageMouseListener = new MouseAdapter() {
            //simulate active cursor, we could choose another cursor though
            public void mouseEntered(MouseEvent e) {
                e.getComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            //go back to normal
            public void mouseExited(MouseEvent e) {
                e.getComponent().setCursor(Cursor.getDefaultCursor());
            }
        };
        bheader.addMouseListener(languageMouseListener);
        zeroInsets(this);
        setBorder(null);
        adjustSizes(bheader, 28, 16);
        add(bheader);
    }

    private static void setSizes(JButton b, int width, int height) {
        Dimension d = new Dimension(width, height);
        b.setMaximumSize(d);
        b.setMinimumSize(d);
        b.setPreferredSize(d);
    }

    private static void zeroInsets(JComponent jc) {
        Insets insets = jc.getInsets();
        insets.left = 0;
        insets.right = 0;
        insets.top = 0;
        insets.bottom = 0;
    }

    void updateLanguageFlag() {
        bheader.setContentAreaFilled(false);
        bheader.setBorderPainted(false);
        bheader.setOpaque(false);
        bheader.setIcon(LanguageFlagFactory.getFlag(ApplicationSettings.COUNTRY.getValue(),
                ApplicationSettings.LANGUAGE.getValue(),
                true));
        String tip = GUIMediator.getLocale().getDisplayName();
        bheader.setToolTipText(tip);
        setToolTipText(tip);
    }

    /**
     * We overide addMouseListener to pass the StatusBar MouseListener
     * to our internal Button.
     */
    public void addMouseListener(MouseListener m) {
        bheader.addMouseListener(m);
    }

    private void adjustSizes(JComponent jc, int width, int height) {
        zeroInsets(jc);
        setSizes((JButton) jc, width, height);
    }
}
