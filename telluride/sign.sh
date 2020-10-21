#!/bin/bash
codesign --verbose=4 -s KET68JTS3L telluride_macos
codesign -vvv telluride_macos
