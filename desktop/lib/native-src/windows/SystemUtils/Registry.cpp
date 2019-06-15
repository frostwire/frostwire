
// Microsoft Visual Studio compiles this Windows native code into SystemUtilities.dll
// LimeWire uses these functions from the class com.limegroup.gnutella.util.SystemUtils

// Include the standard Windows DLL header and more headers
#include "stdafx.h"
#include "SystemUtilities.h"
#include "Registry.h"
#include <jni.h>

// Takes a root key handle name, a key path, and a registry variable name
// Gets the information from the registry
// Returns the text, blank if not found or any error
JNIEXPORT jstring JNICALL Java_org_limewire_util_SystemUtils_registryReadTextNative(JNIEnv *e, jclass c, jstring root, jstring path, jstring name) {
	return MakeJavaString(e, RegistryReadText(e, RegistryName(GetJavaString(e, root)), GetJavaString(e, path), GetJavaString(e, name)));
}
CString RegistryReadText(JNIEnv *e, HKEY root, LPCTSTR path, LPCTSTR name) {

	// Open the key
	CRegistry registry;
	if (!registry.Open(root, path, false)) return _T("");

	// Get the size required
	DWORD size;
	int result = RegQueryValueEx(
		registry.Key, // Handle to an open key
		name,         // Name of the value to read
		0,
		NULL,
		NULL,         // No data buffer, we're requesting the size
		&size);       // Required size in bytes including the null terminator
	if (result != ERROR_SUCCESS) return _T("");

	// Open a string
	CString s;
	LPTSTR buffer = s.GetBuffer(size / sizeof(TCHAR)); // How many characters we'll write, including the null terminator

	// Read the binary data
	result = RegQueryValueEx(
		registry.Key,   // Handle to an open key
		name,           // Name of the value to read
		0,
		NULL,
		(LPBYTE)buffer, // Data buffer, writes the null terminator
		&size);         // Size of data buffer in bytes
	s.ReleaseBuffer();
	if (result != ERROR_SUCCESS) ThrowIOException(e, _T("couldn't read text")); // Throw an exception back to Java

	// Return the string
	return s;
}

// Takes a root key handle name, a key path, a registry variable name, and an integer
// Stores the information in the registry
// Returns false on error
JNIEXPORT jboolean JNICALL Java_org_limewire_util_SystemUtils_registryWriteNumberNative(JNIEnv *e, jclass c, jstring root, jstring path, jstring name, jint value) {
	return RegistryWriteNumber(RegistryName(GetJavaString(e, root)), GetJavaString(e, path), GetJavaString(e, name), value);
}
bool RegistryWriteNumber(HKEY root, LPCTSTR path, LPCTSTR name, int value) {

	// Open the key
	CRegistry registry;
	if (!registry.Open(root, path, true)) return false;

	// Set or make and set the number value
	int result = RegSetValueEx(
		registry.Key,         // Handle to an open key
		name,                 // Name of the value to set or make and set
		0,
		REG_DWORD,            // Variable type is a 32-bit number
		(const BYTE *)&value, // Address of the value data to load
		sizeof(DWORD));       // Size of the value data
	if (result != ERROR_SUCCESS) return false;
	return true;
}

// Takes a root key handle name, a key path, a registry variable name, and value text
// Stores the information in the registry
// Returns false on error
JNIEXPORT jboolean JNICALL Java_org_limewire_util_SystemUtils_registryWriteTextNative(JNIEnv *e, jclass c, jstring root, jstring path, jstring name, jstring value) {
	return RegistryWriteText(RegistryName(GetJavaString(e, root)), GetJavaString(e, path), GetJavaString(e, name), GetJavaString(e, value));
}
bool RegistryWriteText(HKEY root, LPCTSTR path, LPCTSTR name, LPCTSTR value) {

	// Open the key
	CRegistry registry;
	if (!registry.Open(root, path, true)) return false;

	// Set or make and set the text value
	int result = RegSetValueEx(
		registry.Key,                          // Handle to an open key
		name,                                  // Name of the value to set or make and set
		0,
		REG_SZ,                                // Variable type is a null-terminated string
		(const BYTE *)value,                   // Address of the value data to load
		(lstrlen(value) + 1) * sizeof(TCHAR)); // Size of the value data in bytes, add 1 to write the null terminator
	if (result != ERROR_SUCCESS) return false;
	return true;
}

