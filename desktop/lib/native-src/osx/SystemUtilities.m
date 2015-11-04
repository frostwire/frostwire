/**
 * A collection of system utilities for OSX that
 * require native code.
 */

#include <sys/time.h>
#include <sys/resource.h>
#include <JavaVM/jni.h>
#include <ApplicationServices/ApplicationServices.h>
#include <sys/types.h>
#include <sys/stat.h>


// compile with:
// cc -c -dynamiclib -o libSystemUtilities.ppc -I/System/Library/Frameworks/JavaVM.framework/Headers SystemUtilities.m -arch ppc
// cc -c -dynamiclib -o libSystemUtilities.i386 -I/System/Library/Frameworks/JavaVM.framework/Headers SystemUtilities.m -arch i386
// cc -dynamiclib -o libSystemUtilities.jnilib libSystemUtilities.ppc libSystemUtilities.i386 -framework JavaVM -framework Carbon -arch ppc -arch i386

// or build two thin JNI libraries and merge them with lipo to an Universal Binary

extern double CGSSecondsSinceLastInputEvent(unsigned long envType);

JNIEXPORT jint JNICALL Java_org_limewire_util_SystemUtils_setFileWriteable
  (JNIEnv *env, jclass clazz, jstring fileName) {
	const char* cFileName;
	int retVal;
	struct stat fileStat;
	cFileName = (*env)->GetStringUTFChars(env, fileName, JNI_FALSE);
	retVal = stat(cFileName, &fileStat);
	
//	printf("attempting to set [%s], current flags: [%i], mode: [%i]\n", cFileName, fileStat.st_flags, fileStat.st_mode);
	if(retVal == 0) {
		int oldPerms = fileStat.st_mode & 07777;
		int mask = S_IRUSR | S_IWUSR;
		// OSX bases directory write permissions on the 'x' flag, not the 'w' one.
		if((fileStat.st_mode & S_IFDIR) == S_IFDIR) {
//			printf("marking [%s] with x flag too.\n", cFileName);
			mask |= S_IXUSR;
		}
//		printf("chmoding [%s] with mask [%i], oldPerms [%i]\n", cFileName, mask, oldPerms);
		retVal = chmod(cFileName, oldPerms | mask);
	}
	// free the memory for the string
	(*env)->ReleaseStringUTFChars(env, fileName, cFileName);
	return retVal;
}


JNIEXPORT jlong JNICALL Java_org_limewire_util_SystemUtils_idleTime
  (JNIEnv *env, jclass clazz) {  
	CFTimeInterval timeInterval = CGEventSourceSecondsSinceLastEventType (kCGEventSourceStateCombinedSessionState, kCGAnyInputEventType);
	return (jlong)(1000.0 * timeInterval);    
}

JNIEXPORT jint JNICALL Java_org_limewire_util_SystemUtils_setOpenFileLimit0
  (JNIEnv *env, jclass clazz, jint limit) {
    int retval = 0;
	int maxFiles = (int)limit;
    struct rlimit rl;
    
    // retrieve current values.
    if (getrlimit(RLIMIT_NOFILE, &rl) == -1)
        rl.rlim_cur = rl.rlim_max = 0;

    // if either are below, raise them as necessary.
	if (rl.rlim_cur < maxFiles || rl.rlim_max < maxFiles) {
        // raise only what we need to.
        if(rl.rlim_cur < maxFiles)
            rl.rlim_cur = maxFiles;
        if( rl.rlim_max < maxFiles )
            rl.rlim_max = maxFiles;
        // set with new values.
        retval = setrlimit(RLIMIT_NOFILE, &rl);
    }
    return (jint)retval;
}