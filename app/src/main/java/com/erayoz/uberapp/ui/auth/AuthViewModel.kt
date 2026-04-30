package com.erayoz.uberapp.ui.auth

import androidx.lifecycle.ViewModel
import com.erayoz.uberapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    val title: String = if (authRepository.isSignedIn()) "Welcome back" else "Authentication"
}
