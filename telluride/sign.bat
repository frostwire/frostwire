@echo OFF
REM @echo ON
"C:\Program Files (x86)\Windows Kits\10\App Certification Kit\signtool.exe" sign /v /sm /s MY /n "FrostWire LLC" /tr http://timestamp.digicert.com /v "telluride.exe"