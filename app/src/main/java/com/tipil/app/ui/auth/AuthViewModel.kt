package com.tipil.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isSignedIn: Boolean = false,
    val isLoading: Boolean = false,
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // Check if user is already signed in
        firebaseAuth.currentUser?.let { user ->
            _uiState.value = AuthUiState(
                isSignedIn = true,
                userId = user.uid,
                displayName = user.displayName ?: "",
                photoUrl = user.photoUrl?.toString() ?: ""
            )
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = firebaseAuth.signInWithCredential(credential).await()
                val user = result.user
                if (user != null) {
                    _uiState.value = AuthUiState(
                        isSignedIn = true,
                        userId = user.uid,
                        displayName = user.displayName ?: "",
                        photoUrl = user.photoUrl?.toString() ?: ""
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Sign-in failed. Please try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Sign-in failed"
                )
            }
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
        _uiState.value = AuthUiState()
    }

    fun onSignInError(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
