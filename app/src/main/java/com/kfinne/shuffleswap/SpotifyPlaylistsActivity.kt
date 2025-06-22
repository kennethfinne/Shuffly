package com.kfinne.shuffleswap

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.kfinne.shuffleswap.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.io.IOException

// Data classes
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val images: List<SpotifyImage>,
    val owner: SpotifyUser,
    @SerializedName("tracks") val trackInfo: SpotifyTrackInfo
)

data class SpotifyImage(
    val url: String,
    val height: Int?,
    val width: Int?
)

data class SpotifyUser(
    @SerializedName("display_name") val displayName: String
)

data class SpotifyTrackInfo(
    val total: Int
)

data class SpotifyPlaylistResponse(
    val items: List<SpotifyPlaylist>
)

data class Track(
    val id: String?,
    val name: String,
    val uri: String?,
    val artists: List<Artist>
)

data class Artist(val name: String)

data class PlaylistTracksResponse(val items: List<TrackItem>)
data class TrackItem(val track: Track?)
data class UserResponse(val id: String)

// Retrofit interface
interface SpotifyApiService {
    @GET("me/playlists?limit=50")
    suspend fun getUserPlaylists(
        @Header("Authorization") authorization: String
    ): SpotifyPlaylistResponse
}

// ViewModel
class ModernPlaylistViewModel : ViewModel() {
    private val _playlists = mutableStateOf<List<SpotifyPlaylist>>(emptyList())
    val playlists: State<List<SpotifyPlaylist>> = _playlists

    private val _selectedPlaylists = mutableStateOf<List<SpotifyPlaylist>>(emptyList())
    val selectedPlaylists: State<List<SpotifyPlaylist>> = _selectedPlaylists

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _playlistName = mutableStateOf("ShufflyMix")
    val playlistName: State<String> = _playlistName

    private val _isShuffling = mutableStateOf(false)
    val isShuffling: State<Boolean> = _isShuffling

    private val _shuffleComplete = mutableStateOf(false)
    val shuffleComplete: State<Boolean> = _shuffleComplete

    private val httpClient = OkHttpClient()
    private val gson = Gson()

