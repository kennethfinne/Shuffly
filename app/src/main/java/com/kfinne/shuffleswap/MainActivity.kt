package com.kfinne.shuffleswap

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat

class MainActivity : AppCompatActivity() {

    private lateinit var shuffleMixCard: CardView
    private lateinit var makeMixCard: CardView
    private lateinit var albumArt: ImageView
    private lateinit var progressSeekBar: SeekBar
    private lateinit var playPauseButton: ImageView

    private var isPlaying = false
    private var currentProgress = 35
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private lateinit var loginStatusText: TextView
    private var accessToken: String? = null

    // Request code for auth activity
    private val AUTH_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Don't set content view since we're navigating away immediately
        supportActionBar?.hide()

        checkAuthenticationAndNavigate()
    }

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
        startActivityForResult(intent, AUTH_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AUTH_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    // Authentication successful
                    val token = data?.getStringExtra("ACCESS_TOKEN")
                    if (token != null) {
                        accessToken = token
                        onAuthenticationSuccess(token)
                    } else {
                        showAuthError("No access token received")
                        // Close the app or handle error appropriately
                        finish()
                    }
                }
                RESULT_CANCELED -> {
                    // User cancelled authentication
                    val error = data?.getStringExtra("AUTH_ERROR")
                    if (error != null) {
                        showAuthError(error)
                    } else {
                        showAuthCancelled()
                    }
                    // Close the app since user cancelled login
                    finish()
                }
            }
        }
    }

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

    // Remove all the UI-related methods since we're not using the MainActivity UI anymore
    // Keep them commented out in case you want to use them later

    /*
    private fun enableSpotifyFeatures() {
        // Enable cards and other Spotify-dependent UI elements
        shuffleMixCard.isEnabled = true
        makeMixCard.isEnabled = true

        // You can also update the UI to reflect the authenticated state
        // For example, change card colors, add indicators, etc.
    }

    private fun initViews() {
        shuffleMixCard = findViewById(R.id.shuffleMixCard)
        makeMixCard = findViewById(R.id.makeMixCard)
        albumArt = findViewById(R.id.albumArt)
        progressSeekBar = findViewById(R.id.progressSeekBar)
        playPauseButton = findViewById(R.id.playPauseButton)
        // loginStatusText = findViewById(R.id.loginStatusText) // Only if you add a separate status TextView

        // Initially disable Spotify-dependent features until authenticated
        shuffleMixCard.isEnabled = false
        makeMixCard.isEnabled = false
    }

    private fun setupClickListeners() {
        shuffleMixCard.setOnClickListener {
            if (accessToken != null) {
                animateCardClick(it)
                //handleFeatureClick("ShuffleMix")
                val intent = Intent(this, SpotifyPlaylistsActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            }
        }

        makeMixCard.setOnClickListener {
            if (accessToken != null) {
                animateCardClick(it)
                //handleFeatureClick("MakeMix")
                val intent = Intent(this, CheckBPMinPlaylistActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            }
        }

        playPauseButton.setOnClickListener {
            if (accessToken != null) {
                togglePlayPause()
            } else {
                Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            }
        }

        // Add ripple effect to other buttons
        val controlButtons = listOf(
            findViewById<ImageView>(R.id.shuffleButton),
            findViewById<ImageView>(R.id.previousButton),
            findViewById<ImageView>(R.id.nextButton),
            findViewById<ImageView>(R.id.likeButton)
        )

        controlButtons.forEach { button ->
            button.setOnClickListener {
                if (accessToken != null) {
                    animateButtonClick(it)
                } else {
                    Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun animateCardClick(view: View) {
        // Scale down animation
        val scaleDown = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.98f)
        scaleDown.duration = 100
        scaleDown.interpolator = AccelerateDecelerateInterpolator()

        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.98f)
        scaleDownY.duration = 100
        scaleDownY.interpolator = AccelerateDecelerateInterpolator()

        // Scale back up
        val scaleUp = ObjectAnimator.ofFloat(view, "scaleX", 0.98f, 1f)
        scaleUp.duration = 100
        scaleUp.startDelay = 100
        scaleUp.interpolator = AccelerateDecelerateInterpolator()

        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.98f, 1f)
        scaleUpY.duration = 100
        scaleUpY.startDelay = 100
        scaleUpY.interpolator = AccelerateDecelerateInterpolator()

        scaleDown.start()
        scaleDownY.start()
        scaleUp.start()
        scaleUpY.start()
    }

    private fun animateButtonClick(view: View) {
        val animator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.7f, 1f)
        animator.duration = 200
        animator.start()
    }

    private fun handleFeatureClick(feature: String) {
        // Handle feature clicks here - now with access token available
        when (feature) {
            "ShuffleMix" -> {
                // Navigate to ShuffleMix feature or handle it here
                // You can use accessToken for Spotify API calls
                Toast.makeText(this, "ShuffleMix clicked - Token available!", Toast.LENGTH_SHORT).show()
            }
            "MakeMix" -> {
                // Navigate to MakeMix feature or handle it here
                // You can use accessToken for Spotify API calls
                Toast.makeText(this, "MakeMix clicked - Token available!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun togglePlayPause() {
        isPlaying = !isPlaying
        if (isPlaying) {
            playPauseButton.setImageResource(R.drawable.ic_pause)
            startProgressAnimation()
        } else {
            playPauseButton.setImageResource(R.drawable.ic_play)
            stopProgressAnimation()
        }

        // Animate play button
        val scaleAnimator = ObjectAnimator.ofFloat(playPauseButton, "scaleX", 1f, 1.1f, 1f)
        val scaleAnimatorY = ObjectAnimator.ofFloat(playPauseButton, "scaleY", 1f, 1.1f, 1f)
        scaleAnimator.duration = 200
        scaleAnimatorY.duration = 200
        scaleAnimator.start()
        scaleAnimatorY.start()
    }

    private fun startAnimations() {
        // Stagger card animations
        val cards = listOf(shuffleMixCard, makeMixCard)
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 60f

            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay((index * 100).toLong())
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun setupProgressBar() {
        progressSeekBar.progress = currentProgress
        progressSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentProgress = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startProgressAnimation() {
        progressRunnable = object : Runnable {
            override fun run() {
                if (currentProgress < 100) {
                    currentProgress += 1
                    progressSeekBar.progress = currentProgress
                    handler.postDelayed(this, 1000) // Update every second
                } else {
                    // Song finished
                    isPlaying = false
                    playPauseButton.setImageResource(R.drawable.ic_play)
                    currentProgress = 0
                    progressSeekBar.progress = 0
                }
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressAnimation() {
        progressRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun startAlbumArtPulse() {
        val pulseAnimator = ObjectAnimator.ofFloat(albumArt, "alpha", 1f, 0.7f, 1f)
        pulseAnimator.duration = 2000
        pulseAnimator.repeatCount = ValueAnimator.INFINITE
        pulseAnimator.start()
    }
    */
}