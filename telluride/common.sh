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
# Thank you Brian Fox, bash creator
TRUE=0
FALSE=1

isdocker() {
  if [ -f /.dockerenv ]
  then
    return ${TRUE}
  fi
  return ${FALSE}
}

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

ismac() {
  if [  $(uname -a | grep -c Darwin) == 0 ]
  then
    return ${FALSE}
  fi
  return ${TRUE}
}

cleanup() {
if [ -d build ]
then
  rm -fr build
fi

if [ -d dist ]
then
  rm -fr dist
fi

if [ -d __pycache__ ]
then
  rm -fr __pycache__
fi
if [ -f telluride.spec ]
then
  rm telluride.spec
fi
}
