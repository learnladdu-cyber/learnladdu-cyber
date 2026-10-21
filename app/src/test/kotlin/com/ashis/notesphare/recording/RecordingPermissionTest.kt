package com.ashis.notesphare.recording

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingPermissionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun microphonePermissionIsDetectedAsDenied() {
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.RECORD_AUDIO)

        assertFalse(hasMicrophonePermission(context))
    }

    @Test
    fun microphonePermissionIsDetectedAsGranted() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.RECORD_AUDIO)

        assertTrue(hasMicrophonePermission(context))
    }
}
