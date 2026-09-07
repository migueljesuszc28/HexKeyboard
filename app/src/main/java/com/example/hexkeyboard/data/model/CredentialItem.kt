package com.example.hexkeyboard.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CredentialItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String,
    val password: String,
    val timestamp: Long = System.currentTimeMillis()
)
