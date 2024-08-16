@echo OFF
REM @echo ON
"C:\Program Files (x86)\Windows Kits\10\App Certification Kit\signtool.exe" sign /v /sm /s MY /n "FrostWire LLC" /tr http://timestamp.digicert.com /fd SHA256 /td SHA256 "telluride.exe"