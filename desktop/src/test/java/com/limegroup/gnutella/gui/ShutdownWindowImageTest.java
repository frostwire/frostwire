/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The shutdown window, built on the EDT, inflated and decoded its 800x500 JPEG there. The decode
 * must run off the EDT and the decoded image must be handed back on the EDT.
 */
@Timeout(15)
class ShutdownWindowImageTest {

  @Test
  void backgroundImageIsDecodedOffTheEdtAndDeliveredOnIt() throws Exception {
    Image decodedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    AtomicReference<Thread> edt = new AtomicReference<>();
    AtomicReference<Thread> decoder = new AtomicReference<>();
    AtomicReference<Thread> receiver = new AtomicReference<>();
    AtomicReference<Image> received = new AtomicReference<>();
    CountDownLatch delivered = new CountDownLatch(1);

    SwingUtilities.invokeAndWait(
        () -> {
          edt.set(Thread.currentThread());
          ShutdownWindow.decodeOffEdt(
              () -> {
                decoder.set(Thread.currentThread());
                return decodedImage;
              },
              image -> {
                receiver.set(Thread.currentThread());
                received.set(image);
                delivered.countDown();
              });
        });

    assertTrue(delivered.await(10, TimeUnit.SECONDS));
    assertNotSame(edt.get(), decoder.get(), "the JPEG must not be decoded on the EDT");
    assertSame(edt.get(), receiver.get(), "the image must be installed on the EDT");
    assertSame(decodedImage, received.get());
  }
}
