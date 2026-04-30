package com.erayoz.uberapp.ui.role

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RoleSelectionViewModel @Inject constructor() : ViewModel() {
    fun selectPassenger() = Unit

    fun selectDriver() = Unit
}
