# Approved transparent launcher artwork

These original PNGs were recovered from the user's installed GBSat 1.3.5 build 285 (`app.gbsat.android`) and explicitly selected by the user for the current app's launcher.

- mdpi: `res/9w.png` (48 x 48)
- hdpi: `res/yn.png` (72 x 72)
- xhdpi: `res/FS.png` (96 x 96)
- xxhdpi: `res/RJ.png` (144 x 144)
- xxxhdpi: `res/o-.png` (192 x 192)

Run `tools/build-transparent-launcher-icons.ps1` to restore both standalone raster resource names. It copies the files byte-for-byte and checks their hashes. Do not reintroduce adaptive foreground padding, an extra outline or an opaque background. The app's internal/adaptive foreground artwork is a separate resource and remains unchanged by this command.
