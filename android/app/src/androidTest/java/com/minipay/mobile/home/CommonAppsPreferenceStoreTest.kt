package com.minipay.mobile.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommonAppsPreferenceStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences by lazy { context.getSharedPreferences("common_apps_test", Context.MODE_PRIVATE) }
    private val store by lazy { CommonAppsPreferenceStore(preferences) }

    @Before
    fun clearBefore() {
        preferences.edit().clear().commit()
    }

    @After
    fun clearAfter() {
        preferences.edit().clear().commit()
    }

    @Test
    fun uninitializedUsersReceiveDefaultsAndWritesStayUserScoped() {
        assertEquals(defaultCommonAppIds, store.read("user-a").map { it.id })

        assertTrue(store.write("user-a", resolveCommonApps(listOf("scan", "bills"))))
        assertEquals(listOf("scan", "bills"), store.read("user-a").map { it.id })
        assertEquals(defaultCommonAppIds, store.read("user-b").map { it.id })
    }

    @Test
    fun intentionalEmptySelectionPersistsAndAnonymousWritesAreRejected() {
        assertTrue(store.write("user-a", emptyList()))
        assertTrue(store.read("user-a").isEmpty())
        assertFalse(store.write(null, resolveCommonApps(listOf("scan"))))
        assertEquals(defaultCommonAppIds, store.read(null).map { it.id })
    }
}
