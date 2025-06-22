package com.kfinne.shuffleswap.data

import com.google.gson.annotations.SerializedName

// Spotify API Models
data class Playlist(
    val id: String,
    val name: String,
    val tracks: Tracks
)

data class Tracks(
    val total: Int
)

data class PlaylistsResponse(
    val items: List<Playlist>
)

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artist: String
)

// App Models
data class Song(
    val id: String,
    val name: String,
    val artist: String,
    val bpm: Double,
    val playlistName: String
)
