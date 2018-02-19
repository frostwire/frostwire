#include "SystemUtilities.hpp"

#include <dlfcn.h>
#include <errno.h>
#include <assert.h>

static void* jawt_handle = NULL;
static jboolean (JNICALL *pJAWT_GetAWT)(JNIEnv*,JAWT*);
#define JAWT_GetAWT (*pJAWT_GetAWT)

JNIEXPORT jlong JNICALL Java_org_limewire_util_SystemUtils_getWindowHandleNative(JNIEnv *env, jclass c, jobject frame, jstring bin)
{
    jlong handle = 0;
    JAWT_DrawingSurface* ds;
    JAWT_DrawingSurfaceInfo* dsi;
    jint lock;
    JAWT awt;

    awt.version = JAWT_VERSION_1_4;

    if (!pJAWT_GetAWT)
    {
        if ((jawt_handle = dlopen("libjawt.so", RTLD_LAZY|RTLD_GLOBAL)) == NULL)
        {
            //char msg[MSG_SIZE];
            //throwByName(env, EUnsatisfiedLink, LOAD_ERROR(msg, sizeof(msg)));
            return 0;
        }
        if ((pJAWT_GetAWT = (void*)dlsym(jawt_handle, "JAWT_GetAWT")) == NULL)
        {
            //char msg[MSG_SIZE], buf[MSG_SIZE];
            //snprintf(msg, sizeof(msg), "Error looking up JAWT method %s: %s",
            //    METHOD_NAME, LOAD_ERROR(buf, sizeof(buf)));
            //throwByName(env, EUnsatisfiedLink, msg);
            return 0;
        }
    }

    if (!JAWT_GetAWT(env, &awt))
    {
        //throwByName(env, EUnsatisfiedLink, "Can't load JAWT");
        return 0;
    }

    ds = awt.GetDrawingSurface(env, frame);
    if (ds == NULL)
    {
        //throwByName(env, EError, "Can't get drawing surface");
    }
    else
    {
        lock = ds->Lock(ds);
        if ((lock & JAWT_LOCK_ERROR) != 0)
        {
            awt.FreeDrawingSurface(ds);
            //throwByName(env, EError, "Can't get drawing surface lock");
            return 0;
        }
        dsi = ds->GetDrawingSurfaceInfo(ds);
        if (dsi == NULL)
        {
            //throwByName(env, EError, "Can't get drawing surface info");
        }
        else
        {
            JAWT_X11DrawingSurfaceInfo* xdsi = (JAWT_X11DrawingSurfaceInfo*)dsi->platformInfo;
            if (xdsi != NULL)
            {
                handle = xdsi->drawable;
                if (!handle)
                {
                    //throwByName(env, EIllegalState, "Can't get Drawable");
                }
            }
            else
            {
                //throwByName(env, EError, "Can't get X11 platform info");
            }
            ds->FreeDrawingSurfaceInfo(dsi);
        }

        ds->Unlock(ds);
        awt.FreeDrawingSurface(ds);
    }

    return handle;
}
