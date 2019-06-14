package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.settings.StatusBarSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.util.Locale;

public class LanguageWindow extends JDialog {
    /**
     *
     */
    private static final long serialVersionUID = 1794098818746665242L;
    private static final int MIN_DIALOG_WIDTH = 350;
    private static final String TRANSLATE_URL = "https://github.com/frostwire/frostwire";
    private JCheckBox showLanguageCheckbox;
    private JComboBox<Object> localeComboBox;
    private final JPanel mainPanel;
    private OkayAction okayAction;
    private CancelAction cancelAction;
    private Locale currentLocale;
    private URLLabel helpTranslateLabel;
    private boolean defaultLocaleSelectable;
    private final Font dialogFont;

    public LanguageWindow() {
        super(GUIMediator.getAppFrame());
        this.currentLocale = GUIMediator.getLocale();
        initializeWindow();
        dialogFont = new Font("Dialog", Font.PLAIN, 11);
        Locale[] locales = LanguageUtils.getLocales(dialogFont);
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        getContentPane().add(mainPanel, BorderLayout.CENTER);
        initializeContent(locales);
        initializeButtons();
        initializeWindow();
        updateLabels(currentLocale);
        pack();
        if (getWidth() < MIN_DIALOG_WIDTH) {
            setSize(350, getHeight());
            setResizable(false);
        }
    }

    private void initializeWindow() {
        setModal(true);
        // setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        GUIUtils.addHideAction(this);
    }

    private void initializeContent(Locale[] locales) {
        JPanel container = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        // add locales to model and select the best match
        DefaultComboBoxModel<Object> localeModel = new DefaultComboBoxModel<>();
        int selectedScore = -1;
        int selectedIndex = -1;
        Locale systemLocale = Locale.getDefault();
        for (int i = 0; i < locales.length; i++) {
            localeModel.addElement(locales[i]);
            int score = LanguageUtils.getMatchScore(currentLocale, locales[i]);
            if (score > selectedScore) {
                selectedScore = score;
                selectedIndex = i;
            }
            if (locales[i].equals(systemLocale)) {
                defaultLocaleSelectable = true;
            }
        }
        localeComboBox = new JComboBox<>(localeModel);
        localeComboBox.setFont(dialogFont);
        localeComboBox.setRenderer(LanguageFlagFactory.getListRenderer());
        localeComboBox.setMaximumRowCount(15);
        if (selectedIndex != -1) {
            localeComboBox.setSelectedIndex(selectedIndex);
        }
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 1.0;
        container.add(localeComboBox, c);
        // reflect the changed language right away so someone who doesn't speak
        // English or whatever language it the default can understand what the
        // buttons say
        localeComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                Locale selected = (Locale) e.getItem();
                if (selected != null && !currentLocale.equals(selected)) {
                    updateLabels(selected);
                    // hide the flag by default for english locales to save
                    // space in the status bar
                    showLanguageCheckbox.setSelected(!LanguageUtils.isEnglishLocale(selected));
                    currentLocale = selected;
                }
            }
        });
        container.add(Box.createVerticalStrut(5), c);
        helpTranslateLabel = new URLLabel(TRANSLATE_URL, "");
        helpTranslateLabel.setFont(dialogFont);
        container.add(helpTranslateLabel, c);
        container.add(Box.createVerticalStrut(15), c);
        showLanguageCheckbox = new JCheckBox();
        showLanguageCheckbox.setFont(dialogFont);
        showLanguageCheckbox.setSelected(StatusBarSettings.LANGUAGE_DISPLAY_ENABLED.getValue());
        c.anchor = GridBagConstraints.LINE_START;
        container.add(showLanguageCheckbox, c);
        container.add(Box.createVerticalStrut(15), c);
        mainPanel.add(container, BorderLayout.CENTER);
    }

    private void initializeButtons() {
        okayAction = new OkayAction();
        cancelAction = new CancelAction();
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        JButton buttonOK = new JButton(okayAction);
        buttonOK.setFont(dialogFont);
        buttonPanel.add(buttonOK);
        if (!LanguageUtils.isEnglishLocale(currentLocale)) {
            buttonPanel.add(new JButton(new UseEnglishAction()));
        }
        JButton buttonCancel = new JButton(cancelAction);
        buttonCancel.setFont(dialogFont);
        buttonPanel.add(buttonCancel);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void switchLanguage(Locale locale, boolean showLanguageInStatusBar) {
        // if the selected locale is less specific than the default locale (e.g.
        // has no country or variant set), retain these properties, unless the user
        // specifically did not want to select the default locale
        if (!defaultLocaleSelectable && LanguageUtils.matchesDefaultLocale(locale)) {
            locale = Locale.getDefault();
        }
        if (!locale.equals(GUIMediator.getLocale())) {
            LanguageUtils.setLocale(locale);
            GUIMediator.instance().getStatusLine().updateLanguage();
            String message = I18n.trl(
                    "FrostWire must be restarted for the new language to take effect.", locale);
            //com.frostwire.gui.updates.UpdateManager.getInstance().checkForUpdates(); // check if it's possible to load the new overlay ad for next frostwire load. In the future should be loaded automatically from this function checkforupdates.
            JLabel labelMessage = new JLabel(message);
            labelMessage.setFont(dialogFont);
            JOptionPane.showMessageDialog(this, labelMessage, I18n.tr("Message"), JOptionPane.INFORMATION_MESSAGE);
        }
        StatusBarSettings.LANGUAGE_DISPLAY_ENABLED.setValue(showLanguageInStatusBar);
        if (LanguageUtils.isEnglishLocale(locale)) {
            StatusBarSettings.LANGUAGE_DISPLAY_ENGLISH_ENABLED.setValue(showLanguageInStatusBar);
        }
        GUIMediator.instance().getStatusLine().refresh();
        dispose();
    }

    private void updateLabels(Locale locale) {
        setTitle(I18n.trl("Change Language", locale));
        okayAction.putValue(Action.NAME, I18n.trl("OK", locale));
        cancelAction.putValue(Action.NAME, I18n.trl("Cancel", locale));
        helpTranslateLabel.setText(I18n.trl("Help Translate FrostWire", locale));
        showLanguageCheckbox.setText(I18n.trl("Show Language in status bar", locale));
    }

    private class OkayAction extends AbstractAction {
        OkayAction() {
        }

        public void actionPerformed(ActionEvent event) {
            Locale locale = (Locale) localeComboBox.getSelectedItem();
            switchLanguage(locale, showLanguageCheckbox.isSelected());
        }
    }

    private class CancelAction extends AbstractAction {
        CancelAction() {
        }

        public void actionPerformed(ActionEvent event) {
            GUIUtils.getDisposeAction().actionPerformed(event);
        }
    }

    private class UseEnglishAction extends AbstractAction {
        UseEnglishAction() {
            // note: this string is intentionally not translated
            putValue(NAME, "Use English");
        }

        public void actionPerformed(ActionEvent event) {
            switchLanguage(Locale.ENGLISH, showLanguageCheckbox.isSelected());
        }
    }
}
