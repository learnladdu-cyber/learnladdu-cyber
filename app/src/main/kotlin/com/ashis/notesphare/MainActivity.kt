package com.ashis.notesphare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashis.notesphare.ui.NoteSphereRoot
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val openNoteId = MutableStateFlow<Long?>(null)
    private val authCallbackUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)
        handleAuthIntent(intent)
        val container = (application as NoteSphereApp).container
        setContent {
            val pendingNoteId by openNoteId.collectAsStateWithLifecycle()
            val pendingAuthUri by authCallbackUri.collectAsStateWithLifecycle()
            NoteSphereRoot(
                container = container,
                pendingNoteId = pendingNoteId,
                onPendingNoteConsumed = { openNoteId.value = null },
                authCallbackUri = pendingAuthUri,
                onAuthCallbackConsumed = { authCallbackUri.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val noteId = intent?.getLongExtra(EXTRA_NOTE_ID, -1L) ?: -1L
        if (noteId > 0L && intent != null) {
            openNoteId.value = noteId
            intent.removeExtra(EXTRA_NOTE_ID)
        }
    }

    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "notesphare" && data.host == "auth-callback") {
            authCallbackUri.value = data
            intent.data = null
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "com.ashis.notesphare.extra.NOTE_ID"
    }
}
