package app.gbsat.emulator

/** Saturn disc images in the games folder, read through the Storage Access Framework (see the kit's SafGames). */
object Storage : gb.frame.SafGames(setOf("cue", "chd", "iso", "mds", "ccd"), { Emulator.gamesDir })
