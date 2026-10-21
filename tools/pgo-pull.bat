@echo off
rem Copies the PGO training data from the phone (USB debugging on, PGO training build installed and played).
rem Result: gbsat-pgo.zip in this folder - send it to be merged into apps/android/pgo/gbsat.profdata.
setlocal
cd /d "%~dp0"
where adb >nul 2>nul
if errorlevel 1 (
  echo adb not found. Copy this file to the platform-tools folder and run it there.
  pause
  exit /b 1
)
echo Files on the phone:
adb shell ls -l /sdcard/Android/media/app.gbsat.android/pgo
if exist gbsat-pgo rmdir /s /q gbsat-pgo
adb pull /sdcard/Android/media/app.gbsat.android/pgo gbsat-pgo
if not exist gbsat-pgo\*.profraw (
  echo.
  echo No training data found. Play with the PGO training build for over a minute, then run this again.
  pause
  exit /b 1
)
powershell -NoProfile -Command "Compress-Archive -Force -Path gbsat-pgo\* -DestinationPath gbsat-pgo.zip"
echo.
echo Done: %CD%\gbsat-pgo.zip
pause
