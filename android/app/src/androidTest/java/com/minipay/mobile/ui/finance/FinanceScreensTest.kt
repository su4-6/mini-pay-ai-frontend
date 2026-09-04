package com.minipay.mobile.ui.finance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.minipay.mobile.finance.BankCard
import com.minipay.mobile.finance.FinanceDestination
import com.minipay.mobile.finance.FinanceUiState
import com.minipay.mobile.finance.FundingResult
import com.minipay.mobile.finance.CollectionCode
import com.minipay.mobile.finance.CounterpartyProfile
import com.minipay.mobile.finance.TransferIntent
import com.minipay.mobile.finance.TransferOrder
import com.minipay.mobile.finance.TransferRecipientUi
import com.minipay.mobile.finance.TransferRecipientOrigin
import com.minipay.mobile.finance.TransferSource
import com.minipay.mobile.finance.WalletSummary
import com.minipay.mobile.finance.WalletBill
import com.minipay.mobile.finance.RecentTransferCounterparty
import com.minipay.mobile.profile.UserProfile
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FinanceScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun friendTransferRecordsMatchRequestedFiltersAndHideCardTransfer() {
        val recipient = TransferRecipientUi("user-2", "小满", "小满", null, null, null,
            TransferSource.FORM, TransferRecipientOrigin.CONTACT)
        var requestedDirection: String? = "unset"
        composeRule.setContent {
            MilingTheme {
                FriendTransferRecordsScreen(
                    recipient = recipient,
                    state = FinanceUiState(loading = false),
                    onBack = {},
                    load = { _, direction, _, _, _ -> requestedDirection = direction },
                    onBillClick = {},
                    onTransfer = {}
                )
            }
        }
        composeRule.onNodeWithText("转入").performClick()
        composeRule.runOnIdle { assertEquals("INCOME", requestedDirection) }
        composeRule.onNodeWithText("转账到卡").assertDoesNotExist()
        composeRule.onNodeWithText("向TA转账").assertIsDisplayed()
    }

    @Test
    fun recentTransferContactButtonKeepsSelectedUser() {
        var selected: String? = null
        composeRule.setContent {
            MilingTheme {
                RecentTransferContactsScreen(
                    state = FinanceUiState(
                        loading = false,
                        recentTransferCounterparties = listOf(
                            RecentTransferCounterparty("user-2", "小满", lastTransferAt = "2026-08-08T00:00:00Z")
                        )
                    ),
                    onBack = {}, load = {}, onTransfer = { selected = it.userId }
                )
            }
        }
        composeRule.onNodeWithText("转账").performClick()
        composeRule.runOnIdle { assertEquals("user-2", selected) }
    }

    @Test
    fun recipientLookupWaitsForExplicitConfirmation() {
        var resolvedMobile: String? = null
        composeRule.setContent {
            MilingTheme {
                TransferRecipientLookupScreen(
                    state = FinanceUiState(loading = false),
                    onBack = {},
                    onResolve = { resolvedMobile = it },
                    onClear = {},
                    onSelect = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("输入收款人手机号").performTextInput("15515887517")
        composeRule.runOnIdle { assertEquals(null, resolvedMobile) }
        composeRule.onNodeWithText("确认").performClick()
        composeRule.runOnIdle { assertEquals("15515887517", resolvedMobile) }
    }

    @Test
    fun recipientResultRequiresASecondTapBeforeEnteringTheAmountPage() {
        val recipient = TransferRecipientUi(
            receiverUserId = "0197f000-0000-7000-8000-000000000001",
            nickname = "小满",
            display = "小满（张*）",
            accountMasked = "155****7517",
            legalNameMasked = "张*",
            avatarUrl = null,
            transferSource = TransferSource.FORM,
            origin = TransferRecipientOrigin.MOBILE_LOOKUP
        )
        var selected: TransferRecipientUi? = null
        composeRule.setContent {
            MilingTheme {
                TransferRecipientLookupScreen(
                    state = FinanceUiState(loading = false, recipientLookupResult = recipient),
                    onBack = {},
                    onResolve = {},
                    onClear = {},
                    onSelect = { selected = it }
                )
            }
        }

        composeRule.onNodeWithText("155****7517").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, selected) }
        composeRule.onNodeWithContentDescription("选择收款人 小满（张*）").performClick()
        composeRule.runOnIdle { assertEquals(recipient, selected) }
    }

    @Test
    fun paymentPasswordPanelKeepsTheFullKeypadAndReturnsToThePreviousStep() {
        var returned = false
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, 1.3f)) {
                MilingTheme {
                    PaymentPasswordSheet(
                        purpose = "充值",
                        counterparty = "演示银行卡 **** 4020",
                        amountCent = 1200,
                        password = "",
                        busy = false,
                        error = null,
                        onClose = { returned = true },
                        onDigit = {},
                        onDelete = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("请输入支付密码").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("支付密码第1位，未输入").assertIsDisplayed()
        composeRule.onNodeWithText("0").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("删除支付密码").assertIsDisplayed()
        val passwordSafeBounds = composeRule.onNodeWithTag("finance-sheet-safe-area").getUnclippedBoundsInRoot()
        val passwordSheetBounds = composeRule.onNodeWithTag("payment-password-sheet").getUnclippedBoundsInRoot()
        val passwordLastRowBounds = composeRule.onNodeWithTag("payment-password-keypad-last-row").getUnclippedBoundsInRoot()
        assertTrue(passwordLastRowBounds.bottom <= passwordSheetBounds.bottom)
        assertTrue(passwordSheetBounds.bottom <= passwordSafeBounds.bottom)
        assertTrue(passwordLastRowBounds.top >= passwordSheetBounds.top)
        composeRule.onNodeWithContentDescription("关闭支付密码").performClick()
        composeRule.runOnIdle { assertEquals(true, returned) }
    }

    @Test
    fun bankBalancePasswordPanelKeepsZeroInsideTheSafeArea() {
        val state = FinanceUiState(
            loading = false,
            cards = listOf(BankCard("card-1", "演示银行", "DEBIT", "**** 4020", "ACTIVE"))
        )
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, 1.3f)) {
                MilingTheme {
                    BankBalanceScreen(
                        state = state,
                        cardId = "card-1",
                        onBack = {},
                        query = { _, _ -> }
                    )
                }
            }
        }

        composeRule.onNodeWithText("0").assertIsDisplayed()
        val safeBounds = composeRule.onNodeWithTag("finance-sheet-safe-area").getUnclippedBoundsInRoot()
        val sheetBounds = composeRule.onNodeWithTag("payment-password-sheet").getUnclippedBoundsInRoot()
        val zeroRowBounds = composeRule.onNodeWithTag("payment-password-keypad-last-row").getUnclippedBoundsInRoot()
        assertTrue(zeroRowBounds.bottom <= sheetBounds.bottom)
        assertTrue(sheetBounds.bottom <= safeBounds.bottom)
    }

    @Test
    fun rechargeUsesTheMaskedSelfCardBeforeChoosingTheFundingCard() {
        val state = FinanceUiState(
            loading = false,
            wallet = WalletSummary(
                walletId = "wallet-1", availableAmountCent = 10000, frozenAmountCent = 0,
                totalAmountCent = 10000, currency = "CNY", status = "ACTIVE",
                annualOutflowYear = 2026, annualOutflowLimitCent = 100000,
                annualOutflowUsedCent = 0, annualOutflowRemainingCent = 100000
            ),
            currentUserProfile = UserProfile(
                userId = "user-1", nickname = "小满", miniPayNo = "MP001", version = 1,
                legalNameMasked = "张*"
            ),
            cards = listOf(BankCard("card-1", "演示银行", "DEBIT", "**** 4020", "ACTIVE"))
        )
        composeRule.setContent {
            MilingTheme {
                FundingScreen(
                    title = "充值", state = state, requirePassword = true, preselectedCardId = null,
                    onBack = {}, onBindCard = {}, onRecords = {}, onCompleted = {}, onSubmit = { _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("小满").assertIsDisplayed()
        composeRule.onNodeWithText("实名：张*").assertIsDisplayed()
        composeRule.onNodeWithText("充值至我的余额").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("数字 1").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("演示银行储蓄卡 **** 4020").assertIsDisplayed()
        val methodSafeBounds = composeRule.onNodeWithTag("finance-sheet-safe-area").getUnclippedBoundsInRoot()
        val methodSheetBounds = composeRule.onNodeWithTag("payment-method-sheet").getUnclippedBoundsInRoot()
        val confirmBounds = composeRule.onNodeWithTag("payment-method-confirm").getUnclippedBoundsInRoot()
        assertTrue(confirmBounds.bottom <= methodSheetBounds.bottom)
        assertTrue(methodSheetBounds.bottom <= methodSafeBounds.bottom)
        composeRule.onNodeWithText("演示银行储蓄卡 **** 4020").performClick()
        composeRule.onNodeWithTag("payment-method-confirm").performClick()
        composeRule.onNodeWithText("请输入支付密码").assertIsDisplayed()
        composeRule.onNodeWithText("0").performClick()
        composeRule.onNodeWithContentDescription("删除支付密码").performClick()
    }

    @Test
    fun walletShowsSingleBankCardEntryWithoutCardSummary() {
        var opened: FinanceDestination? = null
        val state = FinanceUiState(
            loading = false,
            wallet = WalletSummary(
                walletId = "wallet-1",
                availableAmountCent = 10000,
                frozenAmountCent = 0,
                totalAmountCent = 10000,
                currency = "CNY",
                status = "ACTIVE",
                annualOutflowYear = 2026,
                annualOutflowLimitCent = 100000,
                annualOutflowUsedCent = 0,
                annualOutflowRemainingCent = 100000,
                sandboxNotice = "沙箱资产",
                recentBills = emptyList()
            ),
            cards = listOf(BankCard("card-1", "沙箱银行", "DEBIT", "**** 4020", "ACTIVE"))
        )
        composeRule.setContent {
            MilingTheme { WalletHome(state, {}, { opened = it }) }
        }

        composeRule.onNodeWithText("银行卡").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("**** 4020").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(FinanceDestination.CARDS, opened) }
    }

    @Test
    fun walletExposesRechargeWithdrawalAndRecordEntries() {
        var opened: FinanceDestination? = null
        val state = FinanceUiState(
            loading = false,
            wallet = WalletSummary(
                walletId = "wallet-1", availableAmountCent = 10000, frozenAmountCent = 0,
                totalAmountCent = 10000, currency = "CNY", status = "ACTIVE",
                annualOutflowYear = 2026, annualOutflowLimitCent = 100000,
                annualOutflowUsedCent = 0, annualOutflowRemainingCent = 100000
            )
        )
        composeRule.setContent { MilingTheme { WalletHome(state, {}, { opened = it }) } }

        composeRule.onNodeWithText("充值").assertIsDisplayed()
        composeRule.onNodeWithText("提现").assertIsDisplayed()
        composeRule.onNodeWithText("充值记录").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(FinanceDestination.RECHARGE_RECORDS, opened) }
    }

    @Test
    fun fundingResultExplainsProcessingAndOpensMatchingRecords() {
        var recordType: String? = null
        composeRule.setContent {
            MilingTheme {
                FundingResultScreen(
                    result = FundingResult("WITHDRAWAL", "card-1", 1200, "PROCESSING"),
                    cards = listOf(BankCard("card-1", "沙箱银行", "DEBIT", "**** 4020", "ACTIVE")),
                    onBack = {}, onWallet = {}, onRecords = { recordType = it }
                )
            }
        }

        composeRule.onNodeWithText("处理中").assertIsDisplayed()
        composeRule.onNodeWithText("资金正在处理中，请在记录中查看最终结果").assertIsDisplayed()
        composeRule.onNodeWithText("查看提现记录").performClick()
        composeRule.runOnIdle { assertEquals("WITHDRAWAL", recordType) }
    }

    @Test
    fun fundingWithoutAnActiveCardOffersBindingInsideThePaymentMethodSheet() {
        var openedBinding = false
        composeRule.setContent {
            MilingTheme {
                FundingScreen(
                    title = "充值",
                    state = FinanceUiState(loading = false),
                    requirePassword = false,
                    preselectedCardId = null,
                    onBack = {},
                    onBindCard = { openedBinding = true },
                    onRecords = {},
                    onCompleted = {},
                    onSubmit = { _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription("数字 1").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("添加银行卡").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(true, openedBinding) }
    }

    @Test
    fun withdrawalCardSelectionKeepsThePasswordKeypadInsideTheSheet() {
        val state = FinanceUiState(
            loading = false,
            cards = listOf(BankCard("card-1", "演示银行", "DEBIT", "**** 4020", "ACTIVE"))
        )
        composeRule.setContent {
            MilingTheme {
                FundingScreen(
                    title = "提现",
                    state = state,
                    requirePassword = true,
                    preselectedCardId = null,
                    onBack = {},
                    onBindCard = {},
                    onRecords = {},
                    onCompleted = {},
                    onSubmit = { _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription("数字 1").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("演示银行储蓄卡 **** 4020").performClick()
        composeRule.onNodeWithTag("payment-method-confirm").performClick()
        composeRule.onNodeWithText("请输入支付密码").assertIsDisplayed()
        val safeBounds = composeRule.onNodeWithTag("finance-sheet-safe-area").getUnclippedBoundsInRoot()
        val sheetBounds = composeRule.onNodeWithTag("payment-password-sheet").getUnclippedBoundsInRoot()
        val keypadBounds = composeRule.onNodeWithTag("payment-password-keypad-last-row").getUnclippedBoundsInRoot()
        assertTrue(keypadBounds.bottom <= sheetBounds.bottom)
        assertTrue(sheetBounds.bottom <= safeBounds.bottom)
    }

    @Test
    fun addCardExplainsThatTheVerificationCodeIsSandboxOnly() {
        composeRule.setContent {
            MilingTheme {
                AddCardScreen(
                    state = FinanceUiState(loading = false),
                    onBack = {},
                    bind = { _, _, _, _ -> },
                    onBound = {}
                )
            }
        }

        composeRule.onNodeWithText("沙箱验证码").assertIsDisplayed()
        composeRule.onNodeWithText("当前演示环境固定为 123456，不会发送真实短信。").assertIsDisplayed()
    }

    @Test
    fun balanceDetailsOffersAllIncomeAndExpenseFilters() {
        val requests = mutableListOf<Pair<String?, String?>>()
        composeRule.setContent {
            MilingTheme {
                BillsScreen(
                    title = "余额变动明细",
                    state = FinanceUiState(loading = false),
                    onBack = {},
                    load = { direction, businessType, _ -> requests += direction to businessType }
                )
            }
        }

        composeRule.onNodeWithText("全部").assertIsDisplayed()
        composeRule.onNodeWithText("收入").assertIsDisplayed()
        composeRule.onNodeWithText("支出").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("EXPENSE", requests.last().first) }
    }

    @Test
    fun bankCardListShowsMaskedCardAndAddEntry() {
        val state = FinanceUiState(
            loading = false,
            cards = listOf(BankCard("card-1", "沙箱银行", "DEBIT", "**** **** **** 4020", "ACTIVE"))
        )
        composeRule.setContent {
            MilingTheme { CardsScreen(state, {}, {}, {}) }
        }

        composeRule.onNodeWithText("沙箱银行").assertIsDisplayed()
        composeRule.onNodeWithText("**** **** **** 4020").assertIsDisplayed()
        composeRule.onNodeWithText("添加银行卡").assertIsDisplayed()
    }

    @Test
    fun realNameGateShowsClearSecurityContextAndPrimaryAction() {
        var started = false
        composeRule.setContent {
            MilingTheme {
                RealNameRequiredScreen(
                    processing = false,
                    onAction = { started = true },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("完成实名认证后继续").assertIsDisplayed()
        composeRule.onNodeWithText("认证材料").assertIsDisplayed()
        composeRule.onNodeWithText("安全保护").assertIsDisplayed()
        composeRule.onNodeWithText("开始认证").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(true, started) }
    }

    @Test
    fun realNameSuccessDialogGivesExplicitFeedback() {
        composeRule.setContent {
            MilingTheme { RealNameSuccessDialog(onContinue = {}) }
        }

        composeRule.onNodeWithText("实名认证成功").assertIsDisplayed()
        composeRule.onNodeWithText("正在安全返回你刚才的操作").assertIsDisplayed()
    }

    @Test
    fun scanScreenExposesReceiveAndGalleryActions() {
        var openedReceive = false
        composeRule.setContent {
            MilingTheme {
                ScanScreen(
                    state = FinanceUiState(loading = false),
                    onBack = {},
                    onOpenReceiveCode = { openedReceive = true },
                    resolve = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription("打开个人收款码").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("从相册识别二维码").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("动态扫码框").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(true, openedReceive) }
    }

    @Test
    fun receiveCodeShowsNicknameAndMaskedLegalNameInsteadOfInternalCodeType() {
        composeRule.setContent {
            MilingTheme {
                ReceiveScreen(
                    state = FinanceUiState(
                        loading = false,
                        collectionCode = CollectionCode("PERSONAL_COLLECTION", "minipay://collect/1", "2099-01-01T00:00:00Z"),
                        collectionRecipient = UserProfile("user-1", "小满", "MP001", version = 1, legalNameMasked = "张*")
                    ),
                    onBack = {}, load = {}, loadRecords = {}, observeReceipts = {}, onRecords = {}
                )
            }
        }

        composeRule.onNodeWithText("小满（张*）").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("默认收款头像").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("收款保障").assertIsDisplayed()
        composeRule.onNodeWithText("PERSONAL_COLLECTION收款码").assertDoesNotExist()
    }

    @Test
    fun receiveCodeFailureOffersRetryInsteadOfAnIndefiniteSpinner() {
        var retried = false
        composeRule.setContent {
            MilingTheme {
                ReceiveScreen(
                    state = FinanceUiState(loading = false, collectionCodeError = "网络连接失败，请稍后重试"),
                    onBack = {}, load = { retried = true }, loadRecords = {}, observeReceipts = {}, onRecords = {}
                )
            }
        }

        composeRule.onNodeWithText("重新加载").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(true, retried) }
    }

    @Test
    fun receiveCodeVoiceReminderToggleIsAccessibleAndDispatchesChange() {
        var enabled = false
        composeRule.setContent {
            MilingTheme {
                ReceiveScreen(
                    state = FinanceUiState(
                        loading = false,
                        receiptSpeechEnabled = false,
                        collectionCode = CollectionCode("PERSONAL_COLLECTION", "minipay://collect/1", "2099-01-01T00:00:00Z")
                    ),
                    onBack = {},
                    load = {},
                    loadRecords = {},
                    observeReceipts = {},
                    onReceiptSpeechEnabledChange = { enabled = it },
                    onRecords = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("开启到账语音提醒").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(true, enabled) }
    }

    @Test
    fun scannedPersonalCollectionUsesOnlyTheAccountBalance() {
        val recipient = TransferRecipientUi(
            receiverUserId = "0197f000-0000-7000-8000-000000000001",
            display = "小满（张*）",
            nickname = "小满",
            legalNameMasked = "张*",
            accountMasked = "155****7517",
            avatarUrl = null,
            transferSource = TransferSource.PERSONAL_COLLECTION_CODE,
            origin = TransferRecipientOrigin.SCAN
        )
        composeRule.setContent {
            MilingTheme {
                TransferPaymentFlow(
                    state = FinanceUiState(loading = false),
                    recipient = recipient,
                    onBack = {},
                    onRecords = {},
                    createIntent = { _, amount, _, _, _, done ->
                        done(TransferIntent("intent-1", amount, "PENDING_CONFIRMATION", "2099-01-01T00:00:00Z"))
                    },
                    confirm = { _, _, _, done -> done(TransferOrder("transfer-1", "intent-1", 100, "SUCCEEDED")) },
                    refreshOrder = { _, _ -> },
                    onFinished = {}
                )
            }
        }

        composeRule.onNodeWithText("1").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("账户余额").assertIsDisplayed()
        composeRule.onNodeWithText("添加银行卡").assertDoesNotExist()
    }

    @Test
    fun mobileRecipientCreatesATransferWithTheFormSource() {
        var transferSource: String? = null
        val recipient = TransferRecipientUi(
            receiverUserId = "0197f000-0000-7000-8000-000000000001",
            display = "小满（张*）",
            nickname = "小满",
            legalNameMasked = "张*",
            accountMasked = "155****7517",
            avatarUrl = null,
            transferSource = TransferSource.FORM,
            origin = TransferRecipientOrigin.MOBILE_LOOKUP
        )
        composeRule.setContent {
            MilingTheme {
                TransferPaymentFlow(
                    state = FinanceUiState(loading = false),
                    recipient = recipient,
                    onBack = {},
                    onRecords = {},
                    createIntent = { _, amount, _, source, _, done ->
                        transferSource = source
                        done(TransferIntent("intent-1", amount, "PENDING_CONFIRMATION", "2099-01-01T00:00:00Z"))
                    },
                    confirm = { _, _, _, done -> done(TransferOrder("transfer-1", "intent-1", 100, "SUCCEEDED")) },
                    refreshOrder = { _, _ -> },
                    onFinished = {}
                )
            }
        }

        composeRule.onNodeWithText("1").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.runOnIdle { assertEquals("FORM", transferSource) }
        composeRule.onNodeWithText("账户余额").assertIsDisplayed()
    }

    @Test
    fun contactsButtonOpensFriendPickerAndKeepsConversationId() {
        val friend = TransferRecipientUi(
            receiverUserId = "user-2",
            nickname = "逐玲",
            display = "小玲",
            accountMasked = "155****7517",
            legalNameMasked = null,
            avatarUrl = null,
            transferSource = TransferSource.FORM,
            origin = TransferRecipientOrigin.CONTACT,
            conversationId = "conv_friend"
        )
        var selected: TransferRecipientUi? = null
        composeRule.setContent {
            MilingTheme {
                TransferRecipientLookupScreen(
                    state = FinanceUiState(loading = false, transferFriends = listOf(friend)),
                    onBack = {},
                    onResolve = {},
                    onClear = {},
                    onSelect = { selected = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("打开好友通讯录").performClick()
        composeRule.onNodeWithText("选择好友").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("选择好友小玲").performClick()
        composeRule.runOnIdle { assertEquals("conv_friend", selected?.conversationId) }
    }

    @Test
    fun processingTransferStopsPollingAndOffersRecordsAfterTimeout() {
        var recordsOpened = false
        val recipient = TransferRecipientUi(
            receiverUserId = "0197f000-0000-7000-8000-000000000001",
            display = "小满（张*）",
            nickname = "小满",
            legalNameMasked = "张*",
            accountMasked = "155****7517",
            avatarUrl = null,
            transferSource = TransferSource.FORM,
            origin = TransferRecipientOrigin.MOBILE_LOOKUP
        )
        composeRule.setContent {
            MilingTheme {
                TransferPaymentFlow(
                    state = FinanceUiState(loading = false),
                    recipient = recipient,
                    onBack = {},
                    onRecords = { recordsOpened = true },
                    createIntent = { _, amount, _, _, _, done ->
                        done(TransferIntent("intent-1", amount, "PENDING_CONFIRMATION", "2099-01-01T00:00:00Z"))
                    },
                    confirm = { _, _, _, done ->
                        done(TransferOrder("transfer-1", "intent-1", 100, "PROCESSING"))
                    },
                    refreshOrder = { _, done ->
                        done(TransferOrder("transfer-1", "intent-1", 100, "PROCESSING"))
                    },
                    onFinished = {}
                )
            }
        }

        composeRule.onNodeWithText("1").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("付款").performClick()
        repeat(6) { composeRule.onNodeWithText("1").performClick() }
        composeRule.onNodeWithContentDescription("支付处理中").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(31_000)
        composeRule.onNodeWithText("处理时间较长").assertIsDisplayed()
        composeRule.onNodeWithText("查看转账记录").performClick()
        composeRule.runOnIdle { assertEquals(true, recordsOpened) }
    }

    @Test
    fun receiptRecordsShowPayerProfileStatusAndOpenDetail() {
        var loadedWithReset: Boolean? = null
        var openedBillId: String? = null
        val bill = collectionReceiptBill()
        composeRule.setContent {
            MilingTheme {
                ReceiptRecordsScreen(
                    state = FinanceUiState(
                        loading = false,
                        bills = listOf(bill),
                        collectionRecordTotal = 1
                    ),
                    onBack = {},
                    load = { loadedWithReset = it },
                    onBillClick = { openedBillId = it }
                )
            }
        }

        composeRule.onNodeWithText("小满").assertIsDisplayed()
        composeRule.onNodeWithText("实名 张* · 个人收钱码").assertIsDisplayed()
        composeRule.onNodeWithText("已入余额").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("小满", substring = true)).performClick()
        composeRule.runOnIdle {
            assertEquals(true, loadedWithReset)
            assertEquals("bill-1", openedBillId)
        }
    }

    @Test
    fun receiptRecordFiltersAreMutuallyExclusiveAndMerchantHasAnEmptyState() {
        composeRule.setContent {
            MilingTheme {
                ReceiptRecordsScreen(
                    state = FinanceUiState(
                        loading = false,
                        bills = listOf(collectionReceiptBill()),
                        collectionRecordTotal = 1
                    ),
                    onBack = {},
                    load = {}
                )
            }
        }

        composeRule.onNodeWithText("全部").assertIsSelected()
        composeRule.onNodeWithText("经营码").performClick()
        composeRule.onNodeWithText("全部").assertIsNotSelected()
        composeRule.onNodeWithText("经营码").assertIsSelected()
        composeRule.onNodeWithText("暂无经营码收款").assertIsDisplayed()
    }

    @Test
    fun receiptRecordsCanLoadTheNextPage() {
        var reset: Boolean? = null
        composeRule.setContent {
            MilingTheme {
                ReceiptRecordsScreen(
                    state = FinanceUiState(
                        loading = false,
                        bills = listOf(collectionReceiptBill()),
                        collectionRecordPage = 1,
                        collectionRecordTotal = 21
                    ),
                    onBack = {},
                    load = { reset = it }
                )
            }
        }

        composeRule.onNodeWithText("加载更多").performClick()
        composeRule.runOnIdle { assertEquals(false, reset) }
    }

    private fun collectionReceiptBill() = WalletBill(
        billId = "bill-1",
        businessType = "TRANSFER",
        businessNo = "T1",
        direction = "INCOME",
        amountCent = 1_000,
        counterpartyDisplay = "站内付款人",
        counterpartyUserId = "user-2",
        counterpartyProfile = CounterpartyProfile(
            userId = "user-2",
            nickname = "小满",
            legalNameMasked = "张*"
        ),
        source = "PERSONAL_COLLECTION_CODE",
        status = "SUCCEEDED",
        occurredAt = "2026-08-09T08:13:00Z"
    )
}
