package com.lifemanga.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lifemanga.android.ui.LifeMangaNavHost
import com.lifemanga.android.ui.theme.LifeMangaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifeMangaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LifeMangaNavHost()
                }
            }
        }
    }
}
