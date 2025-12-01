# FrostWire Wayland ARM64 Support Fix

## Problem
FrostWire had a kill switch that prevented it from running on ARM64 Linux systems with Wayland display server. The application would immediately exit with an error message when launched on such systems. Additionally, the JVM rendering flags were configured to use XRender (X.org-only) on all Linux systems, which doesn't work with Wayland on ARM64.

## Solution
Enabled OpenGL rendering for ARM64 Linux systems, which is compatible with both Wayland and XWayland, while keeping XRender for x86_64 Linux systems for backwards compatibility.

## Changes Made

### 1. Updated `/home/gubatron/workspace/frostwire/desktop/build.gradle` (lines 85-104)

**Before:**
```gradle
if (OperatingSystem.current().isMacOsX()) {
    applicationDefaultJvmArgs += ['--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED', '-Dsun.java2d.metal=true']
} else if (OperatingSystem.current().isLinux()) {
    // Optimize rendering for Linux - works with X.org
    applicationDefaultJvmArgs += [
        '-Dsun.java2d.xrender=true',            // Use XRender - most stable on Linux with X.org
        '-Dsun.java2d.opengl=false',            // OpenGL can crash entire display session
    ]
}
```

**After:**
```gradle
if (OperatingSystem.current().isMacOsX()) {
    applicationDefaultJvmArgs += ['--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED', '-Dsun.java2d.metal=true']
} else if (OperatingSystem.current().isLinux()) {
    def arch = System.getProperty('os.arch').toLowerCase()
    def isARM64 = arch == "aarch64" || arch == "arm64"

    if (isARM64) {
        // For ARM64 Linux, enable Wayland/XWayland support with OpenGL
        applicationDefaultJvmArgs += [
            '-Dsun.java2d.opengl=true',            // Use OpenGL on ARM64 for Wayland compatibility
            '-Dsun.java2d.xrender=false',          // Disable XRender as it's not Wayland-compatible
        ]
    } else {
        // For x86_64 Linux, optimize rendering with XRender for X.org
        applicationDefaultJvmArgs += [
            '-Dsun.java2d.xrender=true',           // Use XRender - most stable on Linux x86_64 with X.org
            '-Dsun.java2d.opengl=false',           // OpenGL can crash entire display session on x86_64
        ]
    }
}
```

**Rationale:**
- ARM64 systems have better OpenGL support in Wayland environments
- XRender is X.org-specific and doesn't work with Wayland
- x86_64 systems continue to use XRender for backwards compatibility

### 2. Updated `/home/gubatron/workspace/frostwire/desktop/src/main/java/com/limegroup/gnutella/gui/Main.java`

**Removed:**
- The Wayland kill switch check (lines 56-70 in original)
- The `showWaylandIncompatibilityDialog()` method that displayed an error dialog

**Kept:**
- The stderr warning messages are NOT part of this removal. The kill switch logic was the issue being removed, not the messages themselves.

The removed code was:
```java
// Check if we're on Linux and running under Wayland
if (OSUtils.isLinux() && isARM64) {
    String sessionType = System.getenv("XDG_SESSION_TYPE");
    if (sessionType != null && sessionType.equalsIgnoreCase("wayland")) {
        System.err.println("\n===================================================");
        System.err.println("FrostWire is not compatible with Wayland on ARM64 chips,");
        System.err.println("It will make your session crash if we continue.\n");
        System.err.println("FrostWire requires an X.org session to run on ARM64.");
        System.err.println("Your Linux arm64 system is currently using Wayland.");
        System.err.println("\nPlease switch to X.org or Xwayland and try again.\n");
        System.err.println("You can switch to X.org in the login screen of your\ndistro, usually on the settings icon at the bottom\ncorner of the screen.");
        System.err.println("===================================================\n");
        showWaylandIncompatibilityDialog();
        System.exit(1);
    }
}
```

## Testing Status
- ✅ Code compiles successfully (`./gradlew compileJava`)
- ⏳ Runtime testing not yet performed (may freeze session on ARM64 Wayland systems)

## Next Steps (if session survives)
1. Test FrostWire launch on ARM64 Linux with Wayland
2. Verify rendering works correctly with OpenGL backend
3. Monitor for any crashes or display issues
4. Test fallback to XWayland if native Wayland support has issues
5. Consider adding a startup log message indicating OpenGL rendering is being used on ARM64

## Files Modified
1. `/home/gubatron/workspace/frostwire/desktop/build.gradle`
2. `/home/gubatron/workspace/frostwire/desktop/src/main/java/com/limegroup/gnutella/gui/Main.java`

## Git Status
- Not yet committed
- Ready for testing and potential adjustments
