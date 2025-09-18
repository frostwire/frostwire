/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.tests;

import com.frostwire.search.knaben.KnabenSearchPerformer;
import com.frostwire.search.knaben.KnabenSearchResult;
import com.frostwire.util.Logger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for Knaben search performer JSON parsing
 * gradle test --tests "com.frostwire.tests.KnabenSearchPerformerUnitTest.testJsonParsing"
 */
public class KnabenSearchPerformerUnitTest {
    private final static Logger LOG = Logger.getLogger(KnabenSearchPerformerUnitTest.class);
    
    @Test
    public void testJsonParsing() {
        // Sample JSON response that might be returned by Knaben API
        String sampleJson = """
        {
            "torrents": [
                {
                    "name": "Ubuntu 22.04 LTS Desktop",
                    "infohash": "1234567890abcdef1234567890abcdef12345678",
                    "size": 4000000000,
                    "seeds": 50,
                    "created": "2024-01-15 10:30:00"
                },
                {
                    "title": "Ubuntu Server 22.04",
                    "hash": "abcdef1234567890abcdef1234567890abcdef12",
                    "length": 2000000000,
                    "seeders": 25,
                    "upload_date": "2024-01-10"
                }
            ]
        }
        """;
        
        KnabenSearchPerformer performer = new KnabenSearchPerformer(1, "ubuntu", 10000);
        
        try {
            // Use reflection to access the private parseJsonResponse method
            Method parseMethod = KnabenSearchPerformer.class.getDeclaredMethod("parseJsonResponse", String.class);
            parseMethod.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            List<KnabenSearchResult> results = (List<KnabenSearchResult>) parseMethod.invoke(performer, sampleJson);
            
            assertNotNull(results);
            assertEquals(2, results.size());
            
            // Test first result
            KnabenSearchResult result1 = results.get(0);
            assertEquals("Ubuntu 22.04 LTS Desktop", result1.getDisplayName());
            assertEquals("1234567890abcdef1234567890abcdef12345678", result1.getHash());
            assertEquals(4000000000L, result1.getSize());
            assertEquals(50, result1.getSeeds());
            assertEquals("Knaben", result1.getSource());
            
            // Test second result with different field names
            KnabenSearchResult result2 = results.get(1);
            assertEquals("Ubuntu Server 22.04", result2.getDisplayName());
            assertEquals("abcdef1234567890abcdef1234567890abcdef12", result2.getHash());
            assertEquals(2000000000L, result2.getSize());
            assertEquals(25, result2.getSeeds());
            
            LOG.info("JSON parsing test passed successfully");
            
        } catch (Exception e) {
            LOG.error("JSON parsing test failed", e);
            fail("JSON parsing test failed: " + e.getMessage());
        }
    }
    
    @Test
    public void testAlternativeJsonFormat() {
        // Test with a different JSON format (direct array)
        String sampleJson = """
        [
            {
                "filename": "Ubuntu 22.04.torrent",
                "info_hash": "fedcba0987654321fedcba0987654321fedcba09",
                "bytes": 3000000000,
                "seeder": 30,
                "date": "2024-01-12 15:45:30"
            }
        ]
        """;
        
        KnabenSearchPerformer performer = new KnabenSearchPerformer(1, "ubuntu", 10000);
        
        try {
            Method parseMethod = KnabenSearchPerformer.class.getDeclaredMethod("parseJsonResponse", String.class);
            parseMethod.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            List<KnabenSearchResult> results = (List<KnabenSearchResult>) parseMethod.invoke(performer, sampleJson);
            
            assertNotNull(results);
            assertEquals(1, results.size());
            
            KnabenSearchResult result = results.get(0);
            assertEquals("Ubuntu 22.04.torrent", result.getDisplayName());
            assertEquals("fedcba0987654321fedcba0987654321fedcba09", result.getHash());
            assertEquals(3000000000L, result.getSize());
            assertEquals(30, result.getSeeds());
            
            LOG.info("Alternative JSON format test passed successfully");
            
        } catch (Exception e) {
            LOG.error("Alternative JSON format test failed", e);
            fail("Alternative JSON format test failed: " + e.getMessage());
        }
    }
    
    @Test
    public void testActualKnabenApiFormat() {
        // Test with the actual format returned by Knaben API (hits array)
        String sampleJson = """
        {
            "hits": [
                {
                    "bytes": 328613232,
                    "cachedOrigin": "The Pirate Bay (proxy)",
                    "category": "XXX",
                    "categoryId": [5000000],
                    "date": "2019-09-01T22:00:00+00:00",
                    "details": "https://knaben.xyz/thepiratebay/description.php?id=123456",
                    "name": "Sample Torrent File",
                    "infohash": "1234567890abcdef1234567890abcdef12345678",
                    "seeds": 42
                }
            ]
        }
        """;
        
        KnabenSearchPerformer performer = new KnabenSearchPerformer(1, "test", 10000);
        
        try {
            Method parseMethod = KnabenSearchPerformer.class.getDeclaredMethod("parseJsonResponse", String.class);
            parseMethod.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            List<KnabenSearchResult> results = (List<KnabenSearchResult>) parseMethod.invoke(performer, sampleJson);
            
            assertNotNull(results);
            assertEquals(1, results.size());
            
            KnabenSearchResult result = results.get(0);
            assertEquals("Sample Torrent File", result.getDisplayName());
            assertEquals("1234567890abcdef1234567890abcdef12345678", result.getHash());
            assertEquals(328613232L, result.getSize());
            assertEquals(42, result.getSeeds());
            assertTrue(result.getDetailsUrl().contains("knaben.xyz"));
            
            LOG.info("Actual Knaben API format test passed successfully");
            
        } catch (Exception e) {
            LOG.error("Actual Knaben API format test failed", e);
            fail("Actual Knaben API format test failed: " + e.getMessage());
        }
    }
    
    @Test
    public void testHtmlErrorHandling() {
        // Test that HTML responses are handled gracefully
        String htmlResponse = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>404 Not Found</title>
        </head>
        <body>
            <h1>404 Not Found</h1>
            <p>The requested resource was not found.</p>
        </body>
        </html>
        """;
        
        KnabenSearchPerformer performer = new KnabenSearchPerformer(1, "ubuntu", 10000);
        
        try {
            Method parseMethod = KnabenSearchPerformer.class.getDeclaredMethod("parseJsonResponse", String.class);
            parseMethod.setAccessible(true);
            
            // This should throw an exception for HTML content
            Exception exception = assertThrows(Exception.class, () -> {
                parseMethod.invoke(performer, htmlResponse);
            });
            
            // The exception should contain a helpful message about HTML being returned
            assertTrue(exception.getCause().getMessage().contains("HTML instead of JSON"));
            
            LOG.info("HTML error handling test passed successfully");
            
        } catch (Exception e) {
            LOG.error("HTML error handling test failed", e);
            fail("HTML error handling test failed: " + e.getMessage());
        }
    }
}