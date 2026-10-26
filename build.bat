@echo off
setlocal
cd /d %~dp0
if exist out rmdir /s /q out
if exist foci-trace.jar del /q foci-trace.jar
mkdir out
for /r src %%f in (*.java) do echo %%f>>sources.txt
javac --add-modules jdk.httpserver -encoding UTF-8 -cp lib\hsqldb.jar -d out @sources.txt
if errorlevel 1 exit /b 1
java -cp out;lib\hsqldb.jar za.co.focigroup.focitrace.SelfTest
if errorlevel 1 exit /b 1
del sources.txt
jar cfm foci-trace.jar MANIFEST.MF -C out .
echo Built foci-trace.jar
endlocal
