/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.util;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Unit test for buffered I/O improvements in ZipUtils.
 * Verifies that zip extraction works correctly with the new buffered I/O implementation.
 * 
 * @author copilot
 */
public class BufferedIOTest {
    
    private static boolean testResult(String testName, boolean passed) {
        System.out.println((passed ? "PASSED" : "FAILED") + ": " + testName);
        return passed;
    }
    
    /**
     * Test that ZipUtils can successfully extract a zip file with the new buffered I/O
     */
    private static boolean testZipExtraction() {
        File tempZip = null;
        File tempOutput = null;
        try {
            // Create a temporary test zip file
            tempZip = File.createTempFile("test", ".zip");
            tempOutput = new File(System.getProperty("java.io.tmpdir"), "test_unzip_" + System.currentTimeMillis());
            
            // Create a simple zip file with test content
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
                // Add a text file entry
                ZipEntry entry = new ZipEntry("test.txt");
                zos.putNextEntry(entry);
                String testContent = "Test content for buffered I/O validation. ";
                // Write enough data to test buffer behavior (more than 32KB)
                for (int i = 0; i < 1000; i++) {
                    zos.write(testContent.getBytes());
                }
                zos.closeEntry();
                
                // Add a directory entry
                ZipEntry dirEntry = new ZipEntry("testdir/");
                zos.putNextEntry(dirEntry);
                zos.closeEntry();
                
                // Add a file in the directory
                ZipEntry fileInDir = new ZipEntry("testdir/nested.txt");
                zos.putNextEntry(fileInDir);
                zos.write("Nested file content".getBytes());
                zos.closeEntry();
            }
            
            // Test unzipping
            boolean result = ZipUtils.unzip(tempZip, tempOutput);
            if (!result) {
                return testResult("Zip extraction returned false", false);
            }
            
            // Verify extracted files exist
            File extractedFile = new File(tempOutput, "test.txt");
            if (!extractedFile.exists()) {
                return testResult("Extracted file does not exist", false);
            }
            
            // Verify extracted directory exists
            File extractedDir = new File(tempOutput, "testdir");
            if (!extractedDir.exists() || !extractedDir.isDirectory()) {
                return testResult("Extracted directory does not exist", false);
            }
            
            // Verify nested file exists
            File nestedFile = new File(tempOutput, "testdir/nested.txt");
            if (!nestedFile.exists()) {
                return testResult("Nested file does not exist", false);
            }
            
            // Verify file size (should be ~40KB)
            long fileSize = extractedFile.length();
            if (fileSize < 40000 || fileSize > 50000) {
                return testResult("Extracted file size unexpected: " + fileSize, false);
            }
            
            return testResult("Zip extraction with buffered I/O", true);
            
        } catch (Exception e) {
            e.printStackTrace();
            return testResult("Zip extraction threw exception: " + e.getMessage(), false);
        } finally {
            // Cleanup
            if (tempZip != null && tempZip.exists()) {
                tempZip.delete();
            }
            if (tempOutput != null && tempOutput.exists()) {
                deleteRecursively(tempOutput);
            }
        }
    }
    
    /**
     * Test that BufferedOutputStream properly handles large writes
     */
    private static boolean testBufferedOutputStream() {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("buffered_test", ".dat");
            
            // Write data using BufferedOutputStream with 32KB buffer
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, 32768)) {
                
                byte[] data = new byte[32768];
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) (i % 256);
                }
                
                // Write multiple buffers to test buffering behavior
                for (int i = 0; i < 10; i++) {
                    bos.write(data);
                }
            }
            
            // Verify file was written correctly
            long expectedSize = 32768L * 10;
            if (tempFile.length() != expectedSize) {
                return testResult("BufferedOutputStream size mismatch: expected " + expectedSize + ", got " + tempFile.length(), false);
            }
            
            return testResult("BufferedOutputStream large writes", true);
            
        } catch (Exception e) {
            e.printStackTrace();
            return testResult("BufferedOutputStream test threw exception: " + e.getMessage(), false);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
    
    public static void main(String[] args) {
        System.out.println("=== Buffered I/O Test ===");
        
        boolean allTestsPassed = true;
        
        allTestsPassed &= testBufferedOutputStream();
        allTestsPassed &= testZipExtraction();
        
        System.out.println("\n" + (allTestsPassed ? "ALL TESTS PASSED" : "SOME TESTS FAILED"));
        System.exit(allTestsPassed ? 0 : 1);
    }
}
