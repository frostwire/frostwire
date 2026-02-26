#!/bin/bash
#
# FrostWire Installation Script for Linux
# This script installs FrostWire as a system application with desktop integration
#

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FROSTWIRE_HOME="${SCRIPT_DIR}"

echo "FrostWire Installer"
echo "==================="
echo ""
echo "Installation directory: ${FROSTWIRE_HOME}"
echo ""

# Verify frostwire script exists
if [ ! -f "${FROSTWIRE_HOME}/frostwire" ]; then
    echo "ERROR: frostwire executable not found at ${FROSTWIRE_HOME}/frostwire"
    exit 1
fi

# Verify frostwire.jar exists
if [ ! -f "${FROSTWIRE_HOME}/frostwire.jar" ]; then
    echo "ERROR: frostwire.jar not found at ${FROSTWIRE_HOME}/frostwire.jar"
    exit 1
fi

# Verify frostwire.1 man page exists
if [ ! -f "${FROSTWIRE_HOME}/frostwire.1" ]; then
    echo "ERROR: frostwire.1 man page not found at ${FROSTWIRE_HOME}/frostwire.1"
    exit 1
fi

# Detect Zorin OS (Core edition uses GNOME 46 + Mutter window manager)
IS_ZORIN=false
if grep -qi "zorin" /etc/os-release 2>/dev/null; then
    IS_ZORIN=true
    echo "Detected Zorin OS - will apply extra desktop integration steps"
    echo ""
fi

# Create .desktop file for desktop integration
DESKTOP_FILE_NAME="frostwire"
DESKTOP_FILE_USER="${HOME}/.local/share/applications/${DESKTOP_FILE_NAME}.desktop"
DESKTOP_FILE_SYSTEM="/usr/local/share/applications/${DESKTOP_FILE_NAME}.desktop"

# Determine if we can write to system directories
if [ -w "/usr/local/share/applications" ]; then
    DESKTOP_FILE="${DESKTOP_FILE_SYSTEM}"
    INSTALL_MODE="system"
else
    DESKTOP_FILE="${DESKTOP_FILE_USER}"
    INSTALL_MODE="user"
fi

echo "Creating desktop entry in ${INSTALL_MODE} mode..."
echo "Desktop file: ${DESKTOP_FILE}"
echo ""

# Install icon files to the system icon directory
ICON_DIR="${HOME}/.local/share/icons/hicolor"
if [ "${INSTALL_MODE}" = "system" ]; then
    ICON_DIR="/usr/local/share/icons/hicolor"
fi

echo "Installing icons..."
for size in 16x16 32x32 48x48 64x64 128x128 256x256 512x512; do
    ICON_SRC="${FROSTWIRE_HOME}/usr/share/icons/hicolor/${size}/apps/frostwire.png"
    ICON_DEST="${ICON_DIR}/${size}/apps"

    if [ -f "${ICON_SRC}" ]; then
        mkdir -p "${ICON_DEST}"
        cp -v "${ICON_SRC}" "${ICON_DEST}/frostwire.png"
        echo "✓ Installed icon: ${size}"
    else
        echo "⚠ Icon not found: ${ICON_SRC}"
    fi
done
echo ""

# Create the desktop directory if it doesn't exist
mkdir -p "$(dirname "${DESKTOP_FILE}")"

# Generate the .desktop file with the correct paths
cat > "${DESKTOP_FILE}" << EOF
[Desktop Entry]
Version=1.0
Name=FrostWire
GenericName=P2P Bittorrent and cloud downloader
GenericName[es]=P2P cliente Bittorrent
Comment=Search and explore all kinds of files on the Bittorrent network and download videos from the cloud
Comment[es]=Busque y explore todo tipo de archivos en la red Bittorrent
Exec=${FROSTWIRE_HOME}/frostwire %U
Icon=frostwire
Terminal=false
Type=Application
Categories=Network;FileTransfer;P2P;
MimeType=application/x-bittorrent;x-scheme-handler/magnet;
X-Ubuntu-Gettext-Domain=frostwire
X-AppInstall-Keywords=torrent
Keywords=P2P,BitTorrent,Torrent,FrostWire,Vuze,uTorrent,Transmission,Transfer,Cloud,Video,Downloader;
StartupNotify=true
StartupWMClass=com-limegroup-gnutella-gui-Main
X-FrostWire-InstallDir=${FROSTWIRE_HOME}
EOF

echo "✓ Desktop file created successfully"
echo ""

