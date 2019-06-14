/*
 * Copyright (C) 2004 Sun Microsystems, Inc. All rights reserved. Use is
 * subject to license terms.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package com.frostwire.util.filetypes;

import org.limewire.util.OSUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.URL;

/**
 * Bottom layer java wrapper for Windows registry relevant APIs
 */
class WinRegistryWrapper {
    /**
     * Windows handles to <tt>HKEY_CURRENT_USER</tt> and
     * <tt>HKEY_LOCAL_MACHINE</tt> hives.
     */
    public final static int HKEY_CLASSES_ROOT = 0x80000000;
    public final static int HKEY_CURRENT_USER = 0x80000001;
    public final static int HKEY_LOCAL_MACHINE = 0x80000002;
    public final static int HKEY_USERS = 0x80000003;
    public final static int HKEY_CURRENT_CONFIG = 0x80000005;
    /* Windows error or status codes. */
    public static final int ERROR_SUCCESS = 0;
    public static final int ERROR_FILE_NOT_FOUND = 2;
    public static final int ERROR_ACCESS_DENIED = 5;
    public static final int ERROR_ITEM_EXIST = 0;
    private static final int ERROR_ITEM_NOTEXIST = 9;
    /* Constants for Windows registry element size limits */
    public static final int MAX_KEY_LENGTH = 255;
    public static final int MAX_VALUE_NAME_LENGTH = 255;
    /* Windows security masks */
    public static final int DELETE = 0x10000;
    public static final int KEY_QUERY_VALUE = 1;
    private static final int KEY_SET_VALUE = 2;
    public static final int KEY_CREATE_SUB_KEY = 4;
    public static final int KEY_ENUMERATE_SUB_KEYS = 8;
    private static final int KEY_READ = 0x20019;
    private static final int KEY_WRITE = 0x20006;
    public static final int KEY_ALL_ACCESS = 0xf003f;
    /* Constants used to interpret returns of native functions  */
    private static final int OPENED_KEY_HANDLE = 0;
    private static final int ERROR_CODE = 1;
    private static final int SUBKEYS_NUMBER = 0;
    private static final int VALUES_NUMBER = 2;

