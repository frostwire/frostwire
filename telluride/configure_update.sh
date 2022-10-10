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

# Python3 by default for macOS (and Windows to be tested)
# set -x
PIP_CMD='python3 -m pip'
PYINSTALLER_PACKAGE='pyinstaller'
PIP_OPTIONS='install --upgrade --no-cache-dir'
source ./common.sh

if [ isdocker == ${FALSE} ] && [ isubuntu == ${TRUE} ]
then
  echo "Don't run this on a host ubuntu, please use the Docker image so we make the resulting binaries will be compatible with as many linux distributions as possible"
  exit 1
fi

if iswindows
then
  PIP_CMD='python -m pip'
fi

${PIP_CMD} cache purge
${PIP_CMD} cache info
${PIP_CMD} ${PIP_OPTIONS} pip
${PIP_CMD} ${PIP_OPTIONS} astroid
${PIP_CMD} ${PIP_OPTIONS} pylint
${PIP_CMD} ${PIP_OPTIONS} yt_dlp
${PIP_CMD} ${PIP_OPTIONS} flask
${PIP_CMD} ${PIP_OPTIONS} ${PYINSTALLER_PACKAGE}

${PIP_CMD} show pip astroid pylint yt_dlp pyinstaller flask werkzeug
