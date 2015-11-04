#include <JavaVM/jni.h>
#include <Carbon/Carbon.h>
#include <Cocoa/Cocoa.h>

// compile with:
// cc -c -dynamiclib -o libDock.ppc -I/System/Library/Frameworks/JavaVM.framework/Headers Dockjnilib.m -arch ppc
// cc -c -dynamiclib -o libDock.i386 -I/System/Library/Frameworks/JavaVM.framework/Headers Dockjnilib.m -arch i386
// cc -dynamiclib -o libDock.jnilib libDock.ppc libDock.i386 -framework JavaVM -framework Carbon -framework Cocoa -arch ppc -arch i386

#define ICON_WIDTH 128
#define ICON_HEIGHT 128

#ifdef __cplusplus
extern "C" {
#endif

#define OS_NATIVE(func) Java_com_limegroup_gnutella_gui_dock_Dock_##func

JNIEXPORT jint JNICALL OS_NATIVE(RequestUserAttention)
	(JNIEnv *env, jobject clazz, jint requestType)
{
	NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
	jint request = (jint)[[NSApplication sharedApplication] 
					requestUserAttention: (NSRequestUserAttentionType)requestType];
	[pool release];
	return request;
}

JNIEXPORT void JNICALL OS_NATIVE(CancelUserAttentionRequest)
	(JNIEnv *env, jobject clazz, jint request)
{
	NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
	[[NSApplication sharedApplication] cancelUserAttentionRequest: request];
	[pool release];
}

JNIEXPORT void JNICALL OS_NATIVE(RestoreApplicationDockTileImage)
	(JNIEnv *env, jobject clazz)
{
	RestoreApplicationDockTileImage();
}

JNIEXPORT void JNICALL OS_NATIVE(DrawDockTileImage)
	(JNIEnv *env, jobject clazz, jintArray pixel, jboolean overlay)
{
	jint *icon = NULL;
	CGDataProviderRef provider = NULL;
	CGImageRef image = NULL;
	CGColorSpaceRef cs = NULL;
	
	// ARGB
	icon = (*env)->GetIntArrayElements(env, pixel, 0);
	provider = CGDataProviderCreateWithData(0, icon, ICON_WIDTH * ICON_HEIGHT * 4, 0);
	cs = CGColorSpaceCreateDeviceRGB();
	image = CGImageCreate(ICON_WIDTH, 
							ICON_HEIGHT, 
							8, 
							32, 
							ICON_WIDTH*4, 
							cs, 
							kCGBitmapByteOrder32Host | kCGImageAlphaFirst, 
							provider, 
							NULL, 
							0, 
							kCGRenderingIntentDefault);
							
	if (overlay) {
		OverlayApplicationDockTileImage(image);
	} else {
		SetApplicationDockTileImage(image);
	}
	
	CFRelease(cs);
	CGDataProviderRelease(provider);
	CGImageRelease(image);
	(*env)->ReleaseIntArrayElements(env, pixel, icon, 0);
}

#ifdef __cplusplus
}
#endif
