package com.minipay.mobile.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import com.minipay.mobile.R
import com.minipay.mobile.ai.AgentActionRequest
import com.minipay.mobile.ai.AiHomeConversation
import com.minipay.mobile.ai.AiHomeMessage
import com.minipay.mobile.ai.AiHomeUiState
import com.minipay.mobile.ai.AiInputMode
import com.minipay.mobile.finance.FinanceDestination
import com.minipay.mobile.voice.VoiceInputState
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingDivider
import com.minipay.mobile.ui.theme.MilingGradientEnd
import com.minipay.mobile.ui.theme.MilingGradientStart
import com.minipay.mobile.ui.theme.MilingIconSecondary
import com.minipay.mobile.ui.theme.MilingLilac
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import com.minipay.mobile.ui.theme.MilingTheme
import kotlinx.coroutines.launch

sealed interface MilingHomeAction {
    data object ReturnToRecommendation : MilingHomeAction
    data object StartNewConversation : MilingHomeAction
    data class SelectSession(val sessionId: String) : MilingHomeAction
    data class RenameConversation(val conversation: AiHomeConversation, val title: String) : MilingHomeAction
    data class DeleteConversation(val conversation: AiHomeConversation) : MilingHomeAction
    data object OpenProfile : MilingHomeAction
    data object OpenConversation : MilingHomeAction
    data class SetSoundEnabled(val enabled: Boolean) : MilingHomeAction
    data object Scan : MilingHomeAction
    data object ReceiveMoney : MilingHomeAction
    data object AddFriend : MilingHomeAction
    data object Transfer : MilingHomeAction
    data object CheckBalance : MilingHomeAction
    data object ViewBills : MilingHomeAction
    data class OpenFinance(val destination: FinanceDestination) : MilingHomeAction
    data object OpenLifeAssistant : MilingHomeAction
    data object OpenFood : MilingHomeAction
    data class SetInputMode(val mode: AiInputMode) : MilingHomeAction
    data object VoiceHoldStarted : MilingHomeAction
    data object VoiceHoldReleased : MilingHomeAction
    data object VoiceHoldCancelled : MilingHomeAction
    data class UpdateDraft(val value: String) : MilingHomeAction
    data class SubmitPrompt(val prompt: String) : MilingHomeAction
    data class ContinueCard(val message: AiHomeMessage, val request: AgentActionRequest) : MilingHomeAction
    data class PrepareCheckout(val message: AiHomeMessage) : MilingHomeAction
    data class RequestPayment(val message: AiHomeMessage) : MilingHomeAction
    data class CancelOrder(val message: AiHomeMessage) : MilingHomeAction
}

private data class Suggestion(
    val text: String,
    val icon: ImageVector,
    val testTag: String
)

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val action: MilingHomeAction,
    val testTag: String
)

private data class PlusAction(
    val label: String,
    val icon: ImageVector,
    val action: MilingHomeAction,
    val testTag: String
)

private val suggestions = listOf(
    Suggestion("今天花了多少钱？", Icons.Outlined.BarChart, "suggestion_today_spending"),
    Suggestion("帮我给小李转 50 元", Icons.AutoMirrored.Outlined.Send, "suggestion_transfer"),
    Suggestion("分析一下本月账单", Icons.Outlined.Description, "suggestion_monthly_bills")
)

private val quickActions = listOf(
    QuickAction("转账", Icons.Outlined.SwapVert, MilingHomeAction.Transfer, "quick_transfer"),
    QuickAction("查余额", Icons.Outlined.AccountBalanceWallet, MilingHomeAction.CheckBalance, "quick_balance"),
    QuickAction("看账单", Icons.AutoMirrored.Outlined.ReceiptLong, MilingHomeAction.ViewBills, "quick_bills"),
    QuickAction("生活助手", Icons.Outlined.GridView, MilingHomeAction.OpenLifeAssistant, "quick_life_assistant")
)

