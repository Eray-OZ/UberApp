package com.erayoz.uberapp.ui.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erayoz.uberapp.data.repository.AuthRepository
import com.erayoz.uberapp.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoleSelectionUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedRole: String? = null
)

@HiltViewModel
class RoleSelectionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoleSelectionUiState())
    val uiState: StateFlow<RoleSelectionUiState> = _uiState.asStateFlow()

    fun selectRole(role: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            userRepository.updateUserRole(uid, role).onSuccess {
                _uiState.update { it.copy(isLoading = false, selectedRole = role) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
