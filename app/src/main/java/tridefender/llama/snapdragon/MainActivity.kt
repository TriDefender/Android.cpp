package tridefender.llama.snapdragon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import tridefender.llama.snapdragon.ui.MainScreen
import tridefender.llama.snapdragon.ui.theme.LlamaServerGUIWrapperTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LlamaServerGUIWrapperTheme {
                MainScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}