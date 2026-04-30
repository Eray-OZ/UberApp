package com.erayoz.uberapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    fun isSignedIn(): Boolean = firebaseAuth.currentUser != null
}
