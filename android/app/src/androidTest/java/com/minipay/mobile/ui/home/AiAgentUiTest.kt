package com.minipay.mobile.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.minipay.mobile.ai.AiHomeMessage
import com.minipay.mobile.ai.AiHomeMessageRole
import com.minipay.mobile.ai.AiHomeUiState
import com.minipay.mobile.ai.AiPaymentPrompt
import com.minipay.mobile.finance.FinanceDestination
import com.minipay.mobile.ui.theme.MilingTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AiAgentUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun foodEntryCardUsesFixedTrustedDestination() {
        var action: MilingHomeAction? = null
        compose.setContent {
            MilingTheme {
                FoodEntryCard(onAction = { action = it })
            }
        }

        compose.onNodeWithContentDescription("意向外卖 Logo").assertIsDisplayed()
        compose.onNodeWithText("意向外卖").assertIsDisplayed()
        compose.onNodeWithText("外卖点餐、到店自取").assertIsDisplayed()
        compose.onNodeWithText("进入意向外卖").performClick()
        compose.runOnIdle { assertEquals(MilingHomeAction.OpenFood, action) }
    }

    @Test
    fun transferCardRendersAuthoritativeAmountAndRequiresNativeConfirmation() {
        var requested = false
        val message = AiHomeMessage(
            id = "message-1",
            runId = "run-1",
            role = AiHomeMessageRole.ASSISTANT,
            text = "请核对收款人和金额",
            cardType = "payment.transfer-intent",
            cardPayload = Json.parseToJsonElement(
                """{"recipient":{"nickname":"小李","phoneMasked":"138****0000"},"amountCent":5000}"""
            ).jsonObject
        )
        val userMessage = AiHomeMessage(
            id = "user-message-1",
            runId = "run-1",
            role = AiHomeMessageRole.USER,
            text = "转给13838517417 50元"
        )
        compose.setContent {
            MilingTheme {
                AiConversationPane(
                    state = AiHomeUiState(loading = false, messages = listOf(userMessage, message)),
                    onAction = { requested = it is MilingHomeAction.RequestPayment }
                )
            }
        }

        compose.onNodeWithText("¥50.00").assertIsDisplayed()
        compose.onNodeWithText("转给13838517417 50元").assertIsDisplayed()
        compose.onNodeWithText("138****0000").assertIsDisplayed()
        compose.onNodeWithText("确认并验证支付密码").performClick()
        assertTrue(requested)
    }

    @Test
    fun internalTaskMetadataIsNotRenderedAsAUserFacingCard() {
        val message = AiHomeMessage(
            id = "message-2",
            runId = "run-2",
            role = AiHomeMessageRole.ASSISTANT,
            text = "请告诉我收款人和转账金额。",
            cardType = "agent.missing-slots",
            cardPayload = Json.parseToJsonElement(
                """{"taskType":"transfer","missingSlots":{"recipient":"请输入收款人"}}"""
            ).jsonObject
        )
        compose.setContent {
            MilingTheme {
                AiConversationPane(
                    state = AiHomeUiState(loading = false, messages = listOf(message)),
                    onAction = {}
                )
            }
        }

        compose.onNodeWithText("请告诉我收款人和转账金额。").assertIsDisplayed()
        compose.onNodeWithText("任务信息").assertDoesNotExist()
        compose.onNodeWithText("taskType").assertDoesNotExist()
    }

    @Test
    fun paymentDialogSubmitsOnlyAfterSixSecureKeypadDigits() {
        var confirmedPassword: String? = null
        compose.setContent {
            MilingTheme {
                AiPaymentConfirmationDialog(
                    prompt = AiPaymentPrompt(
                        "message-1",
                        AiPaymentPrompt.Type.TRANSFER,
                        "确认转账",
                        "小李",
                        5000
                    ),
                    submitting = false,
                    onDismiss = {},
                    onConfirm = { confirmedPassword = it }
                )
            }
        }

        (1..5).forEach { digit ->
            compose.onNodeWithContentDescription("数字 $digit").performClick()
        }
        compose.runOnIdle { assertEquals(null, confirmedPassword) }
        compose.onNodeWithContentDescription("数字 6").performClick()
        compose.runOnIdle { assertEquals("123456", confirmedPassword) }
    }

    @Test
    fun duplicateFriendCandidatesRequireExplicitSelection() {
        var selected: MilingHomeAction.ContinueCard? = null
        val message = AiHomeMessage(
            id = "message-contact",
            runId = "run-contact",
            role = AiHomeMessageRole.ASSISTANT,
            text = "找到多位匹配的好友，请选择收款人。",
            cardType = "agent.contact-selection",
            cardPayload = Json.parseToJsonElement(
                """{"items":[{"recipientUserId":"0198f200-0000-7000-8000-000000000005","nickname":"张三","phoneMasked":"138****0000","legalNameMasked":"张*"},{"recipientUserId":"0198f200-0000-7000-8000-000000000006","nickname":"老张","phoneMasked":"139****0000","legalNameMasked":"张*"}]}"""
            ).jsonObject
        )
        compose.setContent {
            MilingTheme {
                AiConversationPane(
                    state = AiHomeUiState(loading = false, messages = listOf(message)),
                    onAction = { if (it is MilingHomeAction.ContinueCard) selected = it }
                )
            }
        }

        compose.onNodeWithText("张三").performClick()
        assertEquals("SELECT_TRANSFER_RECIPIENT", selected?.request?.action)
        assertEquals("0198f200-0000-7000-8000-000000000005", selected?.request?.recipientUserId)
    }

    @Test
    fun walletCardShowsLocalizedFinancialDetailsAndRoutesToWallet() {
        var action: MilingHomeAction? = null
        val message = AiHomeMessage(
            id = "wallet-message",
            role = AiHomeMessageRole.ASSISTANT,
            text = "这是你的 MiniPay 沙箱钱包余额。",
            cardType = "wallet.summary",
            cardPayload = Json.parseToJsonElement(
                """{
                    "availableAmountCent":994000,
                    "frozenAmountCent":1200,
                    "annualOutflowRemainingCent":800000,
                    "status":"ACTIVE",
                    "sandboxNotice":"MiniPay 沙箱资产，仅用于功能体验。",
                    "recentBills":[
                      {"billId":"1","businessType":"TRANSFER","direction":"OUT","amountCent":2000,"counterpartyDisplay":"小明","status":"SUCCEEDED","occurredAt":"2026-08-09T01:16:00Z"},
                      {"billId":"2","businessType":"RECHARGE","direction":"IN","amountCent":5000,"counterpartyDisplay":"余额充值","status":"SUCCEEDED","occurredAt":"2026-08-08T01:16:00Z"},
                      {"billId":"3","businessType":"PAYMENT","direction":"OUT","amountCent":800,"counterpartyDisplay":"沙箱商家","status":"PAID","occurredAt":"2026-08-07T01:16:00Z"},
                      {"billId":"4","businessType":"REFUND","direction":"IN","amountCent":100,"counterpartyDisplay":"不应显示的第四条","status":"SUCCEEDED","occurredAt":"2026-08-06T01:16:00Z"}
                    ]
                }"""
            ).jsonObject
        )
        compose.setContent {
            MilingTheme {
                AiConversationPane(
                    state = AiHomeUiState(loading = false, messages = listOf(message)),
                    onAction = { action = it }
                )
            }
        }

        compose.onNodeWithText("MiniPay 钱包").assertIsDisplayed()
        compose.onNodeWithText("正常").assertIsDisplayed()
        compose.onNodeWithText("冻结金额").assertIsDisplayed()
        compose.onNodeWithText("年度剩余额度").assertIsDisplayed()
        compose.onNodeWithText("不应显示的第四条").assertDoesNotExist()
        compose.onNodeWithText("钱包详情").performClick()
        assertEquals(MilingHomeAction.OpenFinance(FinanceDestination.WALLET), action)
    }

    @Test
    fun billCardLimitsRowsAndOpensAuthoritativeBillsPage() {
        var action: MilingHomeAction? = null
        val items = (1..4).joinToString(",") { index ->
            val profile = if (index == 1) {
                """, "counterpartyProfile":{"userId":"user-1","nickname":"张三","avatarUrl":"https://example.test/avatar.jpg","avatarUrlExpiresAt":"2099-08-09T01:16:00Z","legalNameMasked":"张*三"}"""
            } else ""
            """{"billId":"$index","businessType":"TRANSFER","direction":"OUT","amountCent":${index}00,"counterpartyDisplay":"收款人$index","status":"SUCCEEDED","occurredAt":"2026-08-0${index}T01:16:00Z"$profile}"""
        }
        val message = AiHomeMessage(
            id = "bill-message",
            role = AiHomeMessageRole.ASSISTANT,
            text = "这是 Wallet 返回的权威交易记录。",
            cardType = "wallet.bill-list",
            cardPayload = Json.parseToJsonElement(
                """{"from":"2026-08-01T00:00:00Z","to":"2026-08-09T00:00:00Z","total":4,"items":[$items]}"""
            ).jsonObject
        )
        compose.setContent {
            MilingTheme {
                AiConversationPane(
                    state = AiHomeUiState(loading = false, messages = listOf(message)),
                    onAction = { action = it }
                )
            }
        }

        compose.onNodeWithText("交易记录").assertIsDisplayed()
        compose.onNodeWithText("张三").assertIsDisplayed()
        compose.onNodeWithText("实名 张*三").assertIsDisplayed()
        compose.onNodeWithContentDescription("张三 头像").assertIsDisplayed()
        compose.onNodeWithText("收款人3").assertIsDisplayed()
        compose.onNodeWithText("收款人4").assertDoesNotExist()
        compose.onNodeWithText("查看全部账单").performClick()
        assertEquals(MilingHomeAction.OpenFinance(FinanceDestination.BILLS), action)
    }

    @Test
    fun memoryCandidateIsSavedOnlyAfterExplicitConfirmation() {
        var confirmed: MilingHomeAction.ContinueCard? = null
        val message = AiHomeMessage(
            id = "message-memory",
            runId = "run-memory",
            role = AiHomeMessageRole.ASSISTANT,
            text = "我识别到一条可能长期有用的信息，请确认是否保存。",
            cardType = "memory.confirmation",
            cardPayload = Json.parseToJsonElement(
                """{"candidateId":"0198f400-0000-7000-8000-000000000005","memoryType":"ALLERGEN_AVOIDANCE","displayValue":"对牛奶过敏"}"""
            ).jsonObject
        )
        compose.setContent {
            MilingTheme {
                AiConversationPane(
                    state = AiHomeUiState(loading = false, messages = listOf(message)),
                    onAction = { if (it is MilingHomeAction.ContinueCard) confirmed = it }
                )
            }
        }

        compose.onNodeWithText("保存到长期记忆").performClick()
        assertEquals("CONFIRM_MEMORY", confirmed?.request?.action)
        assertEquals("0198f400-0000-7000-8000-000000000005", confirmed?.request?.candidateId)
    }

    @Test
    fun adjacentMessagesInSameMinuteShareOneTimestamp() {
        val firstMinute = Instant.parse("2026-08-09T01:30:05Z")
        val messages = listOf(
            AiHomeMessage("first", role = AiHomeMessageRole.USER, text = "第一条", createdAt = firstMinute),
            AiHomeMessage(
                "second",
                role = AiHomeMessageRole.ASSISTANT,
                text = "第二条",
                createdAt = Instant.parse("2026-08-09T01:30:59Z")
            ),
            AiHomeMessage(
                "third",
                role = AiHomeMessageRole.USER,
                text = "第三条",
                createdAt = Instant.parse("2026-08-09T01:31:00Z")
            )
        )
        compose.setContent {
            MilingTheme {
                AiConversationPane(
                    state = AiHomeUiState(loading = false, messages = messages),
                    onAction = {}
                )
            }
        }

        compose.onNodeWithTag("ai_message_time_first").assertIsDisplayed()
        compose.onAllNodesWithTag("ai_message_time_second").assertCountEquals(0)
        compose.onNodeWithTag("ai_message_time_third").assertIsDisplayed()
    }

    @Test
    fun imeLayoutKeepsLatestMessageAboveComposer() {
        val messages = (1..12).map { index ->
            AiHomeMessage(
                id = "message-$index",
                role = if (index % 2 == 0) AiHomeMessageRole.ASSISTANT else AiHomeMessageRole.USER,
                text = "第 $index 条用于验证键盘布局的消息内容",
                createdAt = Instant.parse("2026-08-09T01:${index.toString().padStart(2, '0')}:00Z")
            )
        }
        compose.setContent {
            MilingTheme {
                Column(Modifier.height(420.dp)) {
                    AiConversationPane(
                        state = AiHomeUiState(loading = false, messages = messages),
                        onAction = {},
                        imeVisible = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(Modifier.height(72.dp).testTag("test_composer"))
                }
            }
        }
        compose.waitForIdle()

        val latestBounds = compose.onNodeWithTag("ai_message_message-12")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val composerBounds = compose.onNodeWithTag("test_composer")
            .getUnclippedBoundsInRoot()
        assertTrue(latestBounds.bottom + 12.dp <= composerBounds.top)
    }

    @Test
    fun paymentPasswordIsNotRestoredFromSavedInstanceState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MilingTheme {
                AiPaymentConfirmationDialog(
                    prompt = AiPaymentPrompt(
                        "message-sensitive",
                        AiPaymentPrompt.Type.TRANSFER,
                        "确认转账",
                        "小李",
                        5000
                    ),
                    submitting = false,
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }
        (1..5).forEach { digit ->
            compose.onNodeWithContentDescription("数字 $digit").performClick()
        }
        compose.onNodeWithContentDescription("支付密码第1位，已输入").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription("支付密码第1位，未输入").assertIsDisplayed()
    }

    @Test
    fun addressDetailsAreNotRestoredFromSavedInstanceState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MilingTheme {
                AiAddressDialog(submitting = false, onDismiss = {}, onSave = {})
            }
        }
        compose.onNodeWithTag("ai_address_mobile").performTextReplacement("13800138000")
        compose.onNodeWithTag("ai_address_detail").performTextReplacement("上海市浦东新区测试路 1 号")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("ai_address_mobile").assertTextEquals("")
        compose.onNodeWithTag("ai_address_detail").assertTextEquals("")
    }
}
