# FrostWire Flatpak dependency update script
# Run this script inside a Flatpak sandbox from the root of the repo.

source /usr/lib/sdk/openjdk/enable.sh
rm -rf gradle-dependencies.json


cd desktop
rm -rf gradle-cache/
gradle -g gradle-cache/ --info --console plain clean build > gradle-log.txt
