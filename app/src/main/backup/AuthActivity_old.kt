package com.kfinne.shuffleswap

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

class AuthActivity_old : AppCompatActivity() {

    companion object {
        const val TAG = "AuthActivity"
        const val CLIENT_ID = "c415144bad27415289dc29bb6ef42cfc"
        const val REDIRECT_URI = "shuffleswap://callback"
        const val AUTH_URL = "https://accounts.spotify.com/authorize"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"

        const val PREFS_NAME = "ShuffleSwapPrefs"
        const val ACCESS_TOKEN_KEY = "access_token"
        const val TOKEN_EXPIRY_KEY = "token_expiry"
        const val REFRESH_TOKEN_KEY = "refresh_token"
        const val CODE_VERIFIER_KEY = "code_verifier"
    }

    private lateinit var sharedPrefs: SharedPreferences
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val data = intent.data
        if (data != null && data.scheme == "shuffleswap" && data.host == "callback") {
            handleSpotifyCallback(data)
        } else {
            startSpotifyAuth()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { handleSpotifyCallback(it) }
    }

    private fun startSpotifyAuth() {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        sharedPrefs.edit().putString(CODE_VERIFIER_KEY, codeVerifier).apply()

        val authUri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("scope", "user-read-private playlist-read-private playlist-modify-private playlist-modify-public")
            .build()

        val intent = Intent(Intent.ACTION_VIEW, authUri)
        startActivity(intent)
    }

    private fun handleSpotifyCallback(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        when {
            code != null -> exchangeAuthorizationCodeForToken(code)
            error != null -> finishWithError(error)
            else -> finishWithCancellation()
        }
    }

    private fun exchangeAuthorizationCodeForToken(code: String) {
        val codeVerifier = sharedPrefs.getString(CODE_VERIFIER_KEY, null)
        if (codeVerifier == null) {
            finishWithError("Code verifier missing")
            return
        }

        val requestBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", CLIENT_ID)
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { finishWithError("Network error: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        try {
                            val tokenResponse = gson.fromJson(response.body?.string(), TokenResponse::class.java)
                            saveTokens(tokenResponse)
                            runOnUiThread { navigateToFrontPage(tokenResponse.access_token) }
                        } catch (e: Exception) {
                            runOnUiThread { finishWithError("Token parsing error: ${e.message}") }
                        }
                    } else {
                        runOnUiThread { finishWithError("Token exchange failed: ${response.code}") }
                    }
                }
            }
        })
    }

    private fun saveTokens(tokenResponse: TokenResponse) {
        val expiryTimestamp = System.currentTimeMillis() + (tokenResponse.expires_in * 1000)

        sharedPrefs.edit().apply {
            putString(ACCESS_TOKEN_KEY, tokenResponse.access_token)
            putLong(TOKEN_EXPIRY_KEY, expiryTimestamp)
            tokenResponse.refresh_token?.let { putString(REFRESH_TOKEN_KEY, it) }
            apply()
        }
    }

    private fun navigateToFrontPage(accessToken: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("ACCESS_TOKEN", accessToken)
        startActivity(intent)
        finish()
    }

    private fun finishWithError(error: String) {
        Log.e(TAG, "Auth error: $error")
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("AUTH_ERROR", error)
        startActivity(intent)
        finish()
    }

    private fun finishWithCancellation() {
        Log.d(TAG, "Auth cancelled")
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("AUTH_CANCELLED", true)
        startActivity(intent)
        finish()
    }

    private fun generateCodeVerifier(): String {
        val randomBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(randomBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    data class TokenResponse(
        val access_token: String,
        val token_type: String,
        val expires_in: Long,
        val refresh_token: String?,
        val scope: String
    )
}