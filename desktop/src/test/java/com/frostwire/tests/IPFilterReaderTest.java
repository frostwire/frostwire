package com.frostwire.tests;

import com.limegroup.gnutella.gui.options.panes.IPFilterPaneItem;
import com.limegroup.gnutella.gui.options.panes.ipfilter.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class IPFilterReaderTest {
    private static final String RESOURCES = "src/test/resources/ipfilter/";

    private IPFilterInputStreamReader createReader(String filename) throws Exception {
        File file = new File(RESOURCES + filename);
        String name = filename.toLowerCase();
        if (name.endsWith(".p2p")) {
            return new P2PIPFilterInputStreamReader(file);
        } else if (name.endsWith(".dat")) {
            return new DatFilterInputStreamReader(file);
        } else if (name.endsWith(".cidr")) {
            return new CidrFilterInputStreamReader(file);
        } else if (name.endsWith(".hosts")) {
            return new HostsFilterInputStreamReader(file);
        }
        throw new IllegalArgumentException("Unknown format for: " + filename);
    }

    private List<IPRange> readAll(IPFilterInputStreamReader reader) throws Exception {
        List<IPRange> ranges = new ArrayList<>();
        IPRange range;
        while ((range = reader.readLine()) != null) {
            ranges.add(range);
        }
        return ranges;
    }

    // --- P2P tests ---

    @Test
    public void testP2PReaderBasicParsing() throws Exception {
        IPFilterInputStreamReader reader = createReader("sample.p2p");
        try {
            List<IPRange> ranges = readAll(reader);
            assertEquals(4, ranges.size());
            assertEquals("192.168.1.100", ranges.get(0).startAddress());
            assertEquals("192.168.1.200", ranges.get(0).endAddress());
            assertEquals("BadActor", ranges.get(0).description());
            assertEquals("10.0.0.1", ranges.get(1).startAddress());
            assertEquals("10.0.0.255", ranges.get(1).endAddress());
            assertEquals("8.8.8.8", ranges.get(3).startAddress());
            assertEquals("8.8.8.8", ranges.get(3).endAddress());
        } finally {
            reader.close();
        }
    }

    @Test
    public void testP2PReaderSkipsComments() throws Exception {
        IPFilterInputStreamReader reader = createReader("sample.p2p");
        try {
            List<IPRange> ranges = readAll(reader);
            for (IPRange r : ranges) {
                assertFalse(r.description().startsWith("#"),
                        "Should not include comment lines: " + r.description());
            }
        } finally {
            reader.close();
        }
    }

    @Test
    public void testP2PReaderEmptyFile(@TempDir Path tempDir) throws Exception {
        File emptyFile = tempDir.resolve("empty.p2p").toFile();
        emptyFile.createNewFile();
        IPFilterInputStreamReader reader = new P2PIPFilterInputStreamReader(emptyFile);
        try {
            List<IPRange> ranges = readAll(reader);
            assertTrue(ranges.isEmpty());
        } finally {
            reader.close();
        }
    }

    // --- DAT tests ---

    @Test
    public void testDATReaderBasicParsing() throws Exception {
        IPFilterInputStreamReader reader = createReader("sample.dat");
        try {
            List<IPRange> ranges = readAll(reader);
            assertEquals(2, ranges.size());
            assertEquals("Tracker", ranges.get(0).description());
            assertEquals("203.0.113.1", ranges.get(0).startAddress());
            assertEquals("203.0.113.50", ranges.get(0).endAddress());
            assertEquals("Spoiler", ranges.get(1).description());
            assertEquals("198.51.100.0", ranges.get(1).startAddress());
            assertEquals("198.51.100.255", ranges.get(1).endAddress());
        } finally {
            reader.close();
        }
    }

    @Test
    public void testDATReaderSkipsComments() throws Exception {
        IPFilterInputStreamReader reader = createReader("sample.dat");
        try {
            List<IPRange> ranges = readAll(reader);
            for (IPRange r : ranges) {
                assertFalse(r.description().startsWith("#"),
                        "Should not include comment lines");
            }
        } finally {
            reader.close();
        }
    }

    // --- CIDR tests ---

    @Test
    public void testCIDRReaderBasicParsing() throws Exception {
        IPFilterInputStreamReader reader = createReader("sample.cidr");
        try {
            List<IPRange> ranges = readAll(reader);
            assertEquals(4, ranges.size());
            assertEquals("192.168.0.0", ranges.get(0).startAddress());
            assertEquals("192.168.0.255", ranges.get(0).endAddress());
            assertEquals("192.168.0.0/24", ranges.get(0).description());
            assertEquals("10.0.0.0", ranges.get(1).startAddress());
            assertEquals("10.255.255.255", ranges.get(1).endAddress());
        } finally {
            reader.close();
        }
    }

    @Test
    public void testCIDRReaderSkipsComments() throws Exception {
        IPFilterInputStreamReader reader = createReader("sample.cidr");
        try {
            List<IPRange> ranges = readAll(reader);
            for (IPRange r : ranges) {
                assertFalse(r.description().startsWith("#"),
                        "Should not include comment lines");
            }
        } finally {
            reader.close();
        }
    }

    @Test
    public void testCIDRReaderEdgeCases() throws Exception {
        IPFilterInputStreamReader reader = createReader("sample_mixed.cidr");
        try {
            List<IPRange> ranges = readAll(reader);
            assertEquals(3, ranges.size());
            assertEquals("192.168.1.1", ranges.get(0).startAddress());
            assertEquals("192.168.1.1", ranges.get(0).endAddress());
            assertEquals("0.0.0.0", ranges.get(1).startAddress());
            assertEquals("255.255.255.255", ranges.get(1).endAddress());
            assertEquals("8.8.8.8", ranges.get(2).startAddress());
            assertEquals("8.8.8.8", ranges.get(2).endAddress());
        } finally {
            reader.close();
        }
    }

    // --- Hosts tests ---

    @Test
    public void testHostsReaderBasicParsing() throws Exception {
        IPFilterInputStreamReader reader = createReader("sample.hosts");
        try {
            List<IPRange> ranges = readAll(reader);
            assertEquals(3, ranges.size());
            assertEquals("ad.tracker.com", ranges.get(0).description());
            assertEquals("0.0.0.0", ranges.get(0).startAddress());
            assertEquals("tracking.example.com", ranges.get(1).description());
            assertEquals("malware.evil.org", ranges.get(2).description());
        } finally {
            reader.close();
        }
    }

    @Test
    public void testHostsReaderSkipsLocalhost() throws Exception {
        IPFilterInputStreamReader reader = createReader("sample.hosts");
        try {
            List<IPRange> ranges = readAll(reader);
            for (IPRange r : ranges) {
                assertNotEquals("127.0.0.1", r.startAddress(),
                        "Should skip 127.0.0.1 entries");
            }
        } finally {
            reader.close();
        }
    }

    @Test
    public void testHostsReaderSkipsComments() throws Exception {
        IPFilterInputStreamReader reader = createReader("sample.hosts");
        try {
            List<IPRange> ranges = readAll(reader);
            for (IPRange r : ranges) {
                assertFalse(r.description().startsWith("#"),
                        "Should not include comment lines");
            }
        } finally {
            reader.close();
        }
    }

    @Test
    public void testHostsReaderSkipsPlainLocalhost(@TempDir Path tempDir) throws Exception {
        File hostsFile = tempDir.resolve("test_hosts").toFile();
        try (FileWriter fw = new FileWriter(hostsFile)) {
            fw.write("localhost blocked.com\n");
            fw.write("::1 ipv6.example.com\n");
            fw.write("0.0.0.0 valid.example.com\n");
        }
        IPFilterInputStreamReader reader = new HostsFilterInputStreamReader(hostsFile);
        try {
            List<IPRange> ranges = readAll(reader);
            assertEquals(1, ranges.size());
            assertEquals("valid.example.com", ranges.get(0).description());
        } finally {
            reader.close();
        }
    }

    // --- Integration tests (compressed) ---

    @Test
    @Tag("integration")
    public void testGZippedP2PDecompression(@TempDir Path tempDir) throws Exception {
        File gzFile = new File(RESOURCES + "level1.p2p.gz");
        if (!gzFile.exists()) {
            return;
        }
        File decompressed = tempDir.resolve("level1.p2p").toFile();
        try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(gzFile));
             FileOutputStream fos = new FileOutputStream(decompressed)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = gis.read(buffer)) > 0) {
                fos.write(buffer, 0, read);
            }
        }
        IPFilterInputStreamReader reader = new P2PIPFilterInputStreamReader(decompressed);
        try {
            List<IPRange> ranges = readAll(reader);
            assertTrue(ranges.size() > 0, "GZipped P2P should contain at least one range");
        } finally {
            reader.close();
        }
    }

    @Test
    @Tag("integration")
    public void testZippedP2PDecompression(@TempDir Path tempDir) throws Exception {
        File zipFile = new File(RESOURCES + "level1.p2p.zip");
        if (!zipFile.exists()) {
            return;
        }
        File decompressed = tempDir.resolve("level1_from_zip.p2p").toFile();
        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis);
             FileOutputStream fos = new FileOutputStream(decompressed)) {
            zis.getNextEntry();
            byte[] buffer = new byte[32768];
            int read;
            while ((read = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, read);
            }
        }
        IPFilterInputStreamReader reader = new P2PIPFilterInputStreamReader(decompressed);
        try {
            List<IPRange> ranges = readAll(reader);
            assertTrue(ranges.size() > 0, "Zipped P2P should contain at least one range");
        } finally {
            reader.close();
        }
    }

    // --- Format detection tests ---

    @Test
    public void testFormatDetectionP2P(@TempDir Path tempDir) throws Exception {
        File p2pFile = tempDir.resolve("detect.p2p").toFile();
        try (FileWriter fw = new FileWriter(p2pFile)) {
            fw.write("Tracker:1.2.3.4-5.6.7.8\n");
            fw.write("BadGuy:10.0.0.1-10.0.0.255\n");
            fw.write("Another:192.168.1.1-192.168.1.100\n");
        }
        assertEquals(IPFilterFormat.P2P, IPFilterPaneItem.getIPFilterFileFormat(p2pFile));
    }

    @Test
    public void testFormatDetectionCIDR(@TempDir Path tempDir) throws Exception {
        File cidrFile = tempDir.resolve("detect.cidr").toFile();
        try (FileWriter fw = new FileWriter(cidrFile)) {
            fw.write("192.168.0.0/24\n");
            fw.write("10.0.0.0/8\n");
            fw.write("172.16.0.0/12\n");
        }
        assertEquals(IPFilterFormat.CIDR, IPFilterPaneItem.getIPFilterFileFormat(cidrFile));
    }

    @Test
    public void testFormatDetectionHosts(@TempDir Path tempDir) throws Exception {
        File hostsFile = tempDir.resolve("detect.hosts").toFile();
        try (FileWriter fw = new FileWriter(hostsFile)) {
            fw.write("0.0.0.0 ad.tracker.com\n");
            fw.write("0.0.0.0 tracking.example.com\n");
            fw.write("0.0.0.0 malware.evil.org\n");
        }
        assertEquals(IPFilterFormat.HOSTS, IPFilterPaneItem.getIPFilterFileFormat(hostsFile));
    }

    @Test
    public void testFormatDetectionUnknown(@TempDir Path tempDir) throws Exception {
        File unknownFile = tempDir.resolve("detect.txt").toFile();
        try (FileWriter fw = new FileWriter(unknownFile)) {
            fw.write("This is just some random text\n");
            fw.write("No IP patterns here\n");
            fw.write("Nothing to detect\n");
        }
        assertNull(IPFilterPaneItem.getIPFilterFileFormat(unknownFile));
    }

    @Test
    public void testEndToEndImport() throws Exception {
        IPFilterInputStreamReader p2pReader = createReader("sample.p2p");
        try {
            List<IPRange> ranges = readAll(p2pReader);
            assertFalse(ranges.isEmpty());
            for (IPRange r : ranges) {
                assertNotNull(r.startAddress());
                assertNotNull(r.endAddress());
                assertNotNull(r.description());
                assertFalse(r.startAddress().isEmpty());
                assertFalse(r.endAddress().isEmpty());
            }
        } finally {
            p2pReader.close();
        }

        IPFilterInputStreamReader cidrReader = createReader("sample.cidr");
        try {
            List<IPRange> ranges = readAll(cidrReader);
            assertFalse(ranges.isEmpty());
            for (IPRange r : ranges) {
                assertNotNull(r.startAddress());
                assertNotNull(r.endAddress());
            }
        } finally {
            cidrReader.close();
        }

        IPFilterInputStreamReader hostsReader = createReader("sample.hosts");
        try {
            List<IPRange> ranges = readAll(hostsReader);
            assertFalse(ranges.isEmpty());
            for (IPRange r : ranges) {
                assertNotNull(r.startAddress());
                assertEquals(r.startAddress(), r.endAddress());
            }
        } finally {
            hostsReader.close();
        }
    }
}
