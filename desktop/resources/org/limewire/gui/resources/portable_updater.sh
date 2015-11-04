#!/bin/bash

portableSource=$1
portableTarget=$2

function IsFrostWireRunning() {
    RESULT=`ps acx | grep -i FrostWire | awk {'print $1'}`
    if [ "${RESULT:-null}" = null ]; then
        echo "0"
    else
        echo "1"
    fi
}

function WaitFrostWireStopped() {
    for (( i=1; i<=30; i++ ))
    do
        if [ "$(IsFrostWireRunning)" = "0" ]; then
            echo "1"
            return
        fi

        sleep 1s
    done
    
    echo "0"
}

function CopyFrostWireFiles() {
    `mv $portableTarget $portableTarget.bak`
    `mv $portableSource $portableTarget`
    `rm -rf $portableTarget.bak`
}

function LaunchFrostWire() {
    # only for Mac OS X for now
    `open $portableTarget`
}

if [ "$(WaitFrostWireStopped)" = "1" ]; then
    sleep 1s
    CopyFrostWireFiles
    LaunchFrostWire
fi