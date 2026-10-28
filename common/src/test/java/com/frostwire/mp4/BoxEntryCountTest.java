/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp4;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.util.HexFormat;
import java.util.LinkedList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entry counts read from a file are checked against the bytes left before anything is allocated
 * for them (frostwire/frostwire#1354): a 16-byte box claiming two billion entries must be rejected
 * as a bad file, not allocate gigabytes.
 */
class BoxEntryCountTest {

    /** Every box with an entry table, and the fields it reads between its full box header and the count. */
    static Stream<Object[]> boxesWithEntryTables() {
        return Stream.of(
                new Object[]{"elst", 0},
                new Object[]{"sbgp", 4}, // grouping_type
                new Object[]{"co64", 0},
                new Object[]{"stco", 0},
                new Object[]{"ctts", 0},
                new Object[]{"dref", 0},
                new Object[]{"stsd", 0},
                new Object[]{"stsz", 4}, // sample_size
                new Object[]{"stz2", 4}, // reserved and field_size
                new Object[]{"stsh", 0},
                new Object[]{"stsc", 0},
                new Object[]{"stss", 0},
                new Object[]{"stts", 0},
                new Object[]{"trun", 0});
    }

    @ParameterizedTest
    @MethodSource("boxesWithEntryTables")
    void hugeEntryCountsAreRejectedBeforeAllocating(String type, int fieldsBeforeCount) {
        byte[] file = boxWithCount(type, fieldsBeforeCount, Integer.MAX_VALUE);
        IOException e = assertThrows(IOException.class, () -> read(file));
        assertTrue(e.getMessage().startsWith("Invalid entry count"), e.getMessage());
    }

    @ParameterizedTest
    @MethodSource("boxesWithEntryTables")
    void negativeEntryCountsAreRejected(String type, int fieldsBeforeCount) {
        byte[] file = boxWithCount(type, fieldsBeforeCount, -1);
        IOException e = assertThrows(IOException.class, () -> read(file));
        assertTrue(e.getMessage().startsWith("Invalid entry count"), e.getMessage());
    }

    @Test
    void fuzzerInputsAreRejectedThroughTheLibraryPath() throws IOException {
        // Minimized from the Jazzer findings: elst and sbgp boxes whose counts came from the input.
        for (String hex : new String[]{"00000000656c73746e72652d65000000", "00000000736267700000000000000000ff000000"}) {
            File f = File.createTempFile("entry-count", ".m4a");
            try {
                Files.write(f.toPath(), HexFormat.of().parseHex(hex));
                try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
                    // MP4Parser's call: a file channel knows its own size.
                    assertThrows(IOException.class, () -> IsoFile.head(in, ByteBuffer.allocate(100 * 1024)), hex);
                }
            } finally {
                assertTrue(f.delete());
            }
        }
    }

    @Test
    void countsThatFitStillParse() throws IOException {
        // Four 4-byte chunk offsets fill the rest of the 32-byte box exactly.
        byte[] file = boxWithCount("stco", 0, 4);
        LinkedList<Box> boxes = new LinkedList<>();
        IsoMedia.read(new InputChannel(Channels.newChannel(new ByteArrayInputStream(file)), file.length),
                ByteBuffer.allocate(1024), b -> boxes.add(b));
        ChunkOffsetBox stco = (ChunkOffsetBox) boxes.getFirst();
        assertEquals(4, stco.entry_count);
    }

    private static void read(byte[] file) throws IOException {
        IsoMedia.read(new InputChannel(Channels.newChannel(new ByteArrayInputStream(file)), file.length),
                ByteBuffer.allocate(1024), b -> true);
    }

    /** A 32-byte box: header, full box header, the fields before the count, the count, zero padding. */
    private static byte[] boxWithCount(String type, int fieldsBeforeCount, int count) {
        ByteBuffer b = ByteBuffer.allocate(32);
        b.putInt(32);
        b.putInt(Bits.make4cc(type));
        b.putInt(0); // version and flags
        if (type.equals("stz2")) {
            b.putInt(16); // three reserved bytes, then a 16-bit field_size
        } else {
            b.put(new byte[fieldsBeforeCount]);
        }
        b.putInt(count);
        return b.array();
    }
}
