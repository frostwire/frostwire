/*
 * Created by Angel Leon (@gubatron)
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

package com.frostwire.tests;

import com.frostwire.search.PerformersHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for PerformersHelper JSON utility functions
 * gradle test --tests "com.frostwire.tests.PerformersHelperJsonTest.testJsonHelperFunctions"
 */
public class PerformersHelperJsonTest {
    
    @Test
    public void testJsonHelperFunctions() {
        // Test JSON parsing helper functions
        String testJson = """
        {
            "name": "Test Torrent",
            "size": "1024000000",
            "seeds": 50,
            "infohash": "1234567890abcdef1234567890abcdef12345678"
        }
        """;
        
        JsonElement element = JsonParser.parseString(testJson);
        JsonObject obj = element.getAsJsonObject();
        
        // Test getJsonString
        assertEquals("Test Torrent", PerformersHelper.getJsonString(obj, "name", "title"));
        assertEquals("Test Torrent", PerformersHelper.getJsonString(obj, "title", "name")); // Alternative order
        assertEquals("", PerformersHelper.getJsonString(obj, "nonexistent"));
        
        // Test getJsonLong with string number
        assertEquals(1024000000L, PerformersHelper.getJsonLong(obj, "size", "length"));
        assertEquals(0L, PerformersHelper.getJsonLong(obj, "nonexistent"));
        
        // Test getJsonInt
        assertEquals(50, PerformersHelper.getJsonInt(obj, "seeds", "seeders"));
        assertEquals(0, PerformersHelper.getJsonInt(obj, "nonexistent"));
        
        // Test info hash validation
        assertEquals("1234567890abcdef1234567890abcdef12345678", 
                    PerformersHelper.validateAndNormalizeInfoHash("1234567890ABCDEF1234567890ABCDEF12345678"));
        assertNull(PerformersHelper.validateAndNormalizeInfoHash("invalid"));
        assertNull(PerformersHelper.validateAndNormalizeInfoHash(""));
    }
    
    @Test
    public void testJsonArrayDetection() {
        // Test object with torrents array
        String jsonWithObject = """
        {
            "torrents": [
                {"name": "Test1"},
                {"name": "Test2"}
            ]
        }
        """;
        
        JsonElement element1 = JsonParser.parseString(jsonWithObject);
        JsonArray array1 = PerformersHelper.findJsonArrayInResponse(element1);
        assertNotNull(array1);
        assertEquals(2, array1.size());
        
        // Test direct array
        String jsonDirectArray = """
        [
            {"name": "Test1"},
            {"name": "Test2"}
        ]
        """;
        
        JsonElement element2 = JsonParser.parseString(jsonDirectArray);
        JsonArray array2 = PerformersHelper.findJsonArrayInResponse(element2);
        assertNotNull(array2);
        assertEquals(2, array2.size());
        
        // Test object with results array
        String jsonWithResults = """
        {
            "results": [
                {"name": "Test1"}
            ]
        }
        """;
        
        JsonElement element3 = JsonParser.parseString(jsonWithResults);
        JsonArray array3 = PerformersHelper.findJsonArrayInResponse(element3);
        assertNotNull(array3);
        assertEquals(1, array3.size());
        
        // Test object without known array fields
        String jsonWithoutArray = """
        {
            "unknown": "value"
        }
        """;
        
        JsonElement element4 = JsonParser.parseString(jsonWithoutArray);
        JsonArray array4 = PerformersHelper.findJsonArrayInResponse(element4);
        assertNull(array4);
    }
}