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

package com.limegroup.gnutella.gui;

import com.frostwire.util.OSUtils;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;

class ShutdownWindow extends JDialog {
  /** Decoded off the EDT; the panel paints without it until it arrives. Only touched on the EDT. */
  private Image backgroundImage;

  ShutdownWindow() {
    super(GUIMediator.getAppFrame());
    final String image_path =
        String.format(
            "org/limewire/gui/images/%s.jpg",
            !OSUtils.isWindowsAppStoreInstall()
                ? "app_shutdown"
                : "windows_appstore_install_shutdown");
    final String backgroundUrl =
        !OSUtils.isWindowsAppStoreInstall()
            ? "https://www.frostwire.com/android/?from=shutdown"
            : "https://www.frostwire.com/give/?from=shutdown";

    setResizable(false);
    setTitle(I18n.tr("Shutting down FrostWire..."));
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    JPanel backgroundPanel =
        new JPanel() {
          @Override
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (backgroundImage != null) {
              g.drawImage(backgroundImage, 0, 0, null);
            }
          }
        };
    backgroundPanel.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseReleased(MouseEvent e) {
            e.consume();
            GUIMediator.openURL(backgroundUrl);
          }
        });
    backgroundPanel.setLayout(null);
    backgroundPanel.setSize(800, 500);
    add(backgroundPanel);
    Insets insets = backgroundPanel.getInsets();
    JLabel label = new JLabel(I18n.tr("Please wait while FrostWire shuts down..."));
    // Defer font loading to avoid EDT violation
    SwingUtilities.invokeLater(() -> label.setFont(new Font("Dialog", Font.PLAIN, 16)));
    Dimension labelPrefSize = label.getPreferredSize();
    backgroundPanel.add(label);
    label.setBounds(65 + insets.left, 400 + insets.top, labelPrefSize.width, labelPrefSize.height);
    JProgressBar bar = new LimeJProgressBar();
    bar.setIndeterminate(true);
    bar.setStringPainted(false);
    backgroundPanel.add(bar);
    bar.setBounds(55 + insets.left, 428 + insets.top, 680, 30);
    getContentPane().setPreferredSize(new Dimension(800, 500));
    pack();
    // Inflating and decoding the JPEG on the EDT stalled it while the window closed.
    decodeOffEdt(
        () -> {
          ImageIcon icon = ResourceManager.getImageFromResourcePath(image_path);
          return icon != null ? icon.getImage() : null;
        },
        image -> {
          backgroundImage = image;
          backgroundPanel.repaint();
        });
  }

  /**
   * Runs {@code decode} on a new daemon thread and hands a non-null result to {@code onEdt} on the
   * EDT.
   */
  static Thread decodeOffEdt(Supplier<Image> decode, Consumer<Image> onEdt) {
    Thread thread =
        new Thread(
            () -> {
              Image image = decode.get();
              if (image != null) {
                SwingUtilities.invokeLater(() -> onEdt.accept(image));
              }
            },
            "ShutdownWindow-image");
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  public static void main(String[] args) {
    ShutdownWindow window = new ShutdownWindow();
    window.setVisible(true);
  }
}