private val plusActions = listOf(
    PlusAction("扫一扫", Icons.Outlined.CropFree, MilingHomeAction.Scan, "plus_scan"),
    PlusAction("收款", Icons.Outlined.QrCode2, MilingHomeAction.ReceiveMoney, "plus_receive"),
    PlusAction("添加朋友", Icons.Outlined.PersonAdd, MilingHomeAction.AddFriend, "plus_add_friend")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MilingHomeScreen(
    onAction: (MilingHomeAction) -> Unit,
    aiState: AiHomeUiState = AiHomeUiState(loading = false),
    soundEnabled: Boolean = aiState.aiSpeechEnabled,
    drawerProfileName: String = "小满",
    drawerAvatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var drawerSearching by rememberSaveable { mutableStateOf(false) }
    var drawerSearchQuery by rememberSaveable { mutableStateOf("") }
    val selectedConversationId = aiState.selectedConversationId
    val conversationGroups = remember(aiState.conversations) {
        listOf(DrawerConversationGroup(
            label = "最近会话",
            conversations = aiState.conversations.map { DrawerConversation(it.id, it.title, it.version) }
        ))
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val drawerSwipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var previousInputMode by remember { mutableStateOf(aiState.inputMode) }
    fun dismissInput() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(drawerState, imeVisible) {
        var previousOffset: Float? = null
        snapshotFlow { drawerState.currentOffset }.collect { offset ->
            val previous = previousOffset
            if (imeVisible && previous != null && offset.isFinite() && offset > previous + 0.5f) {
                dismissInput()
            }
            previousOffset = offset.takeIf { it.isFinite() }
        }
    }

    LaunchedEffect(aiState.inputMode) {
        if (aiState.inputMode == AiInputMode.VOICE) {
            dismissInput()
        } else if (previousInputMode == AiInputMode.VOICE) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        previousInputMode = aiState.inputMode
    }

    BackHandler(enabled = menuExpanded) {
        menuExpanded = false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Keep the closed drawer from stealing Miling's left swipe back to Home. Once open,
        // restore Material's drag handling so a left swipe can dismiss the drawer naturally.
        gesturesEnabled = drawerState.isOpen,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        drawerContent = {
            SessionDrawer(
                drawerState = drawerState,
                groups = conversationGroups,
                selectedConversationId = selectedConversationId,
                isSearching = drawerSearching,
                searchQuery = drawerSearchQuery,
                onSearchQueryChange = { drawerSearchQuery = it },
                onEnterSearch = { drawerSearching = true },
                onExitSearch = {
                    drawerSearching = false
                    drawerSearchQuery = ""
                },
                onStartNewConversation = {
                    coroutineScope.launch {
                        drawerState.close()
                        onAction(MilingHomeAction.StartNewConversation)
                    }
                },
                onSelectConversation = { conversation ->
                    coroutineScope.launch {
                        drawerState.close()
                        onAction(MilingHomeAction.SelectSession(conversation.id))
                    }
                },
                onRenameConversation = { conversation, title ->
                    onAction(MilingHomeAction.RenameConversation(
                        AiHomeConversation(conversation.id, conversation.title, conversation.version),
                        title
                    ))
                },
                onDeleteConversation = { conversation ->
                    onAction(MilingHomeAction.DeleteConversation(
                        AiHomeConversation(conversation.id, conversation.title, conversation.version)
                    ))
                },
                onOpenProfile = {
                    coroutineScope.launch {
                        drawerState.close()
                        onAction(MilingHomeAction.OpenProfile)
                    }
                },
                profileName = drawerProfileName,
                avatarUrl = drawerAvatarUrl
            )
        }
    ) {
        Box(
        modifier = modifier
            .fillMaxSize()
            .background(MilingSurface)
            .pointerInput(drawerState, drawerSwipeThresholdPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var rightSwipeDistance = 0f
                    var ownsGesture = false
                    val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        if (drawerState.isClosed && overSlop > 0f) {
                            ownsGesture = true
                            rightSwipeDistance = overSlop
                            change.consume()
                        }
                    }
                    if (ownsGesture && slopChange != null) {
                        horizontalDrag(slopChange.id) { change ->
                            rightSwipeDistance = (rightSwipeDistance + change.positionChange().x)
                                .coerceAtLeast(0f)
                            change.consume()
                        }
                        if (rightSwipeDistance >= drawerSwipeThresholdPx) {
                            coroutineScope.launch { drawerState.open() }
                        }
                    }
                }
            }
            .testTag("miling_home")
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            HomeTopBar(
                soundEnabled = soundEnabled,
                menuExpanded = menuExpanded,
                onOpenDrawer = {
                    dismissInput()
                    menuExpanded = false
                    drawerSearching = false
                    drawerSearchQuery = ""
                    coroutineScope.launch { drawerState.open() }
                },
                onToggleSound = {
                    onAction(MilingHomeAction.SetSoundEnabled(!soundEnabled))
                },
                onOpenConversation = { onAction(MilingHomeAction.OpenConversation) },
                onToggleMenu = {
                    dismissInput()
                    menuExpanded = !menuExpanded
                }
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val compactHeight = maxHeight < 610.dp
                val compactWidth = maxWidth < 360.dp
                val heroTopSpace = if (compactHeight) 20.dp else 68.dp
                val mascotSize = if (compactWidth) 104.dp else 116.dp

                if (aiState.messages.isNotEmpty()) {
                    AiConversationPane(
                        state = aiState,
                        onAction = onAction,
                        imeVisible = imeVisible,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxHeight()
                            .widthIn(max = 600.dp)
                    )
                } else if (!imeVisible) Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxHeight()
                        .widthIn(max = 600.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(heroTopSpace))
                    MilingMascot(size = mascotSize)
                    Spacer(Modifier.height(8.dp))
                    GradientHeadline()
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "你的智能支付与生活助手",
                        color = MilingTextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics {
                            contentDescription = "你的智能支付与生活助手"
                        }
                    )
                    Spacer(Modifier.height(if (compactHeight) 24.dp else 32.dp))

                    suggestions.forEachIndexed { index, suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            onClick = {
                                onAction(MilingHomeAction.UpdateDraft(suggestion.text))
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        )
                        if (index < suggestions.lastIndex) {
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SafetyPill()
                    Spacer(Modifier.height(if (compactHeight) 20.dp else 28.dp))
                }
            }

            if (!imeVisible) {
                QuickActionsRow(onAction = { action ->
                    dismissInput()
                    onAction(action)
                })
                Spacer(Modifier.height(12.dp))
            }

            MessageComposer(
                prompt = aiState.draftText,
                onPromptChange = { onAction(MilingHomeAction.UpdateDraft(it)) },
                enabled = !aiState.streaming && !aiState.confirmationInFlight,
                inputMode = aiState.inputMode,
                voiceInputState = aiState.voiceInputState,
                focusRequester = focusRequester,
                onInputModeChange = { onAction(MilingHomeAction.SetInputMode(it)) },
                onVoiceHoldStart = { onAction(MilingHomeAction.VoiceHoldStarted) },
                onVoiceHoldRelease = { onAction(MilingHomeAction.VoiceHoldReleased) },
                onVoiceHoldCancel = { onAction(MilingHomeAction.VoiceHoldCancelled) },
                onSubmit = {
                    val trimmed = aiState.draftText.trim()
                    if (trimmed.isNotEmpty()) {
                        onAction(MilingHomeAction.SubmitPrompt(trimmed))
                    }
                },
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.ime)
                )
            )
        }

            if (menuExpanded) {
                QuickPlusMenuOverlay(
                    onDismiss = { menuExpanded = false },
                    onAction = { quickAction ->
                        menuExpanded = false
                        dismissInput()
                        onAction(
                            when (quickAction) {
                                QuickPlusAction.SCAN -> MilingHomeAction.Scan
                                QuickPlusAction.RECEIVE -> MilingHomeAction.ReceiveMoney
                                QuickPlusAction.ADD_FRIEND -> MilingHomeAction.AddFriend
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    soundEnabled: Boolean,
    menuExpanded: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenConversation: () -> Unit,
    onToggleMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp)
    ) {
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .testTag("session_drawer_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "打开会话列表",
                tint = MilingTextPrimary,
                modifier = Modifier.size(30.dp)
            )
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "米灵",
                color = MilingTextPrimary,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            IconButton(
                onClick = onToggleSound,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("sound_toggle")
            ) {
                Icon(
                    imageVector = if (soundEnabled) {
                        Icons.AutoMirrored.Outlined.VolumeUp
                    } else {
                        Icons.AutoMirrored.Outlined.VolumeOff
                    },
                    contentDescription = if (soundEnabled) "关闭声音" else "开启声音",
                    tint = MilingTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenConversation,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("conversation_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "打开对话",
                    tint = MilingTextPrimary,
                    modifier = Modifier.size(27.dp)
                )
            }
            IconButton(
                onClick = onToggleMenu,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("plus_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddCircleOutline,
                    contentDescription = if (menuExpanded) "关闭更多功能" else "打开更多功能",
                    tint = MilingTextPrimary,
                    modifier = Modifier.size(29.dp)
                )
            }
        }
    }
}

@Composable
internal fun MilingMascot(size: androidx.compose.ui.unit.Dp) {
    val bitmap = ImageBitmap.imageResource(R.drawable.miling_mascot)
    val painter = remember(bitmap) {
        BitmapPainter(
            image = bitmap,
            srcOffset = IntOffset(250, 250),
            srcSize = IntSize(524, 720)
        )
    }
    Image(
        painter = painter,
        contentDescription = "米灵智能助手形象",
        modifier = Modifier.size(size)
    )
}

@Composable
private fun GradientHeadline() {
    Text(
        text = "我是米灵",
        style = MaterialTheme.typography.displayLarge.merge(
            TextStyle(
                brush = Brush.horizontalGradient(
                    colors = listOf(MilingGradientStart, MilingLilac, MilingGradientEnd)
                )
            )
        ),
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(MilingRadii.Large)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clip(shape)
            .background(MilingSurface)
            .border(1.dp, MilingBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(suggestion.testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MilingBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = suggestion.icon,
                contentDescription = null,
                tint = MilingPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = suggestion.text,
            color = MilingTextPrimary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MilingIconSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SafetyPill() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MilingBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "安全提示：每笔支付都需要你亲自确认"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            tint = MilingPrimary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "每笔支付都需要你亲自确认",
            color = MilingTextSecondary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun QuickActionsRow(onAction: (MilingHomeAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .testTag("quick_actions"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        quickActions.forEach { item ->
            QuickActionPill(item = item, onClick = { onAction(item.action) })
        }
    }
}

@Composable
private fun QuickActionPill(
    item: QuickAction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(MilingSurface)
            .border(1.dp, MilingBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp)
            .testTag(item.testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MilingTextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = item.label,
            color = MilingTextPrimary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@Composable
private fun DraftConversationHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        MilingMascot(size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("米灵", style = MaterialTheme.typography.titleLarge, color = MilingTextPrimary)
            Text(
                "查余额、转账、看账单或点外卖",
                style = MaterialTheme.typography.bodyMedium,
                color = MilingTextSecondary
            )
        }
    }
}

@Composable
private fun MessageComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    enabled: Boolean,
    inputMode: AiInputMode,
    voiceInputState: VoiceInputState,
    focusRequester: FocusRequester,
    onInputModeChange: (AiInputMode) -> Unit,
    onVoiceHoldStart: () -> Unit,
    onVoiceHoldRelease: () -> Unit,
    onVoiceHoldCancel: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(36.dp)
    val listening = voiceInputState is VoiceInputState.Listening
    val processing = voiceInputState == VoiceInputState.Processing
    val voiceActive = listening || processing
    var dragCancelling by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 20.dp)
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(MilingSurface)
            .border(1.dp, Color(0xFFE7EAF0), shape)
            .padding(horizontal = 10.dp)
            .testTag("message_composer"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                onInputModeChange(
                    if (inputMode == AiInputMode.KEYBOARD) AiInputMode.VOICE else AiInputMode.KEYBOARD
                )
            },
            enabled = enabled && !processing && !listening,
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, MilingBorder, CircleShape)
                .testTag("voice_input_button")
        ) {
            Icon(
                imageVector = if (inputMode == AiInputMode.VOICE) Icons.Outlined.Keyboard else Icons.Outlined.GraphicEq,
                contentDescription = if (inputMode == AiInputMode.VOICE) "切换到文字输入" else "切换到语音输入",
                tint = MilingTextPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        if (inputMode == AiInputMode.VOICE) {
            HoldToTalkButton(
                enabled = enabled && !processing,
                listening = listening,
                processing = processing,
                cancelling = dragCancelling,
                onCancellingChange = { dragCancelling = it },
                onHoldStart = onVoiceHoldStart,
                onHoldRelease = onVoiceHoldRelease,
                onHoldCancel = onVoiceHoldCancel,
                modifier = Modifier.weight(1f)
            )
        } else {
            BasicTextField(
                value = prompt,
                onValueChange = onPromptChange,
                enabled = enabled && !voiceActive,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "消息输入框" }
                    .testTag("message_input"),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MilingTextPrimary),
                cursorBrush = SolidColor(MilingPrimary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (prompt.isEmpty()) {
                            Text(
                                text = "发消息…",
                                color = MilingTextMuted,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        if (inputMode == AiInputMode.KEYBOARD && prompt.isNotBlank() && !voiceActive) {
            IconButton(
                onClick = onSubmit,
                enabled = enabled,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_prompt_button")
            ) {
                Surface(
                    shape = CircleShape,
                    color = MilingPrimary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "发送消息",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldToTalkButton(
    enabled: Boolean,
    listening: Boolean,
    processing: Boolean,
    cancelling: Boolean,
    onCancellingChange: (Boolean) -> Unit,
    onHoldStart: () -> Unit,
    onHoldRelease: () -> Unit,
    onHoldCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentStart by rememberUpdatedState(onHoldStart)
    val currentRelease by rememberUpdatedState(onHoldRelease)
    val currentCancel by rememberUpdatedState(onHoldCancel)
    val currentCancellingChange by rememberUpdatedState(onCancellingChange)
    val background = when {
        cancelling -> Color(0xFFFFE9EA)
        listening -> MilingPrimary
        else -> Color(0xFFF6F8FC)
    }
    val foreground = when {
        cancelling -> Color(0xFFB3261E)
        listening -> Color.White
        else -> MilingTextPrimary
    }
    val label = when {
        processing -> "正在识别…"
        cancelling -> "松开取消"
        listening -> "松手完成，上滑取消"
        else -> "按住说话"
    }
    val stateLabel = when {
        processing -> "正在识别语音"
        cancelling -> "松开将取消录音"
        listening -> "正在录音，上滑可以取消"
        else -> "等待按住说话"
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(background)
            .border(1.dp, if (listening) MilingPrimary else MilingBorder, RoundedCornerShape(26.dp))
            .pointerInput(Unit) {
                val cancelThresholdPx = 72.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!currentEnabled) return@awaitEachGesture
                    down.consume()
                    var finished = false
                    var shouldCancel = false
                    currentStart()
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                finished = true
                                currentCancellingChange(false)
                                if (shouldCancel) currentCancel() else currentRelease()
                                break
                            }
                            val nextCancel = down.position.y - change.position.y >= cancelThresholdPx
                            if (nextCancel != shouldCancel) {
                                shouldCancel = nextCancel
                                currentCancellingChange(nextCancel)
                            }
                            change.consume()
                        }
                    } finally {
                        if (!finished) {
                            currentCancellingChange(false)
                            currentCancel()
                        }
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = stateLabel
                if (!enabled) disabled()
                onClick {
                    if (!enabled) return@onClick false
                    if (listening) currentRelease() else currentStart()
                    true
                }
            }
            .testTag("voice_input_hold")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (listening && !cancelling) VoiceWaveform(foreground)
            Text(
                text = label,
                color = foreground,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VoiceWaveform(color: Color) {
    Canvas(Modifier.size(width = 26.dp, height = 20.dp)) {
        val heights = listOf(0.4f, 0.75f, 1f, 0.65f, 0.9f, 0.45f)
        val barWidth = size.width / 11f
        heights.forEachIndexed { index, ratio ->
            val x = index * barWidth * 2f + barWidth / 2f
            val height = size.height * ratio
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (size.height - height) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
private fun PlusMenuOverlay(
    onDismiss: () -> Unit,
    onAction: (MilingHomeAction) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
            .testTag("plus_menu_scrim")
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .widthIn(max = 600.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp)
                    .offset(y = 53.dp)
                    .width(128.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .testTag("plus_menu")
            ) {
                MenuPointer(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 13.dp)
                        .requiredSize(width = 20.dp, height = 12.dp)
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 11.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MilingSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder),
                    shadowElevation = 2.dp
                ) {
                    Column {
                        plusActions.forEachIndexed { index, item ->
                            PlusMenuItem(
                                item = item,
                                onClick = { onAction(item.action) }
                            )
                            if (index < plusActions.lastIndex) {
                                Spacer(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MilingDivider)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuPointer(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val triangle = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(triangle, color = MilingSurface)
        drawLine(
            color = MilingBorder,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = MilingBorder,
            start = Offset(size.width / 2f, 0f),
            end = Offset(0f, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun PlusMenuItem(
    item: PlusAction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(item.testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MilingPrimary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = item.label,
            color = MilingTextPrimary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Preview(showBackground = true, widthDp = 432, heightDp = 911)
@Composable
private fun MilingHomePreview() {
    MilingTheme {
        MilingHomeScreen(onAction = {})
    }
}
