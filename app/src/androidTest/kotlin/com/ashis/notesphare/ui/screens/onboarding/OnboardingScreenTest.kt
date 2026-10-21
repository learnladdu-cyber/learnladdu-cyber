package com.ashis.notesphare.ui.screens.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ashis.notesphare.domain.ThemeMode
import com.ashis.notesphare.ui.theme.NoteSphereTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingCanBeCompleted() {
        var completed = false
        composeRule.setContent {
            NoteSphereTheme(themeMode = ThemeMode.LIGHT) {
                OnboardingScreen(onComplete = { completed = true })
            }
        }

        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Get started").performClick()

        composeRule.runOnIdle { assertTrue(completed) }
    }
}
