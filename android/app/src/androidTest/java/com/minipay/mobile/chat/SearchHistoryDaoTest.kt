package com.minipay.mobile.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SearchHistoryDaoTest {
    private lateinit var database: ChatDatabase
    private lateinit var dao: ChatDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.chatDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun historyIsAccountScopedDeduplicatedAndLimited() = runBlocking {
        repeat(21) { index ->
            dao.recordSearchHistory("user-a", "query-$index", index.toLong())
        }
        dao.recordSearchHistory("user-a", "query-10", 100L)
        dao.recordSearchHistory("user-b", "private-query", 200L)

        val firstAccount = dao.observeSearchHistory("user-a").first()
        assertEquals(20, firstAccount.size)
        assertEquals("query-10", firstAccount.first())
        assertEquals(listOf("private-query"), dao.observeSearchHistory("user-b").first())

        dao.clearSearchHistory("user-a")
        assertEquals(emptyList<String>(), dao.observeSearchHistory("user-a").first())
        assertEquals(listOf("private-query"), dao.observeSearchHistory("user-b").first())
    }
}
