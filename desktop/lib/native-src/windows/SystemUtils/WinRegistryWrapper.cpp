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

#include "stdafx.h"
//#include <afx.h>
//#include <afxdisp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
//#include <atlconv.h>
#include "WinRegistryWrapper.h"
#ifdef __cplusplus
extern "C" {
#endif

const int MAX_VALUE_LENGTH = 4096;
const int MAX_MIME_DATA_LENGTH = 256;

JNIEXPORT jintArray JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegOpenKey
             (JNIEnv* env, jclass cl, jint hKey, jbyteArray lpSubKey, jint securityMask) {
    HKEY handle;
    const char* str;
    jint tmp[2];
    int errorCode=-1;
    jintArray result;
    //Windows API invocation
    str = (const char*)env->GetByteArrayElements(lpSubKey, NULL);
    errorCode =  RegOpenKeyEx((HKEY)hKey, str, 0, securityMask, &handle);
    env->ReleaseByteArrayElements(lpSubKey, (signed char*)str, 0);
    //constructs return value
    tmp[0]= (int) handle;
    tmp[1]= errorCode;
    result = env->NewIntArray(2);
    if (result != NULL) {
	    env->SetIntArrayRegion(result, 0, 2, tmp);
    }
    return result;
}
 
JNIEXPORT jint JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegCloseKey
             (JNIEnv* env, jclass cl, jint hKey) {
    return (jint) RegCloseKey((HKEY) hKey);        
};
     
JNIEXPORT jintArray JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegCreateKeyEx
             (JNIEnv* env, jclass cl, jint hKey, jbyteArray lpSubKey) {
    HKEY handle;
    const char* str;
    jint tmp[3];
    DWORD lpdwDisposition;
    int errorCode;
    jintArray result;
    //Windows API invocation
    str = (const char*)env->GetByteArrayElements(lpSubKey, NULL);
    errorCode =  RegCreateKeyEx((HKEY)hKey, str, 0, NULL, 
                    REG_OPTION_NON_VOLATILE, KEY_READ, 
                    NULL, &handle, &lpdwDisposition);
    env->ReleaseByteArrayElements(lpSubKey, (signed char*)str, 0);
    //Construct the return array
    tmp[0]= (int) handle;
    tmp[1]= errorCode;
    tmp[2]= lpdwDisposition;
    result = env->NewIntArray(3);
    if (result != NULL) {
	    env->SetIntArrayRegion(result, 0, 3, tmp);
    }
    return result;
}
     
JNIEXPORT jint JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegDeleteKey
            (JNIEnv* env, jclass cl, jint hKey, jbyteArray lpSubKey) {
    const char* str;
    int result; 
    
    //Windows API invocation
    str = (const char*)env->GetByteArrayElements(lpSubKey, NULL);
    result = RegDeleteKey((HKEY)hKey, str);   
    env->ReleaseByteArrayElements(lpSubKey, (signed char*)str, 0);
    
    return  result;
};
     
JNIEXPORT jint JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegFlushKey
      (JNIEnv* env, jclass cl, jint hKey) {
      return RegFlushKey ((HKEY)hKey);
}
 
JNIEXPORT jbyteArray JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegQueryValueEx
       (JNIEnv* env, jclass cl, jint hKey, jbyteArray valueName) {
    const char* valueNameStr;
    unsigned char buffer[MAX_VALUE_LENGTH];
    jbyteArray result = NULL;
    DWORD valueType;
    DWORD valueSize = MAX_VALUE_LENGTH;
  
    valueNameStr = (const char*)env->GetByteArrayElements(valueName, NULL);
    if (RegQueryValueEx((HKEY)hKey, valueNameStr, NULL, &valueType, buffer,
          &valueSize) == ERROR_SUCCESS) {
        if (valueSize > 0) {
            if ((valueType == REG_SZ)||(valueType == REG_EXPAND_SZ)) {
                result = env->NewByteArray(valueSize);
                if (result != NULL) {
	                env->SetByteArrayRegion(result, 0, valueSize, 	               
	                (jbyte*)buffer);
	            }
            }
        } else {
            result = NULL;
        }
    }
    env->ReleaseByteArrayElements(valueName, (signed char*)valueNameStr, 0);
    return result;
} 

JNIEXPORT jint JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegSetValueEx
  (JNIEnv* env, jclass cl, jint hKey, jbyteArray valueName, jbyteArray data) {
    const char* valueNameStr;
    const char* dataStr;
    int size = -1; 
    int nameSize = -1;
    int error_code = -1;
    if ((valueName == NULL)||(data == NULL)) {return -1;}
    size = env->GetArrayLength(data);
    dataStr = (const char*)env->GetByteArrayElements(data, NULL);
    valueNameStr = (const char*)env->GetByteArrayElements(valueName, NULL);
  
    // Check that if dataStr contains unexpanded references to environment variables
    // (for example, "%PATH%"), sets the value type as REG_EXPAND_SZ. 
    // Or else, set as REG_SZ.
    boolean isDataExpandStr = false;
    int strIndex = -1, tmpIndex; 
    for(tmpIndex = 0; tmpIndex < strlen(dataStr); tmpIndex++) {
        if(dataStr[tmpIndex] == '%') {
            if(strIndex == -1) {
                strIndex = tmpIndex;
            } else {
                isDataExpandStr = true;
                break;
            }
        } else if((dataStr[tmpIndex] == ' ') || (dataStr[tmpIndex] == '\t')) {
            strIndex = -1;
        }
    }
  
    if(isDataExpandStr)  {
        error_code = RegSetValueEx((HKEY)hKey, valueNameStr, 0, 
                                                  REG_EXPAND_SZ, (const unsigned char*)dataStr, size);
    } else {
        error_code = RegSetValueEx((HKEY)hKey, valueNameStr, 0, 
                                              REG_SZ, (const unsigned char*)dataStr, size);
    }
                                                  
    env->ReleaseByteArrayElements(data, (signed char*)dataStr, 0);
    env->ReleaseByteArrayElements(valueName, (signed char*)valueNameStr, 0);
    return error_code;
}
 
JNIEXPORT jint JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegDeleteValue
          (JNIEnv* env, jclass cl, jint hKey, jbyteArray valueName) {
    const char* valueNameStr;
    int error_code = -1;
    if (valueName == NULL) {return -1;}
    valueNameStr = (const char*)env->GetByteArrayElements(valueName, NULL);
    error_code = RegDeleteValue((HKEY)hKey, valueNameStr);
    env->ReleaseByteArrayElements(valueName, (signed char*)valueNameStr, 0);
    return error_code;
}

JNIEXPORT jintArray JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegQueryInfoKey
                                (JNIEnv* env, jclass cl, jint hKey) {
    jintArray result;
    jint tmp[5];
    unsigned long valuesNumber = -1;
    unsigned long maxValueNameLength = -1;
    unsigned long maxSubKeyLength = -1;
    unsigned long subKeysNumber = -1;
    int errorCode = -1;
    errorCode = RegQueryInfoKey((HKEY)hKey, NULL, NULL, NULL,
           &subKeysNumber, &maxSubKeyLength, NULL, 
           &valuesNumber, &maxValueNameLength,
           NULL, NULL, NULL);
    tmp[0]= subKeysNumber;
    tmp[1]= (int)errorCode;
    tmp[2]= valuesNumber;
    tmp[3]= maxSubKeyLength;
    tmp[4]= maxValueNameLength;
    result = env->NewIntArray(5);
    if (result != NULL) {
	    env->SetIntArrayRegion(result, 0, 5, tmp);
    }
    return result;
}
 
JNIEXPORT jbyteArray JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegEnumKeyEx
   (JNIEnv* env, jclass cl, jint hKey , jint subKeyIndex, jint maxKeyLength) {
    unsigned long size = maxKeyLength;
    jbyteArray result;
    jbyte buffer[MAX_VALUE_LENGTH];
    
    if (RegEnumKeyEx((HKEY) hKey, subKeyIndex, (LPTSTR)buffer, &size, NULL, NULL,
                                           NULL, NULL) != ERROR_SUCCESS){
        result = NULL;
    } else {
        result = env->NewByteArray(size + 1);
        if (result != NULL) {
	        env->SetByteArrayRegion(result, 0, size + 1, (jbyte*)buffer);
        }
    }
    return result;
}
 
JNIEXPORT jbyteArray JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_RegEnumValue
  (JNIEnv* env, jclass cl, jint hKey , jint valueIndex, jint maxValueNameLength){
    unsigned long size = maxValueNameLength;
    jbyteArray result;
    jbyte buffer[MAX_VALUE_LENGTH];
    int error_code;

    error_code = RegEnumValue((HKEY) hKey, valueIndex, (LPTSTR)buffer, 
                                     &size, NULL, NULL, NULL, NULL);
    if (error_code != ERROR_SUCCESS){
        result = NULL;
    } else {
        result = env->NewByteArray(size + 1);
        if (result != NULL) {
	        env->SetByteArrayRegion(result, 0, size + 1, (jbyte*)buffer);
        }
    }
    return result;
}

/*
JNIEXPORT jbyteArray JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_FindMimeFromData
  (JNIEnv* env, jclass cl, jbyteArray url, jbyteArray data) { 
    USES_CONVERSION;
    jbyteArray result;
    WCHAR buffer[MAX_MIME_DATA_LENGTH];
    LPWSTR lpBuffer = (LPWSTR)buffer;
    const char* mimeStr;
    int error_code;
  
    if(url != NULL) { 
        const char* urlStr;
        urlStr = (const char*)env->GetByteArrayElements(url, NULL);
        LPCWSTR lpUrlString = A2W(urlStr);
        error_code = FindMimeFromData(NULL, lpUrlString, NULL, 0, NULL, 0, (LPWSTR*)&lpBuffer, 0);
        env->ReleaseByteArrayElements(url, (signed char*)urlStr, 0);
    } else {
        jbyte* dataBytes = env->GetByteArrayElements(data, NULL);
        int size = env->GetArrayLength(data);
        error_code = FindMimeFromData(NULL, NULL, (void*)dataBytes, size, NULL, 0, (LPWSTR*)&lpBuffer, 0);
        env->ReleaseByteArrayElements(data, dataBytes, 0);
    }

    if (error_code != NOERROR) {
        result = NULL;
    } else {
        mimeStr = W2A(lpBuffer);
        result = env->NewByteArray(strlen(mimeStr) + 1);
        if (result != NULL) {
	        env->SetByteArrayRegion(result, 0, strlen(mimeStr) + 1, (jbyte*)mimeStr);
        }
    }
    return result;
}
*/

JNIEXPORT jbyteArray JNICALL Java_com_frostwire_util_filetypes_WinRegistryWrapper_ExpandEnvironmentStrings
  (JNIEnv *env, jclass cl, jbyteArray envVariable) {
    const char* envVariableStr;
    jbyteArray result;
    int resultInt;
    jbyte buffer[MAX_VALUE_LENGTH];
    envVariableStr = (const char*)env->GetByteArrayElements(envVariable, NULL);
 
    // If the function succeeds, the return value is the number of TCHARs stored in the buffer.
    // Or else, the return value is zero. 
    if((resultInt = ExpandEnvironmentStrings(envVariableStr, (LPTSTR)buffer, MAX_VALUE_LENGTH)) == ERROR_SUCCESS) {
        result = NULL;
    } else {
        result = env->NewByteArray(sizeof(TCHAR)*resultInt);
        if (result != NULL) {
	        env->SetByteArrayRegion(result, 0, sizeof(TCHAR)*resultInt, (jbyte*)buffer);
        }
    }
    env->ReleaseByteArrayElements(envVariable, (signed char*)envVariableStr, 0);
    return result;
}  
  
#ifdef __cplusplus
}
#endif
