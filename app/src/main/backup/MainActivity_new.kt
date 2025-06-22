package com.kfinne.shuffleswap

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorCompat

class MainActivity_new : AppCompatActivity() {

    private lateinit var shuffleMixCard: CardView
    private lateinit var makeMixCard: CardView
    private lateinit var albumArt: ImageView
    private lateinit var progressSeekBar: SeekBar
    private lateinit var playPauseButton: ImageView

    private var isPlaying = false
    private var currentProgress = 35
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_new)

        initViews()
        setupClickListeners()
        startAnimations()
        setupProgressBar()
        startAlbumArtPulse()
    }

    private fun initViews() {
        shuffleMixCard = findViewById(R.id.shuffleMixCard)
        makeMixCard = findViewById(R.id.makeMixCard)
        albumArt = findViewById(R.id.albumArt)
        progressSeekBar = findViewById(R.id.progressSeekBar)
        playPauseButton = findViewById(R.id.playPauseButton)
    }

    private fun setupClickListeners() {
        shuffleMixCard.setOnClickListener {
            animateCardClick(it)
            handleFeatureClick("ShuffleMix")
        }

        makeMixCard.setOnClickListener {
            animateCardClick(it)
            handleFeatureClick("MakeMix")
        }

        playPauseButton.setOnClickListener {
            togglePlayPause()
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
                animateButtonClick(it)
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
        // Handle feature clicks here
        when (feature) {
            "ShuffleMix" -> {
                // Navigate to ShuffleMix feature
            }
            "MakeMix" -> {
                // Navigate to MakeMix feature
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

            ViewCompat.animate(card)
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

    override fun onDestroy() {
        super.onDestroy()
        stopProgressAnimation()
    }
}