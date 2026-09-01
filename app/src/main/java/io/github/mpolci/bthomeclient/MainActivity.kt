package io.github.mpolci.bthomeclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.mpolci.bthomeclient.ui.theme.BTHomeClientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BTHomeClientTheme {
                BTHomeClientApp()
            }
        }
    }
}

