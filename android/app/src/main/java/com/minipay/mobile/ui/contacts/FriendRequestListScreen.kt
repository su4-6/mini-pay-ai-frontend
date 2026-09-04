package com.minipay.mobile.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipay.mobile.chat.ContactsViewModel
import com.minipay.mobile.chat.ReceivedRequest
import com.minipay.mobile.network.AutoRefreshEffect
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary

@Composable
fun FriendRequestListRoute(onBack: () -> Unit, viewModel: ContactsViewModel = hiltViewModel()) {
    val requests by viewModel.receivedRequests.collectAsStateWithLifecycle()
    val error by viewModel.requestError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AutoRefreshEffect(onRefresh = viewModel::refresh)
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearRequestError()
        }
    }
    FriendRequestListScreen(
        requests = requests,
        onBack = onBack,
        onAccept = viewModel::acceptRequest,
        onReject = viewModel::rejectRequest
    )
}

@Composable
private fun FriendRequestListScreen(
    requests: List<ReceivedRequest>,
    onBack: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MilingBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = MilingSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text("新的朋友", style = MaterialTheme.typography.titleLarge, color = MilingTextPrimary)
        }
        if (requests.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MilingTextMuted, modifier = Modifier.size(48.dp))
                Text("暂无新的好友申请", color = MilingTextMuted)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(requests, key = { it.id }) { request ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MilingPrimary, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(request.nickname, style = MaterialTheme.typography.titleMedium, color = MilingTextPrimary)
                            Text(request.phoneMasked ?: request.minipayNo, color = MilingTextMuted)
                        }
                        OutlinedButton(onClick = { onReject(request.id) }) { Text("拒绝") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onAccept(request.id) }) { Text("接受") }
                    }
                }
            }
        }
    }
}
