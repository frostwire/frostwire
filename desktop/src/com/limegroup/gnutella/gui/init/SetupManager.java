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

package com.limegroup.gnutella.gui.init;

import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.shell.FrostAssociations;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.InstallSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.setting.SettingsGroupManager;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedList;
import java.util.List;

/**
 * This class manages the setup wizard.  It constructs all of the primary
 * classes and acts as the mediator between the various objects in the
 * setup windows.
 */
public final class SetupManager {
    static final int ACTION_PREVIOUS = 1;
    static final int ACTION_NEXT = 2;
    static final int ACTION_FINISH = 4;
    static final int ACTION_CANCEL = 8;
    /**
     * the dialog window that holds all other gui elements for the setup.
     */
    private FramedDialog dialogFrame;
    /**
     * the holder for the setup windows
     */
    private SetupWindowHolder _setupWindowHolder;
    /**
     * holder for the current setup window.
     */
    private SetupWindow _currentWindow;
    private Dimension holderPreferredSize;
    private final PreviousAction previousAction = new PreviousAction();
    private final NextAction nextAction = new NextAction();
    private final FinishAction finishAction = new FinishAction();
    private final CancelAction cancelAction = new CancelAction();
    private final LanguageAwareAction[] actions = new LanguageAwareAction[]{previousAction, nextAction, finishAction, cancelAction};

    private boolean shouldShowAssociationsWindow() {
        if (CommonUtils.isPortable() || (InstallSettings.ASSOCIATION_OPTION.getValue() == FrostAssociations.CURRENT_ASSOCIATIONS)) {
            return false;
        }
        // display a window if silent grab failed.
        return !GUIMediator.getAssociationManager().checkAndGrab(false);
    }

    private SaveStatus shouldShowSaveDirectoryWindow() {
        // If it's not setup, definitely show it!
        if (!InstallSettings.SAVE_DIRECTORY.getValue()) {
            return SaveStatus.NEEDS;
        }
        if (!InstallSettings.LAST_FROSTWIRE_VERSION_WIZARD_INVOKED.getValue().equals(String.valueOf(FrostWireUtils.getBuildNumber()))) {
            return SaveStatus.NEEDS;
        }
        return SaveStatus.NO;
    }

    /**
     * Constructs the appropriate setup windows if needed.
     */
    public void createIfNeeded() {
        _setupWindowHolder = new SetupWindowHolder();
        List<SetupWindow> windows = new LinkedList<>();
        SaveStatus saveDirectoryStatus = shouldShowSaveDirectoryWindow();
        if (saveDirectoryStatus != SaveStatus.NO) {
            windows.add(new BitTorrentSettingsWindow(this));
        }
        if (!InstallSettings.SPEED.getValue() || !InstallSettings.START_STARTUP.getValue() && GUIUtils.shouldShowStartOnStartupWindow()) {
            windows.add(new MiscWindow(this));
        }
        if (shouldShowAssociationsWindow()) {
            windows.add(new AssociationsWindow(this));
        }
        if (windows.size() > 0) {
            windows.add(new SocialRecommendationsWindow(this));
        }
        //THIS HAS TO GO LAST
        IntentWindow intentWindow = new IntentWindow(this);
        if (!intentWindow.isConfirmedWillNot()) {
            windows.add(intentWindow);
        }
        // Nothing to install?.. Begone.
        if (windows.size() == 0) {
            return;
        }
        // If the INSTALLED value is set, that means that a previous
        // installer has already been run.
        boolean partial = ApplicationSettings.INSTALLED.getValue();
        // We need to ask the user's language very very first,
        // so make sure that if the LanguageWindow is the first item,
        // that the WelcomeWindow is inserted second.
        // It's a little more tricky than that, though, because
        // it could be possible that the LanguageWindow was the only
        // item to be installed -- if that's the case, don't even
        // insert the WelcomeWindow & FinishWindow at all.
        //if(partial && !(windows.size() == 1 && windows.get(0) instanceof IntentWindow))
        windows.add(0, new WelcomeWindow(this, partial));
        holderPreferredSize = new Dimension(0, 0);
        // Iterate through each displayed window and set them up correctly.
        SetupWindow prior = null;
        for (SetupWindow current : windows) {
            _setupWindowHolder.add(current);
            //noinspection ReplaceNullCheck
            if (prior == null) {
                current.setPrevious(current);
            } else {
                current.setPrevious(prior);
            }
            if (prior != null) {
                prior.setNext(current);
            }
            prior = current;
            Dimension d = current.calculatePreferredSize();
            if (d.width > holderPreferredSize.width) {
                holderPreferredSize.width = d.width;
            }
            if (d.height > holderPreferredSize.height) {
                holderPreferredSize.height = d.height;
            }
        }
        holderPreferredSize.width += 20;
        holderPreferredSize.height += 20;
        if (holderPreferredSize.width > 900) {
            holderPreferredSize.width = 900;
        }
        if (holderPreferredSize.height > 750) {
            holderPreferredSize.height = 750;
        }
        assert prior != null;
        prior.setNext(prior);
        // Actually display the setup dialog.
        createDialog(windows.get(0));
    }

