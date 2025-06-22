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

class AuthActivity : AppCompatActivity() {

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

        // Add buffer time (5 minutes) to refresh before actual expiry
        const val TOKEN_REFRESH_BUFFER_MS = 5 * 60 * 1000L
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
            // Check if we have a valid token or can refresh it
            checkExistingToken()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { handleSpotifyCallback(it) }
    }

    private fun checkExistingToken() {
        val accessToken = sharedPrefs.getString(ACCESS_TOKEN_KEY, null)
        val tokenExpiry = sharedPrefs.getLong(TOKEN_EXPIRY_KEY, 0)
        val refreshToken = sharedPrefs.getString(REFRESH_TOKEN_KEY, null)

        val currentTime = System.currentTimeMillis()

        when {
            // Token is still valid (with buffer)
            accessToken != null && tokenExpiry > currentTime + TOKEN_REFRESH_BUFFER_MS -> {
                Log.d(TAG, "Using existing valid token")
                finishWithSuccess(accessToken)
            }
            // Token expired but we have refresh token
            refreshToken != null -> {
                Log.d(TAG, "Token expired, attempting refresh")
                refreshAccessToken(refreshToken)
            }
            // No valid token or refresh token, need full auth
            else -> {
                Log.d(TAG, "No valid tokens, starting full auth")
                startSpotifyAuth()
            }
        }
    }

    private fun refreshAccessToken(refreshToken: String) {
        val requestBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", CLIENT_ID)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Token refresh network error: ${e.message}")
                runOnUiThread {
                    // If refresh fails, try full auth
                    startSpotifyAuth()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        try {
                            val tokenResponse = gson.fromJson(response.body?.string(), TokenResponse::class.java)
                            // Preserve existing refresh token if new one isn't provided
                            if (tokenResponse.refresh_token == null) {
                                tokenResponse.refresh_token = refreshToken
                            }
                            saveTokens(tokenResponse)
                            runOnUiThread {
                                Log.d(TAG, "Token refresh successful")
                                finishWithSuccess(tokenResponse.access_token)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Token refresh parsing error: ${e.message}")
                            runOnUiThread {
                                // If parsing fails, try full auth
                                startSpotifyAuth()
                            }
                        }
                    } else {
                        Log.e(TAG, "Token refresh failed: ${response.code}")
                        runOnUiThread {
                            // If refresh fails (e.g., refresh token expired), need full auth
                            clearTokens()
                            startSpotifyAuth()
                        }
                    }
                }
            }
        })
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
                            runOnUiThread { finishWithSuccess(tokenResponse.access_token) }
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

        Log.d(TAG, "Tokens saved. Access token expires at: ${java.util.Date(expiryTimestamp)}")
    }

    private fun clearTokens() {
        sharedPrefs.edit().apply {
            remove(ACCESS_TOKEN_KEY)
            remove(TOKEN_EXPIRY_KEY)
            remove(REFRESH_TOKEN_KEY)
            apply()
        }
        Log.d(TAG, "Tokens cleared")
    }

    private fun finishWithSuccess(accessToken: String) {
        Log.d(TAG, "Auth successful")
        val resultIntent = Intent()
        resultIntent.putExtra("ACCESS_TOKEN", accessToken)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun finishWithError(error: String) {
        Log.e(TAG, "Auth error: $error")
        val resultIntent = Intent()
        resultIntent.putExtra("AUTH_ERROR", error)
        setResult(RESULT_CANCELED, resultIntent)
        finish()
    }

    private fun finishWithCancellation() {
        Log.d(TAG, "Auth cancelled")
        setResult(RESULT_CANCELED)
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
        var refresh_token: String?, // Made var so we can preserve existing refresh token
        val scope: String
    )
}