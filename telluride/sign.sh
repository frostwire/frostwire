#!/bin/bash
if [ $(uname -a | grep -c Darwin) == 0 ]
then
  echo "telluride/sign.sh: this script is only meant for macos, exiting"
  exit 0
fi
ARCH=`arch`
codesign --verbose=4 \
         -s KET68JTS3L \
         --entitlements Entitlements.plist \
         --options runtime \
         -f \
         telluride_macos.${ARCH}
codesign -vvv telluride_macos.${ARCH}

# This tool must be symlinked from our private repo given it includes credentials
if [ -f ./notarizeMacOsApp.sh ]
then
  ./notarizeMacOsApp.sh telluride_macos.${ARCH} com.frostwire.Telluride
else
  echo "telluride/sign.sh: telluride_macos signed but not sent for notarization, notarizeMacOsApp.sh not found (symlink from private tools repository)"
fi
