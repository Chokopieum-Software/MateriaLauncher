package ui.viewmodel

import funlauncher.auth.Account
import funlauncher.auth.AccountManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MicrosoftLoginState { Idle, Waiting, Success, Error }

class AccountViewModel(
    private val accountManager: AccountManager,
    private val coroutineScope: CoroutineScope
) {
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()
    private val _loginState = MutableStateFlow(MicrosoftLoginState.Idle)
    val loginState = _loginState.asStateFlow()
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError = _loginError.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadAccounts()
    }

    fun loadAccounts() {
        coroutineScope.launch {
            _accounts.value = accountManager.loadAccounts()
        }
    }

    fun loginWithMicrosoft() {
        if (_isLoggingIn.value) return
        coroutineScope.launch {
            _isLoggingIn.value = true
            _loginState.value = MicrosoftLoginState.Waiting
            _loginError.value = null
            _errorMessage.value = null
            try {
                withContext(Dispatchers.IO) { accountManager.loginWithMicrosoft() }
                loadAccounts()
                _loginState.value = MicrosoftLoginState.Success
            } catch (e: kotlinx.coroutines.CancellationException) {
                _loginState.value = MicrosoftLoginState.Idle
                throw e
            } catch (e: Exception) {
                _loginError.value = e.message ?: "Не удалось войти через Microsoft."
                _loginState.value = MicrosoftLoginState.Error
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    fun dismissLoginResult() {
        if (_isLoggingIn.value) return
        _loginState.value = MicrosoftLoginState.Idle
        _loginError.value = null
    }

    fun logoutFromMicrosoft() {
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) { accountManager.logoutFromMicrosoft() }
                loadAccounts()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Не удалось выйти из Microsoft."
            }
        }
    }

    fun addOfflineAccount(username: String, onSuccess: () -> Unit = {}) {
        coroutineScope.launch {
            _errorMessage.value = null
            try {
                val newAccount = funlauncher.auth.OfflineAccount.create(username)
                if (withContext(Dispatchers.IO) { accountManager.addAccount(newAccount) }) {
                    loadAccounts()
                    onSuccess()
                } else {
                    _errorMessage.value = "Аккаунт с таким именем уже существует."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Не удалось добавить офлайн-аккаунт."
            }
        }
    }

    fun deleteAccount(account: Account) {
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) { accountManager.deleteAccount(account) }
                loadAccounts()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Не удалось удалить аккаунт."
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
