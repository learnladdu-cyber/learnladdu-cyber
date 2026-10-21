package com.ashis.notesphare.ui.screens.transcription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.TranscriptionEntity
import com.ashis.notesphare.data.repository.AttachmentRepository
import com.ashis.notesphare.data.repository.SettingsRepository
import com.ashis.notesphare.data.repository.TranscriptionRepository
import com.ashis.notesphare.domain.AttachmentKind
import com.ashis.notesphare.domain.TranscriptionLanguage
import com.ashis.notesphare.domain.TranscriptionStatus
import com.ashis.notesphare.transcription.TranscriptionConsentRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TranscriptionUiState(
    val isLoading: Boolean = true,
    val attachment: AttachmentEntity? = null,
    val transcript: TranscriptionEntity? = null,
    val language: TranscriptionLanguage = TranscriptionLanguage.ENGLISH,
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    val hasConfig: Boolean = false,
    val configDirty: Boolean = false,
    val textDraft: String = "",
    val draftDirty: Boolean = false,
    val isTranscribing: Boolean = false,
    val isSaving: Boolean = false,
    val progress: Float = 0f,
    val showConsent: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val onDeviceAvailable: Boolean = false
) {
    val status: TranscriptionStatus
        get() = TranscriptionStatus.fromStorage(transcript?.status)
}

class TranscriptionViewModel(
    private val attachmentId: Long,
    private val attachmentRepository: AttachmentRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TranscriptionUiState())
    val state: StateFlow<TranscriptionUiState> = _state.asStateFlow()

    private var transcriptionJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _state.update {
                it.copy(
                    language = settings.transcriptionLanguage,
                    endpoint = settings.transcriptionEndpoint,
                    apiKey = settings.transcriptionApiKey,
                    model = settings.transcriptionModel,
                    hasConfig = settings.hasTranscriptionConfig
                )
            }
            val attachment = attachmentRepository.getAttachment(attachmentId)
            if (attachment == null || attachment.kind != AttachmentKind.AUDIO.name) {
                _state.update {
                    it.copy(isLoading = false, error = "This audio recording is unavailable.")
                }
                return@launch
            }
            _state.update { it.copy(isLoading = false, attachment = attachment) }
        }
        viewModelScope.launch {
            transcriptionRepository.observeForAttachment(attachmentId).collect { transcript ->
                _state.update { current ->
                    val draft = if (current.draftDirty || current.isTranscribing) {
                        current.textDraft
                    } else {
                        transcript?.text.orEmpty()
                    }
                    current.copy(
                        transcript = transcript,
                        textDraft = draft,
                        draftDirty = if (transcript?.text.orEmpty() == draft) {
                            current.draftDirty
                        } else {
                            current.draftDirty
                        }
                    )
                }
            }
        }
    }

    fun onLanguageChange(language: TranscriptionLanguage) {
        _state.update { it.copy(language = language, configDirty = true) }
        viewModelScope.launch {
            settingsRepository.setTranscriptionLanguage(language)
        }
    }

    fun onEndpointChange(value: String) {
        _state.update { it.copy(endpoint = value, configDirty = true) }
    }

    fun onApiKeyChange(value: String) {
        _state.update { it.copy(apiKey = value, configDirty = true) }
    }

    fun onModelChange(value: String) {
        _state.update { it.copy(model = value, configDirty = true) }
    }

    fun saveConfiguration() {
        viewModelScope.launch {
            persistConfiguration()
            _state.update {
                it.copy(
                    hasConfig = it.apiKey.isNotBlank() && it.endpoint.isNotBlank(),
                    configDirty = false,
                    message = "Transcription settings saved"
                )
            }
        }
    }

    fun startTranscription() {
        val current = _state.value
        if (current.isTranscribing) return
        if (current.attachment == null) {
            _state.update { it.copy(error = "This recording is unavailable.") }
            return
        }
        if (current.apiKey.isBlank() || current.endpoint.isBlank()) {
            _state.update {
                it.copy(error = "Add a transcription endpoint and API key first.")
            }
            return
        }
        _state.update { it.copy(showConsent = true, error = null) }
    }

    fun confirmRemoteConsent() {
        _state.update { it.copy(showConsent = false) }
        launchTranscription()
    }

    fun dismissConsent() {
        _state.update { it.copy(showConsent = false) }
    }

    fun retry() {
        if (_state.value.apiKey.isBlank() || _state.value.endpoint.isBlank()) {
            _state.update {
                it.copy(error = "Add a transcription endpoint and API key first.")
            }
            return
        }
        _state.update { it.copy(showConsent = true, error = null) }
    }

    private fun launchTranscription() {
        transcriptionJob?.cancel()
        transcriptionJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isTranscribing = true,
                    progress = 0f,
                    error = null,
                    message = null
                )
            }
            try {
                persistConfiguration()
                val result = transcriptionRepository.transcribe(
                    attachmentId = attachmentId,
                    language = _state.value.language,
                    remoteConsent = true
                ) { progress ->
                    _state.update { it.copy(progress = progress.coerceIn(0f, 1f)) }
                }
                _state.update {
                    it.copy(
                        isTranscribing = false,
                        progress = 1f,
                        textDraft = result.text,
                        draftDirty = false,
                        message = "Transcription complete. Review and edit the text, then save."
                    )
                }
            } catch (cancellation: CancellationException) {
                _state.update {
                    it.copy(
                        isTranscribing = false,
                        progress = 0f,
                        message = "Transcription cancelled"
                    )
                }
                throw cancellation
            } catch (consent: TranscriptionConsentRequiredException) {
                _state.update {
                    it.copy(isTranscribing = false, showConsent = true, progress = 0f)
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        isTranscribing = false,
                        progress = 0f,
                        error = throwable.message ?: "Transcription failed"
                    )
                }
            }
        }
    }

    fun cancelTranscription() {
        transcriptionJob?.cancel()
        transcriptionJob = null
        _state.update { it.copy(isTranscribing = false, progress = 0f) }
    }

    fun onTextChange(value: String) {
        _state.update { it.copy(textDraft = value, draftDirty = true) }
    }

    fun saveTranscript() {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                transcriptionRepository.saveEditedText(attachmentId, _state.value.textDraft)
            }.onSuccess { saved ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        draftDirty = false,
                        message = if (saved) "Transcription saved" else "Could not save transcription"
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = throwable.message ?: "Could not save transcription"
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private suspend fun persistConfiguration() {
        val current = _state.value
        settingsRepository.setTranscriptionEndpoint(current.endpoint)
        settingsRepository.setTranscriptionApiKey(current.apiKey)
        settingsRepository.setTranscriptionModel(current.model)
        settingsRepository.setTranscriptionLanguage(current.language)
        _state.update {
            it.copy(
                hasConfig = it.apiKey.isNotBlank() && it.endpoint.isNotBlank(),
                configDirty = false
            )
        }
    }

    override fun onCleared() {
        transcriptionJob?.cancel()
        super.onCleared()
    }
}
