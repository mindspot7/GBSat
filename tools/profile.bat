@echo off
rem CPU profile: records 20 seconds of the running game with simpleperf (built into Android) and saves
rem %NAME%-profile_<GAME>.txt next to this script, named after the game being played (from the app's log),
rem so several games can be recorded in a row without renaming. Asks for another game at the end.
rem Before running: install the profiling build, start a game, connect the device by USB (USB debugging on).
setlocal EnableDelayedExpansion
rem ---- app settings (edit when copying to another app) ----
set APP=app.gbsat.android
set NAME=gbsat
rem ---------------------------------------------------------
cd /d "%~dp0"
where adb >nul 2>&1 || (echo adb not found: run this from the platform-tools folder or add it to PATH & pause & exit /b 1)
adb get-state >nul 2>&1 || (echo No device: connect it by USB and accept the debugging prompt & pause & exit /b 1)

:next
echo.
echo Start the game on the device and play; press a key here to record 20 seconds.
pause >nul

echo Recording 20 seconds... keep playing.
adb shell simpleperf record --app %APP% -e cpu-clock -f 2000 --duration 20 -o /data/local/tmp/%NAME%.data
if errorlevel 1 (echo Recording failed: is the profiling build installed and the game running? & pause & exit /b 1)
rem Game name: the last "Game: <title>" line the app wrote to the system log when it loaded a disc (the performance log
rem read from the shell could be a stale copy and give the previous game), made filename-safe on the device (letters
rem and digits, the rest becomes "_")
set GAME=
for /f "usebackq delims=" %%G in (`adb shell "logcat -d -s GBSat:I 2>/dev/null | grep 'Game: ' | tail -n 1 | sed 's/.*Game: //' | tr -cs 'A-Za-z0-9' '_' | cut -c1-32 | sed 's/^_*//;s/_*$//'"`) do set "GAME=%%G"
if not defined GAME set GAME=game
if "!GAME!"=="game" set GAME=game
set "OUT=%NAME%-profile_!GAME!.txt"
set N=2
:unique
if exist "!OUT!" (set "OUT=%NAME%-profile_!GAME!_!N!.txt" & set /a N+=1 & goto unique)

echo Building the report...
> "!OUT!" echo === %NAME% profile: !GAME! ===
>> "!OUT!" adb shell getprop ro.product.model
>> "!OUT!" adb shell getprop ro.build.version.release
>> "!OUT!" echo.
>> "!OUT!" echo === Per thread ===
>> "!OUT!" adb shell simpleperf report -i /data/local/tmp/%NAME%.data --sort comm --percent-limit 0.5
>> "!OUT!" echo.
>> "!OUT!" echo === Per function ===
>> "!OUT!" adb shell simpleperf report -i /data/local/tmp/%NAME%.data --sort comm,symbol --percent-limit 0.2
adb shell rm /data/local/tmp/%NAME%.data
echo Done: %CD%\!OUT!
choice /c YN /m "Record another game"
if not errorlevel 2 goto next
