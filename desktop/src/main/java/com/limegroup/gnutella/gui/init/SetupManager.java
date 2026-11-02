/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.init;

import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.shell.FrostAssociations;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.InstallSettings;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.*;
import org.limewire.setting.SettingsGroupManager;
import org.limewire.util.CommonUtils;

/**
 * This class manages the setup wizard. It constructs all of the primary classes and acts as the
 * mediator between the various objects in the setup windows.
 */
public final class SetupManager {
  static final int ACTION_PREVIOUS = 1;
  static final int ACTION_NEXT = 2;
  static final int ACTION_FINISH = 4;
  static final int ACTION_CANCEL = 8;

  /** the dialog window that holds all other gui elements for the setup. */
  private FramedDialog dialogFrame;

  /** Released once the wizard has closed, or right away when there is no wizard to show. */
  private final CountDownLatch closed = new CountDownLatch(1);

  /** the holder for the setup windows */
  private SetupWindowHolder _setupWindowHolder;

  /** holder for the current setup window. */
  private SetupWindow _currentWindow;

  private final PreviousAction previousAction = new PreviousAction();
  private final NextAction nextAction = new NextAction();
  private final FinishAction finishAction = new FinishAction();
  private final CancelAction cancelAction = new CancelAction();
  private final LanguageAwareAction[] actions =
      new LanguageAwareAction[] {previousAction, nextAction, finishAction, cancelAction};

  /** Decides which pages to show; {@link #decide()} outside of tests. */
  private final Supplier<Pages> decider;

  /** The pages {@link #decidePages()} chose, read by {@link #createIfNeeded()} on the EDT. */
  private volatile Pages pages;

  /** Exits the JVM; {@link Finalizer#exitOffEdt(int)} outside of tests. */
  private final IntConsumer exiter;

  /** Runs the pages' {@link SetupWindow#diskWork()}; the background queue outside of tests. */
  private final Executor diskExecutor;

  /** Set when the user quit from the wizard, so startup must not resume once it closes. */
  private volatile boolean exiting;

  public SetupManager() {
    this(SetupManager::decide);
  }

  SetupManager(Supplier<Pages> decider) {
    this(decider, Finalizer::exitOffEdt);
  }

  SetupManager(Supplier<Pages> decider, IntConsumer exiter) {
    this(decider, exiter, BackgroundQueuedExecutorService::schedule);
  }

  SetupManager(Supplier<Pages> decider, IntConsumer exiter, Executor diskExecutor) {
    this.decider = decider;
    this.exiter = exiter;
    this.diskExecutor = diskExecutor;
  }

  /**
   * Decides which pages the wizard needs. That reads settings, the intent file and, on Windows, the
   * file associations in the registry, so it must run on the startup thread before {@link
   * #createIfNeeded()}: done on the EDT, a slow disk or an antivirus scan froze startup before the
   * wizard appeared.
   */
  public void decidePages() {
    pages = decider.get();
  }

  private static Pages decide() {
    boolean saveDirectory = shouldShowSaveDirectoryWindow() != SaveStatus.NO;
    if (saveDirectory) {
      prepareSaveDirectoryPage();
    }
    return new Pages(
        saveDirectory,
        !InstallSettings.START_STARTUP.getValue() && GUIUtils.shouldShowStartOnStartupWindow(),
        shouldShowAssociationsWindow(),
        !IntentWindow.isConfirmedOnDisk(),
        ApplicationSettings.INSTALLED.getValue());
  }

  private static boolean shouldShowAssociationsWindow() {
    if (CommonUtils.isPortable()
        || (InstallSettings.ASSOCIATION_OPTION.getValue()
            == FrostAssociations.CURRENT_ASSOCIATIONS)) {
      return false;
    }
    // display a window if silent grab failed.
    return !GUIMediator.getAssociationManager().checkAndGrab(false);
  }

  /**
   * Loads {@link SharingSettings} before the save directory page is built on the EDT: its static
   * initializer creates the default Torrents and Torrent Data folders, and on a slow disk that
   * stalled the EDT when the page loaded the class (Icebase #501881c9eb54).
   */
  static void prepareSaveDirectoryPage() {
    SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
  }

