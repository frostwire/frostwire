
// Microsoft Visual Studio compiles this Windows native code into SystemUtilities.dll
// LimeWire uses these functions from the class com.limegroup.gnutella.util.SystemUtils

// Include the standard Windows DLL header and more headers
#include "stdafx.h"
#include "SystemUtilities.h"

// Make the global object that holds icon handles
CSystemUtilities Handle;

// A Windows DLL has a DllMain method that Windows calls when it loads the DLL
BOOL APIENTRY DllMain(HANDLE hModule, DWORD ul_reason_for_call, LPVOID lpReserved) {

	// Indicate success
	return TRUE;
}

// Takes multibyte text
// In the Unicode build, converts it to wide characters
CString AtoT(LPCSTR t) {
#ifdef _UNICODE

	// This is the Unicode build, convert multibyte characters in t to wide characters
	int characters = MultiByteToWideChar( // Find out how many wide characters t will become, including the null terminator
		CP_UTF8,                          // t is encoded in UTF-8
		0,                                // No special options
		t,                                // The source multibyte text to convert
		-1,                               // t is null terminated
		NULL,                             // No destination buffer, find out how much space we need
		0);
	if (characters == 0) return _T(""); // Error

	// Open a wide string and have it prepare the correct size
	CStringW w;
	LPWSTR buffer = w.GetBufferSetLength(characters); // Takes the number of wide characters, including the null terminator

	// Convert multibyte t into wide characters
	MultiByteToWideChar(
		CP_UTF8,     // t is encoded in UTF-8
		0,           // No special options
		t,           // The source multibyte text to convert
		-1,          // t is null terminated
		buffer,      // The destination buffer where MultiByteToWideChar() will write wide characters
		characters); // The size of buffer in wide characters

	// Close the string, and return it
	w.ReleaseBuffer(); // MultiByteToWideChar() wrote the null terminator
	return w;

#else

	// This is the ASCII build, no conversion necessary
	return CString(t);

#endif
}

// Takes multibyte text in the ASCII build, or wide characters in the Unicode build
// In the Unicode build, converts them to ASCII
CStringA TtoA(LPCTSTR t) {
#ifdef _UNICODE

	// This is the Unicode build, convert wide characters in t to multibyte text
	int bytes = WideCharToMultiByte( // Find out how many bytes t will become, including the null terminator
		CP_UTF8,                     // Use UTF-8 encoding
		0,                           // No special options
		t,                           // The source wide characters to convert
		-1,                          // t is null terminated
		NULL,                        // No destination buffer, find out how much space we need
		0,
		NULL,                        // No default character
		NULL);
	if (bytes == 0) return _T(""); // Error

	// Open a multibyte string and have it prepare the correct size
	CStringA a;
	LPSTR buffer = a.GetBufferSetLength(bytes); // Takes the size in bytes, including the null terminator

	// Convert the wide characters in t into multibyte text
	WideCharToMultiByte(
		CP_UTF8, // Use UTF-8 encoding
		0,       // No special options
		t,       // The source wide characters to convert
		-1,      // t is null terminated
		buffer,  // The destination buffer where WideCharToMultiByte() will write multibyte text
		bytes,   // The size of the buffer in bytes
		NULL,    // No default character
		NULL);

	// Close the string, and return it
	a.ReleaseBuffer(); // WideCharToMultiByte() wrote the null terminator
	return a;

#else

	// This is the ASCII build, no conversion necessary
	return CStringA(t);

#endif
}

// Takes the JNI environment object, and a Java string
// Safely gets the text from the Java string, copies it into a new CString, and frees the Java string
// Returns the CString
CString GetJavaString(JNIEnv *e, jstring j) {
	#ifdef _UNICODE

	// Get a character pointer into the text characters of the Java string
	const jchar* c = (e->GetStringChars(j, NULL));
	if (c == NULL) // the jvm has OOM'd
	{
		return _T(""); 
	}
	int num = e->GetStringLength(j);

	PWCHAR copied = new WCHAR[num+1];
	ZeroMemory(copied,(num+1) * sizeof(WCHAR));
	wmemcpy_s(copied,num,(PWCHAR)c,num);
	e->ReleaseStringChars(j, c);

	// Make a new Windows CString object with that text
	CString s = CString(copied); 
	delete copied;
	
	#else
	const char * chars = e->GetStringUTFChars(j,NULL);
	if (chars == NULL) // the jvm has OOM'd
	{
		return _T(""); 
	}
	CString s = chars;
	e->ReleaseStringUTFChars(j, chars);
	#endif

	// Return the CString we made
	return s;
}


