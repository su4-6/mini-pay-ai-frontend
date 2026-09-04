package com.minipay.mobile.chat

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "friend_search_history",
    primaryKeys = ["ownerUserId", "query"],
    indices = [Index(value = ["ownerUserId", "searchedAt"])]
)
data class SearchHistoryEntity(
    val ownerUserId: String,
    val query: String,
    val searchedAt: Long
)
