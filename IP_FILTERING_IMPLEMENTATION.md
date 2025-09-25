# FrostWire IP Filtering Implementation

## Overview
This implementation adds comprehensive IP filtering functionality to FrostWire for both desktop and Android platforms. Users can now block connections from known bad IP addresses and IP ranges to protect their privacy and security.

## Features Implemented

### Desktop Version
1. **Enable/Disable IP Filtering**: Added a checkbox in the IP Filter options panel to enable or disable IP filtering
2. **IP Filter Rules Management**: Enhanced existing IP filter import functionality to actually apply rules to the BitTorrent session
3. **Real-time Application**: IP filters are applied immediately when:
   - Importing new filter lists
   - Manually adding IP ranges
   - Loading saved filter lists on startup
   - Enabling/disabling the feature
4. **Peer Eviction**: When filters are applied, existing connections from blocked IPs are disconnected
5. **Persistent Settings**: IP filter enabled/disabled state is saved and restored

### Android Version
1. **Settings Integration**: Added IP filtering toggle in BitTorrent preferences
2. **Cross-platform Compatibility**: Uses common interface for IP range handling
3. **Simple Enable/Disable**: Users can turn IP filtering on/off through the settings

## Technical Implementation

### Core Components

#### BTEngine (common/src/main/java/com/frostwire/bittorrent/BTEngine.java)
- `applyIPFilter(List<IPRange> ranges)`: Applies IP filter rules to the libtorrent session
- `clearIPFilter()`: Removes all IP filter rules
- `getCurrentIPFilter()`: Gets current IP filter instance
- `evictBlockedPeers()`: Disconnects peers with blocked IP addresses

#### IPRange Interface (common/src/main/java/com/frostwire/bittorrent/IPRange.java)
- Common interface for IP ranges across platforms
- Methods: `description()`, `startAddress()`, `endAddress()`

### Desktop Integration

#### IPFilterPaneItem (desktop/src/com/limegroup/gnutella/gui/options/panes/IPFilterPaneItem.java)
- Added enable/disable checkbox
- Enhanced import functionality to apply filters
- Integrated with BTEngine for real-time filter application

#### FilterSettings (desktop/src/com/limegroup/gnutella/settings/FilterSettings.java)
- Added `IP_FILTER_ENABLED` boolean setting for persistence

### Android Integration

#### TorrentPreferenceFragment (android/src/com/frostwire/android/gui/fragments/preference/TorrentPreferenceFragment.java)
- Added IP filter toggle preference
- Handles enable/disable functionality

#### Constants (android/src/com/frostwire/android/core/Constants.java)
- Added `PREF_KEY_TORRENT_IP_FILTER_ENABLED` constant

#### Settings XML (android/res/xml/settings_torrent.xml)
- Added SwitchPreference for IP filtering

#### Strings (android/res/values/strings.xml)
- Added localized strings for IP filter setting

## Usage

### Desktop
1. Go to Tools → Options → IP Filter
2. Check "Enable IP Filtering" to activate the feature
3. Import IP filter lists from URLs or local files (supports .gz compressed files)
4. Add individual IP ranges manually if needed
5. Clear all filters using the "Clear IP Block List" button

### Android
1. Go to Settings → BitTorrent → Enable IP Filtering
2. Toggle the switch to enable/disable IP filtering
3. (Note: For full functionality, IP filter rule management UI would need to be added)

## IP Filter File Format

The implementation supports the P2P/PeerGuardian format:
```
Label:start_ip-end_ip
```

Examples:
```
Malware:5.9.179.87-5.9.179.87
Exploit Kit:5.39.47.28-5.39.47.28
Botnet on Telekom Malaysia:1.9.96.105-1.9.96.105
```

## Technical Details

### libtorrent Integration
The implementation uses libtorrent's built-in IP filtering capabilities:
- `session.set_ip_filter(ip_filter)`: Applies filter to session
- `ip_filter.add_rule(start_ip, end_ip, access_flags)`: Adds IP range rule
- `access_flags = 1`: Blocks the IP range
- `access_flags = 0`: Allows the IP range

### Thread Safety
- IP filter operations are performed on appropriate threads
- UI updates are dispatched to the event dispatch thread
- Background processing for large filter lists

### Error Handling
- Robust error handling for malformed IP addresses
- Graceful handling of invalid filter file formats
- Logging for debugging and monitoring

## Future Enhancements
1. Android IP filter rule management UI
2. Automatic updates of IP filter lists
3. Whitelist functionality for trusted IPs
4. Statistics on blocked connections
5. Integration with reputation services

## Testing
To test the implementation:
1. Enable IP filtering in settings
2. Import a known IP filter list (e.g., from I-Blocklist.com)
3. Monitor logs to see filter application and peer eviction
4. Verify that connections from blocked IPs are prevented