package com.ashis.notesphare

import android.app.Application
import android.content.Context
import com.ashis.notesphare.auth.AuthRepository
import com.ashis.notesphare.auth.DataStoreAuthSessionStore
import com.ashis.notesphare.auth.SupabaseConfigProvider
import com.ashis.notesphare.data.local.NoteSphereDatabase
import com.ashis.notesphare.data.repository.FolderRepository
import com.ashis.notesphare.data.repository.NoteRepository
import com.ashis.notesphare.data.repository.AttachmentRepository
import com.ashis.notesphare.data.repository.RecordingRepository
import com.ashis.notesphare.data.repository.SettingsRepository
import com.ashis.notesphare.data.repository.SettingsTranscriptionEngineProvider
import com.ashis.notesphare.data.repository.TagRepository
import com.ashis.notesphare.data.repository.TranscriptionRepository
import com.ashis.notesphare.files.FileStorageManager
import com.ashis.notesphare.notifications.NotificationHelper
import com.ashis.notesphare.reminders.AlarmReminderScheduler
import com.ashis.notesphare.recording.AndroidAudioMetadataReader
import com.ashis.notesphare.reminders.ReminderScheduler
import com.ashis.notesphare.sync.AttachmentSyncManager
import com.ashis.notesphare.sync.ConflictResolver
import com.ashis.notesphare.sync.LocalSyncApplier
import com.ashis.notesphare.sync.LocalSyncWriter
import com.ashis.notesphare.sync.SupabaseStorageRemote
import com.ashis.notesphare.sync.SupabaseSyncRemote
import com.ashis.notesphare.sync.SyncCoordinator
import com.ashis.notesphare.sync.SyncEngine
import com.ashis.notesphare.sync.SyncScheduler
import com.ashis.notesphare.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NoteSphereApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
        container.syncWriter.onQueued = { SyncScheduler.requestImmediate(this) }
        SyncScheduler.ensurePeriodic(this)
        container.applicationScope.launch {
            runCatching { container.attachmentRepository.cleanupOrphans() }
        }
        container.applicationScope.launch {
            container.authRepository.state
                .map { it.isSignedIn }
                .distinctUntilChanged()
                .collect { signedIn ->
                    if (signedIn) SyncScheduler.requestImmediate(this@NoteSphereApp)
                }
        }
    }
}

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: NoteSphereDatabase = NoteSphereDatabase.build(appContext)

    val authRepository = AuthRepository(
        config = SupabaseConfigProvider.fromBuild(),
        store = DataStoreAuthSessionStore(appContext),
        scope = applicationScope
    )

    val syncWriter = LocalSyncWriter(
        database = database,
        noteDao = database.noteDao(),
        folderDao = database.folderDao(),
        tagDao = database.tagDao(),
        noteTagDao = database.noteTagDao(),
        checklistDao = database.checklistDao(),
        attachmentDao = database.attachmentDao(),
        transcriptionDao = database.transcriptionDao(),
        outboxDao = database.outboxDao(),
        currentUserId = { authRepository.currentSession()?.userId },
        ownerScope = authRepository.state.map { it.userId }.distinctUntilChanged()
    )

    private val syncRemote = SupabaseSyncRemote(SupabaseConfigProvider.fromBuild())

    private val syncApplier = LocalSyncApplier(
        noteDao = database.noteDao(),
        folderDao = database.folderDao(),
        tagDao = database.tagDao(),
        noteTagDao = database.noteTagDao(),
        checklistDao = database.checklistDao(),
        attachmentDao = database.attachmentDao(),
        transcriptionDao = database.transcriptionDao(),
        outboxDao = database.outboxDao(),
        conflictDao = database.conflictDao()
    )

    val conflictResolver = ConflictResolver(
        conflictDao = database.conflictDao(),
        outboxDao = database.outboxDao(),
        applier = syncApplier
    )

    val syncEngine = SyncEngine(
        database = database,
        outboxDao = database.outboxDao(),
        conflictDao = database.conflictDao(),
        cursorDao = database.syncCursorDao(),
        remote = syncRemote,
        applier = syncApplier,
        tokenProvider = { runCatching { authRepository.validAccessToken() }.getOrNull() },
        ownerProvider = { authRepository.currentSession()?.userId }
    )

    val reminderScheduler: ReminderScheduler = AlarmReminderScheduler(appContext)

    val notificationHelper = NotificationHelper(appContext)

    val noteRepository = NoteRepository(
        noteDao = database.noteDao(),
        checklistDao = database.checklistDao(),
        reminderScheduler = reminderScheduler,
        sync = syncWriter
    )

    val folderRepository = FolderRepository(database.folderDao(), syncWriter)

    val tagRepository = TagRepository(
        tagDao = database.tagDao(),
        noteTagDao = database.noteTagDao(),
        noteDao = database.noteDao(),
        sync = syncWriter
    )

    val settingsRepository = SettingsRepository(appContext)

    val storageManager = FileStorageManager(appContext)

    val attachmentSyncManager = AttachmentSyncManager(
        attachmentDao = database.attachmentDao(),
        outboxDao = database.outboxDao(),
        conflictDao = database.conflictDao(),
        storage = storageManager,
        remote = SupabaseStorageRemote(SupabaseConfigProvider.fromBuild()),
        writer = syncWriter,
        bucket = SupabaseConfigProvider.fromBuild().storageBucket
    )

    val syncCoordinator = SyncCoordinator(
        engine = syncEngine,
        attachments = attachmentSyncManager,
        attachmentDao = database.attachmentDao(),
        tokenProvider = { runCatching { authRepository.validAccessToken() }.getOrNull() },
        ownerProvider = { authRepository.currentSession()?.userId },
        refreshReminders = { noteRepository.rescheduleUpcomingReminders() }
    )

    val attachmentRepository = AttachmentRepository(
        attachmentDao = database.attachmentDao(),
        recordingSessionDao = database.recordingSessionDao(),
        noteDao = database.noteDao(),
        storage = storageManager,
        sync = syncWriter
    )

    val recordingRepository = RecordingRepository(
        recordingSessionDao = database.recordingSessionDao(),
        attachmentDao = database.attachmentDao(),
        noteDao = database.noteDao(),
        storage = storageManager,
        audioMetadataReader = AndroidAudioMetadataReader(),
        sync = syncWriter
    )

    val transcriptionRepository = TranscriptionRepository(
        attachmentDao = database.attachmentDao(),
        transcriptionDao = database.transcriptionDao(),
        storage = storageManager,
        engineProvider = SettingsTranscriptionEngineProvider(settingsRepository),
        sync = syncWriter
    )
}
