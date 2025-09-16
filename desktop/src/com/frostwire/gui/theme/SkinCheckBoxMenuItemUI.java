/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.theme;

import com.apple.laf.AquaMenuItemUI;
import com.frostwire.util.OSUtils;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.synth.SynthCheckBoxMenuItemUI;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinCheckBoxMenuItemUI extends SynthCheckBoxMenuItemUI {
    public static ComponentUI createUI(JComponent comp) {
        ThemeMediator.testComponentCreationThreadingViolation();
        if (OSUtils.isMacOSX() && !(comp instanceof SkinCheckBoxMenuItem)) {
            return AquaMenuItemUI.createUI(comp);
        } else {
            return new SkinCheckBoxMenuItemUI();
        }
    }
}
