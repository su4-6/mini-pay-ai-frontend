package com.minipay.mobile.ui.home

import com.minipay.mobile.finance.FinanceDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppServicesTest {
    @Test
    fun searchMatchesNamesAndAliases() {
        assertEquals("生活缴费", searchServices("电费").single().name)
        assertEquals("添加朋友", searchServices("加好友").single().name)
    }

    @Test
    fun availableServicesHaveConcreteDestinations() {
        val scan = searchServices("扫码").single()
        assertTrue(scan.available)
        assertEquals(ServiceDestination.Finance(FinanceDestination.SCAN), scan.destination)
        assertFalse(searchServices("神券").single().available)
    }

    @Test
    fun billSearchOpensAllBillsInsteadOfBalanceDetails() {
        val bills = searchServices("账单").single()

        assertEquals(
            ServiceDestination.Finance(FinanceDestination.ALL_BILLS),
            bills.destination
        )
    }
}
