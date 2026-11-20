package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.theme.PakAmber
import com.example.ui.theme.PakCharcoal
import com.example.ui.theme.PakError
import com.example.ui.theme.PakGreen
import com.example.ui.theme.PakGreenDark
import com.example.ui.theme.PakGreenLight
import com.example.ui.theme.PakOutline
import com.example.ui.theme.PakSubtleText
import com.example.ui.theme.PakWhite
import com.example.ui.viewmodel.AdminViewModel

@Composable
fun AdminDashboardScreen(
    isAuthorizedAdmin: Boolean,
    adminViewModel: AdminViewModel,
    onBack: () -> Unit
) {
    if (!isAuthorizedAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PakWhite)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("رسائی ممنوع: یہ سیکشن صرف مجاز ایڈمنسٹریٹرز کے لیے ہے", color = PakError)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = PakGreen)) {
                    Text("واپس جائیں")
                }
            }
        }
        return
    }

    val usersState by adminViewModel.allUsers.collectAsStateWithLifecycle()
    val channelsState by adminViewModel.allChannels.collectAsStateWithLifecycle()
    val videosState by adminViewModel.allVideos.collectAsStateWithLifecycle()
    val reportsState by adminViewModel.allReports.collectAsStateWithLifecycle()
    val config by adminViewModel.platformConfig.collectAsStateWithLifecycle()
    val statusMessage by adminViewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            adminViewModel.clearMessage()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var suspendTargetUser by remember { mutableStateOf<UserProfile?>(null) }
    var suspendReason by remember { mutableStateOf("پلیٹ فارم کے قواعد کی خلاف ورزی") }

    Scaffold(
        containerColor = PakWhite,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "واپس")
                }
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = PakGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PakTube ایڈمن ڈیش بورڈ",
                    style = MaterialTheme.typography.titleLarge,
                    color = PakCharcoal
                )
            }
            HorizontalDivider(color = PakOutline)

            val tabs = listOf("اعداد و شمار", "رپورٹس", "ویڈیوز", "چینلز", "صارفین")
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = PakWhite,
                contentColor = PakGreen,
                edgePadding = 12.dp
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    val usersCount = (usersState as? UiState.Success)?.data?.size ?: 0
                    val channelsCount = (channelsState as? UiState.Success)?.data?.size ?: 0
                    val videosList = (videosState as? UiState.Success)?.data ?: emptyList()
                    val shortsCount = videosList.count { it.shortFlag }
                    val normalCount = videosList.count { !it.shortFlag }
                    val pendingReports = (reportsState as? UiState.Success)?.data?.count { it.status == "PENDING" } ?: 0

                    var maxShortText by remember(config.maxShortDurationSeconds) {
                        mutableStateOf(config.maxShortDurationSeconds.toString())
                    }
                    var maxNormalText by remember(config.maxNormalDurationSeconds) {
                        mutableStateOf(config.maxNormalDurationSeconds.toString())
                    }
                    var maxMbText by remember(config.maxUploadSizeMb) {
                        mutableStateOf(config.maxUploadSizeMb.toString())
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "پلیٹ فارم کے اعداد و شمار",
                                style = MaterialTheme.typography.titleLarge,
                                color = PakCharcoal
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AdminStatCard("صارفین", usersCount.toString(), Modifier.weight(1f))
                                AdminStatCard("چینلز", channelsCount.toString(), Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AdminStatCard("نارمل ویڈیوز", normalCount.toString(), Modifier.weight(1f))
                                AdminStatCard("شارٹس", shortsCount.toString(), Modifier.weight(1f))
                                AdminStatCard("زیرِ التواء رپورٹس", pendingReports.toString(), Modifier.weight(1f))
                            }
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = PakOutline)
                            Text(
                                text = "ویڈیو دورانیہ اور اپ لوڈ کی حدود",
                                style = MaterialTheme.typography.titleLarge,
                                color = PakCharcoal
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = maxShortText,
                                onValueChange = { maxShortText = it.filter(Char::isDigit) },
                                label = { Text("شارٹس کا زیادہ سے زیادہ دورانیہ (10-180 سیکنڈ)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = maxNormalText,
                                onValueChange = { maxNormalText = it.filter(Char::isDigit) },
                                label = { Text("نارمل ویڈیو کا زیادہ سے زیادہ دورانیہ (30-600 سیکنڈ)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = maxMbText,
                                onValueChange = { maxMbText = it.filter(Char::isDigit) },
                                label = { Text("زیادہ سے زیادہ فائل سائز (1-25 MB)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    adminViewModel.updateDurationLimits(
                                        maxShortSeconds = maxShortText.toLongOrNull() ?: 60L,
                                        maxNormalSeconds = maxNormalText.toLongOrNull() ?: 180L,
                                        maxUploadMb = maxMbText.toLongOrNull() ?: 12L
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PakGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("admin_save_limits_button")
                            ) {
                                Text("حدود محفوظ کریں")
                            }
                        }
                    }
                }
                1 -> {
                    when (val st = reportsState) {
                        UiState.Loading -> LoadingStateView()
                        is UiState.Error -> ErrorStateView(st.message)
                        is UiState.Success -> {
                            if (st.data.isEmpty()) {
                                EmptyStateView("کوئی رپورٹ موجود نہیں", "صارفین کی تمام رپورٹس یہاں آئیں گی")
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(st.data, key = { it.id }) { rep ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = PakWhite),
                                            border = BorderStroke(1.dp, PakOutline),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "${rep.targetType}: ${rep.targetTitle}",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = PakCharcoal,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Surface(
                                                        color = if (rep.status == "PENDING") PakAmber.copy(alpha = 0.2f) else PakGreenLight,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = rep.status,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("وجہ: ${rep.reason}", style = MaterialTheme.typography.bodyMedium, color = PakError)
                                                if (rep.details.isNotBlank()) {
                                                    Text(rep.details, style = MaterialTheme.typography.bodyMedium, color = PakSubtleText)
                                                }
                                                if (rep.status == "PENDING") {
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Button(
                                                            onClick = { adminViewModel.resolveReport(rep, "RESOLVED", removeContent = true) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = PakError)
                                                        ) {
                                                            Text("مواد ہٹائیں اور حل کریں")
                                                        }
                                                        OutlinedButton(
                                                            onClick = { adminViewModel.resolveReport(rep, "DISMISSED", removeContent = false) }
                                                        ) {
                                                            Text("مسترد کریں")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    when (val st = videosState) {
                        UiState.Loading -> LoadingStateView()
                        is UiState.Error -> ErrorStateView(st.message)
                        is UiState.Success -> {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(st.data, key = { it.id }) { vid ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = PakWhite),
                                        border = BorderStroke(1.dp, PakOutline)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(vid.title, style = MaterialTheme.typography.titleMedium, color = PakCharcoal)
                                                Text(
                                                    "${vid.channelName} • ${vid.status} • ${if (vid.shortFlag) "شارٹ" else "ویڈیو"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (vid.status == "ACTIVE") PakGreenDark else PakError
                                                )
                                            }
                                            OutlinedButton(onClick = { adminViewModel.toggleVideoStatus(vid) }) {
                                                Text(if (vid.status == "ACTIVE") "ہٹائیں" else "بحال کریں")
                                            }
                                            IconButton(onClick = { adminViewModel.deleteVideoPermanently(vid.id) }) {
                                                Icon(Icons.Filled.DeleteForever, contentDescription = "حذف", tint = PakError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    when (val st = channelsState) {
                        UiState.Loading -> LoadingStateView()
                        is UiState.Error -> ErrorStateView(st.message)
                        is UiState.Success -> {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(st.data, key = { it.id }) { chan ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = PakWhite),
                                        border = BorderStroke(1.dp, PakOutline)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(chan.name, style = MaterialTheme.typography.titleMedium, color = PakCharcoal)
                                                Text(
                                                    "${chan.handle} • ${chan.subscriberCount} سبسکرائبرز • ${if (chan.activeFlag) "فعال" else "معطل"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = PakSubtleText
                                                )
                                            }
                                            OutlinedButton(onClick = { adminViewModel.toggleChannelActive(chan) }) {
                                                Text(if (chan.activeFlag) "معطل کریں" else "فعال کریں")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    when (val st = usersState) {
                        UiState.Loading -> LoadingStateView()
                        is UiState.Error -> ErrorStateView(st.message)
                        is UiState.Success -> {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(st.data, key = { it.userId }) { usr ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = PakWhite),
                                        border = BorderStroke(1.dp, PakOutline)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(usr.displayName, style = MaterialTheme.typography.titleMedium, color = PakCharcoal)
                                            Text(usr.email, style = MaterialTheme.typography.bodyMedium, color = PakSubtleText)
                                            Text(
                                                text = if (usr.suspendedFlag) "حالت: معطل (${usr.suspendedReason})" else "حالت: فعال",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (usr.suspendedFlag) PakError else PakGreenDark
                                            )
                                            if (usr.userId != adminViewModel.adminUserId) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = {
                                                            if (usr.suspendedFlag) {
                                                                adminViewModel.toggleUserSuspension(usr, "")
                                                            } else {
                                                                suspendTargetUser = usr
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (usr.suspendedFlag) PakGreen else PakError
                                                        )
                                                    ) {
                                                        Text(if (usr.suspendedFlag) "بحال کریں" else "اکاؤنٹ معطل کریں")
                                                    }
                                                    OutlinedButton(onClick = { adminViewModel.grantAdmin(usr) }) {
                                                        Text("ایڈمن بنائیں")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suspendTargetUser?.let { target ->
        AlertDialog(
            onDismissRequest = { suspendTargetUser = null },
            containerColor = PakWhite,
            title = { Text("${target.displayName} کو معطل کریں") },
            text = {
                OutlinedTextField(
                    value = suspendReason,
                    onValueChange = { if (it.length <= 300) suspendReason = it },
                    label = { Text("معطلی کی وجہ") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.toggleUserSuspension(target, suspendReason)
                        suspendTargetUser = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PakError)
                ) {
                    Text("معطل کریں")
                }
            },
            dismissButton = {
                TextButton(onClick = { suspendTargetUser = null }) {
                    Text("منسوخ کریں")
                }
            }
        )
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PakGreenLight.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, PakOutline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = PakGreenDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = PakCharcoal
            )
        }
    }
}
