/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.auth

import funlauncher.database.dao.AccountDao
import funlauncher.managers.PathManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class AccountManager(pathManager: PathManager) {
    private val accountDao = AccountDao()
    private var _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accountsFlow: StateFlow<List<Account>> = _accounts.asStateFlow()

    init {
        migrateFromJson(pathManager)
        accountDao.clearMicrosoftTokens()
        _accounts.value = accountDao.getAll()
    }

    private fun migrateFromJson(pathManager: PathManager) {
        val accountsFile = pathManager.getAppDataDirectory().resolve("accounts.json").toFile()
        if (accountsFile.exists()) {
            println("--- Запуск миграции аккаунтов из accounts.json ---")
            val content = try {
                accountsFile.readText()
            } catch (e: Exception) {
                println("Критическая ошибка чтения файла accounts.json для миграции:")
                e.printStackTrace()
                return
            }

            if (content.isNotBlank()) {
                val json = Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    serializersModule = SerializersModule {
                        polymorphic(Account::class) {
                            subclass(OfflineAccount::class)
                            subclass(MicrosoftAccount::class)
                        }
                    }
                }
                try {
                    val jsonAccounts = json.decodeFromString<List<Account>>(content)
                    jsonAccounts.forEach { account ->
                        if (!accountDao.exists(account.username)) {
                            accountDao.add(account)
                            println("Мигрирован аккаунт: ${account.username}")
                        }
                    }
                } catch (e: SerializationException) {
                    println("Ошибка десериализации accounts.json во время миграции:")
                    e.printStackTrace()
                }
            }
            // Переименовываем файл после успешной миграции, чтобы избежать повторной миграции
            accountsFile.renameTo(pathManager.getAppDataDirectory().resolve("accounts.json.migrated").toFile())
            println("--- Миграция аккаунтов завершена ---")
        }
    }

    fun loadAccounts(): List<Account> {
        val loaded = accountDao.getAll()
        _accounts.value = loaded
        println("--- Загрузка аккаунтов из БД, найдено ${loaded.size} ---")
        return loaded
    }

    fun hasLicensedAccount(): Boolean {
        return _accounts.value.any { it.isLicensed }
    }

    fun addAccount(account: Account): Boolean {
        if (accountDao.exists(account.username)) {
            println("Аккаунт с именем ${account.username} уже существует.")
            return false
        }

        accountDao.add(account)
        _accounts.value = accountDao.getAll()
        return true
    }

    fun loginWithMicrosoft(): Boolean = synchronized(MateriaAuthenticator.sessionLock) {
        val profile = MateriaAuthenticator().login()
        accountDao.saveMicrosoft(MicrosoftAccount(
            username = profile.name, uuid = profile.id, accessToken = "", skinUrl = profile.skinUrl
        ))
        _accounts.value = accountDao.getAll()
        true
    }

    fun logoutFromMicrosoft() = synchronized(MateriaAuthenticator.sessionLock) {
        MateriaAuthenticator().logout(accountDao.getAll().filterIsInstance<MicrosoftAccount>().map { it.uuid })
        accountDao.deleteAllMicrosoft()
        _accounts.value = accountDao.getAll()
    }
    fun deleteAccount(account: Account) = synchronized(MateriaAuthenticator.sessionLock) {
        if (account is MicrosoftAccount) MateriaAuthenticator().forget(account.uuid)
        accountDao.delete(account)
        _accounts.value = accountDao.getAll()
    }

    fun getAccounts(): List<Account> {
        return _accounts.value
    }
}
