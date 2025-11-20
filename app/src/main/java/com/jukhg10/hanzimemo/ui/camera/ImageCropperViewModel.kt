package com.jukhg10.hanzimemo.ui.camera

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel para manejar la selección y cropping de imágenes
 */
data class ImageCropperUiState(
    val originalImageUri: Uri? = null,
    val croppedImagePath: String? = null,
    val isProcessing: Boolean = false,
    val error: String? = null
)

class ImageCropperViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ImageCropperUiState())
    val uiState: StateFlow<ImageCropperUiState> = _uiState.asStateFlow()

    fun setOriginalImageUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(originalImageUri = uri)
    }

    fun setCroppedImagePath(path: String) {
        _uiState.value = _uiState.value.copy(croppedImagePath = path)
    }

    fun setError(message: String?) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    fun setProcessing(isProcessing: Boolean) {
        _uiState.value = _uiState.value.copy(isProcessing = isProcessing)
    }

    fun clear() {
        _uiState.value = ImageCropperUiState()
    }
}
