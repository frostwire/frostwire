
// Microsoft Visual Studio compiles this Windows native code into SystemUtilities.dll
// LimeWire uses these functions from the class com.limegroup.gnutella.util.SystemUtils

// Include the standard Windows DLL header and more headers
#include "stdafx.h"
#include "SystemUtilities.h"
#include "Shell.h"
#include "Registry.h"

// Access the icon handles
extern CSystemUtilities Handle;

// Returns the path of this running program, like "C:\Folder\Program.exe", or blank if error
JNIEXPORT jstring JNICALL Java_org_limewire_util_SystemUtils_getRunningPathNative(JNIEnv *e, jclass c) {
	return MakeJavaString(e, GetRunningPath());
}
CString GetRunningPath() {

	// Ask Windows for our path
	TCHAR bay[MAX_PATH];
	if (!GetModuleFileName(NULL, bay, MAX_PATH)) return _T("");
	return bay;
}

JNIEXPORT jstring JNICALL Java_org_limewire_util_SystemUtils_getShortFileNameNative(JNIEnv *e, jclass c, jstring name) {
	return MakeJavaString(e, GetShortFileName(GetJavaString(e, name)));
}

CString GetShortFileName(LPCTSTR name) {
	
	// prepend prefix so GetShortPathNameW() handles long strings correctly
	CString longFileName = _T("\\\\?\\");
	longFileName += name;
	
	// prepare string length
	int cchBuffer = longFileName.GetLength() + 1;
	
	// prepare input wchar buffer
	WCHAR * wcsLongName = new WCHAR[cchBuffer];
	memset(wcsLongName, 0, (cchBuffer) * sizeof(WCHAR) );
	
	// convert tchar string to wcs
	mbstowcs(wcsLongName, (LPCTSTR)longFileName, cchBuffer);

	// prepare output wchar buffer
	WCHAR * wcsShortName = new WCHAR[cchBuffer];
	memset(wcsShortName, 0, (cchBuffer) * sizeof(WCHAR) );

	// call API to get short path name
	GetShortPathNameW(wcsLongName, wcsShortName, cchBuffer);

	// store result (convert to tchar)
	CString shortFileName = wcsShortName;

	// removed \\?\ prefix
	CString finalShortFileName = (LPCTSTR)shortFileName + 4;
	
	delete []wcsLongName;
	delete []wcsShortName;

	return finalShortFileName;
}

// Takes a special folder name, like "ApplicationData"
// Looks up the full path to that folder for the current user as the user has customized it
// Returns the path like "C:\Documents and Settings\UserName\Application Data", or blank on error
JNIEXPORT jstring JNICALL Java_org_limewire_util_SystemUtils_getSpecialPathNative(JNIEnv *e, jclass c, jstring name) {
	return MakeJavaString(e, GetSpecialPath(GetJavaString(e, name)));
}
CString GetSpecialPath(LPCTSTR name) {

	// Look up the special folder ID from the given special folder name
	int id;
	if      (name == CString(_T("Documents")))         id = CSIDL_PERSONAL;
	else if (name == CString(_T("ApplicationData")))   id = CSIDL_APPDATA;
	else if (name == CString(_T("Desktop")))           id = CSIDL_DESKTOPDIRECTORY;
	else if (name == CString(_T("StartMenu")))         id = CSIDL_STARTMENU;
	else if (name == CString(_T("StartMenuPrograms"))) id = CSIDL_PROGRAMS;
	else if (name == CString(_T("StartMenuStartup")))  id = CSIDL_STARTUP;
	else return _T(""); // The given name is not in our list

	// Get the path of the special folder
	TCHAR bay[MAX_PATH];
	CString path;
	if (SHGetSpecialFolderPath(NULL, bay, id, false)) path = bay;
	return path; // If SHGetSpecialFolderPath failed, path will still be blank
}

// Takes the JNI environment and class
// The jobject frame is a AWT Component like a JFrame that is backed by a real Windows window
// bin is the path to the folder that has the file "jawt.dll", like "C:\Program Files\Java\jre1.5.0_05\bin"
// icon is the path to a Windows .exe or .ico file on the disk that contains the icons
// Gets the window handle, and uses it to set the icon
// Returns blank on success, or a text message about what didn't work
JNIEXPORT jstring JNICALL Java_org_limewire_util_SystemUtils_setWindowIconNative(JNIEnv *e, jclass c, jobject frame, jstring bin, jstring icon) {
	return MakeJavaString(e, SetWindowIcon(e, c, frame, GetJavaString(e, bin), GetJavaString(e, icon)));
}
CString SetWindowIcon(JNIEnv *e, jclass c, jobject frame, LPCTSTR bin, LPCTSTR icon) {

	// Get the Window handle from Java
	CString message;
	HWND window = GetJavaWindowHandle(e, c, frame, bin, &message);
	if (!window) return message; // Return the message that tells what happened

	// If we don't already have the icons, load them from the given .exe or .ico file
	GetIcons(icon);

	// Set both sizes of the window's icon
	if (Handle.Icon)      SendMessage(window, WM_SETICON, ICON_BIG,   (LPARAM)Handle.Icon);
	if (Handle.SmallIcon) SendMessage(window, WM_SETICON, ICON_SMALL, (LPARAM)Handle.SmallIcon);

	// Return blank on success
	return _T("");
}

// Tell Windows we changed a file type association, so it needs to refresh icons in the shell
JNIEXPORT jboolean JNICALL Java_org_limewire_util_SystemUtils_flushIconCacheNative(JNIEnv *e, jclass c) {
	return FlushIconCache();
}
bool FlushIconCache() {

	// Notify the Windows shell we changed a file type association
	SHChangeNotify(
		SHCNE_ASSOCCHANGED, // A file type association has changed
		0, NULL, NULL);     // No event-dependent values

	// Always report success
	return true;
}

// Takes a path to a .exe or .ico file on the disk
// Loads the icons, keeping their handles in Handle.Icon and Handle.SmallIcon
void GetIcons(LPCTSTR icon) {

	// Don't load the icons twice
	if (Handle.Icon || Handle.SmallIcon) return;

	// The path is to a .exe file
	if (CString(icon).Right(4).CompareNoCase(_T(".exe")) == 0) {

		// Extract the large and small icons from the first icon set in the .exe file
		ExtractIconEx(
			icon,                // Path to the .exe file with the icon
			0,                   // Extract the first icon in the program
			&(Handle.Icon),      // Handle for large icon
			&(Handle.SmallIcon), // Handle for small icon
			1);                  // Extract 1 set of icons

	// The path is to a .ico file
	} else if (CString(icon).Right(4).CompareNoCase(_T(".ico")) == 0) {

		// Load the large and small icons from the .ico file
		Handle.Icon      = (HICON)LoadImage(NULL, icon, IMAGE_ICON, 32, 32, LR_LOADFROMFILE);
		Handle.SmallIcon = (HICON)LoadImage(NULL, icon, IMAGE_ICON, 16, 16, LR_LOADFROMFILE);
	}
}
