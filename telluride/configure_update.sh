#!/bin/bash

##########################################################################
# Created by Angel Leon (@gubatron)
# Copyright (c) 2011-2021, FrostWire(R). All rights reserved.
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

# Python3 by default for macOS (and Windows to be tested)
PIP_CMD='python3 -m pip'
PYINSTALLER_PACKAGE='pyinstaller'
PIP_OPTIONS='install --upgrade --no-cache-dir'
source ./common.sh

if [ isdocker == ${FALSE} ] && [ isubuntu == ${TRUE} ]
then
  sudo apt-get install python3 python3-pip pylint3
  sudo update-alternatives --install /usr/bin/python python /usr/bin/python3 10
  PYINSTALLER_PACKAGE='PyInstaller'
fi

if iswindows
then
  PIP_CMD='python -m pip'
fi

${PIP_CMD} cache purge
${PIP_CMD} cache info
${PIP_CMD} ${PIP_OPTIONS} pip
${PIP_CMD} ${PIP_OPTIONS} pylint
${PIP_CMD} ${PIP_OPTIONS} youtube_dl
${PIP_CMD} ${PIP_OPTIONS} pycryptodome
# breaks after 21.6.0 with ModuleNotFoundError: No module named 'websockets.legacy.protocol'
# Waiting to hear from sanic team at https://github.com/sanic-org/sanic/issues/2227
# They've suggested upgrading to websocket 9 (tried 9.0 and 9.1, and still failed)
${PIP_CMD} ${PIP_OPTIONS} sanic==21.3.4
${PIP_CMD} ${PIP_OPTIONS} ${PYINSTALLER_PACKAGE}

${PIP_CMD} show pip pylint youtube_dl pycryptodome sanic pyinstaller websockets
