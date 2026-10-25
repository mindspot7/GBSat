package app.gbsat.android.ui

import androidx.compose.runtime.Composable
import gb.frame.EmuPalettes
import gb.frame.EmuTheme

/** GBSat's look: the shared UI kit with the purple-forward Dreamcast Arcade palette. */
@Composable
fun GBSatTheme(content: @Composable () -> Unit) = EmuTheme(EmuPalettes.DreamcastArcade, content)
