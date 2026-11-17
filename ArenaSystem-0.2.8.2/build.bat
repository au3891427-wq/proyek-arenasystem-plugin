@echo off
setlocal
cd /d "%~dp0"
call mvn clean package
if errorlevel 1 exit /b 1
if not exist "target\ArenaSystem-0.2.8.2.jar" exit /b 2
echo Build successful: target\ArenaSystem-0.2.8.2.jar
endlocal
