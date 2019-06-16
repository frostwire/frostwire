//
//  MacOSXUtils.m
//  GURL
//
//  Created by Curtis Jones on 2008.04.08.
//
//
// June 15th 2019
// cc -framework Foundation -framework CoreServices -framework CoreFoundation -dynamiclib -o ../../native/libMacOSXUtils.jnilib -I${JAVA_HOME}/include MacOSXUtils.m -I{$JAVA_HOME}/include/darwin -arch x86_64
#import <jni.h>
#import <Foundation/Foundation.h>

#ifdef __cplusplus
extern "C" {
#endif

#define OS_NATIVE(func) Java_com_limegroup_gnutella_util_MacOSXUtils_##func

JNIEXPORT void JNICALL OS_NATIVE(SetLoginStatusNative)
	(JNIEnv *env, jobject obj, jboolean onoff, jstring path )
{
    // convert the path to a local NSString
    const jchar *chars = (*env)->GetStringChars(env, path, NULL);
    NSString *appPath = [NSString stringWithCharacters:(UniChar *)chars
                                                   length:(*env)->GetStringLength(env, path)];
    (*env)->ReleaseStringChars(env, path, chars);
    
	// Create url of path
	CFURLRef url = (CFURLRef)[NSURL fileURLWithPath:appPath];
    
	// Create a reference to the shared file list for current user only.  all users use: kLSSharedFileListGlobalLoginItems
	LSSharedFileListRef loginItems = LSSharedFileListCreate(NULL,
                                                            kLSSharedFileListSessionLoginItems, NULL);
	if (loginItems) {
        
        if (onoff) {
        
            //Insert an item to the list.
            LSSharedFileListItemRef item = LSSharedFileListInsertItemURL(loginItems,
                                                                     kLSSharedFileListItemLast, NULL, NULL,
                                                                     url, NULL, NULL);
            if (item){
              NSLog(@"Objective-C:SetLoginStatusNative(appPath=%@)", appPath);
              CFRelease(item);
            }
            
        } else {
            
            //Remove item from the list
            UInt32 seedValue;
            
            //Retrieve the list of Login Items
            NSArray  *loginItemsArray = (NSArray *)LSSharedFileListCopySnapshot(loginItems, &seedValue);
            
            for(int i=0; i< [loginItemsArray count]; i++){
                LSSharedFileListItemRef itemRef = (LSSharedFileListItemRef)[loginItemsArray
                                                                        objectAtIndex:i];
                //Resolve the item with URL
                if (LSSharedFileListItemResolve(itemRef, 0, (CFURLRef*) &url, NULL) == noErr) {
                    NSString * urlPath = [(NSURL*)url path];
                    NSLog(@"Objective-C:SetLoginStatusNative(urlPath=%@)", urlPath);
                    CFRelease(urlPath);
                    if ([urlPath compare:appPath] == NSOrderedSame){
                        LSSharedFileListItemRemove(loginItems,itemRef);
                    }
                }
            }
            
            [loginItemsArray release];
        }
    
        CFRelease(loginItems);
    }
}

#ifdef __cplusplus
}
#endif
