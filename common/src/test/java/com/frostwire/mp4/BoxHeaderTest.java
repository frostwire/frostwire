/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp4;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Box headers read from a file are checked before the box is read (frostwire/frostwire#1356): a
 * malformed file is an {@link IOException}, never an undocumented runtime exception.
 */
class BoxHeaderTest {

    /** Minimized Jazzer findings, one per symptom. */
    @ParameterizedTest
    @ValueSource(strings = {
            "a500000000000000", // size past the end of the file (was UnsupportedOperationException)
            "000000006573647300000000", // esds running to the end, shorter than its fields (was IllegalArgumentException)
            "0074000066747970", // ftyp larger than the 100 KiB buffer (was IllegalArgumentException)
            "0000000b66747970000000", // ftyp shorter than its fields (was BufferUnderflowException)
            "0000000c6674797000000000", // same, one byte longer
            "0000006c766d68640000000000000000000000000000000000000000", // vmhd past the file (was NullPointerException)
            "0000001ccb7479704d342500712034414d6d343269737561646d6f6d00000010636f76720020001400736f6d0000001066726565002000140000000000000010e4667265652000003a4d304120000000010110667265652048484848480000001ccb7479704d001c667479704d35003a00000000", // covr shorter than its data header (was NegativeArraySizeException)
    })
    void fuzzerFilesAreIOExceptions(String hex) throws IOException {
        byte[] file = HexFormat.of().parseHex(hex);
        assertThrows(IOException.class, () -> read(file, new ArrayList<>()));
        // The Library's call on a real file.
        File f = File.createTempFile("box-header", ".m4a");
        try {
            Files.write(f.toPath(), file);
            try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
                assertThrows(IOException.class, () -> IsoFile.head(in, ByteBuffer.allocate(100 * 1024)));
            }
        } finally {
            assertTrue(f.delete());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 7})
    void sizesSmallerThanTheHeaderAreRejected(int size) {
        byte[] file = ByteBuffer.allocate(16).putInt(size).putInt(Bits.make4cc("free")).array();
        IOException e = assertThrows(IOException.class, () -> read(file, new ArrayList<>()));
        assertTrue(e.getMessage().startsWith("Invalid size"), e.getMessage());
    }

    @Test
    void aUuidBoxMustHoldItsUserType() {
        // A dref entry typed uuid but sized 8: its header alone takes 24 bytes.
        ByteBuffer b = ByteBuffer.allocate(28);
        b.putInt(28).putInt(Bits.make4cc("dref")).putInt(0).putInt(1);
        b.putInt(8).putInt(Box.uuid).putInt(0);
        IOException e = assertThrows(IOException.class, () -> read(b.array(), new ArrayList<>()));
        assertTrue(e.getMessage().startsWith("Invalid size"), e.getMessage());
    }

    @Test
    void childrenLargerThanTheirParentAreRejected() {
        ByteBuffer b = ByteBuffer.allocate(32);
        b.putInt(16).putInt(Bits.make4cc("moov")); // moov holds 8 bytes of children...
        b.putInt(24).putInt(Bits.make4cc("free")); // ...but its child claims 24
        IOException e = assertThrows(IOException.class, () -> read(b.array(), new ArrayList<>()));
        assertTrue(e.getMessage().contains("larger than its parent"), e.getMessage());
    }

    @Test
    void extraBytesInALeafBoxAreSkippedNotReadAsChildren() throws IOException {
        // vmhd: 12 bytes of fields (full box header, graphicsmode, opcolor) plus 8 extra bytes,
        // then a sibling that must still be found.
        ByteBuffer b = ByteBuffer.allocate(28 + 8);
        b.putInt(28).putInt(Bits.make4cc("vmhd")).putInt(1).put(new byte[8]).put(new byte[8]);
        b.putInt(8).putInt(Bits.make4cc("free"));
        List<Box> boxes = new ArrayList<>();
        read(b.array(), boxes);
        assertEquals(List.of("vmhd", "free"), boxes.stream().map(Box::toString).toList());
    }

    @Test
    void anEmptyBoxDoesNotSwallowTheRestOfTheFile() throws IOException {
        ByteBuffer b = ByteBuffer.allocate(24);
        b.putInt(8).putInt(Bits.make4cc("free")); // no payload
        b.putInt(8).putInt(Bits.make4cc("abcd")); // unknown type, no payload
        b.putInt(8).putInt(Bits.make4cc("skip"));
        List<Box> boxes = new ArrayList<>();
        read(b.array(), boxes);
        assertEquals(List.of("free", "abcd", "skip"), boxes.stream().map(Box::toString).toList());
    }

    private static void read(byte[] file, List<Box> boxes) throws IOException {
        IsoMedia.read(new InputChannel(Channels.newChannel(new ByteArrayInputStream(file)), file.length),
                ByteBuffer.allocate(100 * 1024), b -> boxes.add(b));
    }
}