    private val spotifyApi = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/v1/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyApiService::class.java)

    fun loadPlaylists(accessToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = spotifyApi.getUserPlaylists("Bearer $accessToken")
                _playlists.value = response.items
            } catch (e: Exception) {
                _error.value = "Failed to load playlists: ${e.message}"
                Log.e("ModernPlaylistViewModel", "Error loading playlists", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun togglePlaylistSelection(playlist: SpotifyPlaylist) {
        val current = _selectedPlaylists.value.toMutableList()
        if (current.contains(playlist)) {
            current.remove(playlist)
        } else if (current.size < 2) {
            current.add(playlist)
        }
        _selectedPlaylists.value = current
    }

    fun updatePlaylistName(name: String) {
        _playlistName.value = name
    }

    fun shuffleAndCreatePlaylist(accessToken: String, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        if (_selectedPlaylists.value.size != 2) return

        viewModelScope.launch {
            _isShuffling.value = true
            try {
                val tracks1 = fetchPlaylistTracks(_selectedPlaylists.value[0], accessToken)
                val tracks2 = fetchPlaylistTracks(_selectedPlaylists.value[1], accessToken)

                val mixedTracks = createAlternatingTracks(tracks1, tracks2)
                val userId = getUserId(accessToken)

                if (userId != null) {
                    val existingPlaylistId = findExistingPlaylist(_playlistName.value, accessToken)

                    if (existingPlaylistId != null) {
                        clearAndUpdatePlaylist(existingPlaylistId, mixedTracks, accessToken)
                        withContext(Dispatchers.Main) {
                            onComplete(_playlistName.value)
                        }
                    } else {
                        val newPlaylistId = createNewPlaylist(userId, mixedTracks, accessToken)
                        withContext(Dispatchers.Main) {
                            onComplete(_playlistName.value)
                        }
                    }
                    _shuffleComplete.value = true
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Failed to get user information")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Error creating playlist: ${e.message}")
                }
            } finally {
                _isShuffling.value = false
            }
        }
    }

    private suspend fun fetchPlaylistTracks(playlist: SpotifyPlaylist, accessToken: String): List<Track> {
        return withContext(Dispatchers.IO) {
            val url = "https://api.spotify.com/v1/playlists/${playlist.id}/tracks?fields=items(track(id,name,artists(name),uri))"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val tracksResponse = gson.fromJson(responseBody, PlaylistTracksResponse::class.java)
                    tracksResponse.items.mapNotNull { it.track }.filter { it.uri != null }
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun createAlternatingTracks(tracks1: List<Track>, tracks2: List<Track>): List<Track> {
        val shuffled1 = tracks1.shuffled()
        val shuffled2 = tracks2.shuffled()
        val mixedTracks = mutableListOf<Track>()
        val maxSongs = 20
        var fromList1 = true

        for (i in 0 until maxSongs) {
            val track = if (fromList1 && shuffled1.isNotEmpty()) {
                shuffled1[i % shuffled1.size]
            } else if (!fromList1 && shuffled2.isNotEmpty()) {
                shuffled2[i % shuffled2.size]
            } else {
                break
            }
            mixedTracks.add(track)
            fromList1 = !fromList1
        }
        return mixedTracks
    }

    private suspend fun getUserId(accessToken: String): String? {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val userResponse = gson.fromJson(responseBody, UserResponse::class.java)
                    userResponse.id
                } else null
            }
        }
    }

    private suspend fun findExistingPlaylist(playlistName: String, accessToken: String): String? {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me/playlists")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val playlistsResponse = gson.fromJson(responseBody, SpotifyPlaylistResponse::class.java)
                    playlistsResponse.items.find { it.name == playlistName }?.id
                } else null
            }
        }
    }

    private suspend fun createNewPlaylist(userId: String, tracks: List<Track>, accessToken: String): String? {
        return withContext(Dispatchers.IO) {
            val playlistData = mapOf(
                "name" to _playlistName.value,
                "description" to "Mixed playlist created by ShuffleSwap",
                "public" to false
            )

            val requestBody = RequestBody.create(
                "application/json".toMediaType(),
                gson.toJson(playlistData)
            )

            val request = Request.Builder()
                .url("https://api.spotify.com/v1/users/$userId/playlists")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val playlist = gson.fromJson(responseBody, SpotifyPlaylist::class.java)
                    addTracksToPlaylist(playlist.id, tracks, accessToken)
                    playlist.id
                } else null
            }
        }
    }

    private suspend fun clearAndUpdatePlaylist(playlistId: String, tracks: List<Track>, accessToken: String) {
        withContext(Dispatchers.IO) {
            // Clear existing tracks
            val clearRequest = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/$playlistId/tracks")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .method("PUT", RequestBody.create("application/json".toMediaType(), "{\"uris\":[]}"))
                .build()

            httpClient.newCall(clearRequest).execute().use { response ->
                if (response.isSuccessful) {
                    addTracksToPlaylist(playlistId, tracks, accessToken)
                }
            }
        }
    }

    private suspend fun addTracksToPlaylist(playlistId: String, tracks: List<Track>, accessToken: String) {
        withContext(Dispatchers.IO) {
            val trackUris = tracks.mapNotNull { it.uri }
            val requestData = mapOf("uris" to trackUris)
            val requestBody = RequestBody.create(
                "application/json".toMediaType(),
                gson.toJson(requestData)
            )

            val request = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/$playlistId/tracks")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute()
        }
    }
}

class SpotifyPlaylistsActivity : ComponentActivity() {
    private val viewModel: ModernPlaylistViewModel by viewModels()
    private lateinit var accessToken: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accessToken = intent.getStringExtra("ACCESS_TOKEN") ?: ""

        if (accessToken.isEmpty()) {
            // Handle authentication error
            finish()
            return
        }

