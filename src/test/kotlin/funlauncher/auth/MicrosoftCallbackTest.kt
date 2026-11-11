package funlauncher.auth

import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import kotlin.test.*

class MicrosoftCallbackTest {
    @Test fun msalUsesCustomRedirectQueryPkceAndAccountPicker() {
        val state = MicrosoftCallback.randomValue()
        val verifier = MicrosoftCallback.randomValue()
        val parameters = MateriaAuthenticator().authorizationParameters(state, verifier).requestParameters()
        assertEquals(listOf(MicrosoftCallback.redirectUri.toString()), parameters["redirect_uri"])
        assertEquals(listOf("query"), parameters["response_mode"])
        assertEquals(listOf(state), parameters["state"])
        assertEquals(listOf("S256"), parameters["code_challenge_method"])
        assertEquals(listOf(MicrosoftCallback.challenge(verifier)), parameters["code_challenge"])
        assertEquals(listOf("select_account"), parameters["prompt"])
    }

    @Test fun pkceMatchesRfc7636() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            MicrosoftCallback.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun rejectsWrongDestinationsAndAmbiguousParameters() {
        val state = MicrosoftCallback.randomValue()
        val uri = MicrosoftCallback.redirectUri
        assertEquals("a+b", MicrosoftCallback.parameters("$uri?state=$state&code=a%2Bb")["code"])
        assertEquals("a+b", MicrosoftCallback.parameters("$uri/?state=$state&code=a%2Bb")["code"])
        for (url in listOf(
            "http://localhost?state=$state&code=secret", "$uri/other?state=$state",
            "$uri?state=../../test", "$uri?state=$state&state=$state",
            "$uri?state=$state&code=a&code=b", "$uri?state=$state#code=a"
        )) assertFailsWith<IllegalArgumentException> { MicrosoftCallback.parameters(url) }
    }

    @Test fun forwardsOnlyMatchingStateAndRemovesEndpoint() {
        val root = Files.createTempDirectory("materia-callback-test")
        val state = MicrosoftCallback.randomValue()
        val endpoint = root.resolve("$state.port")
        try {
            val code = MicrosoftCallback.receiveCode(state, root) {
                val port = Files.readString(endpoint).toInt()
                for (responseState in listOf(MicrosoftCallback.randomValue(), state)) {
                    Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                        DataOutputStream(socket.getOutputStream()).writeUTF(
                            "${MicrosoftCallback.redirectUri}/?state=$responseState&code=test-code")
                    }
                }
            }
            assertEquals("test-code", code)
            assertFalse(Files.exists(endpoint))
        } finally { Files.deleteIfExists(root) }
    }

    @Test fun reportsDeniedLoginAndRemovesEndpoint() {
        val root = Files.createTempDirectory("materia-callback-denied")
        val state = MicrosoftCallback.randomValue()
        val endpoint = root.resolve("$state.port")
        try {
            assertFailsWith<IllegalStateException> {
                MicrosoftCallback.receiveCode(state, root) {
                    Socket(InetAddress.getLoopbackAddress(), Files.readString(endpoint).toInt()).use { socket ->
                        DataOutputStream(socket.getOutputStream()).writeUTF(
                            "${MicrosoftCallback.redirectUri}?state=$state&error=access_denied")
                    }
                }
            }
            assertFalse(Files.exists(endpoint))
        } finally { Files.deleteIfExists(root) }
    }
}
