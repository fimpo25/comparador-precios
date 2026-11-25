package com.majestad.precios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.majestad.precios.ui.BuscarScreen
import com.majestad.precios.ui.theme.ComparadorTheme
import com.majestad.precios.ui.update.UpdateScreen
import com.majestad.precios.update.UpdateManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateManager.autoCheck()
        setContent {
            ComparadorTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "buscar") {
                        composable("buscar") {
                            BuscarScreen(onAbrirActualizaciones = { nav.navigate("updates") })
                        }
                        composable("updates") {
                            UpdateScreen(onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
