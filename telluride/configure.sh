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

# Thank you Brian Fox, bash creator
TRUE=0
FALSE=1

#Python3 by default for macOS (and Windows to be tested)
PIP_CMD=pip3
PYTHON_CMD=python3

isubuntu() {
  if [ $(uname -a | grep -c Ubuntu) == 0 ]
  then
    FALSE
  else
    TRUE
  fi		 
}

if isubuntu
then
	# Ubuntu isn't getting along with Python3 or PIP3 when it comes to installing pyinstaller
	echo "IN UBUNTU"
  PIP_CMD=pip
	PYTHON_CMD=python
  sudo apt-get install python-pip
	sudo apt-get install python
fi

${PYTHON_CMD} -m pip install --upgrade pip
${PIP_CMD} install youtube_dl --upgrade
${PIP_CMD} install pyinstaller --upgrade
