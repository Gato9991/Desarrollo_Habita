package com.habita.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habita.app.data.DemoLocationsRepository
import com.habita.app.model.PropertyLocation
import com.habita.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluarPropiedadScreen(
    onAnalyzeLocation: (PropertyLocation) -> Unit = {},
    onOpenInfo: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("Av. Providencia 1234") }
    var selectedLocation by remember { mutableStateOf<PropertyLocation>(DemoLocationsRepository.providenciaDemo) }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = HabitaBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Evaluar propiedad",
                        style = Typography.titleLarge,
                        color = HabitaTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(HabitaLightGreenBg)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = HabitaForestGreen
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenInfo) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Información del proyecto",
                            tint = HabitaForestGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HabitaBackground)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Main Title Question
            Text(
                text = "¿Qué dirección quieres conocer?",
                style = Typography.headlineLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
                color = HabitaTextPrimary
            )

            // Subtitle
            Text(
                text = "Ingresa una propiedad para estimar la habitabilidad de su entorno.",
                style = Typography.bodyLarge,
                color = HabitaTextSecondary
            )

            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    selectedLocation = DemoLocationsRepository.searchLocation(query)
                },
                placeholder = { Text("Calle, número y comuna") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = HabitaForestGreen
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HabitaForestGreen,
                    unfocusedBorderColor = Color(0xFFC8E0D4),
                    focusedContainerColor = HabitaSurface,
                    unfocusedContainerColor = HabitaSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            )

            // Demo Location Card
            Card(
                colors = CardDefaults.cardColors(containerColor = HabitaLightGreenBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        searchQuery = "Av. Providencia 1234"
                        selectedLocation = DemoLocationsRepository.providenciaDemo
                    }
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "EJEMPLO PARA EL PROTOTIPO",
                        style = Typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.5.sp),
                        color = HabitaForestGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = selectedLocation.direccion,
                        style = Typography.headlineMedium.copy(fontSize = 20.sp),
                        color = HabitaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${selectedLocation.comuna}, ${selectedLocation.ciudad}",
                        style = Typography.bodyMedium,
                        color = HabitaTextSecondary
                    )
                }
            }

            // Quick Demo Selection Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Otras ubicaciones demo:",
                    style = Typography.labelLarge,
                    color = HabitaTextMuted
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        DemoLocationsRepository.sanBernardoDemo,
                        DemoLocationsRepository.estadiNacionalDemo,
                        DemoLocationsRepository.maipuDemo
                    ).forEach { loc ->
                        FilterChip(
                            selected = (selectedLocation.id == loc.id),
                            onClick = {
                                selectedLocation = loc
                                searchQuery = loc.direccion
                            },
                            label = { Text(loc.comuna, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HabitaForestGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Section "Información considerada"
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Información considerada",
                    style = Typography.titleLarge,
                    color = HabitaTextPrimary
                )

                InfoCheckItem("Entorno ambiental y ruido")
                InfoCheckItem("Seguridad y conectividad")
                InfoCheckItem("Servicios de salud y transporte")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button "Analizar habitabilidad"
            Button(
                onClick = { onAnalyzeLocation(selectedLocation) },
                colors = ButtonDefaults.buttonColors(containerColor = HabitaForestGreen),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "Analizar habitabilidad",
                    style = Typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoCheckItem(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HabitaSurface)
            .border(1.dp, Color(0xFFE5EDE8), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(HabitaLightGreenBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = HabitaForestGreen,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = title,
            style = Typography.titleMedium.copy(fontSize = 15.sp),
            color = HabitaTextPrimary
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EvaluarPropiedadScreenPreview() {
    HabitaTheme {
        EvaluarPropiedadScreen()
    }
}
