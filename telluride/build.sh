#!/bin/bash
PYINSTALLER_CMD="pyinstaller"

# Linux's pyinstaller PATH
if [ $(uname -a | grep -c Ubuntu) == 1 ]
then
    PYINSTALLER_CMD="${HOME}/.local/bin/pyinstaller"
    echo "PYINSTALLER_CMD=${PYINSTALLER_CMD}"
fi

# Windows + MINGW
# pylint and pyinstaller might be in a place like this if you are in windows
# We make sure they are in the PATH
# c:\users\myuser\appdata\local\programs\python\python38-32\scripts
if [ $(uname -a | grep -c windows) == 1 ]
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

EXTRA_FLAGS=
if [ $(uname -a | grep -c Darwin) == 1 ]; then
  EXTRA_FLAGS="--osx-bundle-identifier com.frostwire.Telluride"
fi

cleanup
pylint telluride.py
read -p "[Press any key to continue] [Press Ctrl+C to cancel build]"
${PYINSTALLER_CMD} --onefile ${EXTRA_FLAGS} telluride.py

if [ -f dist/telluride ]
then
  if [ $(uname -a | grep -c Ubuntu) == 1 ]
  then
    mv dist/telluride telluride_linux
  elif [ $(uname -a | grep -c Darwin) == 1 ]
  then
  	mv dist/telluride telluride_macos
		./sign.sh
  elif [ $(uname -a | grep -c windows) == 1 ]
  then
    mv dist/telluride.exe .
    ./sign.bat
  fi
  cleanup
  ls -lth
fi
