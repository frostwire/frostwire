#ifndef SYSTEMUTILITIES_HPP
#define SYSTEMUTILITIES_HPP

#include <jni.h>
#include <jawt_md.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL Java_org_limewire_util_SystemUtils_getWindowHandleNative(JNIEnv *e, jclass c, jobject frame, jstring bin);

#ifdef __cplusplus
}
#endif

#endif // SYSTEMUTILITIES_HPP