    static {
        try {
            if (OSUtils.isWindows() && OSUtils.isGoodWindows()) {
                if (OSUtils.isMachineX64()) {
                    System.loadLibrary("SystemUtilities");
                } else {
                    System.loadLibrary("SystemUtilitiesX86");
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Suppress default constructor for noninstantiability.
     */
    private WinRegistryWrapper() {
    }

    /**
     * Java wrapper for Windows registry API RegOpenKey()
     *
     * @param hKey   Windows registry folder
     * @param subKey key name
     * @return ERROR_SUCCESS if succeed, or error code if fail
     */
    private static native int[] RegOpenKey(int hKey, byte[] subKey,
                                           int securityMask);

    /**
     * Java wrapper for Windows registry API RegCloseKey()
     */
    private static native int RegCloseKey(int hKey);

    /**
     * Java wrapper for Windows registry API RegCreateKeyEx()
     */
    private static native int[] RegCreateKeyEx(int hKey, byte[] subKey);

    /**
     * Java wrapper for Windows registry API RegDeleteKey()
     */
    private static native int RegDeleteKey(int hKey, byte[] subKey);

    /**
     * Java wrapper for Windows registry API RegFlushKey()
     */
    private static native int RegFlushKey(int hKey);

    /**
     * Java wrapper for Windows registry API RegQueryValueEx()
     */
    private static native byte[] RegQueryValueEx(int hKey, byte[] valueName);

    /**
     * Java wrapper for Windows registry API RegSetValueEx()
     */
    private static native int RegSetValueEx(int hKey, byte[] valueName,
                                            byte[] value);

    /**
     * Java wrapper for Windows registry API RegDeleteValue()
     */
    private static native int RegDeleteValue(int hKey, byte[] valueName);

    /**
     * Java wrapper for Windows registry API RegQueryInfoKey()
     */
    private static native int[] RegQueryInfoKey(int hKey);

    /**
     * Java wrapper for Windows registry API RegEnumKeyEx()
     */
    private static native byte[] RegEnumKeyEx(int hKey, int subKeyIndex,
                                              int maxKeyLength);

    /**
     * Java wrapper for Windows registry API RegEnumValue()
     */
    private static native byte[] RegEnumValue(int hKey, int valueIndex,
                                              int maxValueNameLength);

    /**
     * Java wrapper for Windows API FindMimeFromData()
     */
    private static native byte[] FindMimeFromData(byte[] url, byte[] data);

    /*
     * Java wrapper for Windows API ExpandEnvironmentStrings()
     */
    private static native byte[] ExpandEnvironmentStrings(byte[] envBytes);

    /**
     * Returns this java string as a null-terminated byte array
     */
    private static byte[] stringToByteArray(String str) {
        if (str == null) {
            return null;
        }
        byte[] srcByte = str.getBytes();
        int srcLength = srcByte.length;
        byte[] result = new byte[srcLength + 1];
        System.arraycopy(srcByte, 0, result, 0, srcLength);
        result[srcLength] = 0;
        return result;
    }

    /**
     * Converts a null-terminated byte array to java string
     */
    private static String byteArrayToString(byte[] array) {
        if (array != null) {
            String temString = new String(array);
            if (temString != null) {
                return temString.substring(0, temString.length() - 1);
            }
        }
        return null;
    }

    /**
     * Creates the specified subkey under the parent key(hKey).
     * <p>
     * If the subkey already exists in the registry, the function just returns successfully.
     * An application can create a subkey several levels deep at the same time. Such as the
     * three preceding subkeys by specifying a string of the following form for the subKey
     * parameter: subkey1\subkey2\subkey3\subkey4
     * </p>
     *
     * @param hKey   specified windows registry folder constant
     * @param subKey given sub key (not null)
     * @return ERROR_SUCCESS if succedd, or error code if fail
     */
    public static int WinRegCreateKeyEx(int hKey, String subKey) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] createResult = RegCreateKeyEx(hKey, lpSubKey);
        if (createResult == null) {
            return -1;
        }
        if (createResult[ERROR_CODE] == ERROR_SUCCESS) {
            RegCloseKey(createResult[OPENED_KEY_HANDLE]);
        }
        return createResult[ERROR_CODE];
    }

    /**
     * Removes the specified subkey under the parent key(hKey) from the registry.
     * The entire sub key, including all of its values, is removed.
     *
     * @param hKey   specified windows registry folder constant
     * @param subKey given sub key (not null)
     * @return ERROR_SUCCESS if succedd, or error code if fail
     */
    public static int WinRegDeleteKey(int hKey, String subKey) {
        int result;
        byte[] lpSubKey = stringToByteArray(subKey);
        result = RegDeleteKey(hKey, lpSubKey);
        if (result == ERROR_SUCCESS) {
            return result;
        } else {
            int res = WinRegSubKeyExist(hKey, subKey);
            if (res == ERROR_ITEM_NOTEXIST) {
                return result;
            } else {
                String subSubKey;
                String[] subSubKeys = WinRegGetSubKeys(hKey, subKey,
                        MAX_KEY_LENGTH);
                if (subSubKeys == null)
                    return result;
                for (String key : subSubKeys) {
                    subSubKey = subKey + "\\" + key;
                    if (subSubKey != null) {
                        WinRegDeleteKey(hKey, subSubKey);
                    }
                }
                result = RegDeleteKey(hKey, lpSubKey);
                return result;
            }
        }
    }

    /**
     * Writes all the attributes of the specified sub key into the registry.
     *
     * @param hKey   specified windows registry folder constant
     * @param subKey given sub key (not null)
     * @return ERROR_SUCCESS if succedd, or error code if fail
     */
    public static int WinRegFlushKey(int hKey, String subKey) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_WRITE);
        if (openResult == null) {
            return -1;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return openResult[ERROR_CODE];
        } else {
            int flushResult = RegFlushKey(openResult[OPENED_KEY_HANDLE]);
            RegCloseKey(openResult[OPENED_KEY_HANDLE]);
            return flushResult;
        }
    }

