//
//  Growl.m
//  GURL
//
//  Created by Curtis Jones on 2008.04.09.
//  Copyright 2008 Lime Wire LLC. All rights reserved.
//

#import <JavaVM/jni.h>
#import <Foundation/Foundation.h>
#import <Growl/Growl.h>

@interface Growl : NSObject <GrowlApplicationBridgeDelegate>

@end

@implementation Growl

- (NSDictionary *)registrationDictionaryForGrowl
{
	NSArray *keys = [NSArray arrayWithObjects:GROWL_APP_NAME, GROWL_APP_ID, GROWL_NOTIFICATIONS_ALL, GROWL_NOTIFICATIONS_DEFAULT, nil];
	NSArray *notifications = [NSArray arrayWithObjects:@"NotifyUser", nil];
	NSArray *objects = [NSArray arrayWithObjects:@"LimeWire", /*appIcon,*/ @"LimeWire", notifications, notifications, nil];
	NSDictionary *dict = [NSDictionary dictionaryWithObjects:objects forKeys:keys];
	
	return dict;
}

@end


#ifdef __cplusplus
extern "C" {
#endif
	
#define OS_NATIVE(func) Java_org_limewire_ui_swing_tray_Growl_##func

JNIEXPORT void JNICALL OS_NATIVE(RegisterGrowl) (JNIEnv *env, jobject obj)
{
	//printf("just called RegisterGrowl");
	[GrowlApplicationBridge setGrowlDelegate:[[Growl alloc] init]];
}

JNIEXPORT void JNICALL OS_NATIVE(SendNotification) (JNIEnv *env, jobject obj, jstring message)
{
	//printf("just called SendNotification");
	NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
	const char *messageCStr = (*env)->GetStringUTFChars(env, message, NULL);
	NSString *messageNSStr = [NSString stringWithCString:messageCStr];
	
	[GrowlApplicationBridge notifyWithTitle:@"Alert!" 
															description:messageNSStr 
												 notificationName:@"NotifyUser" 
																 iconData:nil
																 priority:0
																 isSticky:FALSE
														 clickContext:nil];
	
	(*env)->ReleaseStringUTFChars(env, message, messageCStr);
	
	[pool release];
}

#ifdef __cplusplus
}
#endif