// Takes a root key handle name or open base key, and the path to a key beneath it
// Deletes the key from the registry, including its subkeys
// Returns false on error
JNIEXPORT jboolean JNICALL Java_org_limewire_util_SystemUtils_registryDeleteNative(JNIEnv *e, jclass c, jstring root, jstring path) {
	return RegistryDelete(RegistryName(GetJavaString(e, root)), GetJavaString(e, path));
}
bool RegistryDelete(HKEY base, LPCTSTR path) {

	// Open the key
	CRegistry key;
	if (!key.Open(base, path, true)) return false;

	// Loop for each subkey, deleting them all
	DWORD size;
	TCHAR subkey[MAX_PATH];
	int result;
	while (true) {

		// Get the name of the first subkey
		size = MAX_PATH;
		result = RegEnumKeyEx(key.Key, 0, subkey, &size, NULL, NULL, NULL, NULL);
		if (result == ERROR_NO_MORE_ITEMS) break; // There are no subkeys
		else if (result != ERROR_SUCCESS) return false; // RegEnumKeyEx returned an error

		// Delete it, making the next subkey the new first one
		if (!RegistryDelete(key.Key, subkey)) return false;
	}

	// We've cleared this key of subkeys, close it and delete it
	key.Close();
	result = RegDeleteKey(base, path);
	if (result != ERROR_SUCCESS && result != ERROR_FILE_NOT_FOUND) return false;
	return true;
}

// Takes a root key handle name, a key path, and true to make keys and get write access
// Opens or creates and opens the key with full access
// Returns false on error
bool CRegistry::Open(HKEY root, LPCTSTR path, bool write) {

	// Make sure we were given a key and path
	if (!root || path == CString(_T(""))) return false;

	// Variables for opening the key
	HKEY key;
	DWORD info;
	int result;

	// If the caller wants write access, create the key if it isn't there
	if (write) {

		// Open or create and open the key
		result = RegCreateKeyEx(
			root,                    // Handle to open root key
			path,                    // Subkey name
			0,
			_T(""),
			REG_OPTION_NON_VOLATILE, // Save information in the registry file
			KEY_ALL_ACCESS,          // Get access to read and write values in the key we're making and opening
			NULL,
			&key,                    // The opened or created key handle is put here
			&info);                  // Tells if the key was opened or created and opened

	// If the caller only wants read access, don't create the key when trying to open it
	} else {

		// Open the key
		result = RegOpenKeyEx(
			root,     // Handle to open root key
			path,     // Subkey name
			0,
			KEY_READ, // We only need to read the key we're opening
			&key);    // The opened key handle is put here
	}

	// Check for an error from opening or making and opening the key
	if (result != ERROR_SUCCESS) return false;

	// Save the open key in this CRegistry object
	Key = key;
	return true;
}

// Takes a text name of a registry root key, like "HKEY_LOCAL_MACHINE"
// Returns the HKEY value Windows defines for it, or NULL if not found
HKEY RegistryName(LPCTSTR name) {

	// Look at the text name to return the matching registry root key handle value
	CString s = name;
	if      (s == _T("HKEY_CLASSES_ROOT"))   return HKEY_CLASSES_ROOT;
	else if (s == _T("HKEY_CURRENT_CONFIG")) return HKEY_CURRENT_CONFIG;
	else if (s == _T("HKEY_CURRENT_USER"))   return HKEY_CURRENT_USER;
	else if (s == _T("HKEY_LOCAL_MACHINE"))  return HKEY_LOCAL_MACHINE;
	else if (s == _T("HKEY_USERS"))          return HKEY_USERS;
	else return NULL;
}
