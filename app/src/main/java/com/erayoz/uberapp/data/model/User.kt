package com.erayoz.uberapp.data.model

data class User(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "",
    val createdAt: Long = 0L
)
