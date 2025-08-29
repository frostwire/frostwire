# FrostWire Snap Package

This directory contains the configuration for building FrostWire as a Snap package.

## Building the Snap

### Prerequisites

1. Install snapcraft:
   ```bash
   sudo snap install snapcraft --classic
   ```

2. Install multipass (for building in a clean environment):
   ```bash
   sudo snap install multipass
   ```

### Build Instructions

1. Build FrostWire first:
   ```bash
   ./gradlew build -x test
   ```

2. Build the Snap package:
   ```bash
   snapcraft
   ```

   Or build in a clean environment:
   ```bash
   snapcraft --use-lxd
   ```

3. Install the built snap locally for testing:
   ```bash
   sudo snap install --dangerous --devmode ./frostwire_*.snap
   ```

### Publishing to Snap Store

1. Create a Snap Store account at https://snapcraft.io/
2. Register the name:
   ```bash
   snapcraft register frostwire
   ```
3. Upload to store:
   ```bash
   snapcraft upload frostwire_*.snap
   ```

### Features

- **Universal Linux compatibility**: Works on all major Linux distributions
- **Automatic updates**: Updates are handled by the Snap system
- **Sandboxed security**: Runs in a confined environment with limited system access
- **Easy installation**: Install with `sudo snap install frostwire`

### Permissions

The snap includes the following permissions (plugs):
- `network` and `network-bind`: For BitTorrent and internet connectivity
- `audio-playback` and `audio-record`: For media playback functionality
- `home`: Access to user home directory for downloads
- `desktop`, `x11`, `wayland`: GUI functionality
- `removable-media` and `optical-drive`: Access to external storage
- `opengl`: Hardware accelerated graphics

### Notes

- The application data is stored in `~/snap/frostwire/current/`
- Updates are handled automatically by the Snap system
- When running as a Snap, FrostWire will detect this and show appropriate update messages