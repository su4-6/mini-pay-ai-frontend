package com.minipay.mobile.chat

import androidx.room.Entity

@Entity(tableName = "contacts", primaryKeys = ["ownerUserId", "id"])
data class ContactEntity(
    val ownerUserId: String,
    val id: String,
    val name: String,
    val firstLetter: String,
    val avatarColorIndex: Int,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null
)
