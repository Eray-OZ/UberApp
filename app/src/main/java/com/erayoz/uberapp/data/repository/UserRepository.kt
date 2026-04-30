package com.erayoz.uberapp.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun usersCollectionPath(): String = firestore.collection("users").path
}
