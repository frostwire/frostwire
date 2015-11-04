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

#define OS_NATIVE(func) Java_org_limewire_ui_swing_dock_Dock_##func

@interface Dock : NSObject
+ (NSImage *)limeWireIconAtPath:(NSString *)appPath;
@end
	
@implementation Dock
+ (NSImage *)limeWireIconAtPath:(NSString *)appPath
{
	NSBundle *bundle = [NSBundle bundleWithPath:appPath];
	NSString *iconPath = [bundle pathForResource:@"LimeWire" ofType:@"icns"];
	
	return [[[NSImage alloc] initWithContentsOfFile:iconPath] autorelease];
}
@end

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
	(JNIEnv *env, jobject clazz, jstring appdir)
{
	NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
	
	const char *appdircstr = (*env)->GetStringUTFChars(env, appdir, NULL);
	NSImage *newImage = [Dock limeWireIconAtPath:[NSString stringWithUTF8String:appdircstr]];
	[NSApp setApplicationIconImage:newImage];
	
	(*env)->ReleaseStringUTFChars(env, appdir, appdircstr);
	[pool release];
}

	// get application icon
	//
	// if overlay == true
	//   convert jintArray to nsimage
	//   composite image to app icon
	//   set to new application icon
	// else
	//   composite application icon to blank image
	//   set to new application icon
	
JNIEXPORT void JNICALL OS_NATIVE(DrawDockTileImage)
	(JNIEnv *env, jobject clazz, jintArray pixel, jboolean overlay, jstring appdir)
{
	NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
	
	jint *icon = NULL;
	CGDataProviderRef provider = NULL;
	CGImageRef image = NULL;
	CGColorSpaceRef cs = NULL;
	NSImage *newImage = nil;
	const char *appdircstr = NULL;
	
	appdircstr = (*env)->GetStringUTFChars(env, appdir, NULL);
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
	
	{
		NSRect imageRect = NSMakeRect(0.0, 0.0, 0.0, 0.0);
		
		// Create a new image to receive the Quartz image data.
		newImage = [Dock limeWireIconAtPath:[NSString stringWithUTF8String:appdircstr]];
		imageRect.size.height = [newImage size].height;
		imageRect.size.width = [newImage size].width;
		
		// Get the Quartz context and draw.
		if (overlay) {
			[newImage lockFocus];
			CGContextRef imageContext = (CGContextRef)[[NSGraphicsContext currentContext] graphicsPort];
			CGContextDrawImage(imageContext, *(CGRect*)&imageRect, image);
			[newImage unlockFocus];
		}
	}
	
	[NSApp setApplicationIconImage:newImage];
	
	(*env)->ReleaseStringUTFChars(env, appdir, appdircstr);
	CFRelease(cs);
	CGDataProviderRelease(provider);
	CGImageRelease(image);
	(*env)->ReleaseIntArrayElements(env, pixel, icon, 0);
	[pool release];
}
	
#ifdef __cplusplus
}
#endif
