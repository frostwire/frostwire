#!/bin/bash
codesign --verbose=4 -s KET68JTS3L --options=runtime telluride_macos
codesign -vvv telluride_macos
