package org.awesoma.trumpinvestitions.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.awesoma.trumpinvestitions.ui.screens.auth.inputColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.awesoma.trumpinvestitions.TrumpApp
import org.awesoma.trumpinvestitions.ui.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val vm: AuthViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as TrumpApp

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var serverHost by remember { mutableStateOf(app.settingsManager.serverHost) }
    var serverExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onRegisterSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Регистрация", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value         = username,
            onValueChange = { username = it },
            label         = { Text("Логин") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            colors        = inputColors()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value         = email,
            onValueChange = { email = it },
            label         = { Text("Email") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            colors        = inputColors()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value                = password,
            onValueChange        = { password = it },
            label                = { Text("Пароль") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector        = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль"
                    )
                }
            },
            singleLine = true,
            modifier   = Modifier.fillMaxWidth(),
            colors     = inputColors()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value                = confirmPassword,
            onValueChange        = { confirmPassword = it },
            label                = { Text("Подтвердите пароль") },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(
                        imageVector        = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (confirmPasswordVisible) "Скрыть пароль" else "Показать пароль"
                    )
                }
            },
            singleLine = true,
            modifier   = Modifier.fillMaxWidth(),
            colors     = inputColors()
        )
        val errorText = localError ?: uiState.error
        if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorText, color = Color(0xFFF44336), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                localError = null
                if (password != confirmPassword) {
                    localError = "Пароли не совпадают"
                } else {
                    vm.register(username, email, password)
                }
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Зарегистрироваться")
            }
        }
        TextButton(onClick = onNavigateToLogin) {
            Text("Уже есть аккаунт? Войти")
        }

        Spacer(Modifier.height(24.dp))

        TextButton(onClick = { serverExpanded = !serverExpanded }) {
            Text(
                if (serverExpanded) "▲ Сервер: $serverHost" else "▼ Настройки сервера",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (serverExpanded) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = serverHost,
                onValueChange = { serverHost = it },
                label = { Text("host:port") },
                placeholder = { Text("192.168.0.3:8080") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    app.settingsManager.serverHost = serverHost.trim()
                    app.rebuildNetwork()
                    serverExpanded = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить")
            }
        }
    }
}
