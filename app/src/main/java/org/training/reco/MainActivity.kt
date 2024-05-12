package org.training.reco

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberImagePainter
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        val database = FirebaseDatabase.getInstance().reference

        setContent {
            AppContent(database)
        }
    }

    data class User(
        val email: String,
        var profileImgUrl: String,
        val playlists: List<String>
    )




    @Composable
    fun AppContent(database: DatabaseReference) {
        val navController = rememberNavController()

        Scaffold(
            bottomBar = {
                BottomNavigation {
                    BottomNavigationItem(
                        selected = navController.currentDestination?.route == "Playlists",
                        onClick = {
                            navController.navigate("Playlists")
                        },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                        label = { Text(text = "Playlists") }
                    )

                    BottomNavigationItem(
                        selected = navController.currentDestination?.route == "playlist",
                        onClick = {
                            navController.navigate("playlist")
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
            // Apply padding to the content
            Box(modifier = Modifier.padding(innerPadding)) {
                // Use NavHost for navigation
                NavHost(navController, startDestination = "playlist") {
                    composable("Playlists") { PlaylistsPage() }
                    composable("playlist") { PlaylistPage() }
                    composable("profile") { ProfilePage(database) } // Pass database reference
                }
            }
        }
    }


    @Composable
    fun PlaylistsPage() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Playlists Page")
        }
    }

    @Composable
    fun PlaylistPage() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Find Song Page")
        }
    }

    @Composable
    fun ProfilePage(database: DatabaseReference) {
        val context = LocalContext.current
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val user = remember { mutableStateOf<User?>(null) }
        val auth = FirebaseAuth.getInstance()
        val newImageUrl = remember { mutableStateOf<String?>(null) }
        val firestore = FirebaseFirestore.getInstance()

        // Fetch user data from Firestore
        LaunchedEffect(Unit) {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                firestore.collection("users").document(userId).get().addOnSuccessListener { document ->
                    val email = document.getString("email") ?: "No Email"
                    val profileImg = document.getString("profile_img") ?: "No Image"
                    val playlists = document.get("playlist") as? List<String> ?: listOf()  // Assuming 'playlist' is a field with a list of strings
                    user.value = User(email, profileImg, playlists)
                    Log.d("ProfilePage", "User data: Email = $email, Image = $profileImg")
                }.addOnFailureListener { exception ->
                    Log.e("ProfilePage", "Error fetching user data", exception)
                }
            }
        }

        // Display user data
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column {
                user.value?.let {
                    UserProfileImage(it.profileImgUrl)
                    Text("Email: ${it.email}")
                    it.playlists.forEach { playlist ->
                        Text("Playlist: $playlist")
                    }
                }
                Spacer(Modifier.height(16.dp))
                uploadProfileImage(context, userId, newImageUrl)
                if (newImageUrl.value != null) {
                    SaveChangesButton(userId, newImageUrl.value!!) {
                        // Update the user profile image in the state and fetch data again
                        user.value?.profileImgUrl = newImageUrl.value!!
                        newImageUrl.value = null
                        // Optionally fetch user data again here if needed
                    }
                }
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
                            newImageUrl.value = downloadUri.toString() // Temporarily store new image URL
                        }
                    }.addOnFailureListener {
                        Log.e("Upload", "Failed to upload image: ${it.message}")
                    }
                }
            }
        }
        Button(onClick = { launcher.launch(intent) }) {
            Text("Upload New Image")
        }
    }

    @Composable
    fun SaveChangesButton(userId: String, imageUrl: String, onSaveComplete: () -> Unit) {
        Button(onClick = {
            updateProfileImageUrl(userId, imageUrl) {
                onSaveComplete()
            }
        }) {
            Text("Save Changes")
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
        Button(onClick = {
            FirebaseAuth.getInstance().signOut()
            context.startActivity(Intent(context, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }) {
            Text("Logout")
        }
    }

    @Composable
    fun UserProfileImage(imageUrl: String) {
        Image(
            painter = rememberImagePainter(imageUrl),
            contentDescription = "Profile Image",
            modifier = Modifier.size(128.dp).clip(CircleShape)
        )
    }


    private fun navigateToLogin() {
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
