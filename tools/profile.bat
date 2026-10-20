@echo off
rem GBSat CPU profile: records 20 seconds of the running game with simpleperf (built into Android)
rem and saves gbsat-profile.txt next to this script.
rem Before running: install the profiling build, start a game, connect the device by USB (USB debugging on).
setlocal
set PKG=app.gbsat.android
set OUT=%~dp0gbsat-profile.txt
where adb >nul 2>&1 || (echo adb not found: run this from the platform-tools folder or add it to PATH & pause & exit /b 1)
adb get-state >nul 2>&1 || (echo No device: connect it by USB and accept the debugging prompt & pause & exit /b 1)
echo Recording 20 seconds... keep playing.
adb shell simpleperf record --app %PKG% -e cpu-clock -f 2000 --duration 20 -o /data/local/tmp/gbsat.data
if errorlevel 1 (echo Recording failed: is the profiling build installed and the game running? & pause & exit /b 1)
echo Building the report...
> "%OUT%" echo === GBSat profile ===
>> "%OUT%" adb shell getprop ro.product.model
>> "%OUT%" adb shell getprop ro.build.version.release
>> "%OUT%" echo.
>> "%OUT%" echo === Per thread ===
>> "%OUT%" adb shell simpleperf report -i /data/local/tmp/gbsat.data --sort comm --percent-limit 0.5
>> "%OUT%" echo.
>> "%OUT%" echo === Per function ===
>> "%OUT%" adb shell simpleperf report -i /data/local/tmp/gbsat.data --sort comm,symbol --percent-limit 0.2
adb shell rm /data/local/tmp/gbsat.data
echo Done: %OUT%
pause
