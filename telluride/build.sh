#!/bin/bash
##########################################################################
# Created by Angel Leon (@gubatron)
# Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
##########################################################################
#set -x
PYINSTALLER_CMD="pyinstaller"
PYLINT_CMD="pylint"
source ./common.sh

if [[ ismac -eq ${TRUE} || iswindows -eq ${TRUE} ]]
then
    PYLINT_CMD="pylint"
fi

# Linux's pyinstaller PATH
if isubuntu == ${TRUE}
then
    PYINSTALLER_CMD="${HOME}/.local/bin/pyinstaller"
fi

if isdocker == ${TRUE}
then
    #echo "isdocker: true, setting pylint command to 'pylint3'"
    PYINSTALLER_CMD=/usr/local/bin/pyinstaller
    PYLINT_CMD="pylint3"
fi

echo PYINSTALLER_CMD=${PYINSTALLER_CMD}

# Windows + MINGW
# pylint and pyinstaller might be in a place like this if you are in windows
# We make sure they are in the PATH
# c:\users\myuser\appdata\local\programs\python\python38-32\scripts
if iswindows
then
  echo "PYTHON_HOME=${PYTHON_HOME}"
  if [ ! $(which pyinstaller) ]
  then
    if [ -z "${PYTHON_HOME}" ]
	then
	  echo "Aborting, PYTHON_HOME env variable not set"
	  exit 1
	fi

    echo "Adding PYTHON_HOME/scripts to PATH..."
    PATH=${PATH}:${PYTHON_HOME}/scripts
  fi
fi

EXTRA_FLAGS=
if ismac
then
  EXTRA_FLAGS="--osx-bundle-identifier com.frostwire.Telluride"
fi

cleanup
${PYLINT_CMD} --max-line-length=350 telluride.py server.py
read -p "[Press any key to continue] [Press Ctrl+C to cancel build]"
${PYINSTALLER_CMD} --onefile ${EXTRA_FLAGS} telluride.py

if [ -f dist/telluride ]
then
  if isubuntu
  then
    mv dist/telluride telluride_linux
  elif isdocker
  then
    mv dist/telluride telluride_linux
  elif ismac
  then
    ARCH=`arch`
    if [ ${ARCH} == "i386" ]; then
        ARCH=x86_64
    fi
    mv dist/telluride telluride_macos.${ARCH}
    ./sign.sh
  elif iswindows
  then
    mv dist/telluride.exe .
    ./sign.bat
  fi
  cleanup
  ls -lth
fi
#set +x
