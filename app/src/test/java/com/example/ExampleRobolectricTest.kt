package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PakTubeCategories
import com.example.data.model.VideoItem
import com.example.ui.components.ShortPreviewCard
import com.example.ui.components.formatCountUrdu
import com.example.ui.components.formatDuration
import com.example.ui.navigation.PakTubeApp
import com.example.ui.screens.AccountSignInPromptCard
import com.example.ui.screens.PakToYouSplashScreen
import com.example.ui.theme.PakTubeTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun ensureFirebaseInitialized(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId("planning-with-ai-c5801")
                    .setApplicationId("1:387971070592:android:51c5e4c0b4051792808465")
                    .setApiKey("AIzaSyDummyTestKeyForRobolectric12345")
                    .build()
            )
        }
    }

    @Test
    fun `app metadata and urdu navigation strings match PakTube`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PakTube", context.getString(R.string.app_name))
        assertEquals("PakToYou", context.getString(R.string.splash_title))
        assertEquals("ہوم", context.getString(R.string.nav_home))
        assertEquals("شارٹس", context.getString(R.string.nav_shorts))
        assertEquals("بنائیں", context.getString(R.string.nav_create))
        assertEquals("سبسکرپشنز", context.getString(R.string.nav_subscriptions))
        assertEquals("آپ", context.getString(R.string.nav_profile))
        assertTrue(context.getString(R.string.firestore_database_id).isNotBlank())
    }

    @Test
    fun `duration and count formatting helpers format accurately`() {
        assertEquals("01:05", formatDuration(65L))
        assertEquals("00:09", formatDuration(9L))
        assertEquals("1.5K", formatCountUrdu(1500L))
        assertEquals("42", formatCountUrdu(42L))
        assertTrue(PakTubeCategories.allCategories.contains("تعلیم"))
    }

    @Test
    fun `splash screen renders only original logo and PakToYou text without tagline`() {
        composeTestRule.setContent {
            PakTubeTheme(rtl = true) {
                PakToYouSplashScreen()
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("PakToYou").assertIsDisplayed()
        composeTestRule.onNodeWithText("Video Sharing Platform", substring = true).assertDoesNotExist()
    }

    @Test
    fun `app transitions from splash directly to HomeScreen without mandatory sign-in screen`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ensureFirebaseInitialized(context)
        val auth = FirebaseAuth.getInstance()
        composeTestRule.setContent {
            PakTubeTheme(rtl = true) {
                PakTubeApp(auth = auth, showSplashOnLaunch = false)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_brand_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("PakTube").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_search_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_notifications_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_menu_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_chip_سب").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_chip_شارٹس").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_chip_پوسٹس").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_chip_سبسکرپشنز").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_shorts").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_create").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_subscriptions").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_profile").assertIsDisplayed()
    }

    @Test
    fun `on-demand account sign-in card shows Google account selector button`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ensureFirebaseInitialized(context)
        composeTestRule.setContent {
            PakTubeTheme(rtl = true) {
                AccountSignInPromptCard(reason = "چینل بنانے کے لیے گوگل اکاؤنٹ منتخب کریں")
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
    }

    @Test
    fun `short preview card renders title and channel stats`() {
        val sampleShort = VideoItem(
            id = "short_1",
            title = "میری شارٹ",
            channelName = "میرا چینل",
            shortFlag = true,
            likeCount = 12L
        )
        composeTestRule.setContent {
            PakTubeTheme(rtl = true) {
                ShortPreviewCard(video = sampleShort, onClick = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("short_card_short_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("میری شارٹ").assertIsDisplayed()
    }
}
