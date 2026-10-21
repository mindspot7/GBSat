@echo off
rem Copies the PGO training data from the phone (USB debugging on, PGO training build installed and played).
rem Result: gbsat-pgo.zip in this folder - send it to be merged into apps/android/pgo/gbsat.profdata.
setlocal
where adb >nul 2>nul || (echo adb not found. Run this from the platform-tools folder. & pause & exit /b 1)
adb shell am force-stop app.gbsat.android
if exist gbsat-pgo rmdir /s /q gbsat-pgo
adb pull /sdcard/Android/data/app.gbsat.android/files/pgo gbsat-pgo || (echo Nothing found. Did you play with the training build for over a minute? & pause & exit /b 1)
powershell -NoProfile -Command "Compress-Archive -Force -Path gbsat-pgo\* -DestinationPath gbsat-pgo.zip"
echo.
echo Done: %CD%\gbsat-pgo.zip
pause
