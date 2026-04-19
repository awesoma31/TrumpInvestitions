package org.awesoma.trumpinvestitions.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.awesoma.trumpinvestitions.data.stub.StubRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val user = StubRepository.currentUser

    Scaffold(
        topBar = { TopAppBar(title = { Text("Профиль") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text(user.username, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            ListItem(
                headlineContent = { Text("Логин") },
                trailingContent = { Text(user.username) }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Баланс") },
                trailingContent = { Text("$${String.format("%.2f", user.balance)}") }
            )
            HorizontalDivider()

            Spacer(Modifier.height(32.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text("Выйти из аккаунта")
            }
        }
    }
}