    /*
     * Creates the main <tt>JDialog</tt> instance and
     * creates all of the setup window classes, buttons, etc.
     */
    private void createDialog(SetupWindow firstWindow) {
        dialogFrame = new FramedDialog();
        dialogFrame.setTitle("FrostWire Setup");
        WindowAdapter onCloseAdapter = new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                cancelSetup();
            }
        };
        dialogFrame.addWindowListener(onCloseAdapter);
        JDialog dialog = dialogFrame.getDialog();
        dialog.setModal(true);
        dialog.setTitle(I18n.tr("FrostWire Setup Wizard"));
        dialog.addWindowListener(onCloseAdapter);
        // set the layout of the content pane
        Container container = dialog.getContentPane();
        GUIUtils.addHideAction((JComponent) container);
        BoxLayout containerLayout = new BoxLayout(container, BoxLayout.Y_AXIS);
        container.setLayout(containerLayout);
        JPanel setupPanel = new JPanel();
        setupPanel.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
        BoxLayout layout = new BoxLayout(setupPanel, BoxLayout.Y_AXIS);
        setupPanel.setLayout(layout);
        Dimension d = new Dimension(SetupWindow.SETUP_WIDTH, SetupWindow.SETUP_HEIGHT);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation((screenSize.width - d.width) / 2, (screenSize.height - d.height) / 2);
        dialog.setSize((int) d.getWidth(), (int) d.getHeight());
        // create the setup buttons panel
        if (OSUtils.isGoodWindows()) {
            _setupWindowHolder.setPreferredSize(holderPreferredSize);
        }
        setupPanel.add(_setupWindowHolder);
        setupPanel.add(Box.createVerticalStrut(17));
        JPanel bottomRow = new JPanel();
        bottomRow.setLayout(new BoxLayout(bottomRow, BoxLayout.X_AXIS));
        ButtonRow buttons = new ButtonRow(actions, ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE);
        LanguagePanel languagePanel = new LanguagePanel(e -> updateLanguage());
        bottomRow.add(languagePanel);
        bottomRow.add(Box.createHorizontalGlue());
        bottomRow.add(buttons);
        bottomRow.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        setupPanel.add(new Line());
        setupPanel.add(bottomRow);
        show(firstWindow);
        // add the panel and make it visible
        container.add(setupPanel);
        if (!OSUtils.isGoodWindows()) {
            container.setPreferredSize(new Dimension(SetupWindow.SETUP_WIDTH, SetupWindow.SETUP_HEIGHT));
        }
        dialog.pack();
        SplashWindow.instance().setVisible(false);
        dialogFrame.showDialog();
        SplashWindow.instance().setVisible(true);
    }

    /**
     * Enables the bitmask of specified actions, the other actions are
     * explicitly disabled.
     */
    void enableActions(int actions) {
        previousAction.setEnabled((actions & ACTION_PREVIOUS) != 0);
        nextAction.setEnabled((actions & ACTION_NEXT) != 0);
        finishAction.setEnabled((actions & ACTION_FINISH) != 0);
        cancelAction.setEnabled((actions & ACTION_CANCEL) != 0);
    }

    /**
     * Displays the next window in the setup sequence.
     */
    private void next() {
        SetupWindow newWindow = _currentWindow.getNext();
        try {
            _currentWindow.applySettings(true);
            show(newWindow);
        } catch (ApplySettingsException ase) {
            // there was a problem applying the settings from
            // the current window, so display the error message
            // to the user.
            if (ase.getMessage() != null && ase.getMessage().length() > 0)
                GUIMediator.showError(ase.getMessage());
        }
    }

    /**
     * Displays the previous window in the setup sequence.
     */
    private void previous() {
        SetupWindow newWindow = _currentWindow.getPrevious();
        try {
            _currentWindow.applySettings(false);
        } catch (ApplySettingsException ase) {
            // ignore errors when going backwards
        }
        show(newWindow);
    }

    /**
     * Cancels the setup.
     */
    private void cancelSetup() {
        dialogFrame.getDialog().dispose();
        System.exit(0);
    }

    /**
     * Completes the setup.
     */
    private void finishSetup() {
        if (_currentWindow != null) {
            try {
                _currentWindow.applySettings(true);
            } catch (ApplySettingsException e) {
                // there was a problem applying the settings from
                // the current window, so display the error message
                // to the user.
                if (e.getMessage() != null && e.getMessage().length() > 0) {
                    GUIMediator.showError(e.getMessage());
                }
                return;
            }
        }
        dialogFrame.getDialog().dispose();
        ApplicationSettings.INSTALLED.setValue(true);
        InstallSettings.SAVE_DIRECTORY.setValue(true);
        InstallSettings.SPEED.setValue(true);
        InstallSettings.SCAN_FILES.setValue(true);
        InstallSettings.LANGUAGE_CHOICE.setValue(true);
        InstallSettings.EXTENSION_OPTION.setValue(true);
        if (GUIUtils.shouldShowStartOnStartupWindow())
            InstallSettings.START_STARTUP.setValue(true);
        if (OSUtils.isWindows())
            InstallSettings.FIREWALL_WARNING.setValue(true);
        InstallSettings.ASSOCIATION_OPTION.setValue(FrostAssociations.CURRENT_ASSOCIATIONS);
        InstallSettings.LAST_FROSTWIRE_VERSION_WIZARD_INVOKED.setValue(String.valueOf(FrostWireUtils.getBuildNumber()));
        BackgroundExecutorService.schedule(() -> SettingsGroupManager.instance().save());
        if (_currentWindow instanceof IntentWindow) {
            IntentWindow intent = (IntentWindow) _currentWindow;
            if (!intent.isConfirmedWillNot()) {
                GUIMediator.showWarning("FrostWire is not distributed to people who intend to use it for the purposes of copyright infringement.\n\nThank you for your interest; however, you cannot continue to use FrostWire at this time.");
                System.exit(1);
            }
            intent.applySettings(true);
        }
        dialogFrame.getDialog().dispose();
    }

    /**
     * Instructs the buttons to redo their text.
     */
    private void updateLanguage() {
        for (LanguageAwareAction action : actions) {
            action.updateLanguage();
        }
        try {
            _currentWindow.applySettings(false);
        } catch (ApplySettingsException ignored) {
        }
        _currentWindow.handleWindowOpeningEvent();
    }

    /**
     * Show the specified window
     */
    private void show(SetupWindow window) {
        window.handleWindowOpeningEvent();
        _setupWindowHolder.show(window.getKey());
        _currentWindow = window;
    }

    private enum SaveStatus {
        NO, NEEDS
    }

    private abstract class LanguageAwareAction extends AbstractAction {
        private final String nameKey;

        LanguageAwareAction(String nameKey) {
            super(I18n.tr(nameKey));
            this.nameKey = nameKey;
        }

        void updateLanguage() {
            putValue(Action.NAME, I18n.tr(nameKey));
        }
    }

    private class CancelAction extends LanguageAwareAction {
        CancelAction() {
            super(I18n.tr("Cancel"));
        }

        public void actionPerformed(ActionEvent e) {
            cancelSetup();
        }
    }

    private class NextAction extends LanguageAwareAction {
        NextAction() {
            super(I18n.tr("Next >>"));
        }

        public void actionPerformed(ActionEvent e) {
            next();
        }
    }

    private class PreviousAction extends LanguageAwareAction {
        PreviousAction() {
            super(I18n.tr("<< Back"));
        }

        public void actionPerformed(ActionEvent e) {
            previous();
        }
    }

    private class FinishAction extends LanguageAwareAction {
        FinishAction() {
            super(I18n.tr("Finish"));
        }

        public void actionPerformed(ActionEvent e) {
            finishSetup();
        }
    }
}
