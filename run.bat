@echo off
cd /d %~dp0
if not exist foci-trace.jar call build.bat
java --add-modules jdk.httpserver -jar foci-trace.jar
