@echo off
cd /d %~dp0
if not exist foci-trace.jar (
  echo Building FOCI TRACE first...
  call build.bat
  if errorlevel 1 pause & exit /b 1
)
echo.
echo FOCI TRACE will open at http://localhost:8080
start "" http://localhost:8080
java --add-modules jdk.httpserver -jar foci-trace.jar
pause
