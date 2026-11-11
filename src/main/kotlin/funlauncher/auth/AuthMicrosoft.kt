package funlauncher.auth

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.microsoft.aad.msal4j.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MateriaAuthenticator {
    companion object { val sessionLock = Any() }
    private val scopes = setOf("XboxLive.Signin", "offline_access")
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val tokenCacheAspect = object : ITokenCacheAccessAspect {
        override fun beforeCacheAccess(context: ITokenCacheAccessContext) {
            val data = SecureAuthStore.readCache()
            if (!data.isNullOrBlank()) context.tokenCache().deserialize(data)
        }
        override fun afterCacheAccess(context: ITokenCacheAccessContext) {
            if (context.hasCacheChanged()) {
                SecureAuthStore.writeCache(context.tokenCache().serialize())
            }
        }
    }
    private val app = PublicClientApplication.builder(MicrosoftCallback.CLIENT_ID)
        .authority("https://login.microsoftonline.com/consumers/")
        .setTokenCacheAccessAspect(tokenCacheAspect).build()

    fun login(): MinecraftProfile {
        MicrosoftCallback.ensureRegistered()
        return profile(acquireTokenInteractive())
    }

    /** Refresh for the exact Microsoft account associated with this Minecraft UUID. */
    fun refresh(uuid: String): MinecraftProfile {
        val homeId = SecureAuthStore.get("Minecraft:$uuid")
            ?: error("Для этого аккаунта требуется повторный вход Microsoft.")
        val account = app.accounts.join().firstOrNull { it.homeAccountId() == homeId }
            ?: error("Сессия Microsoft отсутствует. Войдите в аккаунт повторно.")
        val result = try {
            app.acquireTokenSilently(SilentParameters.builder(scopes, account).build()).join()
        } catch (e: Exception) {
            throw IllegalStateException("Не удалось обновить сессию Microsoft. Проверьте сеть или войдите повторно.", e)
        }
        val profile = profile(result)
        check(profile.id == uuid) { "Microsoft вернул другой профиль Minecraft. Войдите повторно." }
        return profile
    }

    private fun profile(result: IAuthenticationResult): MinecraftProfile {
        val xbox = authXboxLive(result.accessToken())
        val xsts = authXSTS(xbox.token)
        val profile = getMinecraftProfile(authMinecraft(xsts.userHash, xsts.token))
        SecureAuthStore.set("Minecraft:${profile.id}", result.account().homeAccountId())
        return profile
    }

    fun logout(uuids: List<String>) {
        // Each operation creates a new MSAL client; clearing storage also recovers a corrupt cache.
        SecureAuthStore.clearCache()
        uuids.forEach { SecureAuthStore.delete("Minecraft:$it") }
    }

    fun forget(uuid: String) {
        val homeId = SecureAuthStore.get("Minecraft:$uuid")
        if (homeId != null) {
            app.accounts.join().firstOrNull { it.homeAccountId() == homeId }?.let { app.removeAccount(it).join() }
            SecureAuthStore.delete("Minecraft:$uuid")
        }
    }

    private fun acquireTokenInteractive(): IAuthenticationResult {
        val state = MicrosoftCallback.randomValue()
        val verifier = MicrosoftCallback.randomValue()
        val code = MicrosoftCallback.awaitCode(state) {
            app.getAuthorizationRequestUrl(authorizationParameters(state, verifier)).toURI()
        }
        return app.acquireToken(AuthorizationCodeParameters.builder(code, MicrosoftCallback.redirectUri)
            .scopes(scopes).codeVerifier(verifier).build()).join()
    }

    @Suppress("DEPRECATION")
    internal fun authorizationParameters(state: String, verifier: String): AuthorizationRequestUrlParameters =
        AuthorizationRequestUrlParameters.builder(MicrosoftCallback.redirectUri.toString(), scopes)
                .state(state).codeChallenge(MicrosoftCallback.challenge(verifier)).codeChallengeMethod("S256")
                .prompt(Prompt.SELECT_ACCOUNT)
                // MSAL4J 1.26 rewrites ResponseMode.QUERY to FORM_POST. A custom scheme needs query.
                .extraQueryParameters(mapOf("response_mode" to "query"))
                .build()
    // --- MINECRAFT API ---

    data class XboxResponse(val token: String, val userHash: String)
    data class XSTSResponse(val token: String, val userHash: String)
    data class McTokenResponse(@SerializedName("access_token") val accessToken: String)
    data class MinecraftProfile(
        val id: String,
        val name: String,
        val accessToken: String,
        val skinUrl: String?,
        val isLicensed: Boolean
    )

    private fun authXboxLive(msToken: String): XboxResponse {
        val json = """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msToken"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
        val resp = postJson("https://user.auth.xboxlive.com/user/authenticate", json)
        val jsonObj = gson.fromJson(resp, JsonObject::class.java)
        return XboxResponse(
            jsonObj.get("Token").asString,
            jsonObj.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")[0].asJsonObject.get("uhs").asString
        )
    }

    private fun authXSTS(xboxToken: String): XSTSResponse {
        val json = """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xboxToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}"""
        val resp = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", json)
        val jsonObj = gson.fromJson(resp, JsonObject::class.java)
        val xuiClaims = jsonObj.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")[0].asJsonObject

        return XSTSResponse(
            token = jsonObj.get("Token").asString,
            userHash = xuiClaims.get("uhs").asString
        )
    }

    private fun authMinecraft(uhs: String, xstsToken: String): String {
        val json = """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
        val resp = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", json)
        return gson.fromJson(resp, McTokenResponse::class.java).accessToken
    }

    private fun getMinecraftProfile(mcToken: String): MinecraftProfile {
        val request = Request.Builder()
            .url("https://api.minecraftservices.com/minecraft/profile")
            .header("Authorization", "Bearer $mcToken")
            .build()

        client.newCall(request).execute().use {
            if (!it.isSuccessful) {
                if (it.code == 404) error("У аккаунта нет профиля Minecraft Java. Проверьте покупку игры и создание профиля.")
                error("Не удалось получить профиль Minecraft (HTTP ${it.code}). Попробуйте позже.")
            }

            val json = gson.fromJson(it.body.string(), JsonObject::class.java)
            val id = json.get("id").asString
            val name = json.get("name").asString

            // --- Логика вытаскивания скина ---
            var skinUrl: String? = null
            if (json.has("skins")) {
                val skins = json.getAsJsonArray("skins")
                if (skins.size() > 0) {
                    // Берем первый попавшийся скин (обычно он там один активный)
                    skinUrl = skins.get(0).asJsonObject.get("url").asString
                }
            }
            // ---------------------------------

            return MinecraftProfile(id, name, mcToken, skinUrl, isLicensed = true)
        }
    }

    private fun postJson(url: String, json: String): String {
        val request = Request.Builder().url(url).post(json.toRequestBody(jsonMediaType)).header("Accept", "application/json").build()
        client.newCall(request).execute().use {
            val responseBody = it.body.string()
            if (!it.isSuccessful) {
                val service = java.net.URI(url).host
                throw IllegalStateException("Ошибка авторизации $service (HTTP ${it.code}). Проверьте доступность сервиса и настройки аккаунта Xbox.")
            }
            return responseBody
        }
    }

}
