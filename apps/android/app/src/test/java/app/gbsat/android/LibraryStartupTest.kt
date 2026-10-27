package app.gbsat.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import gb.frame.HomeGame
import gb.frame.LibraryView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LibraryStartupTest {
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("library-startup-test", Context.MODE_PRIVATE)

    @Before fun clearPreferences() { prefs.edit().clear().commit(); LibraryView.init(prefs) }

    @Test fun startupReturnsToOpenedGameAfterBrowsingAnotherCover() {
        LibraryView.gameOpened("played")
        LibraryView.lastGame = "browsed"
        assertEquals("browsed", LibraryView.lastGame)
        LibraryView.init(prefs)
        assertEquals("played", LibraryView.lastGame)
        assertEquals(1, LibraryView.gameIndex(listOf(HomeGame("other", "Other"), HomeGame("played", "Played"))))
    }

    @Test fun olderInstallationKeepsSelectionUntilFirstLaunch() {
        prefs.edit().putString("gbframe.libraryLastGame", "legacy").commit()
        LibraryView.init(prefs)
        assertEquals("legacy", LibraryView.lastGame)
        LibraryView.gameOpened("new")
        LibraryView.init(prefs)
        assertEquals("new", LibraryView.lastGame)
    }

    @Test fun removedGameFallsBackToFirstAvailableCover() {
        LibraryView.gameOpened("removed")
        LibraryView.init(prefs)
        assertEquals(0, LibraryView.gameIndex(listOf(HomeGame("available", "Available"))))
    }
}
