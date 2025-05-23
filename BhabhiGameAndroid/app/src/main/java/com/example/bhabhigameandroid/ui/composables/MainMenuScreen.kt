package com.example.bhabhigameandroid.ui.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun MainMenuScreen(navController: NavController) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFAF8F0) // Primary Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bhabhi Card Game",
                style = MaterialTheme.typography.h3,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333), // Text Color (Title)
                modifier = Modifier.padding(bottom = 48.dp)
            )

            Button(
                onClick = { navController.navigate("game_screen") },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF008080), // Accent Color 1 (Primary Buttons)
                    contentColor = Color(0xFFFFFFFF) // Button Text Color (on Primary)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text("Play vs Bots", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { /* Placeholder for future */ },
                enabled = false,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFD3D3D3), // Disabled Button Background
                    contentColor = Color(0xFFA9A9A9), // Disabled Button Text Color
                    disabledBackgroundColor = Color(0xFFD3D3D3),
                    disabledContentColor = Color(0xFFA9A9A9)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text("Multiplayer (Coming Soon)", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { /* Placeholder for future */ },
                enabled = false,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFD3D3D3), // Disabled Button Background
                    contentColor = Color(0xFFA9A9A9), // Disabled Button Text Color
                    disabledBackgroundColor = Color(0xFFD3D3D3),
                    disabledContentColor = Color(0xFFA9A9A9)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text("Settings (Coming Soon)", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { navController.navigate("rules_screen") },
                enabled = true,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF008080), // Accent Color 1 (Primary Buttons)
                    contentColor = Color(0xFFFFFFFF) // Button Text Color (on Primary)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text("Rules", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
