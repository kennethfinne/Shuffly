package com.kfinne.shuffleswap

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class FrontPage : AppCompatActivity(){

    private lateinit var accessToken: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.hub_frontpage)

        accessToken = intent.getStringExtra("ACCESS_TOKEN") ?: ""

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        findViewById<Button>(R.id.shuffleMixButton).setOnClickListener{
            val intent = Intent(this, SpotifyPlaylistsActivity::class.java)
            intent.putExtra("ACCESS_TOKEN", accessToken)
            startActivity(intent)
        }

        findViewById<ImageButton>(R.id.shuffleIconButton).setOnClickListener{
            val intent = Intent(this, SpotifyPlaylistsActivity::class.java)
            intent.putExtra("ACCESS_TOKEN", accessToken)
            startActivity(intent)
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, FrontPageFragment())
                        .commit()
                    true
                }
                R.id.nav_search -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, SearchFragment())
                        .commit()
                    true
                }
                R.id.nav_profile -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ProfileFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }

        // Start med første fane
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }
    }
}