# Zorin OS specific desktop integration
# On Zorin OS Core (GNOME 46 + Mutter), the desktop file must be executable
# and MIME types must be explicitly registered for the app to appear in the
# GNOME application menu. These steps are harmless on other Zorin OS editions.
if [ "${IS_ZORIN}" = "true" ]; then
    echo "Applying Zorin OS desktop integration..."

    # Mark the desktop file as executable so GNOME 46/Mutter recognises it as trusted
    chmod +x "${DESKTOP_FILE}"
    echo "✓ Desktop file marked as executable"

    # Register MIME types so FrostWire is the default handler for torrents/magnets
    if command -v xdg-mime &> /dev/null; then
        xdg-mime default frostwire.desktop application/x-bittorrent 2>/dev/null || true
        xdg-mime default frostwire.desktop x-scheme-handler/magnet 2>/dev/null || true
        echo "✓ MIME types registered with xdg-mime"
    fi
    echo ""
fi

# Update the desktop and icon databases if available
if command -v update-desktop-database &> /dev/null; then
    echo "Updating desktop database..."
    if [ "${INSTALL_MODE}" = "system" ]; then
        sudo update-desktop-database /usr/local/share/applications
    else
        update-desktop-database "${HOME}/.local/share/applications"
    fi
    echo "✓ Desktop database updated"
    echo ""
fi

# Update icon cache to refresh icon availability
if command -v gtk-update-icon-cache &> /dev/null; then
    echo "Updating icon cache..."
    if [ "${INSTALL_MODE}" = "system" ]; then
        sudo gtk-update-icon-cache -f -t /usr/local/share/icons/hicolor 2>/dev/null || true
    else
        gtk-update-icon-cache -f -t "${HOME}/.local/share/icons/hicolor" 2>/dev/null || true
    fi
    echo "✓ Icon cache updated"
    echo ""
fi

# Create a symlink in a standard bin directory if writable (optional)
if [ -w "/usr/local/bin" ]; then
    echo "Creating symlink in /usr/local/bin..."
    sudo ln -sf "${FROSTWIRE_HOME}/frostwire" /usr/local/bin/frostwire
    echo "✓ Symlink created at /usr/local/bin/frostwire"
    echo ""
fi

# Check if we can create user symlink
if [ ! -e "${HOME}/.local/bin" ]; then
    mkdir -p "${HOME}/.local/bin"
fi

if [ -w "${HOME}/.local/bin" ]; then
    ln -sf "${FROSTWIRE_HOME}/frostwire" "${HOME}/.local/bin/frostwire"
    echo "✓ Symlink created at ${HOME}/.local/bin/frostwire"

    # Check if ~/.local/bin is in PATH
    if [[ ":$PATH:" != *":${HOME}/.local/bin:"* ]]; then
        echo ""
        echo "NOTE: ${HOME}/.local/bin is not in your PATH."
        echo "Add the following line to your ~/.bashrc or ~/.profile to run 'frostwire' from anywhere:"
        echo "  export PATH=\"\${HOME}/.local/bin:\${PATH}\""
    fi
    echo ""
fi

# Install man page
echo "Installing man page..."
MAN_DIR="${HOME}/.local/share/man/man1"
if [ -w "/usr/local/share/man/man1" ]; then
    MAN_DIR="/usr/local/share/man/man1"
    sudo mkdir -p "${MAN_DIR}"
    sudo cp -v "${FROSTWIRE_HOME}/frostwire.1" "${MAN_DIR}/frostwire.1"
    echo "✓ Man page installed to ${MAN_DIR}"
else
    mkdir -p "${MAN_DIR}"
    cp -v "${FROSTWIRE_HOME}/frostwire.1" "${MAN_DIR}/frostwire.1"
    echo "✓ Man page installed to ${MAN_DIR}"
fi
echo ""

echo "Installation Summary"
echo "===================="
echo "FrostWire has been installed successfully!"
echo ""
echo "You can now:"
echo "  1. Find 'FrostWire' in your application menu (GNOME, KDE, XFCE, etc.)"
echo "  2. Launch FrostWire from the command line:"
if [ -w "/usr/local/bin" ]; then
    echo "     $ frostwire"
else
    if [[ ":$PATH:" == *":${HOME}/.local/bin:"* ]]; then
        echo "     $ frostwire"
    else
        echo "     $ ${HOME}/.local/bin/frostwire"
    fi
fi
echo "  3. Double-click on .torrent files or magnet links to open them with FrostWire"
echo "  4. View the man page:"
echo "     $ man frostwire"
echo ""
echo "Desktop file location: ${DESKTOP_FILE}"
echo "Installation directory: ${FROSTWIRE_HOME}"
echo "Man page location: ${MAN_DIR}/frostwire.1"
echo ""

# Zorin OS specific post-install note
if [ "${IS_ZORIN}" = "true" ]; then
    echo "ZORIN OS NOTE:"
    echo "  If FrostWire does not appear in your application menu immediately, please"
    echo "  log out and log back in to refresh the application list."
    echo "  On Zorin OS Core (GNOME), you can also restart GNOME Shell by pressing"
    echo "  Alt+F2, typing 'r', and pressing Enter (X11 sessions only)."
    echo ""
fi
