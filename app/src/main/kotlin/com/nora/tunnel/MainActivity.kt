package com.nora.tunnel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nora.tunnel.ui.nav.NoraNav
import com.nora.tunnel.ui.theme.NoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as NoraApp).settings
        setContent { NoraTheme { NoraNav(settings) } }
    }
}
