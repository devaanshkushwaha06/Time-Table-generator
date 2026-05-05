@echo off
REM ---------------------------------------------------------------------
REM Build & run the Smart Daily Timetable app without Maven.
REM Requires:
REM   - JDK 11+ on PATH
REM   - mysql-connector-j JAR placed in .\lib\  (any 8.x version)
REM ---------------------------------------------------------------------
setlocal EnableDelayedExpansion
set ROOT=%~dp0
set SRC=%ROOT%src\main\java
set RES=%ROOT%src\main\resources
set OUT=%ROOT%build\classes
set LIB=%ROOT%lib

if not exist "%OUT%" mkdir "%OUT%"
if not exist "%ROOT%build" mkdir "%ROOT%build"

set FILES=
for /r "%SRC%" %%f in (*.java) do set FILES=!FILES! "%%f"

echo Compiling...
javac -cp "%LIB%\*" -d "%OUT%" !FILES!
copy /Y "%RES%\db.properties" "%OUT%\db.properties" >nul

echo Running...
java -cp "%OUT%;%LIB%\*" com.smartscheduler.Main
endlocal
