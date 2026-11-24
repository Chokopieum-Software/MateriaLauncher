/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens.wizard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.auth.AccountManager
import funlauncher.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.chokopieum.software.ui.ProgressState
import ui.widgets.MicrosoftLoginFeedback

enum class WizardStep {
    WELCOME,
    APPEARANCE,
    ACCOUNT,
    FINISH
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun FirstRunWizard(
    accountManager: AccountManager?,
    initialTheme: Theme,
    onThemeChange: (Theme) -> Unit,
    onWizardComplete: suspend () -> Unit
) {
    var currentStep by remember { mutableStateOf(WizardStep.WELCOME) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var authSuccess by remember { mutableStateOf(false) }
    var authAttempt by remember { mutableStateOf(0) }

    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun handleLogin() {
        if (isAuthenticating) return
        authAttempt++
        if (accountManager == null) {
            authSuccess = true
            return
        }
        scope.launch {
            isAuthenticating = true
            authError = null
            authSuccess = false
            try {
                val success = withContext(Dispatchers.IO) { accountManager.loginWithMicrosoft() }
                if (success) {
                    authSuccess = true
                } else {
                    authError = "Не удалось войти в аккаунт Microsoft."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                authError = "Критическая ошибка: ${e.message}"
            } finally {
                isAuthenticating = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Заголовок и прогресс
            Text(
                text = "Настройка Materia Launcher",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { (currentStep.ordinal + 1) / WizardStep.values().size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Контент шагов
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(targetState = currentStep) { step ->
                    when (step) {
                        WizardStep.WELCOME -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Добро пожаловать в Materia!", style = MaterialTheme.typography.headlineLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Давайте пройдем быструю настройку лаунчера для его первого запуска.",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        WizardStep.APPEARANCE -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Внешний вид", style = MaterialTheme.typography.headlineSmall)
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Тема", style = MaterialTheme.typography.titleMedium)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val themeOptions = ui.theme.availableThemes
                                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                            themeOptions.forEachIndexed { index, option ->
                                                SegmentedButton(
                                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                                                    onClick = { onThemeChange(option) },
                                                    selected = initialTheme == option
                                                ) { Text(ui.theme.themeLabel(option)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        WizardStep.ACCOUNT -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Аккаунт Minecraft", style = MaterialTheme.typography.headlineSmall)
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Для игры требуется лицензионный аккаунт", style = MaterialTheme.typography.bodyLarge)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        if (isAuthenticating || authSuccess || authError != null) {
                                            key(authAttempt) {
                                                MicrosoftLoginFeedback(
                                                    state = when {
                                                        isAuthenticating -> ProgressState.Loading
                                                        authSuccess -> ProgressState.Success
                                                        else -> ProgressState.Error
                                                    },
                                                    error = authError
                                                )
                                            }
                                        }
                                        if (!isAuthenticating && !authSuccess) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(onClick = ::handleLogin, modifier = Modifier.height(48.dp)) {
                                                Text(if (authError != null) "Попробовать снова" else "Войти через Microsoft")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        WizardStep.FINISH -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Всё готово!", style = MaterialTheme.typography.headlineLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Настройка завершена. Теперь вы можете наслаждаться игрой.",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            // Навигация
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep != WizardStep.WELCOME) {
                    OutlinedButton(enabled = !isAuthenticating && !saving, onClick = {
                        currentStep = WizardStep.values()[currentStep.ordinal - 1] 
                    }) {
                        Text("Назад")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (currentStep != WizardStep.FINISH) {
                    Button(enabled = !isAuthenticating && !saving, onClick = {
                        currentStep = WizardStep.values()[currentStep.ordinal + 1] 
                    }) {
                        Text("Далее")
                    }
                } else {
                    Button(enabled = !saving, onClick = {
                        if (saving) return@Button
                        saving = true
                        saveError = null
                        scope.launch {
                            try {
                                onWizardComplete()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                saveError = "Не удалось сохранить настройки: ${e.localizedMessage}"
                            } finally {
                                saving = false
                            }
                        }
                    }) {
                        Text("Завершить")
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun FirstRunWizardPreview() {
    var theme by remember { mutableStateOf(Theme.Dark) }
    
    MaterialTheme {
        FirstRunWizard(
            accountManager = null, // Mock account manager can be null for preview
            initialTheme = theme,
            onThemeChange = { theme = it },
            onWizardComplete = {}
        )
    }
}
