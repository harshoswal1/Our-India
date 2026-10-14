@file:OptIn(ExperimentalMaterial3Api::class)

package com.ourindia.app.ui.civic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ourindia.app.data.civic.CivicRepository
import com.ourindia.app.ui.partystructure.SegmentedToggle
import com.ourindia.app.ui.theme.CivicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(val busy: Boolean = false, val error: String? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(private val repo: CivicRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state = _state.asStateFlow()

    fun signIn(email: String, password: String, onDone: () -> Unit) = perform({ repo.signIn(email, password) }, onDone)

    fun register(name: String, email: String, password: String, phone: String, onDone: () -> Unit) {
        val problem = when {
            name.isBlank() -> "Enter your name"
            !email.contains('@') -> "Enter a valid email"
            password.length < 8 -> "Use at least 8 characters for the password"
            else -> null
        }
        if (problem != null) { _state.value = AuthUiState(error = problem); return }
        perform({ repo.register(name, email, password, phone) }, onDone)
    }

    private fun perform(call: suspend () -> Result<*>, onDone: () -> Unit) = viewModelScope.launch {
        _state.value = AuthUiState(busy = true)
        call().onSuccess { _state.value = AuthUiState(); onDone() }
            .onFailure { e -> _state.value = AuthUiState(error = e.message) }
    }
}

@Composable
fun AuthScreen(onClose: () -> Unit, onSignedIn: () -> Unit, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = CivicColors.Navy) } },
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CivicColors.Background)
            )
        },
        containerColor = CivicColors.Background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NagrikVoiceMark()
            Text(
                if (mode == 0) "Sign in to report and support issues" else "Create your NagrikVoice account",
                style = MaterialTheme.typography.headlineMedium,
                color = CivicColors.Navy
            )
            Text(
                "Browsing parties and reports never needs an account. Signing in lets you put problems on the record under your name.",
                style = MaterialTheme.typography.bodyMedium,
                color = CivicColors.TextSecondary
            )
            SegmentedToggle(listOf("Sign in", "Create account"), mode, { mode = it })

            if (mode == 1) {
                CivicField(name, { name = it }, "Full name", "As you'd like it on your reports")
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            if (mode == 1) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                    label = { Text("Phone (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (showPassword) "Hide password" else "Show password")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CivicColors.Danger) }
            Button(
                onClick = {
                    if (mode == 0) viewModel.signIn(email, password, onSignedIn)
                    else viewModel.register(name, email, password, phone, onSignedIn)
                },
                enabled = !state.busy && email.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Civic),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                if (state.busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                else Text(if (mode == 0) "Sign in" else "Create account")
            }
            if (state.busy) WakingServerHint()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CivicColors.Surface,
    unfocusedContainerColor = CivicColors.Surface,
    focusedBorderColor = CivicColors.Civic,
    unfocusedBorderColor = CivicColors.Outline,
    focusedLabelColor = CivicColors.CivicDeep
)
