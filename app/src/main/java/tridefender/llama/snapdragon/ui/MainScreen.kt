package tridefender.llama.snapdragon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tridefender.llama.snapdragon.ui.kernel.KernelScreen
import tridefender.llama.snapdragon.ui.launcher.AllConfigScreen
import tridefender.llama.snapdragon.ui.runtime.RuntimeScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Config") },
                    selected = currentRoute == "config",
                    onClick = {
                        if (currentRoute != "config") {
                            navController.navigate("config") {
                                popUpTo("config") { inclusive = true }
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Memory, contentDescription = null) },
                    label = { Text("Kernels") },
                    selected = currentRoute == "kernels",
                    onClick = {
                        if (currentRoute != "kernels") {
                            navController.navigate("kernels") {
                                popUpTo("config") { inclusive = false }
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    label = { Text("Runtime") },
                    selected = currentRoute == "runtime",
                    onClick = {
                        if (currentRoute != "runtime") {
                            navController.navigate("runtime") {
                                popUpTo("config") { inclusive = false }
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "config",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("config") {
                AllConfigScreen()
            }
            composable("kernels") {
                KernelScreen()
            }
            composable("runtime") {
                RuntimeScreen()
            }
        }
    }
}
