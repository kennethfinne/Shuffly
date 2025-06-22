package com.kfinne.shuffleswap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var accessToken: String? = null

    // Request code for auth activity
    private val AUTH_REQUEST_CODE = 1001

    // 1. Register the ActivityResultLauncher
    private lateinit var authActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Don't set content view since we're navigating away immediately
        supportActionBar?.hide()

        // Initialize the launcher
        authActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult() // Prebuilt contract
        ) { result -> // This is your callback for the result
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val token = data?.getStringExtra("ACCESS_TOKEN")
                if (token != null) {
                    accessToken = token
                    onAuthenticationSuccess(token)
                } else {
                    showAuthError("No access token received")
                    finish()
                }
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                val data: Intent? = result.data
                val error = data?.getStringExtra("AUTH_ERROR")
                if (error != null) {
                    showAuthError(error)
                } else {
                    showAuthCancelled()
                }
                finish()
            }
        }

        checkAuthenticationAndNavigate()
    }

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // Don't set content view since we're navigating away immediately
//
//        supportActionBar?.hide()
//        checkAuthenticationAndNavigate()
//    }

    private fun checkAuthenticationAndNavigate() {
        val sharedPrefs = getSharedPreferences(AuthActivity.PREFS_NAME, MODE_PRIVATE)
        val existingToken = sharedPrefs.getString(AuthActivity.ACCESS_TOKEN_KEY, null)
        val tokenExpiry = sharedPrefs.getLong(AuthActivity.TOKEN_EXPIRY_KEY, 0L)

        if (existingToken != null && System.currentTimeMillis() < tokenExpiry) {
            // Token is valid, navigate directly to SpotifyPlaylistsActivity
            accessToken = existingToken
            navigateToSpotifyPlaylists(existingToken)
        } else {
            // Need to authenticate first
            startLoginForResult()
        }
    }

    private fun navigateToSpotifyPlaylists(token: String) {
        val intent = Intent(this, SpotifyPlaylistsActivity::class.java)
        intent.putExtra("ACCESS_TOKEN", token)
        startActivity(intent)
        // Finish MainActivity so user can't go back to it
        finish()
    }

    private fun startLoginForResult() {
        val intent = Intent(this, AuthActivity::class.java)
        //startActivityForResult(intent, AUTH_REQUEST_CODE)
        authActivityLauncher.launch(intent)
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (requestCode == AUTH_REQUEST_CODE) {
//            when (resultCode) {
//                RESULT_OK -> {
//                    // Authentication successful
//                    val token = data?.getStringExtra("ACCESS_TOKEN")
//                    if (token != null) {
//                        accessToken = token
//                        onAuthenticationSuccess(token)
//                    } else {
//                        showAuthError("No access token received")
//                        // Close the app or handle error appropriately
//                        finish()
//                    }
//                }
//                RESULT_CANCELED -> {
//                    // User cancelled authentication
//                    val error = data?.getStringExtra("AUTH_ERROR")
//                    if (error != null) {
//                        showAuthError(error)
//                    } else {
//                        showAuthCancelled()
//                    }
//                    // Close the app since user cancelled login
//                    finish()
//                }
//            }
//        }
//    }

    private fun onAuthenticationSuccess(token: String) {
        // Show success toast
        Toast.makeText(this, "Successfully logged in to Spotify!", Toast.LENGTH_SHORT).show()

        // Navigate directly to SpotifyPlaylistsActivity
        navigateToSpotifyPlaylists(token)
    }

    private fun showAuthError(error: String) {
        Toast.makeText(this, "Login failed: $error", Toast.LENGTH_LONG).show()
    }

    private fun showAuthCancelled() {
        Toast.makeText(this, "Login cancelled", Toast.LENGTH_SHORT).show()
    }
}