// Takes the JNI environment object, and a pointer to text, like a CString cast to LPCTSTR
// Makes a new Java string from the text that we can return back to Java
// Returns the jstring object
jstring MakeJavaString(JNIEnv *e, LPCTSTR t) {

#ifdef _UNICODE
	CString cst(t);	
	return e->NewString((const jchar *)t, cst.GetLength());
#else
	CStringA a = TtoA(t);
	return e->NewStringUTF(a);
#endif
}

// Takes the JNI environment and class
// The jobject frame is a AWT Component like a JFrame that is backed by a real Windows window
// bin is the path to the folder that has the file "jawt.dll", like "C:\Program Files\Java\jre1.5.0_05\bin"
// Gets the window handle from Java, and returns it
// Sets message to report what happened
HWND GetJavaWindowHandle(JNIEnv *e, jclass c, jobject frame, LPCTSTR bin, CString *message) {

	// Make a variable for the window handle we'll get
	HWND window = NULL;
	*message = _T("Start of method");

	// Make sure the path isn't blank
	if (bin == CString(_T(""))) { *message = _T("Blank path"); return NULL; }

	// Make a JAWT structure that will tell Java we're using Java 1.4
	JAWT awt;
	awt.version = JAWT_VERSION_1_4;

	// Load jawt.dll into our process space
	CString path = CString(bin) + CString(_T("\\jawt.dll")); // Compose the complete path to the DLL, like "C:\Program Files\Java\jre1.5.0_05\bin\jawt.dll"
	HMODULE module = LoadLibrary(path); // If the DLL is already in our process space, LoadLibrary() will just get its handle
	if (module) {
		*message = _T("Got module");

		// Get a function pointer to JAWT_GetAWT() in the DLL
		JawtGetAwtSignature JawtGetAwt = (JawtGetAwtSignature)GetProcAddress(module,
#if defined(_WIN64)
			"JAWT_GetAWT");
#else
			"_JAWT_GetAWT@8");
#endif
		if (JawtGetAwt) {
			*message = _T("Got signature");

			// Access Java's Active Widget Toolkit
			jboolean result = JawtGetAwt(e, &awt);
			if (result != JNI_FALSE) {
				*message = _T("Got AWT");

				// Get the drawing surface
				JAWT_DrawingSurface *surface = awt.GetDrawingSurface(e, frame);
				if (surface) {
					*message = _T("Got surface");

					// Lock the drawing surface
					jint lock = surface->Lock(surface);
					if ((lock & JAWT_LOCK_ERROR) == 0) { // If the error bit is not set, keep going
						*message = _T("Locked surface");

						// Get the drawing surface information
						JAWT_DrawingSurfaceInfo *info = surface->GetDrawingSurfaceInfo(surface);
						if (info) {
							*message = _T("Got surface information");

							// Get the Windows-specific drawing surface information
							JAWT_Win32DrawingSurfaceInfo *win = (JAWT_Win32DrawingSurfaceInfo*)info->platformInfo;
							if (win) {
								*message = _T("Got platform-specific surface information");

								// Get the window handle
								window = win->hwnd;
							}
						}

						// Unlock the drawing surface
						surface->Unlock(surface);
					}

					// Free the drawing surface
					awt.FreeDrawingSurface(surface);
				}
			}
		}
	}

	// Return the window handle Java told us
	return window;
}

// Takes the JNI environment and a text message
// Throws an IOException in the Java code that called into native
void ThrowIOException(JNIEnv *e, LPCTSTR t) {

	// Find the Java IOException class
	jclass c = e->FindClass("java/io/IOException");
	if (!c) return;

	// Throw a new IOException back to Java
	CStringA a = TtoA(t); // Convert wide characters to multi-byte
	e->ThrowNew(c, (const char *)(LPCSTR)a);
}
