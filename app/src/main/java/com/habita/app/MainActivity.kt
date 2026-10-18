package com.habita.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.habita.app.data.DemoLocationsRepository
import com.habita.app.model.PropertyLocation
import com.habita.app.ui.screens.DetalleEntornoScreen
import com.habita.app.ui.screens.EvaluarPropiedadScreen
import com.habita.app.ui.screens.RequisitosValidarScreen
import com.habita.app.ui.screens.ResultadoAnalisisScreen
import com.habita.app.ui.theme.HabitaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HabitaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HabitaAppNavigation()
                }
            }
        }
    }
}

sealed class Screen {
    data object EvaluarPropiedad : Screen()
    data class ResultadoAnalisis(val location: PropertyLocation) : Screen()
    data class DetalleEntorno(val location: PropertyLocation) : Screen()
    data object RequisitosValidar : Screen()
}

@Composable
fun HabitaAppNavigation() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.EvaluarPropiedad) }
    var selectedLocation by remember { mutableStateOf<PropertyLocation>(DemoLocationsRepository.providenciaDemo) }

    when (val screen = currentScreen) {
        is Screen.EvaluarPropiedad -> {
            EvaluarPropiedadScreen(
                onAnalyzeLocation = { location ->
                    selectedLocation = location
                    currentScreen = Screen.ResultadoAnalisis(location)
                },
                onOpenInfo = {
                    currentScreen = Screen.RequisitosValidar
                }
            )
        }
        is Screen.ResultadoAnalisis -> {
            ResultadoAnalisisScreen(
                location = screen.location,
                onBack = {
                    currentScreen = Screen.EvaluarPropiedad
                },
                onViewDetail = {
                    currentScreen = Screen.DetalleEntorno(screen.location)
                }
            )
        }
        is Screen.DetalleEntorno -> {
            DetalleEntornoScreen(
                location = screen.location,
                onBack = {
                    currentScreen = Screen.ResultadoAnalisis(screen.location)
                }
            )
        }
        is Screen.RequisitosValidar -> {
            RequisitosValidarScreen(
                onBack = {
                    currentScreen = Screen.EvaluarPropiedad
                }
            )
        }
    }
}
