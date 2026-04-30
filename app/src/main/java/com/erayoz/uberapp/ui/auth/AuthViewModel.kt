package com.erayoz.uberapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erayoz.uberapp.data.model.User
import com.erayoz.uberapp.data.repository.AuthRepository
import com.erayoz.uberapp.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isLoginMode: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val userRole: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        if (authRepository.isSignedIn()) {
            val uid = authRepository.getCurrentUserId() ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                userRepository.getUserProfile(uid).onSuccess { user ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            userRole = user?.role.takeIf { role -> role?.isNotEmpty() == true }
                        )
                    }
                }.onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onDisplayNameChanged(name: String) {
        _uiState.update { it.copy(displayName = name, errorMessage = null) }
    }

    fun toggleMode() {
        _uiState.update { it.copy(isLoginMode = !it.isLoginMode, errorMessage = null) }
    }

    fun submitAuth() {
        val currentState = _uiState.value
        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password cannot be empty") }
            return
        }
        if (!currentState.isLoginMode && currentState.displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Display name is required for sign up") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (currentState.isLoginMode) {
                signIn(currentState.email, currentState.password)
            } else {
                signUp(currentState.email, currentState.password, currentState.displayName)
            }
        }
    }

    private suspend fun signIn(email: String, password: String) {
        authRepository.signIn(email, password).onSuccess { firebaseUser ->
            userRepository.getUserProfile(firebaseUser.uid).onSuccess { user ->
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        userRole = user?.role.takeIf { role -> role?.isNotEmpty() == true }
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }.onFailure { e ->
            _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    private suspend fun signUp(email: String, password: String, displayName: String) {
        authRepository.signUp(email, password).onSuccess { firebaseUser ->
            val newUser = User(
                id = firebaseUser.uid,
                email = email,
                displayName = displayName,
                createdAt = System.currentTimeMillis()
            )
            userRepository.createUserProfile(newUser).onSuccess {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        userRole = null
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }.onFailure { e ->
            _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
