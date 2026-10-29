package com.limegroup.gnutella.gui.shell;

import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.options.ConfigureOptionsAction;
import com.limegroup.gnutella.settings.QuestionsHandler;
import java.util.Collection;
import java.util.concurrent.Executor;

/** Stores all the LimeAssociationOptions that FrostWire is set to use. */
public class ShellAssociationManager {
  private final Collection<LimeAssociationOption> associations;

  public ShellAssociationManager(Collection<LimeAssociationOption> associations) {
    this.associations = associations;
  }

  /**
   * Runs through all the associations that this manager is handling and checks to see if they can
   * be enabled. If 'prompt' is true, this will prompt the user (respecting any 'do not ask again'
   * settings) prior to moving an association to LimeWire from another program. If 'prompt' is
   * false, this will only change associations that are currently unset.
   *
   * <p>Reads and writes the Windows registry: call it off the EDT, or use {@link
   * #checkAndGrabInBackground} from the EDT.
   *
   * @param prompt whether to prompt the user
   * @return true if all allowed and supported associations are registered to us. (only meaningful
   *     if prompt is false).
   */
  public boolean checkAndGrab(boolean prompt) {
    boolean ret = grabAvailable();
    if (!ret && prompt) {
      promptToReassociate(Runnable::run);
    }
    return ret;
  }

  /**
   * The first-visibility check, from the EDT: registering the associations reads and writes the
   * Windows registry, which froze the main window as it first appeared (frostwire/frostwire#1378),
   * so that runs on {@code background}; only the question to the user, when another program holds
   * an association, runs on {@code edt}, and the re-registration it leads to goes back to {@code
   * background}.
   */
  public void checkAndGrabInBackground(Executor background, Executor edt) {
    background.execute(
        () -> {
          if (!grabAvailable()) {
            edt.execute(() -> promptToReassociate(background));
          }
        });
  }

  /**
   * Registers every allowed association that nobody handles.
   *
   * @return true if all allowed and supported associations are registered to us
   */
  private boolean grabAvailable() {
    boolean ret = true;
    for (LimeAssociationOption association : associations) {
      if (association.isAllowed()) {
        if (association.isAvailable()) // grab all available associations
        association.setEnabled(true);
        else if (!association.isEnabled()) ret = false;
      }
    }
    return ret;
  }

  /**
   * Asks the user whether to take back the associations another program holds, and on YES
   * re-registers them through {@code registry}.
   */
  private void promptToReassociate(Executor registry) {
    DialogOption answer =
        GUIMediator.showYesNoOtherMessage(
            I18n.tr(
                "One or more files or protocols that FrostWire uses are no longer associated with FrostWire. Would you like FrostWire to re-associate them?"),
            QuestionsHandler.GRAB_ASSOCIATIONS,
            I18n.tr("Details"));
    if (answer == DialogOption.YES) {
      registry.execute(
          () -> {
            for (LimeAssociationOption association : associations) {
              if (association.isAllowed() && !association.isEnabled()) association.setEnabled(true);
            }
          });
    } else if (answer == DialogOption.OTHER) {
      new ConfigureOptionsAction(I18n.tr("File Associations")).actionPerformed(null);
    }
  }
}