        setContent {
            AppTheme {
                ModernSpotifyPlaylistsScreen(
                    viewModel = viewModel,
                    accessToken = accessToken,
                    onOpenSpotify = { playlistName -> openSpotify(playlistName) }
                )
            }
        }
    }

    private fun openSpotify(playlistName: String) {
        try {
            val intent = Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName("com.spotify.music", "com.spotify.music.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Handler(Looper.getMainLooper()).postDelayed({
                    playPlaylistViaWebApi(playlistName)
                }, 2000)
            } else {
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music"))
                startActivity(playStoreIntent)
            }
        } catch (e: Exception) {
            Log.e("SpotifyPlaylistsActivity", "Could not open Spotify", e)
        }
    }

    private fun playPlaylistViaWebApi(playlistId: String) {
        val playRequest = JSONObject().apply {
            put("context_uri", "spotify:playlist:$playlistId")
            put("position_ms", 0)
        }

        val requestBody = RequestBody.create(
            "application/json".toMediaType(),
            playRequest.toString()
        )

        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/player/play")
            .put(requestBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                // Handle success
            }

            override fun onFailure(call: Call, e: IOException) {
                // Handle failure
            }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernSpotifyPlaylistsScreen(
    viewModel: ModernPlaylistViewModel,
    accessToken: String,
    onOpenSpotify: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    val playlists by viewModel.playlists
    val selectedPlaylists by viewModel.selectedPlaylists
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val playlistName by viewModel.playlistName
    val isShuffling by viewModel.isShuffling
    val shuffleComplete by viewModel.shuffleComplete

    var showSuccessMessage by remember { mutableStateOf(false) }
    var successPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(accessToken) {
        viewModel.loadPlaylists(accessToken)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
        ,
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Header
            Text(
                text = "Shuffly",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Shuffle and alternate two playlists",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Playlist Name Input
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            "Playlist Name",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { viewModel.updatePlaylistName(it) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                }
                            )
                        )
                    }
                }
            }

            // Selected Playlists Display - Fixed size
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedPlaylists.size == 2) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0xFF282828)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .height(120.dp) // Fixed height to prevent size changes
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedPlaylists.size == 2) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Complete",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                "Selected Playlists (${selectedPlaylists.size}/2)",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (selectedPlaylists.isEmpty()) {
                            Text(
                                "Select 2 playlists below to shuffle and mix",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        } else {
                            selectedPlaylists.forEach { playlist ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "• ${playlist.name}",
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "(${playlist.trackInfo.total} songs)",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Shuffle Button
            item {
                Button(
                    onClick = {
                        if (shuffleComplete) {
                            onOpenSpotify(successPlaylistName)
                        } else {
                            viewModel.shuffleAndCreatePlaylist(
                                accessToken = accessToken,
                                onComplete = { name ->
                                    successPlaylistName = name
                                    showSuccessMessage = true
                                },
                                onError = { /* Handle error */ }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = selectedPlaylists.size == 2 && !isShuffling,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (shuffleComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color(0xFF404040)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isShuffling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onBackground,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Creating Playlist...", color = MaterialTheme.colorScheme.onBackground)
                    } else if (shuffleComplete) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Open Spotify",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Spotify", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                    } else {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Mix", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                    }
                }
            }

            // Playlist Selection Grid - 3 columns
            if (!isLoading && playlists.isNotEmpty()) {
                item {
                    Text(
                        "Your Playlists",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(playlists.chunked(3)) { playlistTriple ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        playlistTriple.forEach { playlist ->
                            PlaylistSelectionTile(
                                playlist = playlist,
                                isSelected = selectedPlaylists.contains(playlist),
                                onToggleSelection = { viewModel.togglePlaylistSelection(playlist) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill remaining space if less than 3 items
                        repeat(3 - playlistTriple.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Loading State
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Error State
            error?.let { errorMessage ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Red.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            color = Color.Red,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        // Success Snackbar
        if (showSuccessMessage) {
            LaunchedEffect(showSuccessMessage) {
                kotlinx.coroutines.delay(3000)
                showSuccessMessage = false
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Successfully created '$successPlaylistName'!",
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistSelectionTile(
    playlist: SpotifyPlaylist,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onToggleSelection() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color(0xFF282828)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Playlist cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF404040))
            ) {
                if (playlist.images.isNotEmpty()) {
                    AsyncImage(
                        model = playlist.images.first().url,
                        contentDescription = playlist.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        Color(0xFF1ed760)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = playlist.name.take(2).uppercase(),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Selection overlay
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playlist name
            Text(
                text = playlist.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Track count and owner
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${playlist.trackInfo.total} songs",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = " • ",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = playlist.owner.displayName,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}