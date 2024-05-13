package org.training.reco

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberImagePainter
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONException

class MainActivity : ComponentActivity() {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        val database = FirebaseDatabase.getInstance().reference

        setContent {
            AppContent(database)
        }
    }

    data class User(
        val email: String,
        var profileImgUrl: String
    )

    data class SpotifyTrack(
        val id: String,
        val name: String,
        val album: String,
        val imageUrl: String,
        val shareUrl: String
    )





    @Composable
    fun AppContent(database: DatabaseReference) {
        val navController = rememberNavController()

        Scaffold(
            bottomBar = {
                BottomNavigation {
                    BottomNavigationItem(
                        selected = navController.currentDestination?.route == "Favorites",
                        onClick = {
                            navController.navigate("Favorites")
                        },
                        icon = { Icon(Icons.Filled.Favorite, contentDescription = "Profile") },
                        label = { Text(text = "Favorites") }
                    )

                    BottomNavigationItem(
                        selected = navController.currentDestination?.route == "FindMusic",
                        onClick = {
                            navController.navigate("FindMusic")
                        },
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Profile") },
                        label = { Text(text = "Find Song") }
                    )

                    BottomNavigationItem(
                        selected = navController.currentDestination?.route == "profile",
                        onClick = {
                            navController.navigate("profile")
                        },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                        label = { Text(text = "Profile") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                NavHost(navController, startDestination = "FindMusic") {
                    composable("Favorites") { FavoritesPage() }
                    composable("FindMusic") { FindMusicPage() }
                    composable("profile") { ProfilePage(database) }
                }
            }
        }
    }


    @Composable
    fun FavoritesPage() {
        val context = LocalContext.current
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val songs = remember { mutableStateListOf<SpotifyTrack>() }

        LaunchedEffect(key1 = userId) {
            fetchFavorites(userId) { ids ->
                ids.forEach { id ->
                    getSpotifyTrackDetails(id, context) { track ->
                        songs.add(track)
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs) { song ->
                SongItem(song = song, onClick = { openUrl(context, song.shareUrl) }, onRemove = {
                    removeSongFromFavorites(userId, song.id, songs)
                })
            }
        }
    }

    @Composable
    fun SongItem(song: SpotifyTrack, onClick: () -> Unit, onRemove: () -> Unit) {
        Row(modifier = Modifier.padding(8.dp)) {
            Image(
                painter = rememberImagePainter(song.imageUrl),
                contentDescription = "Album Art",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onClick() }
            )
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Text(song.name, fontWeight = FontWeight.Bold)
                Text(song.album)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }

    fun removeSongFromFavorites(userId: String, songId: String, songs: MutableList<SpotifyTrack>) {
        FirebaseFirestore.getInstance().collection("favorites").document(userId)
            .update("song_ids", FieldValue.arrayRemove(songId))
            .addOnSuccessListener {
                songs.removeAll { it.id == songId }
                Log.d("Firestore", "Song removed from favorites successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error removing song from favorites", e)
            }
    }



    fun fetchFavorites(userId: String, onResult: (List<String>) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("favorites")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val songIds = document.get("song_ids") as? List<String> ?: emptyList()
                onResult(songIds)
            }
            .addOnFailureListener { e ->
                Log.e("FetchFavorites", "Error fetching favorites", e)
            }
    }

    fun getSpotifyTrackDetails(songId: String, context: Context, onResult: (SpotifyTrack) -> Unit) {
        val url = "https://spotify-scraper.p.rapidapi.com/v1/track/metadata?trackId=$songId"
        val request = object : StringRequest(Method.GET, url,
            Response.Listener<String> { response ->
                try {
                    val jsonObj = JSONObject(response)
                    val name = jsonObj.getString("name")
                    val albumName = jsonObj.getJSONObject("album").getString("name")
                    val imageUrl = jsonObj.getJSONObject("album").getJSONArray("cover").getJSONObject(0).getString("url")
                    val shareUrl = jsonObj.getString("shareUrl")  // Assuming the response contains a shareUrl

                    onResult(SpotifyTrack(songId, name, albumName, imageUrl, shareUrl))
                } catch (e: JSONException) {
                    Log.e("API Error", "Failed to parse the song details: ${e.message}")
                }
            },
            Response.ErrorListener { error ->
                Log.e("API Error", "Network error: ${error.message}")
            }) {
            override fun getHeaders(): Map<String, String> {
                val headers = HashMap<String, String>()
                headers["X-RapidAPI-Host"] = "spotify-scraper.p.rapidapi.com"
                headers["X-RapidAPI-Key"] = "aa70132aacmsha524da9b490da06p1bc0cbjsn478c39da0e0f"
                return headers
            }
        }
        Volley.newRequestQueue(context).add(request)
    }


    @Composable
    fun FindMusicPage() {
        val context = LocalContext.current
        val songName = remember { mutableStateOf<String?>(null) }
        val externalLink = remember { mutableStateOf<String?>(null) }
        val isRecording = remember { mutableStateOf(false) }
        val recordingTime = remember { mutableIntStateOf(0) }
        val timerJob = remember { mutableStateOf<Job?>(null) }
        val songId = remember { mutableStateOf<String?>(null) }
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val snackbarHostState = remember { SnackbarHostState() }

        Surface(color = MaterialTheme.colors.background) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    RecordingButton(
                        isRecording = isRecording.value,
                        onClick = {
                            if (mediaRecorder == null) {
                                startRecording()
                                isRecording.value = true
                                recordingTime.value = 0
                                timerJob.value = CoroutineScope(Dispatchers.Main).launch {
                                    while (isActive) {
                                        delay(1000)
                                        recordingTime.value += 1
                                    }
                                }
                            } else {
                                stopRecording()
                                isRecording.value = false
                                timerJob.value?.cancel()
                                uploadAndRecognizeAudio(context, songId, songName, externalLink)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isRecording.value) {
                        Text(
                            "Recording: ${recordingTime.value} seconds",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface
                        )
                    } else {
                        Text(
                            "Tap to record",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    songName.value?.let {
                        Text(
                            "Recognized Song: $it",
                            style = MaterialTheme.typography.subtitle2
                        )
                    }

                    songId.value?.let { id ->
                        Text(
                            "Song id: $id",
                            style = MaterialTheme.typography.subtitle2
                        )
                    }

                    externalLink.value?.let { link ->
                        Row {
                            OutlinedButton(
                                onClick = { openUrl(context, link) }
                            ) {
                                Text("Open Song Link")
                            }
                            if (userId != null && songId.value != null) {
                                Button(
                                    onClick = {
                                        userId?.let { uid -> songId.value?.let { id ->
                                            addToFavorites(uid, id, snackbarHostState)
                                        } }
                                    },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Magenta)
                                ) {
                                    Text("Add to Favorites")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun addToFavorites(userId: String, songId: String, snackbarHostState: SnackbarHostState) {
        val firestore = FirebaseFirestore.getInstance()
        val userFavoritesRef = firestore.collection("favorites").document(userId)

        userFavoritesRef.get().addOnSuccessListener { document ->
            val currentFavorites = document.get("song_ids") as? MutableList<String> ?: mutableListOf()
            if (songId !in currentFavorites) {
                currentFavorites.add(songId)
                userFavoritesRef.update("song_ids", currentFavorites)
                    .addOnSuccessListener {
                        Log.d("Firestore", "Song added to favorites successfully")
                        CoroutineScope(Dispatchers.Main).launch {
                            snackbarHostState.showSnackbar("Song added to favorites")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error adding song to favorites", e)
                    }
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    snackbarHostState.showSnackbar("Song is already in favorites")
                }
            }
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error accessing favorites", e)
            CoroutineScope(Dispatchers.Main).launch {
                snackbarHostState.showSnackbar("Failed to access favorites")
            }
        }
    }



    @Composable
    fun RecordingButton(isRecording: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(180.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(backgroundColor = if (isRecording) Color.Gray else Color.Red)
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Close else Icons.Default.Person,
                contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                tint = Color.White,
                modifier = Modifier.size(50.dp)
            )
        }
    }

    private fun startRecording() {
        audioFile = File(getExternalFilesDir(null), "audio_record.m4a")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(audioFile?.absolutePath)
            prepare()
            start()
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
    }

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    private fun uploadAndRecognizeAudio(context: Context, songId: MutableState<String?>, songName: MutableState<String?>, externalLink: MutableState<String?>) {
        val storageRef = FirebaseStorage.getInstance().reference.child("audio/${audioFile?.name}")
        storageRef.putFile(Uri.fromFile(audioFile)).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                sendAudioToRecognition(context, downloadUri.toString(), songId, songName, externalLink)
            }
        }.addOnFailureListener {
            Log.e("Upload", "Failed to upload audio: ${it.message}")
        }
    }

    private fun sendAudioToRecognition(context: Context, fileUrl: String, songId: MutableState<String?>, songName: MutableState<String?>, externalLink: MutableState<String?>) {
        val url = "https://api.audd.io/"
        val request = object : StringRequest(Method.POST, url,
            Response.Listener<String> { response ->
                try {
                    val jsonObj = JSONObject(response)
                    val result = jsonObj.getJSONObject("result")
                    val spotify = result.getJSONObject("spotify")
                    songName.value = result.getString("title")
                    externalLink.value = result.getString("song_link")
                    songId.value = spotify.getString("id")
                } catch (e: JSONException) {
                    songName.value = "Song not recognized"
                    externalLink.value = null
                    Log.e("API Error", "Failed to parse the song recognition result", e)
                }
            },
            Response.ErrorListener { error ->
                songName.value = "Failed to recognize song"
                externalLink.value = null
                Log.e("API Error", "Network error: ${error.message}")
            }) {

            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["api_token"] = "c391dd06f91b71bd78b1aca6e2595aee"
                params["url"] = fileUrl
                params["return"] = "spotify"
                return params
            }
        }

        Volley.newRequestQueue(context).add(request)
    }




    @Composable
    fun ProfilePage(database: DatabaseReference) {
        val context = LocalContext.current
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val user = remember { mutableStateOf<User?>(null) }
        val auth = FirebaseAuth.getInstance()
        val newImageUrl = remember { mutableStateOf<String?>(null) }
        val firestore = FirebaseFirestore.getInstance()

        LaunchedEffect(Unit) {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                firestore.collection("users").document(userId).get().addOnSuccessListener { document ->
                    val email = document.getString("email") ?: "No Email"
                    val profileImg = document.getString("profile_img") ?: "No Image"
                    user.value = User(email, profileImg)
                }.addOnFailureListener { exception ->
                    Log.e("ProfilePage", "Error fetching user data", exception)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                user.value?.let {
                    UserProfileImage(it.profileImgUrl)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = it.email,
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    uploadProfileImage(context, userId, newImageUrl)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (newImageUrl.value != null) {
                        SaveChangesButton(userId, newImageUrl.value ?: "") {
                            user.value?.profileImgUrl = newImageUrl.value!!
                            newImageUrl.value = null
                        }
                    }

                }
                Spacer(modifier = Modifier.weight(1f))
                LogoutButton(context)
            }
        }
    }

    @Composable
    fun uploadProfileImage(context: Context, userId: String, newImageUrl: MutableState<String?>) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == ComponentActivity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val storageRef = FirebaseStorage.getInstance().reference.child("profiles/$userId.jpg")
                    storageRef.putFile(uri).addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            newImageUrl.value = downloadUri.toString()
                        }
                    }.addOnFailureListener {
                        Log.e("Upload", "Failed to upload image: ${it.message}")
                    }
                }
            }
        }
        Button(onClick = { launcher.launch(intent) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Text(
                text = "Upload New Image",
                style = MaterialTheme.typography.button,
                color = MaterialTheme.colors.primary
            )
        }
    }

    @Composable
    fun SaveChangesButton(userId: String, imageUrl: String, onSaveComplete: () -> Unit) {
        Button(onClick = {
            updateProfileImageUrl(userId, imageUrl) {
                onSaveComplete()
            }
        }) {
            Text(
                text = "Save Changes",
                style = MaterialTheme.typography.button,
                color = MaterialTheme.colors.onPrimary
            )
        }
    }

    fun updateProfileImageUrl(userId: String, imageUrl: String, onComplete: () -> Unit) {
        val userRef = FirebaseFirestore.getInstance().collection("users").document(userId)
        userRef.update("profile_img", imageUrl).addOnSuccessListener {
            Log.d("Firestore", "Profile image updated successfully")
            onComplete()
        }.addOnFailureListener {
            Log.e("Firestore", "Error updating profile image: ${it.message}")
        }
    }

    @Composable
    fun LogoutButton(context: Context) {
        Button(
            onClick = {
                FirebaseAuth.getInstance().signOut()
                context.startActivity(Intent(context, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Logout",
                style = MaterialTheme.typography.button,
                color = MaterialTheme.colors.onPrimary
            )
        }
    }

    @Composable
    fun UserProfileImage(imageUrl: String) {
        Image(
            painter = rememberImagePainter(imageUrl),
            contentDescription = "Profile Image",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
        )
    }


    private fun navigateToLogin() {
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
