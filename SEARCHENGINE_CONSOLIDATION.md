# SearchEngine Consolidation

This document describes the consolidation of SearchEngine classes between Android and Desktop platforms.

## Problem

Previously, FrostWire had separate SearchEngine implementations for Android and Desktop:

- **Android**: `com.frostwire.android.gui.SearchEngine`
- **Desktop**: `com.limegroup.gnutella.gui.search.SearchEngine`

These implementations had significant differences:

1. **Settings Persistence**: 
   - Android used `ConfigurationManager.getBoolean()`
   - Desktop used `BooleanSetting.getValue()`

2. **Telluride Integration**:
   - Android used `TellurideCourier` with Python runtime
   - Desktop used `TellurideSearchPerformer` with HTTP server

3. **Engine Management**:
   - Different initialization patterns and domain handling
   - Inconsistent interfaces and functionality

## Solution

The consolidation uses an **Adapter Pattern** to unify common functionality while preserving platform-specific behavior.

### Architecture

```
CommonSearchEngine (common)
├── SearchEngineSettingsAdapter (interface)
│   ├── AndroidSearchEngineSettingsAdapter (android)
│   └── DesktopSearchEngineSettingsAdapter (desktop)
├── TellurideAdapter (interface)
│   ├── AndroidTellurideAdapter (android)
│   └── DesktopTellurideAdapter (desktop)
└── Platform-specific implementations
    ├── AndroidSearchEngine (android)
    └── DesktopSearchEngine (desktop)
```

### Key Components

1. **`CommonSearchEngine`** - Base class with shared functionality
2. **`SearchEngineSettingsAdapter`** - Interface for settings persistence
3. **`TellurideAdapter`** - Interface for Telluride integration
4. **Platform-specific adapters** - Implement platform behavior
5. **Platform-specific engines** - Extend CommonSearchEngine with concrete implementations

## Usage

### Android

```java
// Get all engines
List<AndroidSearchEngine> engines = AndroidSearchEngine.getEngines(true);

// Find specific engine
AndroidSearchEngine tpb = AndroidSearchEngine.forName("TPB");
if (tpb != null && tpb.isEnabled()) {
    SearchPerformer performer = tpb.getPerformer(token, keywords);
    SearchManager.getInstance().perform(performer);
}

// Use Telluride
Object telluridePerformer = AndroidSearchEngine.TELLURIDE_COURIER
    .getTelluridePerformer(token, pageUrl, adapter);
```

### Desktop

```java
// Get all engines
List<DesktopSearchEngine> engines = DesktopSearchEngine.getEngines();

// Find specific engine
DesktopSearchEngine archiveOrg = engines.stream()
    .filter(e -> e.getName().equals("Archive.org"))
    .findFirst().orElse(null);

if (archiveOrg != null && archiveOrg.isEnabled()) {
    SearchPerformer performer = archiveOrg.getPerformer(token, keywords);
    SearchManager.getInstance().perform(performer);
}

// Use Telluride
SearchPerformer performer = DesktopSearchEngine.getSearchEngineByName("Cloud:")
    .getTellurideSearchPerformer(token, keywords);
```

## Migration Path

### Phase 1: Backward Compatibility (Current)
- Original SearchEngine classes remain unchanged
- New consolidated classes available via bridge methods:
  - `SearchEngine.getConsolidatedEngines()`
  - `SearchEngine.getConsolidatedEngineByName()`

### Phase 2: Gradual Migration
- New code uses consolidated approach
- Existing code gradually migrated
- Both approaches work side by side

### Phase 3: Complete Migration
- All code uses consolidated approach
- Original SearchEngine classes deprecated
- Remove old implementations

## Benefits

1. **Shared Code**: Common functionality implemented once
2. **Consistency**: Same interface and behavior across platforms
3. **Maintainability**: Easier to add new engines and fix bugs
4. **Type Safety**: Better compile-time checking
5. **Flexibility**: Platform-specific behavior through adapters
6. **Testing**: Easier to test with mockable adapters

## Files Created

### Common Module
- `SearchEngineSettingsAdapter.java` - Settings interface
- `TellurideAdapter.java` - Telluride interface  
- `CommonSearchEngine.java` - Base consolidated class
- `ConsolidatedSearchEngineExample.java` - Usage examples

### Android Platform
- `AndroidSearchEngineSettingsAdapter.java` - Android settings implementation
- `AndroidTellurideAdapter.java` - Android Telluride implementation
- `AndroidSearchEngine.java` - Android consolidated engines

### Desktop Platform
- `DesktopSearchEngineSettingsAdapter.java` - Desktop settings implementation
- `DesktopTellurideAdapter.java` - Desktop Telluride implementation
- `DesktopSearchEngine.java` - Desktop consolidated engines

## Future Enhancements

1. **Configuration**: Make engines configurable via external config
2. **Plugins**: Allow dynamic loading of search engines
3. **Metrics**: Add performance monitoring and analytics
4. **Caching**: Implement result caching at the engine level
5. **Retry Logic**: Add configurable retry mechanisms