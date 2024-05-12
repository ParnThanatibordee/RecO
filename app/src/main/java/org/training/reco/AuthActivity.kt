package org.training.reco

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

class AuthActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            if (auth.currentUser == null) {
                LoginScreen(onLoginSuccess = { navigateToMain() }, onNavigateToRegister = { navigateToRegister() })
            } else {
                navigateToMain()
            }
        }
    }

    @Composable
    fun LoginScreen(onLoginSuccess: () -> Unit, onNavigateToRegister: () -> Unit) {
        val email = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val showDialog = remember { mutableStateOf(false) }
        val errorMessage = remember { mutableStateOf("") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF383838)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Welcome Back!", color = Color(0xFFFFFFFF), fontSize = 24.sp, modifier = Modifier.padding(bottom = 24.dp))
                CustomTextField(value = email.value, label = "Email", onValueChange = { email.value = it })
                Spacer(modifier = Modifier.height(16.dp))
                CustomTextField(value = password.value, label = "Password", onValueChange = { password.value = it })
                Spacer(modifier = Modifier.height(24.dp))
                CustomButton(text = "Login", onClick = {
                    if (email.value.isEmpty() || password.value.isEmpty()) {
                        errorMessage.value = "Please fill in all fields."
                        showDialog.value = true
                    } else {
                        loginUser(email.value, password.value, onLoginSuccess, showDialog, errorMessage)
                    }
                })
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onNavigateToRegister) {
                    Text("Need an account? Register", color = Color.White)
                }
            }
        }
        if (showDialog.value) {
            AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = { Text("Login Error") },
                text = { Text(errorMessage.value) },
                confirmButton = {
                    Button(onClick = { showDialog.value = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }



    private fun loginUser(email: String, password: String, onSuccess: () -> Unit, showDialog: MutableState<Boolean>, errorMessage: MutableState<String>) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    errorMessage.value = task.exception?.message ?: "Unknown error"
                    showDialog.value = true
                }
            }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToRegister() {
        setContent {
            RegisterScreen({ navigateToMain() }, { navigateToLoginScreen() })
        }
    }

    private fun navigateToLoginScreen() {
        setContent {
            LoginScreen(onLoginSuccess = { navigateToMain() }, onNavigateToRegister = { navigateToRegister() })
        }
    }

    @Composable
    fun RegisterScreen(onRegistrationComplete: () -> Unit, onBackToLogin: () -> Unit) {
        val email = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val confirmPassword = remember { mutableStateOf("") }
        val showDialog = remember { mutableStateOf(false) }
        val errorMessage = remember { mutableStateOf("") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF383838)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Create Your Account", color = Color(0xFFFFFFFF), fontSize = 24.sp, modifier = Modifier.padding(bottom = 24.dp))
                CustomTextField(value = email.value, label = "Email", onValueChange = { email.value = it })
                Spacer(modifier = Modifier.height(16.dp))
                CustomTextField(value = password.value, label = "Password", onValueChange = { password.value = it })
                Spacer(modifier = Modifier.height(16.dp))
                CustomTextField(value = confirmPassword.value, label = "Confirm Password", onValueChange = { confirmPassword.value = it })
                Spacer(modifier = Modifier.height(24.dp))
                CustomButton(text = "Register", onClick = {
                    if (email.value.isEmpty() || password.value.isEmpty() || confirmPassword.value.isEmpty()) {
                        errorMessage.value = "All fields must be filled out."
                        showDialog.value = true
                    } else if (password.value != confirmPassword.value) {
                        errorMessage.value = "Passwords do not match."
                        showDialog.value = true
                    } else {
                        registerUser(email.value, password.value, onRegistrationComplete, showDialog, errorMessage)
                    }
                })
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onBackToLogin) {
                    Text("Already have an account? Login", color = Color.White)
                }
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CustomTextField(value: String, label: String, onValueChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, style = TextStyle(color = Color(0xFFCAC7C7))) },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFCAC7C7),
                unfocusedBorderColor = Color(0xFFCAC7C7),
                cursorColor = Color.White,
                unfocusedLabelColor = Color(0xFFCAC7C7),
                focusedLabelColor = Color(0xFFCAC7C7)
            ),
            textStyle = LocalTextStyle.current.copy(color = Color.White)
        )
    }




    @Composable
    fun CustomButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier.padding(horizontal = 60.dp)
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFFFFF)),
            modifier = modifier.fillMaxWidth()
        ) {
            Text(text, color = Color.Black)
        }
    }



    private fun registerUser(email: String, password: String, onSuccess: () -> Unit, showDialog: MutableState<Boolean>, errorMessage: MutableState<String>) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    errorMessage.value = task.exception?.message ?: "Unknown error"
                    showDialog.value = true
                }
            }
    }

}
