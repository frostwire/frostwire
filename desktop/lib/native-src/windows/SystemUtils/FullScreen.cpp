// Include the standard Windows DLL header and more headers
#include "stdafx.h"
#include "SystemUtilities.h"
#include "FullScreen.h"

JNIEXPORT jboolean JNICALL Java_org_limewire_util_SystemUtils_toggleFullScreenNative(JNIEnv *e, jclass c, jlong hwnd)
{
    return toggleFullScreen((HWND)hwnd);
}

// currect resolution/bpp on screen:  (should be autodetected by vo_init())
int vo_depthonscreen=0;
int vo_screenwidth=0;
int vo_screenheight=0;

// requested resolution/bpp:  (-x -y -bpp options)
int vo_dx=0;
int vo_dy=0;
int vo_dwidth=0;
int vo_dheight=0;
int vo_dbpp=0;

// last non-fullscreen extends
static int prev_width;
static int prev_height;
static int prev_x;
static int prev_y;

int vo_fs = 0;

HWND vo_window = 0;
int prev_style;

static void updateScreenProperties(void)
{
    DEVMODE dm;
    dm.dmSize = sizeof dm;
    dm.dmDriverExtra = 0;
    dm.dmFields = DM_BITSPERPEL | DM_PELSWIDTH | DM_PELSHEIGHT;
    if (!EnumDisplaySettings(0, ENUM_CURRENT_SETTINGS, &dm))
    {
        printf("vo: win32: unable to enumerate display settings!\n");
        fflush(stdout);
        return;
    }

    vo_screenwidth = dm.dmPelsWidth;
    vo_screenheight = dm.dmPelsHeight;
    vo_depthonscreen = dm.dmBitsPerPel;
    //w32_update_xinerama_info();
}

static int createRenderingContext(void)
{
    HWND layer = HWND_NOTOPMOST;
    RECT r;
    int style = (!vo_fs) ? (WS_OVERLAPPEDWINDOW | WS_SIZEBOX) : (WS_POPUP);
	
    updateScreenProperties();
    ShowWindow(vo_window, SW_HIDE);
    SetWindowLong(vo_window, GWL_STYLE, style);
    if (vo_fs)
    {
        prev_width = vo_dwidth;
        prev_height = vo_dheight;
        prev_x = vo_dx;
        prev_y = vo_dy;
        vo_dwidth = vo_screenwidth;
        vo_dheight = vo_screenheight;
        vo_dx = 0;//xinerama_x;
        vo_dy = 0;//xinerama_y;
    }
    else
    {
        // make sure there are no "stale" resize events
        // that would set vo_d* to wrong values
        //vo_w32_check_events();
        vo_dwidth = prev_width;
        vo_dheight = prev_height;
        vo_dx = prev_x;
        vo_dy = prev_y;
        // HACK around what probably is a windows focus bug:
        // when pressing 'f' on the console, then 'f' again to
        // return to windowed mode, any input into the video
        // window is lost forever.
        SetFocus(vo_window);
    }
    r.left = vo_dx;
    r.right = r.left + vo_dwidth;
    r.top = vo_dy;
    r.bottom = r.top + vo_dheight;
    SetWindowPos(vo_window, layer, r.left, r.top, r.right - r.left, r.bottom - r.top, SWP_SHOWWINDOW);
    return 1;
}

bool toggleFullScreen(HWND hWnd)
{
    // works only with one window
    if (vo_window == 0)
    {
        vo_window = hWnd;
        prev_style = GetWindowLong(hWnd, GWL_STYLE);
    }
    else
    {
        if (vo_window != hWnd)
        {
            return false;
        }
    }

    vo_fs = !vo_fs;

    if (vo_fs)
    {
        RECT r;
        GetWindowRect(hWnd, &r);

        vo_dwidth = r.right - r.left;
        vo_dheight = r.bottom - r.top;
        vo_dx = r.left;
        vo_dy = r.top;
    }

    createRenderingContext();

    return true;
}
