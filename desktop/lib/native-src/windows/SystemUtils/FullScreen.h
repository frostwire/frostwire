#ifndef _Included_org_limewire_util_SystemUtils_FullScreen
#define _Included_org_limewire_util_SystemUtils_FullScreen
#ifdef __cplusplus
extern "C" {
#endif

	// Functions in FullScreen.cpp
	JNIEXPORT jboolean JNICALL Java_org_limewire_util_SystemUtils_toggleFullScreenNative(JNIEnv *e, jclass c, jlong hwnd);

#ifdef __cplusplus
}
#endif
#endif

// Functions in FullScreen.cpp
bool toggleFullScreen(HWND hWnd);
