#!/bin/bash
codesign --verbose=4 -s KET68JTS3L --entitlements Entitlements.plist -f telluride_macos
codesign -vvv telluride_macos

# This tool must be symlinked from our private repo given it includes credentials
./notarizeMacOsApp.sh telluride_macos com.frostwire.Telluride
