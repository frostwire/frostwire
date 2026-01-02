/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
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

import com.frostwire.gui.theme.ThemeMediator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify that theme enum handling works correctly and doesn't crash
 * when encountering unknown theme names from older versions.
 */
public class ThemeEnumMigrationTest {

    @Test
    public void testThemeEnumValueOfWithValidValues() {
        // Test that all valid enum values can be parsed
        assertDoesNotThrow(() -> {
            ThemeMediator.ThemeEnum.valueOf("DEFAULT");
            ThemeMediator.ThemeEnum.valueOf("DARK_FLAT_LAF");
            ThemeMediator.ThemeEnum.valueOf("LIGHT_FLAT_LAF");
        });
    }

    @Test
    public void testThemeEnumValueOfWithInvalidValues() {
        // Test that old/invalid enum values throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            ThemeMediator.ThemeEnum.valueOf("DARK_LAF"); // old name
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            ThemeMediator.ThemeEnum.valueOf("LIGHT_LAF"); // old name
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            ThemeMediator.ThemeEnum.valueOf("UNKNOWN_THEME");
        });
    }

    @Test
    public void testSafeThemeEnumParsing() {
        // Test our safe parsing logic that should be used in the application
        String[] testValues = {"DEFAULT", "DARK_FLAT_LAF", "LIGHT_FLAT_LAF", "DARK_LAF", "LIGHT_LAF", "UNKNOWN"};
        
        for (String value : testValues) {
            ThemeMediator.ThemeEnum result;
            try {
                result = ThemeMediator.ThemeEnum.valueOf(value);
            } catch (IllegalArgumentException e) {
                // This is what should happen for unknown values
                result = ThemeMediator.ThemeEnum.DEFAULT;
            }
            
            assertNotNull(result, "Result should never be null");
            assertTrue(result == ThemeMediator.ThemeEnum.DEFAULT || 
                      result == ThemeMediator.ThemeEnum.DARK_FLAT_LAF || 
                      result == ThemeMediator.ThemeEnum.LIGHT_FLAT_LAF,
                      "Result should be one of the valid enum values");
        }
    }

    @Test
    public void testAllEnumValues() {
        // Verify all expected enum values exist
        ThemeMediator.ThemeEnum[] values = ThemeMediator.ThemeEnum.values();
        assertEquals(3, values.length, "Should have exactly 3 theme enum values");
        
        boolean hasDefault = false, hasDarkFlat = false, hasLightFlat = false;
        for (ThemeMediator.ThemeEnum value : values) {
            switch (value) {
                case DEFAULT:
                    hasDefault = true;
                    break;
                case DARK_FLAT_LAF:
                    hasDarkFlat = true;
                    break;
                case LIGHT_FLAT_LAF:
                    hasLightFlat = true;
                    break;
            }
        }
        
        assertTrue(hasDefault, "Should have DEFAULT enum value");
        assertTrue(hasDarkFlat, "Should have DARK_FLAT_LAF enum value");
        assertTrue(hasLightFlat, "Should have LIGHT_FLAT_LAF enum value");
    }
}