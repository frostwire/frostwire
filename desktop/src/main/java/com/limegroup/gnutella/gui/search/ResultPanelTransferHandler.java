package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.dnd.LimeTransferHandler;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import javax.swing.*;

class ResultPanelTransferHandler extends LimeTransferHandler {
  /** */
  /** The ResultPanel this is handling. */
  private final SearchResultMediator panel;

  ResultPanelTransferHandler(SearchResultMediator panel) {
    super(DnDConstants.ACTION_COPY | DnDConstants.ACTION_MOVE | DnDConstants.ACTION_LINK);
    this.panel = panel;
  }

  /** Creates a Transferable for the selected lines. */
  protected Transferable createTransferable(JComponent c) {
    return new SearchResultTransferable(panel, panel.getAllSelectedLines());
  }
}
