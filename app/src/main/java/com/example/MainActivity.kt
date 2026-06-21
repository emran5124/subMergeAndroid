package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.YoutubeSearchedFor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.ReviewerScreen
import com.example.ui.SettingsScreen
import com.example.ui.SubtitleStudioViewModel
import com.example.ui.YoutubeExtractorScreen
import com.example.ui.AiSubtitleScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SubtitleStudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var selectedTab by remember { mutableStateOf(0) }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text("YT Extractor") },
                                icon = { Icon(Icons.Filled.YoutubeSearchedFor, contentDescription = "YouTube Extractor") },
                                modifier = Modifier.testTag("tab_yt")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text("Reviewer Studio") },
                                icon = { Icon(Icons.Filled.Translate, contentDescription = "Reviewer Studio") },
                                modifier = Modifier.testTag("tab_reviewer")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                label = { Text("AI Subtitles") },
                                icon = { Icon(Icons.Filled.Audiotrack, contentDescription = "AI Audio Subtitles") },
                                modifier = Modifier.testTag("tab_ai")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                label = { Text("API Settings") },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                                modifier = Modifier.testTag("tab_settings")
                            )
                        }
                    }
                ) { innerPadding ->
                    val bottomPaddingModifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                    when (selectedTab) {
                        0 -> YoutubeExtractorScreen(viewModel = viewModel, modifier = bottomPaddingModifier.statusBarsPadding())
                        1 -> ReviewerScreen(viewModel = viewModel, modifier = bottomPaddingModifier)
                        2 -> AiSubtitleScreen(viewModel = viewModel, modifier = bottomPaddingModifier.statusBarsPadding())
                        3 -> SettingsScreen(viewModel = viewModel, modifier = bottomPaddingModifier.statusBarsPadding())
                    }
                }
            }
        }
    }
}
