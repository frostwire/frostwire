# Coil Migration - Replacing Picasso with Coil

## Overview
This document describes the migration from Picasso 3.0.0-alpha06 to Coil 3.0.4 for image loading in the FrostWire Android application.

## Motivation
Picasso 3.0.0-alpha06 has been causing persistent crashes in production related to a HandlerDispatcher race condition with NetworkBroadcastReceiver. Jake Wharton, the original creator of Picasso, has recommended users migrate to Coil, which is actively maintained and doesn't have these issues.

## Changes Made

### 1. Gradle Dependency Update
**File:** `android/build.gradle`

**Before:**
```gradle
implementation 'com.squareup.picasso3:picasso:3.0.0-alpha06'
```

**After:**
```gradle
implementation 'io.coil-kt.coil3:coil:3.0.4'
implementation 'io.coil-kt.coil3:coil-network-okhttp:3.0.4'
```

### 2. ImageLoader.java Migration
**File:** `android/src/com/frostwire/android/util/ImageLoader.java`

#### Key API Changes:

| Picasso API | Coil API | Notes |
|-------------|----------|-------|
| `Picasso.Builder` | `ImageLoader.Builder` | Builder pattern for configuration |
| `RequestCreator` | `ImageRequest` | Request building |
| `.load(uri)` | `ImageRequest.Builder(context).data(uri)` | Loading images |
| `.into(imageView)` | `.target(imageView)` | Setting target view |
| `.resize(w, h)` | `.size(w, h)` | Resizing images |
| `.placeholder(id)` | `.placeholder(id)` | Same API |
| `.noFade()` | `.crossfade(false)` | Opposite logic |
| `.memoryPolicy()` | `.memoryCachePolicy()` | Cache policies |
| `.networkPolicy()` | `.diskCachePolicy()` | Network/disk cache |
| `.evictAll()` | `memoryCache.clear()` + `diskCache.clear()` | Cache clearing |
| `.shutdown()` | `.shutdown()` | Same API |
| `Callback` interface | `ImageRequest.Listener` interface | Callbacks |

#### Removed Workarounds:
- **SafeContextWrapper**: No longer needed! Coil doesn't register deprecated NetworkBroadcastReceiver, so the HandlerDispatcher race condition doesn't exist.
- **Defensive error handling**: Simplified constructor since Coil is more stable.

### 3. Cache Configuration
Coil uses separate memory and disk caches with a cleaner API:

**Before (Picasso):**
```java
OkHttpClient with Cache(cacheDir, maxSize)
```

**After (Coil):**
```java
.memoryCache(() -> new MemoryCache.Builder()
    .maxSizePercent(context, 0.25)
    .build())
.diskCache(() -> new DiskCache.Builder()
    .directory(cacheDir)
    .maxSizeBytes(maxSize)
    .build())
```

### 4. Callback Handling
**Before (Picasso):**
```java
private static final class CallbackWrapper implements com.squareup.picasso3.Callback {
    @Override public void onSuccess() { ... }
    @Override public void onError(Throwable e) { ... }
}
```

**After (Coil):**
```java
requestBuilder.listener(new ImageRequest.Listener() {
    @Override public void onStart(ImageRequest request) { }
    @Override public void onSuccess(ImageRequest request, SuccessResult result) { }
    @Override public void onError(ImageRequest request, ErrorResult result) { }
    @Override public void onCancel(ImageRequest request) { }
});
```

## Benefits of Coil

1. **Actively Maintained**: Regular updates and bug fixes
2. **Modern Architecture**: Built with Kotlin Coroutines
3. **Better Performance**: More efficient memory and disk caching
4. **No NetworkBroadcastReceiver Issues**: Uses modern networking APIs
5. **Smaller APK Size**: More modular dependencies
6. **Better Android Integration**: Works well with latest Android versions

## Testing Checklist

### Manual Testing Required:
- [ ] Verify album art loads correctly in music player
- [ ] Verify promotional images load in main screen
- [ ] Test image loading with poor network conditions
- [ ] Test image caching (offline mode)
- [ ] Verify placeholder images display correctly
- [ ] Test error handling (invalid URLs, 404s, etc.)
- [ ] Verify memory usage is reasonable
- [ ] Test app doesn't crash when backgrounded during image loading
- [ ] Verify no HandlerDispatcher crashes occur

### Automated Testing:
Since ImageLoader heavily depends on Android framework components, comprehensive automated testing would require Android instrumentation tests. Unit tests for this class are limited but the existing architecture has been preserved to maintain compatibility.

## Migration Impact

### No API Changes for Consumers
The `ImageLoader` singleton class maintains the same public API:
- `getInstance(Context)`
- `load(Uri, ImageView)`
- `load(int resourceId, ImageView)`
- `load(Uri, ImageView, int targetWidth, int targetHeight)`
- `get(Uri)` - for synchronous bitmap loading
- `clear()` - cache clearing
- `shutdown()` - cleanup

All calling code continues to work without modification.

### SafeContextWrapper Status
The `SafeContextWrapper` class has been marked as deprecated and can be removed in future cleanup. It was only needed to work around Picasso's NetworkBroadcastReceiver registration issues.

## Rollback Plan

If issues are discovered with Coil, rollback is straightforward:

1. Revert the dependency change in `build.gradle`
2. Revert `ImageLoader.java` changes
3. Remove `@Deprecated` annotation from `SafeContextWrapper`
4. Rebuild and test

The commit history preserves the working Picasso implementation for reference.

## Additional Resources

- [Coil Documentation](https://coil-kt.github.io/coil/)
- [Coil 3.x Migration Guide](https://coil-kt.github.io/coil/upgrading_to_coil3/)
- [Jake Wharton's recommendation](https://github.com/square/picasso/issues/2283#issuecomment-1142415341)
