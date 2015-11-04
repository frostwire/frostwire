
// Microsoft Visual Studio compiles this Windows native code into SystemUtilities.dll
// LimeWire uses these functions from the class com.limegroup.gnutella.util.SystemUtils

// Access Win32 API calls availiable in Windows 2000
#define _WIN32_WINNT 0x0500

// Include Java and Windows headers

#include <jni.h>      // Java types for native code like jstring and JNIEnv
#include <jawt_md.h>  // Access Java's Active Widget Toolkit
#include <windows.h>  // Win32 types, like DWORD
#include <atlstr.h>   // CString, the Windows MFC and ATL string type