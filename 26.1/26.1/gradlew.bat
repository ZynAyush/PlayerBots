@echo off
setlocal
set "GRADLE_VERSION=9.8.0"
set "GRADLE_HOME=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%GRADLE_HOME%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_DIR=%GRADLE_HOME%\gradle-%GRADLE_VERSION%"
if exist "%GRADLE_DIR%\bin\gradle.bat" goto run
if not exist "%GRADLE_HOME%" mkdir "%GRADLE_HOME%"
echo Downloading Gradle %GRADLE_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
if errorlevel 1 (
  echo Failed to download Gradle %GRADLE_VERSION%.
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%GRADLE_ZIP%' -DestinationPath '%GRADLE_HOME%' -Force"
if errorlevel 1 (
  echo Failed to extract Gradle.
  exit /b 1
)
:run
call "%GRADLE_DIR%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
