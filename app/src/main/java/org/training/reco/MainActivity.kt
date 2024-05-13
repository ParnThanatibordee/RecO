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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
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
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                        label = { Text(text = "Favorites") }
                    )

                    BottomNavigationItem(
                        selected = navController.currentDestination?.route == "FindMusic",
                        onClick = {
                            navController.navigate("FindMusic")
                        },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Favorites Page")
        }
    }

    @Composable
    fun FindMusicPage() {
        val context = LocalContext.current
        val songName = remember { mutableStateOf<String?>(null) }
        val externalLink = remember { mutableStateOf<String?>(null) }
        val isRecording = remember { mutableStateOf(false) }
        val recordingTime = remember { mutableIntStateOf(0) } // in seconds
        val timerJob = remember { mutableStateOf<Job?>(null) }

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
                                uploadAndRecognizeAudio(context, songName, externalLink)
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
                            style = MaterialTheme.typography.h6
                        )
                    }

                    externalLink.value?.let { link ->
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { openUrl(context, link) }
                        ) {
                            Text("Open Song Link")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun RecordingButton(isRecording: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(150.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(backgroundColor = if (isRecording) Color.Gray else Color.Red)
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Close else Icons.Default.Person,
                contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                tint = Color.White
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

    private fun uploadAndRecognizeAudio(context: Context, songName: MutableState<String?>, externalLink: MutableState<String?>) {
        val storageRef = FirebaseStorage.getInstance().reference.child("audio/${audioFile?.name}")
        storageRef.putFile(Uri.fromFile(audioFile)).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                sendAudioToRecognition(context, downloadUri.toString(), songName, externalLink)
            }
        }.addOnFailureListener {
            Log.e("Upload", "Failed to upload audio: ${it.message}")
        }
    }

    private fun sendAudioToRecognition(context: Context, fileUrl: String, songName: MutableState<String?>, externalLink: MutableState<String?>) {
        val url = "https://api.audd.io/"
        val request = object : StringRequest(Method.POST, url,
            Response.Listener<String> { response ->
                try {
                    val jsonObj = JSONObject(response)
                    val result = jsonObj.getJSONObject("result")
                    songName.value = result.getString("title")
                    externalLink.value = result.getString("song_link")
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
