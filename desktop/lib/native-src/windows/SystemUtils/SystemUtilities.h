
// Microsoft Visual Studio compiles this Windows native code into SystemUtilities.dll
// LimeWire uses these functions from the class com.limegroup.gnutella.util.SystemUtils

// Define types to match the signatures of functions we'll call in DLLs we load
typedef jboolean(JNICALL *JawtGetAwtSignature)(JNIEnv*, JAWT*);

// The program's single CSystemUtilities object holds icon handles
class CSystemUtilities {
public:

	// Icons
	HICON Icon, SmallIcon;

	// Make the CSystemUtilities object
	CSystemUtilities() {

		// Mark that we haven't gotten the icons yet
		Icon = SmallIcon = NULL;
	}
};

// Functions in SystemUtilities.cpp
CString AtoT(LPCSTR t);
CStringA TtoA(LPCTSTR t);
CString GetJavaString(JNIEnv *e, jstring j);
jstring MakeJavaString(JNIEnv *e, LPCTSTR t);
HWND GetJavaWindowHandle(JNIEnv *e, jclass c, jobject frame, LPCTSTR bin, CString *message);
void ThrowIOException(JNIEnv *e, LPCTSTR t);
