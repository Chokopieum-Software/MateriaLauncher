/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.database.dao

import funlauncher.auth.Account
import funlauncher.auth.MicrosoftAccount
import funlauncher.auth.OfflineAccount
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class AccountDao {

    fun getAll(): List<Account> {
        return transaction {
            // Repair each legacy row by its primary key: several rows can share the zero UUID.
            val broken = Accounts.selectAll().where {
                (Accounts.type eq "offline") and
                    ((Accounts.uuid eq "00000000-0000-0000-0000-000000000000") or
                        (Accounts.uuid eq "00000000000000000000000000000000") or (Accounts.uuid eq ""))
            }.toList()
            broken.forEach { row ->
                Accounts.update({ Accounts.id eq row[Accounts.id] }) {
                    it[uuid] = OfflineAccount.uuidFor(row[Accounts.username])
                }
            }
            Accounts.selectAll().map { toAccount(it) }
        }
    }

    fun add(account: Account) {
        transaction {
            Accounts.insert {
                it[username] = account.username
                it[uuid] = if (account is OfflineAccount &&
                    (account.uuid.isBlank() || account.uuid.replace("-", "").all { c -> c == '0' })) {
                    OfflineAccount.uuidFor(account.username)
                } else account.uuid
                it[accessToken] = if (account is MicrosoftAccount) "" else account.accessToken
                it[isLicensed] = account.isLicensed
                when (account) {
                    is MicrosoftAccount -> {
                        it[type] = "microsoft"
                        it[skinUrl] = account.skinUrl
                    }
                    is OfflineAccount -> {
                        it[type] = "offline"
                    }
                }
            }
        }
    }

    fun clearMicrosoftTokens() {
        transaction {
            Accounts.update({ Accounts.type eq "microsoft" }) { it[accessToken] = "" }
        }
    }

    fun saveMicrosoft(account: MicrosoftAccount) {
        transaction {
            val updated = Accounts.update({ (Accounts.uuid eq account.uuid) and (Accounts.type eq "microsoft") }) {
                it[username] = account.username
                it[skinUrl] = account.skinUrl
                it[accessToken] = ""
                it[isLicensed] = true
            }
            if (updated == 0) add(account)
        }
    }

    fun delete(account: Account) {
        transaction {
            val accountType = if (account is MicrosoftAccount) "microsoft" else "offline"
            Accounts.deleteWhere {
                (Accounts.uuid eq account.uuid) and (Accounts.username eq account.username) and (Accounts.type eq accountType)
            }
        }
    }

    fun deleteAllMicrosoft() {
        transaction {
            Accounts.deleteWhere { Accounts.type eq "microsoft" }
        }
    }

    fun exists(username: String): Boolean {
        return transaction {
            Accounts.selectAll().where { Accounts.username eq username }.count() > 0
        }
    }

    private fun toAccount(row: ResultRow): Account {
        val type = row[Accounts.type]
        return when (type) {
            "microsoft" -> MicrosoftAccount(
                username = row[Accounts.username],
                uuid = row[Accounts.uuid],
                accessToken = "",
                skinUrl = row[Accounts.skinUrl],
                isLicensed = row[Accounts.isLicensed]
            )
            "offline" -> OfflineAccount(
                username = row[Accounts.username],
                uuid = row[Accounts.uuid],
                accessToken = row[Accounts.accessToken],
                isLicensed = row[Accounts.isLicensed]
            )
            else -> throw IllegalArgumentException("Unknown account type: $type")
        }
    }
}
