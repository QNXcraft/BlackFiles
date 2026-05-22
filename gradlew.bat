@echo off
setlocal

set GRADLE_VERSION=2.14.1
set DIST_NAME=gradle-%GRADLE_VERSION%-bin.zip
set DIST_URL=https://services.gradle.org/distributions/%DIST_NAME%

if "%GRADLE_USER_HOME%"=="" (
	set GRADLE_USER_HOME=%USERPROFILE%\.gradle
)

set CACHE_DIR=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin
set ZIP_PATH=%CACHE_DIR%\%DIST_NAME%
set INSTALL_DIR=%CACHE_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%INSTALL_DIR%\bin\gradle.bat

if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"

if not exist "%ZIP_PATH%" (
	echo Downloading Gradle %GRADLE_VERSION%...
	powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('%DIST_URL%','%ZIP_PATH%')"
)

if not exist "%GRADLE_BIN%" (
	echo Extracting Gradle %GRADLE_VERSION%...
	powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%CACHE_DIR%' -Force"
)

call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%