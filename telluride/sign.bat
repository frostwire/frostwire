@echo OFF
:: These environment variables are set in windows10_gitbash_profile, cert pass must be updated regularly.
IF NOT DEFINED CERT_PATH (
  @echo ON
  echo The environment variable CERT_PATH is not set. Aborting signing.
  exit /b 1
)

IF NOT DEFINED CERT_PASS (
  @echo ON
  echo The environment variable CERT_PASS is not set. Aborting signing.
  exit /b 1
)

REM @echo ON
echo Signing using the following environment variable values:
echo CERT_PATH=%CERT_PATH%
echo You will have to enter this CERT_PASS=%CERT_PASS% in the SafeNet eToken window dialog to sign properly

"C:\Program Files (x86)\Windows Kits\10\App Certification Kit\signtool.exe" sign /f %CERT_PATH% /p %CERT_PASS% /n "FrostWire" /fd sha256 /tr http://timestamp.digicert.com /v "telluride.exe"