  private static SaveStatus shouldShowSaveDirectoryWindow() {
    // If it's not setup, definitely show it!
    if (!InstallSettings.SAVE_DIRECTORY.getValue()) {
      return SaveStatus.NEEDS;
    }
    // QA: behave as if this build were just installed or updated.
    if (SetupWizardOverride.isForced()) {
      return SaveStatus.NEEDS;
    }
    if (!InstallSettings.LAST_FROSTWIRE_VERSION_WIZARD_INVOKED
        .getValue()
        .equals(String.valueOf(FrostWireUtils.getBuildNumber()))) {
      return SaveStatus.NEEDS;
    }
    return SaveStatus.NO;
  }

  /**
   * Builds the pages {@link #decidePages()} chose and shows the wizard, on the EDT. It does no file
   * or registry I/O.
   */
  public void createIfNeeded() {
    Pages pages = this.pages;
    if (pages == null) {
      throw new IllegalStateException("decidePages() must run first, off the EDT");
    }
    _setupWindowHolder = new SetupWindowHolder();
    List<SetupWindow> windows = new LinkedList<>();
    if (pages.saveDirectory()) {
      windows.add(new BitTorrentSettingsWindow(this));
      // Seeding/publishing privacy opt-in, shown alongside the BitTorrent sharing
      // settings on first run and after each version update.
      windows.add(new SeedingPublishingWindow(this));
    }
    if (pages.startOnStartup()) {
      windows.add(new MiscWindow(this));
    }
    if (pages.associations()) {
      windows.add(new AssociationsWindow(this));
    }
    if (windows.size() > 0) {
      windows.add(new SocialRecommendationsWindow(this));
    }
    // THIS HAS TO GO LAST
    if (pages.intent()) {
      windows.add(new IntentWindow(this));
    }
    // Nothing to install?.. Begone.
    if (windows.size() == 0) {
      closed.countDown();
      return;
    }

    // If the INSTALLED value is set, that means that a previous
    // installer has already been run.
    boolean partial = pages.partial();
    // We need to ask the user's language very very first,
    // so make sure that if the LanguageWindow is the first item,
    // that the WelcomeWindow is inserted second.
    // It's a little more tricky than that, though, because
    // it could be possible that the LanguageWindow was the only
    // item to be installed -- if that's the case, don't even
    // insert the WelcomeWindow & FinishWindow at all.
    // if(partial && !(windows.size() == 1 && windows.get(0) instanceof IntentWindow))
    windows.add(0, new WelcomeWindow(this, partial));
    linkPages(_setupWindowHolder, windows);
    // Build the dialog structure first
    SetupWindow firstWindow = windows.get(0);
    buildDialog(firstWindow);
    // Defer pack and show to a separate EDT event to avoid blocking current event
    // This allows the EDT watchdog timer to reset between events
    SwingUtilities.invokeLater(this::packAndShowDialog);
  }

  /**
   * Adds the pages to the card holder and chains them in order. The first page is its own previous
   * page and the last page is its own next page.
   *
   * <p>Pages are not built here. Each page builds its components when it is shown ({@link
   * SetupWindow#handleWindowOpeningEvent()}), so the first-run event only builds the first page
   * instead of every page in the wizard.
   */
  static void linkPages(SetupWindowHolder holder, List<SetupWindow> windows) {
    SetupWindow prior = null;
    for (SetupWindow current : windows) {
      holder.add(current);
      current.setPrevious(prior == null ? current : prior);
      if (prior != null) {
        prior.setNext(current);
      }
      prior = current;
    }
    if (prior != null) {
      prior.setNext(prior);
    }
  }

