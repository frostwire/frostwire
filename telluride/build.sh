#!/bin/bash
PYINSTALLER_CMD="pyinstaller"

if [ $(uname -a | grep -c Ubuntu) == 1 ]
then
    PYINSTALLER_CMD="${HOME}/.local/bin/pyinstaller"
    echo "PYINSTALLER_CMD=${PYINSTALLER_CMD}"
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

cleanup
pylint telluride.py
read -p "[Press any key to continue] [Press Ctrl+C to cancel build]"
${PYINSTALLER_CMD} --onefile telluride.py

if [ -f dist/telluride ]
then
  if [ $(uname -a | grep -c Ubuntu) == 1 ]
  then
    mv dist/telluride telluride_linux
  elif [ $(uname -a | grep -c Darwin) == 1 ]
  then
    mv dist/telluride telluride_macos
  fi
  cleanup
  ls -lth
fi
