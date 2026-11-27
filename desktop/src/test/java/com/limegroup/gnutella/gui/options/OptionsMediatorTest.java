/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/** An options tree click that arrives after the dialog was disposed must be ignored (#1594). */
class OptionsMediatorTest {

  @Test
  void selectingANodeAfterTheOptionsWereDisposedDoesNothing() {
    OptionsMediator mediator = OptionsMediator.instance();
    mediator.disposeOptions();
    assertDoesNotThrow(() -> mediator.handleSelection(new OptionsTreeNode("KEY", "Name")));
  }

  @Test
  void reinitializingAPaneAfterTheOptionsWereDisposedDoesNothing() {
    OptionsMediator mediator = OptionsMediator.instance();
    mediator.disposeOptions();
    assertDoesNotThrow(() -> mediator.reinitPane("KEY"));
  }
}