  /*
   * Builds the dialog structure and adds all UI components.
   * The actual packing and showing is deferred to packAndShowDialog().
   */
  private void buildDialog(SetupWindow firstWindow) {
    dialogFrame = new FramedDialog();
    dialogFrame.setTitle("FrostWire Setup");
    WindowAdapter onCloseAdapter =
        new WindowAdapter() {
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
    // Every page is laid out for this size, so the dialog is sized without building them all.
    container.setPreferredSize(new Dimension(SetupWindow.SETUP_WIDTH, SetupWindow.SETUP_HEIGHT));
  }

  /*
   * Packs and shows the dialog in a separate EDT event.
   * This defers the expensive pack() operation to prevent EDT blocking.
   */
  private void packAndShowDialog() {
    boolean showing = false;
    try {
      JDialog dialog = dialogFrame.getDialog();
      // No drag-and-drop in the wizard, so pack() doesn't register its window for it (#1464).
      SetupWindow.removeDropTargets(dialog.getContentPane());
      // Pack the dialog to realize components and trigger native initialization
      dialog.pack();
      SplashWindow.instance().setVisible(false);
      dialogFrame.showDialog(this::wizardClosed);
      showing = true;
    } finally {
      if (!showing) {
        wizardClosed();
      }
    }
  }

  /** Brings the splash back for the rest of startup and lets {@link #awaitClosed()} return. */
  void wizardClosed() {
    if (exiting) {
      // The JVM is exiting: keep the startup thread parked so the core never starts.
      return;
    }
    SplashWindow.instance().setVisible(true);
    closed.countDown();
  }

  /**
   * Blocks the calling (startup) thread until the wizard has closed. The wizard opens in a later
   * EDT event and runs its own modal loop, so {@link #createIfNeeded()} returns while it is still
   * up; without this wait, startup went on to start the BitTorrent core and build and show the main
   * window inside the wizard's modal loop, before the user had chosen the settings they depend on.
   * Returns right away on the EDT, which must never wait for its own modal loop.
   */
  public void awaitClosed() {
    if (EventQueue.isDispatchThread()) {
      return;
    }
    try {
      closed.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Enables the bitmask of specified actions, the other actions are explicitly disabled. */
  void enableActions(int actions) {
    previousAction.setEnabled((actions & ACTION_PREVIOUS) != 0);
    nextAction.setEnabled((actions & ACTION_NEXT) != 0);
    finishAction.setEnabled((actions & ACTION_FINISH) != 0);
    cancelAction.setEnabled((actions & ACTION_CANCEL) != 0);
  }

  /**
   * Applies {@code window}'s settings, then calls {@code then} on the EDT with the error, or null
   * when they were applied. The page reads its settings on the EDT; the disk work they need ({@link
   * SetupWindow#diskWork()}) runs on {@link #diskExecutor} while the wizard's buttons, other than
   * Cancel, are disabled, so a slow disk can't freeze the wizard and the user can't leave the page
   * twice. Without disk work, {@code then} runs right away.
   */
  void applySettings(SetupWindow window, boolean loadCoreComponents, Consumer<String> then) {
    String pageError = null;
    try {
      window.applySettings(loadCoreComponents);
    } catch (ApplySettingsException e) {
      pageError = messageOf(e);
    }
    SetupWindow.DiskWork work = window.diskWork();
    if (work == null) {
      then.accept(pageError);
      return;
    }
    String errorBeforeDisk = pageError;
    enableActions(ACTION_CANCEL);
    diskExecutor.execute(
        () -> {
          String diskError = null;
          boolean ran = false;
          try {
            work.run();
            ran = true;
          } catch (ApplySettingsException e) {
            diskError = messageOf(e);
            ran = true;
          } finally {
            // An unexpected exception keeps the user on the page, as it did on the EDT.
            boolean finished = ran;
            String error = joinErrors(diskError, errorBeforeDisk);
            SwingUtilities.invokeLater(
                () -> {
                  enableActions(window.getAppropriateActions());
                  if (finished) {
                    then.accept(error);
                  }
                });
          }
        });
  }

  private static String messageOf(ApplySettingsException e) {
    return e.getMessage() == null ? "" : e.getMessage();
  }

  /** Both errors, one paragraph each, or null when there are none. */
  private static String joinErrors(String first, String second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return first + "\n\n" + second;
  }

  /** Shows a page's error to the user, unless it has no message. */
  private static void showError(String error) {
    if (!error.isEmpty()) {
      GUIMediator.showError(error);
    }
  }

  /** Displays the next window in the setup sequence. */
  private void next() {
    SetupWindow current = _currentWindow;
    applySettings(
        current,
        true,
        error -> {
          if (error != null) {
            // there was a problem applying the settings from the current window, so display the
            // error message to the user.
            showError(error);
          } else {
            show(current.getNext());
          }
        });
  }

  /** Displays the previous window in the setup sequence. */
  private void previous() {
    SetupWindow current = _currentWindow;
    // ignore errors when going backwards
    applySettings(current, false, error -> show(current.getPrevious()));
  }

  /** Cancels the setup. */
  private void cancelSetup() {
    exit(0);
    dialogFrame.getDialog().dispose();
  }

  /**
   * Quits the app from the wizard. System.exit runs the shutdown hooks and waits for them, so it
   * runs off the EDT; the wizard's modal loop then ends normally, and {@link #wizardClosed()} keeps
   * startup from resuming while the JVM exits.
   */
  void exit(int status) {
    exiting = true;
    exiter.accept(status);
  }

  /** Completes the setup. */
  private void finishSetup() {
    if (_currentWindow == null) {
      completeSetup();
      return;
    }
    applySettings(
        _currentWindow,
        true,
        error -> {
          if (error != null) {
            // there was a problem applying the settings from the current window, so display the
            // error message to the user.
            showError(error);
          } else {
            completeSetup();
          }
        });
  }

  /** Saves that the setup is done and closes the wizard, once the last page's settings applied. */
  private void completeSetup() {
    dialogFrame.getDialog().dispose();
    ApplicationSettings.INSTALLED.setValue(true);
    InstallSettings.SAVE_DIRECTORY.setValue(true);
    InstallSettings.SPEED.setValue(true);
    InstallSettings.SCAN_FILES.setValue(true);
    InstallSettings.LANGUAGE_CHOICE.setValue(true);
    InstallSettings.EXTENSION_OPTION.setValue(true);
    if (GUIUtils.shouldShowStartOnStartupWindow()) InstallSettings.START_STARTUP.setValue(true);
    if (OSUtils.isWindows()) InstallSettings.FIREWALL_WARNING.setValue(true);
    InstallSettings.ASSOCIATION_OPTION.setValue(FrostAssociations.CURRENT_ASSOCIATIONS);
    InstallSettings.LAST_FROSTWIRE_VERSION_WIZARD_INVOKED.setValue(
        String.valueOf(FrostWireUtils.getBuildNumber()));
    BackgroundQueuedExecutorService.schedule(() -> SettingsGroupManager.instance().save());
    if (_currentWindow instanceof IntentWindow) {
      IntentWindow intent = (IntentWindow) _currentWindow;
      if (!intent.isConfirmedWillNot()) {
        GUIMediator.showWarning(
            "FrostWire is not distributed to people who intend to use it for the purposes of copyright infringement.\n\nThank you for your interest; however, you cannot continue to use FrostWire at this time.");
        exit(1);
      } else {
        intent.applySettings(true);
      }
    }
    dialogFrame.getDialog().dispose();
  }

  /** Instructs the buttons to redo their text. */
  private void updateLanguage() {
    for (LanguageAwareAction action : actions) {
      action.updateLanguage();
    }
    SetupWindow current = _currentWindow;
    applySettings(current, false, ignored -> current.handleWindowOpeningEvent());
  }

  /** Show the specified window */
  private void show(SetupWindow window) {
    window.handleWindowOpeningEvent();
    _setupWindowHolder.show(window.getKey());
    _currentWindow = window;
  }

  /**
   * Which pages the wizard shows, decided off the EDT.
   *
   * @param partial whether a previous run already completed setup, so this run only asks about new
   *     features
   */
  record Pages(
      boolean saveDirectory,
      boolean startOnStartup,
      boolean associations,
      boolean intent,
      boolean partial) {
    static final Pages NONE = new Pages(false, false, false, false, false);
  }

  private enum SaveStatus {
    NO,
    NEEDS
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