    /**
     * Retrieves the data associated with the default or unnamed value of a specified
     * registry key. The data must be a null-terminated string.
     *
     * @param hKey      specified windows registry folder constant
     * @param subKey    given sub key (not null)
     * @param valueName given value name (not null)
     * @return content of the value, or null if fail or not exist
     */
    public static String WinRegQueryValueEx(int hKey, String subKey, String valueName) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_READ);
        if (openResult == null) {
            return null;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return null;
        } else {
            byte[] valueBytes;
            byte[] lpValueName = stringToByteArray(valueName);
            valueBytes = RegQueryValueEx(openResult[OPENED_KEY_HANDLE],
                    lpValueName);
            RegCloseKey(openResult[OPENED_KEY_HANDLE]);
            if (valueBytes != null) {
                if ((valueBytes.length == 1) && (valueBytes[0] == 0) && (valueName.equals(""))) {
                    return null;
                } else {
                    return byteArrayToString(valueBytes);
                }
            } else {
                return null;
            }
        }
    }

    /**
     * Sets the data and type of a specified value under a registry key.
     *
     * @param hKey      specified windows registry folder constant
     * @param subKey    given sub key (not null)
     * @param valueName given value name (not null)
     * @param value     given value (not null)
     * @return ERROR_SUCCESS if succedd, or error code if fail
     */
    public static int WinRegSetValueEx(int hKey, String subKey, String valueName, String value) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_SET_VALUE);
        if (openResult == null) {
            return -1;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return openResult[ERROR_CODE];
        } else {
            byte[] lpValueName = stringToByteArray(valueName);
            byte[] lpValue = stringToByteArray(value);
            int setResult = RegSetValueEx(openResult[OPENED_KEY_HANDLE],
                    lpValueName, lpValue);
            RegCloseKey(openResult[OPENED_KEY_HANDLE]);
            return setResult;
        }
    }

    /**
     * Removes a named value from the specified registry key.
     *
     * @param hKey      specified windows registry folder constant
     * @param subKey    given sub key (not null)
     * @param valueName given value name (not null)
     * @return ERROR_SUCCESS if succedd, or error code if fail
     */
    public static int WinRegDeleteValue(int hKey, String subKey, String valueName) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_WRITE);
        if (openResult == null) {
            return -1;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return openResult[ERROR_CODE];
        } else {
            byte[] lpValueName = stringToByteArray(valueName);
            int deleteResult = RegDeleteValue(openResult[OPENED_KEY_HANDLE],
                    lpValueName);
            RegCloseKey(openResult[OPENED_KEY_HANDLE]);
            return deleteResult;
        }
    }

    /**
     * Retrieves information about a specified registry key.
     *
     * @param hKey   specified windows registry folder constant
     * @param subKey given sub key (not null)
     * @return array contain query result
     */
    public static int[] WinRegQueryInfoKey(int hKey, String subKey) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_READ);
        if (openResult == null) {
            return null;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return openResult;
        } else {
            int[] queryResult = RegQueryInfoKey(openResult[OPENED_KEY_HANDLE]);
            RegCloseKey(openResult[OPENED_KEY_HANDLE]);
            return queryResult;
        }
    }

    /**
     * Enumerates subkeys of the specified registry key. The function retrieves information
     * about one subkey each time it is called.
     *
     * @param hKey         specified windows registry folder constant
     * @param subKey       given sub key (not null)
     * @param subKeyIndex  index of the sub key
     * @param maxKeyLength max length of sub keys
     * @return name of the sub key
     */
    public static String WinRegEnumKeyEx(int hKey, String subKey, int subKeyIndex, int maxKeyLength) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_READ);
        if (openResult == null) {
            return null;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return null;
        } else {
            byte[] keyBytes = RegEnumKeyEx(openResult[OPENED_KEY_HANDLE],
                    subKeyIndex, maxKeyLength);
            RegCloseKey(openResult[OPENED_KEY_HANDLE]);
            if (keyBytes != null) {
                return byteArrayToString(keyBytes);
            } else {
                return null;
            }
        }
    }

    /**
     * Enumerates the values for the specified registry key. The function copies one indexed
     * value name and data block for the key each time it is called.
     *
     * @param hKey               specified windows registry folder constant
     * @param subKey             given sub key (not null)
     * @param valueIndex         value index
     * @param maxValueNameLength max length of the value name
     * @return value name
     */
    public static String WinRegEnumValue(int hKey, String subKey, int valueIndex, int maxValueNameLength) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_READ);
        if (openResult == null) {
            return null;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return null;
        } else {
            byte[] valueBytes = RegEnumValue(openResult[OPENED_KEY_HANDLE],
                    valueIndex, maxValueNameLength);
            RegCloseKey(openResult[OPENED_KEY_HANDLE]);
            if (valueBytes != null) {
                return byteArrayToString(valueBytes);
            } else {
                return null;
            }
        }
    }

    /**
     * Enumerates all the sub keys under the specified registry key.
     *
     * @param hKey         specified windows registry folder constant
     * @param subKey       given sub key (not null)
     * @param maxKeyLength max number of sub keys
     * @return a array containing name of the sub keys
     */
    public static String[] WinRegGetSubKeys(int hKey, String subKey, int maxKeyLength) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_READ);
        if (openResult == null) {
            return null;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return null;
        } else {
            int[] queryResult = RegQueryInfoKey(openResult[OPENED_KEY_HANDLE]);
            int subKeysNum = queryResult[SUBKEYS_NUMBER];
            if (subKeysNum == 0) {
                RegCloseKey(openResult[OPENED_KEY_HANDLE]);
                return null;
            } else {
                String[] keyStrings = new String[subKeysNum];
                byte[] keyBytes;
                for (int subKeyIndex = 0; subKeyIndex < subKeysNum; subKeyIndex++) {
                    keyBytes = RegEnumKeyEx(openResult[OPENED_KEY_HANDLE],
                            subKeyIndex, maxKeyLength);
                    keyStrings[subKeyIndex] = byteArrayToString(keyBytes);
                }
                RegCloseKey(openResult[OPENED_KEY_HANDLE]);
                return keyStrings;
            }
        }
    }

    /**
     * Enumerates all the values under the specified registry key.
     *
     * @param hKey           specified windows registry folder constant
     * @param subKey         given sub key (not null)
     * @param maxValueLength max number of values
     * @return a string array containing the name of each value
     */
    public static String[] WinRegGetValues(int hKey, String subKey, int maxValueLength) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_READ);
        if (openResult == null) {
            return null;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return null;
        } else {
            int[] queryResult = RegQueryInfoKey(openResult[OPENED_KEY_HANDLE]);
            int valuesNum = queryResult[VALUES_NUMBER];
            if (valuesNum == 0) {
                RegCloseKey(openResult[OPENED_KEY_HANDLE]);
                return null;
            } else {
                String[] valueStrings = new String[valuesNum];
                byte[] valueBytes;
                for (int valueIndex = 0; valueIndex < valuesNum; valueIndex++) {
                    valueBytes = RegEnumValue(openResult[OPENED_KEY_HANDLE],
                            valueIndex, maxValueLength);
                    valueStrings[valueIndex] = byteArrayToString(valueBytes);
                }
                RegCloseKey(openResult[OPENED_KEY_HANDLE]);
                return valueStrings;
            }
        }
    }

    /**
     * Checks whether the specified registry key exsists in the registry.
     *
     * @param hKey   specified windows registry folder constant
     * @param subKey given sub key (not null)
     * @return ERROR_SUCCESS if succedd, or error code if fail
     */
    public static int WinRegSubKeyExist(int hKey, String subKey) {
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_READ);
        if (openResult == null) {
            return ERROR_ITEM_NOTEXIST;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return ERROR_ITEM_NOTEXIST;
        } else {
            RegCloseKey(openResult[OPENED_KEY_HANDLE]);
            return ERROR_ITEM_EXIST;
        }
    }

    /**
     * Checks whether the specified value exsists in the specified registry key.
     *
     * @param hKey      specified windows registry folder constant
     * @param subKey    given sub key (not null)
     * @param valueName given value name (not null)
     * @return ERROR_ITEM_EXIST if the value exists under given sub key
     */
    public static int WinRegValueExist(int hKey, String subKey, String valueName) {
        if (subKey.trim().equals("")) {
            return ERROR_ITEM_NOTEXIST;
        }
        byte[] lpSubKey = stringToByteArray(subKey);
        int[] openResult = RegOpenKey(hKey, lpSubKey, KEY_READ);
        if (openResult == null) {
            return ERROR_ITEM_NOTEXIST;
        }
        if (openResult[ERROR_CODE] != ERROR_SUCCESS) {
            return ERROR_ITEM_NOTEXIST;
        } else {
            byte[] lpValueName = stringToByteArray(valueName);
            byte[] valueBytes = RegQueryValueEx(openResult[OPENED_KEY_HANDLE],
                    lpValueName);
            RegCloseKey(openResult[OPENED_KEY_HANDLE]);
            if (valueBytes == null) {
                return ERROR_ITEM_NOTEXIST;
            } else {
                if ((valueBytes.length == 1) && (valueBytes[0] == 0) && (valueName.equals(""))) {
                    return ERROR_ITEM_NOTEXIST;
                } else {
                    return ERROR_ITEM_EXIST;
                }
            }
        }
    }

    /**
     * Determines the MIME type from the data provided.
     * Now the input data comes from the specified URL object.
     *
     * @param url given url (not null)
     * @return correponding mime type information, or null if couldn't
     */
    public static String WinFindMimeFromData(URL url) {
        byte[] urlBytes;
        byte[] result;
        String urlString = url.toString();
        urlBytes = stringToByteArray(urlString);
        result = FindMimeFromData(urlBytes, null);
        if (result != null) {
            return byteArrayToString(result);
        } else {
            byte[] dataBytes = new byte[256];
            try (DataInputStream inStream = new DataInputStream(url.openStream())) {
                // Read a buffer size of 256 bytes of data to sniff the mime type.
                inStream.read(dataBytes, 0, 256);
            } catch (IOException e) {
                // Cannot open the connection to the URL, return.
                return null;
            }
            // No matter what happens, always close streams already opened.
            result = FindMimeFromData(null, dataBytes);
            if (result != null) {
                return byteArrayToString(result);
            } else {
                return null;
            }
        }
    }

    /**
     * Expands environment-variable strings and replaces them with their defined values.
     * <p>
     * E.g: "%SystemRoot%\\system32\\NOTEPAD.EXE %1" -> "C:\\system32\\NOTEPAD.EXE %1"
     * </P>
     *
     * @param envVariable given environment variable (not null)
     * @return expression after environment variable replacement
     */
    public static String WinExpandEnvironmentStrings(String envVariable) {
        byte[] envVariableBytes = stringToByteArray(envVariable);
        byte[] resultBytes = ExpandEnvironmentStrings(envVariableBytes);
        return (byteArrayToString(resultBytes));
    }
}  


