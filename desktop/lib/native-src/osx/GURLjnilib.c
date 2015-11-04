
#include <JavaVM/jni.h>
#include <Carbon/Carbon.h>

static JavaVM *jvm;
static jobject ref;
static jmethodID mid;

// compile with:
// cc -c -dynamiclib -o libGURL.ppc -I/System/Library/Frameworks/JavaVM.framework/Headers GURLjnilib.c -arch ppc
// cc -c -dynamiclib -o libGURL.i386 -I/System/Library/Frameworks/JavaVM.framework/Headers GURLjnilib.c -arch i386
// cc -dynamiclib -o libGURL.jnilib libGURL.ppc libGURL.i386 -framework JavaVM -framework Carbon -arch ppc -arch i386

// or build two thin JNI libraries and merge them with lipo to an Universal Binary

#define OS_NATIVE(func)	Java_org_limewire_ui_swing_GURLHandler_##func

static OSErr NativeCallback(const AppleEvent *appleEvt, AppleEvent* reply, UInt32 refcon) {
    
    OSErr anErr = noErr;
    Size actualSize = 0;
    DescType descType = typeChar;
    
    if ((anErr = AESizeOfParam(appleEvt, keyDirectObject, &descType, &actualSize)) == noErr) {
        
        if (0 != actualSize) {
        
            // must be + 1 to allow the closing null character, otherwise
            // we don't know where the string ends.
            Size length = actualSize * sizeof(char) + 1;
            char *dataPtr = (char*)malloc(length);
            
            if (0 != dataPtr) {
                memset(dataPtr, 0, length); //probably not necessary, but safest.
                
                anErr = AEGetParamPtr(appleEvt,
                            keyDirectObject, 
                            typeChar, 
                            0, 
                            dataPtr,
                            actualSize,
                            &actualSize);
                            
                if (noErr == anErr) {
                    
                    JNIEnv *env;
                    (*jvm)->AttachCurrentThread(jvm, (void **)&env, NULL);
        
                    jstring theURL = (*env)->NewStringUTF(env, dataPtr);
                    (*env)->CallVoidMethod(env, ref, mid, theURL);
                }
                
                free(dataPtr);
            } // else we could throw an OutOfMemoryError here.
        }
    }
    
    return anErr;
}

JNIEXPORT jint JNICALL OS_NATIVE(InstallEventHandler)
    (JNIEnv *env, jobject this)
{

    OSErr anErr = noErr;
    
    jclass clazz = (*env)->GetObjectClass(env, this);
    mid = (*env)->GetMethodID(env, clazz, "callback", "(Ljava/lang/String;)V");
    
    if (0 == mid) {
        jclass exception = (*env)->FindClass(env, "java/lang/NoSuchMethodException");
        (*env)->ThrowNew(env, exception, "callback(String) not found");
    }
    
    (*env)->GetJavaVM(env, &jvm);
    ref = (*env)->NewGlobalRef(env, this);
	
    anErr = AEInstallEventHandler(kInternetEventClass, 
			kAEGetURL, 
			NewAEEventHandlerUPP((AEEventHandlerProcPtr)NativeCallback), 
			0, false);
    
    return (jint)anErr;
}

JNIEXPORT jint JNICALL OS_NATIVE(RemoveEventHandler)
    (JNIEnv *env, jobject this) 
{
    OSErr anErr = noErr;
    
    anErr = AERemoveEventHandler(kInternetEventClass, 
			kAEGetURL, 
			NewAEEventHandlerUPP((AEEventHandlerProcPtr)NativeCallback), 
			false);
    
    (*env)->DeleteGlobalRef(env, ref);
    
    jvm = 0;
    ref = 0;
    mid = 0;
    
    return (jint)anErr;
}
