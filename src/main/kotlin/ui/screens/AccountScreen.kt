/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import funlauncher.auth.Account
import funlauncher.auth.AccountManager
import funlauncher.auth.MicrosoftAccount
import funlauncher.auth.OfflineAccount
import ui.viewmodel.AccountViewModel
import ui.viewmodel.MicrosoftLoginState
import ui.widgets.MicrosoftLoginFeedback
import org.chokopieum.software.ui.ProgressState
import ui.widgets.AvatarImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    accountManager: AccountManager,
    onDismiss: () -> Unit,
    onAccountSelected: (Account) -> Unit,
    colorScheme: ColorScheme
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { AccountViewModel(accountManager, coroutineScope) }
    val accounts by viewModel.accounts.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showAddAccountTypeDialog by remember { mutableStateOf(false) }
    var showAddOfflineAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    LaunchedEffect(loginState) {
        if (loginState == MicrosoftLoginState.Success) {
            kotlinx.coroutines.delay(2200)
            viewModel.dismissLoginResult()
        }
    }

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visibleState.currentState) {
        if (!visibleState.currentState && !visibleState.targetState) {
            onDismiss()
        }
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = androidx.compose.ui.unit.IntOffset(16, 50),
        onDismissRequest = { visibleState.targetState = false },
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
    ) {
        MaterialTheme(colorScheme = colorScheme) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(250))
            ) {
                Box(
                    modifier = Modifier
                        .width(350.dp)
                        .heightIn(max = 500.dp)
                        .shadow(24.dp, RoundedCornerShape(16.dp))
                        .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorScheme.surface)
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Аккаунты", color = colorScheme.onSurface) },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = colorScheme.surface
                                )
                            )
                        },
                        floatingActionButton = {
                            ExtendedFloatingActionButton(
                                text = { Text("Добавить") },
                                icon = { Icon(Icons.Default.Add, contentDescription = "Добавить аккаунт") },
                                onClick = { showAddAccountTypeDialog = true },
                                containerColor = colorScheme.primaryContainer,
                                contentColor = colorScheme.onPrimaryContainer
                            )
                        },
                        containerColor = Color.Transparent
                    ) { paddingValues ->
                        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                            if (accounts.isEmpty()) {
                                Text("Нет добавленных аккаунтов.", color = colorScheme.onSurface, modifier = Modifier.align(Alignment.Center))
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(accounts, key = { "${it::class.simpleName}:${it.uuid}:${it.username}" }) { account ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onAccountSelected(account) },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AvatarImage(
                                                    account = account,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = account.username,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = when (account) {
                                                            is OfflineAccount -> "Оффлайн"
                                                            is MicrosoftAccount -> if (account.isLicensed) "Microsoft" else "Microsoft (Xbox)"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { accountToDelete = account },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Удалить",
                                                        tint = colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
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

        if (loginState != MicrosoftLoginState.Idle) {
            Dialog(onDismissRequest = { if (!isLoggingIn) viewModel.dismissLoginResult() }) {
                Surface(
                    modifier = Modifier.padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MicrosoftLoginFeedback(
                            state = when (loginState) {
                                MicrosoftLoginState.Success -> ProgressState.Success
                                MicrosoftLoginState.Error -> ProgressState.Error
                                else -> ProgressState.Loading
                            },
                            error = loginError
                        )
                        if (loginState == MicrosoftLoginState.Error) {
                            TextButton(onClick = { viewModel.dismissLoginResult() }) { Text("Закрыть") }
                        }
                    }
                }
            }
        }

        if (showAddAccountTypeDialog) {
            AlertDialog(
                onDismissRequest = { showAddAccountTypeDialog = false },
                title = { Text("Выберите тип аккаунта", color = colorScheme.onSurface) },
                text = { Text("Какой аккаунт вы хотите добавить?", color = colorScheme.onSurfaceVariant) },
                containerColor = colorScheme.surface,
                confirmButton = {
                    Button(
                        onClick = {
                            showAddAccountTypeDialog = false
                            viewModel.loginWithMicrosoft()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) { Text("Microsoft") }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showAddAccountTypeDialog = false
                            showAddOfflineAccountDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer
                        )
                    ) { Text("Оффлайн") }
                }
            )
        }

        if (showAddOfflineAccountDialog) {
            var username by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddOfflineAccountDialog = false },
                title = { Text("Добавить оффлайн аккаунт", color = colorScheme.onSurface) },
                containerColor = colorScheme.surface,
                text = {
                    Column {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Имя пользователя") },
                            isError = errorMessage != null,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outline,
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface,
                                cursorColor = colorScheme.primary
                            )
                        )
                        errorMessage?.let {
                            Text(it, color = colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.addOfflineAccount(username) {
                                showAddOfflineAccountDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) { Text("Добавить") }
                },
                dismissButton = {
                    Button(
                        onClick = { showAddOfflineAccountDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer
                        )
                    ) { Text("Отмена") }
                }
            )
        }

        accountToDelete?.let { account ->
            AlertDialog(
                onDismissRequest = { accountToDelete = null },
                title = { Text("Подтверждение", color = colorScheme.onSurface) },
                text = { Text("Вы уверены, что хотите удалить аккаунт '${account.username}'?", color = colorScheme.onSurfaceVariant) },
                containerColor = colorScheme.surface,
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteAccount(account)
                            accountToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                    ) { Text("Удалить", color = colorScheme.onError) }
                },
                dismissButton = {
                    Button(
                        onClick = { accountToDelete = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer
                        )
                    ) { Text("Отмена") }
                }
            )
        }

        errorMessage?.let {
            AlertDialog(
                onDismissRequest = { viewModel.clearErrorMessage() },
                title = { Text("Ошибка", color = colorScheme.onSurface) },
                text = { Text(it, color = colorScheme.onSurfaceVariant) },
                containerColor = colorScheme.surface,
                confirmButton = {
                    Button(
                        onClick = { viewModel.clearErrorMessage() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) { Text("OK") }
                }
            )
        }
    }
}
