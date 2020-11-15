@echo off
SETLOCAL ENABLEDELAYEDEXPANSION
SET LIBPATH=lib\native
SET PATH=%PATH%;%LIBPATH%
SET CLASSPATH=build\libs\frostwire.jar

REM -Dsun.java2d.opengl=true
java -Xms32m -Xmx512m -Xss196k -Ddebug=1 -Dcom.sun.management.jmxremote.port=9595 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Djava.rmi.server.hostname=127.0.0.1 -Djava.library.path=%LIBPATH% com.limegroup.gnutella.gui.Main
