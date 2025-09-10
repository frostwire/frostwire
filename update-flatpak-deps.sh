# FrostWire Flatpak dependency update script
# Run this script inside a Flatpak sandbox from the 'desktop/' directory.

source /usr/lib/sdk/openjdk/enable.sh
rm -rf gradle-cache

cd desktop
gradle -g gradle-cache/ --info --console plain clean build > gradle-log.txt
