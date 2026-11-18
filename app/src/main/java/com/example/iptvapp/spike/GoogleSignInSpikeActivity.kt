package com.example.iptvapp.spike

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.launch

// Public by design: this is the token audience, not a credential, and it ships
// in every APK. The Android client ID is deliberately absent -- it is never
// passed in code. Google matches the calling app to it by package name and
// signing certificate, which is why the SHA-1 registration has to be right.
private const val WEB_CLIENT_ID =
    "145618049078-s6qj4kia2313nbpg3orqmcq0fro7ok64.apps.googleusercontent.com"

private const val TAG = "AuthSpike"

/**
 * Phase 0.5 A3 spike: obtain a Google ID token from Credential Manager and show
 * it, so the Supabase id_token exchange (A4) can be run from a terminal without
 * any auth code in the app.
 *
 * Throwaway. It answers one question -- does the Android OAuth client
 * registered for com.tribaltv.app plus this APK's signing certificate actually
 * produce a token Supabase will accept -- and it is deleted once that is known.
 *
 * It has its own LAUNCHER entry so it can be opened without touching
 * MainActivity, which is IPTV code being reused verbatim.
 */
class GoogleSignInSpikeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpikeScreen() }
    }

    private suspend fun requestIdToken(rawNonce: String?): Pair<String, String?> = try {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(WEB_CLIENT_ID)
            // false: show the account picker even for accounts that have never
            // signed in to this app. true fails with NoCredentialException on a
            // fresh device and reads like a configuration error.
            .setFilterByAuthorizedAccounts(false)
            // Only valid when filterByAuthorizedAccounts is true.
            .setAutoSelectEnabled(false)
            .apply { if (rawNonce != null) setNonce(sha256Hex(rawNonce)) }
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credential = CredentialManager.create(this)
            .getCredential(context = this, request = request)
            .credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            "OK" to GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            "Unexpected credential type: ${credential.type}" to null
        }
    } catch (e: GetCredentialCancellationException) {
        "Cancelled by user" to null
    } catch (e: GoogleIdTokenParsingException) {
        "GoogleIdTokenParsingException: ${e.message}" to null
    } catch (e: GetCredentialException) {
        // type is the useful part: NoCredentialException,
        // GetCredentialProviderConfigurationException,
        // GetCredentialUnsupportedException, ...
        Log.w(TAG, "GetCredentialException type=${e.type}", e)
        "GetCredentialException type=${e.type} msg=${e.errorMessage}" to null
    } catch (e: Exception) {
        Log.w(TAG, "unexpected", e)
        "${e.javaClass.simpleName}: ${e.message}" to null
    }

    @Composable
    private fun SpikeScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val clipboard = LocalClipboardManager.current

        var useNonce by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Idle") }
        var idToken by remember { mutableStateOf<String?>(null) }
        var nonce by remember { mutableStateOf<String?>(null) }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Google Sign-In spike", style = MaterialTheme.typography.titleLarge)
                Text(
                    "package  ${context.packageName}\n" +
                        "audience $WEB_CLIENT_ID",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useNonce, onCheckedChange = { useNonce = it })
                    Text("Send a nonce (SHA-256 to Google, raw to Supabase)")
                }
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        status = "Requesting..."
                        idToken = null
                        nonce = null
                        scope.launch {
                            val raw = if (useNonce) UUID.randomUUID().toString() else null
                            val (s, t) = requestIdToken(raw)
                            status = s
                            idToken = t
                            nonce = raw
                            busy = false
                        }
                    },
                ) {
                    Text(if (busy) "Working..." else "Get Google ID token")
                }
                Text(status, fontSize = 13.sp, fontFamily = FontFamily.Monospace)

                idToken?.let { token ->
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(token)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copy ID token") }
                    SelectionContainer {
                        Text(token, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                nonce?.let { n ->
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(n)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copy raw nonce") }
                    SelectionContainer {
                        Text(n, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
