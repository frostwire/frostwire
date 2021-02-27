#!/bin/bash

##########################################################################
# Created by Angel Leon (@gubatron)
# Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

# Thank you Brian Fox, bash creator
TRUE=0
FALSE=1

isubuntu() {
  if [ $(uname -a | grep -c Ubuntu) == 0 ]
  then
    return ${FALSE}
  else
    return ${TRUE}
  fi
}

iswindows() {
  if [ $(uname -a | grep -c windows) == 0 ]
  then
    return ${FALSE}
  else
    return ${TRUE}
  fi
}

if isubuntu
then
  sudo update-alternatives --install /usr/bin/python python /usr/bin/python3 10
  sudo apt-get install python3 python3-pip
  PYINSTALLER_PACKAGE='PyInstaller'
fi

if iswindows
then
  PIP_CMD='python -m pip'
fi

${PIP_CMD} install --upgrade pip
${PIP_CMD} install --upgrade pylint
${PIP_CMD} install --upgrade youtube_dl
${PIP_CMD} install --upgrade pycryptodome
${PIP_CMD} install --upgrade ${PYINSTALLER_PACKAGE}

${PIP_CMD} show pip pylint youtube_dl pycryptodome pyinstaller
