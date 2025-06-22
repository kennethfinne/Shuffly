package com.kfinne.shuffleswap

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

//class MainActivity_old : AppCompatActivity() {

    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupLoginButton()
        handleAuthResult()
    }

    private fun initializeViews() {
        loginButton = findViewById(R.id.loginButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        hideLoadingState()
    }

    private fun setupLoginButton() {
        loginButton.setOnClickListener {
            showLoadingState()
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showLoadingState() {
        loginButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        statusText.text = "Connecting to Spotify..."
        statusText.visibility = View.VISIBLE
    }

    private fun hideLoadingState() {
        loginButton.isEnabled = true
        progressBar.visibility = View.GONE
        statusText.visibility = View.GONE
    }

    private fun handleAuthResult() {
        val authError = intent.getStringExtra("AUTH_ERROR")
        val authCancelled = intent.getBooleanExtra("AUTH_CANCELLED", false)

        when {
            authError != null -> showAuthError(authError)
            authCancelled -> showAuthCancelled()
        }
    }

    private fun showAuthError(error: String) {
        hideLoadingState()
        statusText.text = "Login failed: $error"
        statusText.setTextColor(getColor(android.R.color.holo_red_dark))
        statusText.visibility = View.VISIBLE
        Toast.makeText(this, "Login failed. Please try again.", Toast.LENGTH_LONG).show()
    }

    private fun showAuthCancelled() {
        hideLoadingState()
        statusText.text = "Login cancelled"
        statusText.visibility = View.VISIBLE
        Toast.makeText(this, "Login cancelled", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        checkExistingToken()
    }

    private fun checkExistingToken() {
        val sharedPrefs = getSharedPreferences(AuthActivity.PREFS_NAME, MODE_PRIVATE)
        val accessToken = sharedPrefs.getString(AuthActivity.ACCESS_TOKEN_KEY, null)
        val tokenExpiry = sharedPrefs.getLong(AuthActivity.TOKEN_EXPIRY_KEY, 0L)

        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            navigateToFrontPage(accessToken)
        } else {
            hideLoadingState()
        }
    }

    private fun navigateToFrontPage(accessToken: String) {
        val intent = Intent(this, FrontPage::class.java)
        intent.putExtra("ACCESS_TOKEN", accessToken)
        startActivity(intent)
        finish()
    }
}