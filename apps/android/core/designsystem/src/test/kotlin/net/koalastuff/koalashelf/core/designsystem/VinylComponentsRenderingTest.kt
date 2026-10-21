package net.koalastuff.koalashelf.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VinylComponentsRenderingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `VinylDisc renders without crash in light mode`() {
        composeTestRule.setContent {
            KoalaShelfTheme(themeMode = KoalaThemeMode.LIGHT) {
                Box(modifier = Modifier.size(200.dp)) {
                    VinylDisc(
                        title = "Der Steppenwolf",
                        author = "Hermann Hesse",
                        coverUrl = null,
                        playing = false,
                    )
                }
            }
        }
        composeTestRule.onRoot().assertExists()
    }

    @Test
    fun `VinylDisc renders without crash in dark mode`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            KoalaShelfTheme(themeMode = KoalaThemeMode.DARK) {
                Box(modifier = Modifier.size(200.dp)) {
                    VinylDisc(
                        title = "Dune",
                        author = "Frank Herbert",
                        coverUrl = null,
                        playing = true,
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.onRoot().assertExists()
    }

    @Test
    fun `BookCover renders placeholder without crash when coverUrl is null`() {
        composeTestRule.setContent {
            KoalaShelfTheme(themeMode = KoalaThemeMode.LIGHT) {
                Box(modifier = Modifier.size(140.dp)) {
                    BookCover(
                        title = "Neuromancer",
                        author = "William Gibson",
                        coverUrl = null,
                        modifier = Modifier.size(140.dp),
                    )
                }
            }
        }
        composeTestRule.onRoot().assertExists()
    }

    @Test
    fun `TurntableDeck renders progress and tonearm safely`() {
        composeTestRule.setContent {
            KoalaShelfTheme(themeMode = KoalaThemeMode.DARK) {
                Box(modifier = Modifier.size(320.dp)) {
                    TurntableDeck(
                        title = "Foundation",
                        author = "Isaac Asimov",
                        coverUrl = null,
                        playing = false,
                        progress = 0.45f,
                        interactive = false,
                    )
                }
            }
        }
        composeTestRule.onRoot().assertExists()
    }

    @Test
    fun `Tonearm angle calculation stays strictly bounded`() {
        val base = KoalaMotion.TONEARM_BASE_DEG
        val range = KoalaMotion.TONEARM_RANGE_DEG

        val angleAtStart = base + 0f * range
        val angleAtEnd = base + 1f * range

        assertEquals(18f, angleAtStart, 0.001f)
        assertEquals(40f, angleAtEnd, 0.001f)
        assertTrue(angleAtEnd > angleAtStart)
    }
}
