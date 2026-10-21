package com.ashis.notesphare.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashis.notesphare.auth.AuthRepository
import com.ashis.notesphare.data.local.ConflictDao
import com.ashis.notesphare.data.local.OutboxDao
import com.ashis.notesphare.data.local.SyncConflictEntity
import com.ashis.notesphare.sync.ConflictResolver
import com.ashis.notesphare.sync.LocalSyncWriter
import com.ashis.notesphare.sync.SyncCoordinator
import com.ashis.notesphare.sync.SyncReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CloudUiState(
    val isSyncing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val lastReport: SyncReport? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val syncCoordinator: SyncCoordinator,
    private val syncWriter: LocalSyncWriter,
    private val conflictResolver: ConflictResolver,
    outboxDao: OutboxDao,
    conflictDao: ConflictDao
) : ViewModel() {

    val authState = authRepository.state

    private val _cloud = MutableStateFlow(CloudUiState())
    val cloud: StateFlow<CloudUiState> = _cloud.asStateFlow()

    val pendingCount: StateFlow<Int> = authState
        .flatMapLatest { state ->
            state.userId?.let(outboxDao::observeCount) ?: flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val conflictCount: StateFlow<Int> = authState
        .flatMapLatest { state ->
            state.userId?.let(conflictDao::observeCount) ?: flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val conflicts: StateFlow<List<SyncConflictEntity>> = authState
        .flatMapLatest { state ->
            state.userId?.let(conflictDao::observeForOwner) ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun signIn(email: String, password: String) {
        viewModelScope.launch { authRepository.signIn(email, password) }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch { authRepository.signUp(email, password) }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch { authRepository.sendPasswordReset(email) }
    }

    fun resendVerification(email: String) {
        viewModelScope.launch { authRepository.resendVerification(email) }
    }

    fun updatePassword(password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (authRepository.updatePassword(password)) onSuccess()
        }
    }

    fun clearMessages() {
        authRepository.clearError()
        authRepository.clearMessage()
        _cloud.value = _cloud.value.copy(message = null, error = null)
    }

    fun syncNow() {
        if (_cloud.value.isSyncing) return
        viewModelScope.launch {
            _cloud.value = _cloud.value.copy(isSyncing = true, error = null, message = null)
            val report = runCatching { syncCoordinator.syncNow() }.getOrElse {
                _cloud.value = _cloud.value.copy(
                    isSyncing = false,
                    error = it.message ?: "Cloud sync failed."
                )
                return@launch
            }
            _cloud.value = CloudUiState(
                isSyncing = false,
                message = when {
                    !report.signedIn -> "Sign in to sync."
                    report.failed > 0 -> report.message
                    report.pushed + report.pulled == 0 -> "Everything is up to date."
                    else -> "Synced ${report.pushed} upload(s) and ${report.pulled} change(s)."
                },
                error = if (report.failed > 0) report.message else null,
                lastReport = report
            )
        }
    }

    fun linkLocalData() {
        if (_cloud.value.isSyncing) return
        viewModelScope.launch {
            _cloud.value = _cloud.value.copy(isSyncing = true, error = null, message = null)
            val count = runCatching { syncWriter.claimLocalData() }.getOrElse {
                _cloud.value = _cloud.value.copy(
                    isSyncing = false,
                    error = it.message ?: "Local data could not be linked."
                )
                return@launch
            }
            _cloud.value = _cloud.value.copy(
                isSyncing = false,
                message = "$count local item(s) linked to this account."
            )
            syncNow()
        }
    }

    fun keepLocal(conflict: SyncConflictEntity) {
        viewModelScope.launch {
            conflictResolver.keepLocal(conflict)
            syncNow()
        }
    }

    fun keepRemote(conflict: SyncConflictEntity) {
        viewModelScope.launch {
            conflictResolver.keepRemote(conflict)
            syncNow()
        }
    }

    fun dismissConflict(conflict: SyncConflictEntity) {
        viewModelScope.launch { conflictResolver.dismiss(conflict) }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _cloud.value = CloudUiState(message = "Signed out. Local data remains on this device.")
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            if (authRepository.deleteAccount()) {
                _cloud.value = CloudUiState(
                    message = "Cloud account deleted. Local notes and files remain on this device."
                )
            }
        }
    }
}
