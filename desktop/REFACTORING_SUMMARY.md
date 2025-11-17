# FrostWire Audio Player Refactoring Summary

## Objective
Refactor FrostWire's audio playback system from using **fwplayer** (a custom MPlayer build) to using **SimpleAudio**, a pure Java library.

## Completed Work

### 1. ✅ Removed fwplayer Binaries
All platform-specific fwplayer binaries have been removed from git:
- `desktop/lib/native/fwplayer.exe` (Windows)
- `desktop/lib/native/fwplayer_linux.x86_64` (Linux x86_64)
- `desktop/lib/native/fwplayer_linux.arm64` (Linux ARM64)
- `desktop/lib/native/fwplayer_macos.x86_64` (macOS Intel)
- `desktop/lib/native/fwplayer_macos.arm64` (macOS Apple Silicon)

### 2. ✅ Removed MPlayer-Related Code
Deleted all MPlayer/fwplayer interaction code:
- `desktop/src/main/java/com/frostwire/gui/mplayer/MPlayer.java` 
- `desktop/src/main/java/com/frostwire/gui/mplayer/MPlayerInstance.java`
- `desktop/src/main/java/com/frostwire/gui/library/tags/MPlayerParser.java`

### 3. ✅ Implemented SimpleAudioPlayer
Created new SimpleAudio-based player at:
- `desktop/src/main/java/com/frostwire/gui/audio/SimpleAudioPlayer.java`

Features:
- Cross-platform audio playback (Windows, macOS, Linux)
- Support for file paths and URLs
- Volume control
- Seek/position tracking
- Playback state management (Playing, Paused, Stopped, Failed)
- Listener pattern for UI updates

### 4. ✅ Updated Media Player Integration
- Modified `MediaPlayer.java` to use `SimpleAudioPlayer` instead of `MPlayer`
- Removed platform-specific player path logic from:
  - `MediaPlayerWindows.java`
  - `MediaPlayerOSX.java`
  - `MediaPlayerLinux.java`
- Simplified platform-specific classes to minimal stubs

### 5. ✅ Fixed Metadata Extraction
- Updated `TagsParserFactory.java` to handle unsupported formats gracefully
- Modified `TagsReader.java` to remove MPlayerParser fallback
- Metadata extraction now handled by specialized parsers (MP3, M4A, OGG, FLAC, WAV, WMA)

### 6. ✅ Included SimpleAudio Source Code
Copied SimpleAudio library source into project:
- `desktop/src/main/java/de/ralleytn/simple/audio/*`

## Remaining Work

### Audio Codec Dependencies
SimpleAudio requires the following audio codec libraries to fully support multiple formats:

```gradle
implementation 'org.jcraft:jorbis:0.0.17'        // Vorbis codec
implementation 'org.jcraft:jogg:0.0.7'           // OGG container
implementation 'javazoom:jlayer:1.0.1'           // MP3 codec
implementation 'com.googlecode.soundlibs:tritonus-share:0.3.7-2'  // Tritonus support
```

**Issue**: The frostwire Maven repository returns corrupted metadata/artifacts for these libraries, causing build failures.

**Solution Options**:
1. Add these dependencies to `build.gradle` from Maven Central (primary source)
2. Ensure proper Maven repository ordering to avoid corruption
3. Host codec libraries in FrostWire's own Maven repository
4. Use a different audio codec dependency source

### Build Status
- ✅ Core compilation succeeds without audio codec libraries
- ⚠️ Full compilation with codec support pending dependency resolution
- ℹ️ Audio playback will gracefully handle unsupported formats without codec libraries

## Benefits of Refactoring

1. **Eliminates Maintenance Burden**
   - No longer need to maintain/compile custom MPlayer builds
   - Removes per-architecture binary compilation needs
   - No dependency on abandoned MPlayer codebase

2. **Simplifies Deployment**
   - Single Java library vs. 5 platform-specific binaries
   - Smaller repository size
   - Easier cross-platform distribution

3. **Better Integration**
   - Pure Java implementation (no native code)
   - Better IDE support and debugging
   - Easier to extend with new audio formats

4. **Modern Audio Support**
   - Built-in support for WAV, AIFF, AU, OGG, MP3
   - Easy to add new codec support via plugins
   - Better SDL integration for audio output

## Testing Recommendations

1. Test audio playback on all platforms (Windows, macOS, Linux)
2. Verify volume control and seek operations
3. Test with various audio formats
4. Verify UI updates during playback (progress, duration)
5. Test edge cases (missing files, network timeouts, etc.)

## Files Modified

### Core Changes
- `build.gradle` - Updated dependencies and repositories
- `desktop/src/main/java/com/frostwire/gui/player/MediaPlayer.java`
- `desktop/src/main/java/com/frostwire/gui/player/MediaPlayerWindows.java`
- `desktop/src/main/java/com/frostwire/gui/player/MediaPlayerOSX.java`
- `desktop/src/main/java/com/frostwire/gui/player/MediaPlayerLinux.java`
- `desktop/src/main/java/com/frostwire/gui/library/tags/TagsParserFactory.java`
- `desktop/src/main/java/com/frostwire/gui/library/tags/TagsReader.java`

### New Files
- `desktop/src/main/java/com/frostwire/gui/audio/SimpleAudioPlayer.java`
- `desktop/src/main/java/de/ralleytn/simple/audio/*` (SimpleAudio library source)

### Deleted Files (via git rm)
- `desktop/lib/native/fwplayer.*` (all platforms)
- `desktop/src/main/java/com/frostwire/gui/mplayer/MPlayer.java`
- `desktop/src/main/java/com/frostwire/gui/mplayer/MPlayerInstance.java`
- `desktop/src/main/java/com/frostwire/gui/library/tags/MPlayerParser.java`

## References
- SimpleAudio GitHub: https://github.com/RalleYTN/SimpleAudio
- SimpleAudio Documentation: https://ralleytn.github.io/SimpleAudio/
