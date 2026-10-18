package com.habita.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
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
import com.habita.app.model.HabitaResult
import com.habita.app.model.PropertyLocation
import com.habita.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleEntornoScreen(
    location: PropertyLocation,
    onBack: () -> Unit = {}
) {
    val result: HabitaResult = remember(location) {
        DemoLocationsRepository.calculateResultForLocation(location)
    }

    val score = result.indiceHabita?.toInt() ?: 82
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = HabitaBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Detalle del entorno",
                        style = Typography.titleLarge,
                        color = HabitaTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
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
            Spacer(modifier = Modifier.height(2.dp))

            // Top Compact Summary Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = HabitaLightGreenBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dark Green Score Badge
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(HabitaForestGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$score",
                                style = Typography.headlineMedium.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "/100",
                                style = Typography.bodyMedium.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Address Info
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = location.direccion,
                            style = Typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            color = HabitaTextPrimary
                        )
                        Text(
                            text = "${location.comuna} · ${location.ciudad}",
                            style = Typography.bodyMedium,
                            color = HabitaTextSecondary
                        )
                        Text(
                            text = result.clasificacion,
                            style = Typography.labelLarge.copy(fontSize = 13.sp),
                            color = HabitaForestGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Section "Fortalezas del entorno"
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Fortalezas del entorno",
                    style = Typography.titleLarge,
                    color = HabitaTextPrimary
                )

                location.fortalezas.forEach { fortaleza ->
                    FortalezaCardItem(
                        title = fortaleza.titulo,
                        detail = fortaleza.detalle
                    )
                }
            }

            // Section "Aspectos a revisar"
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Aspectos a revisar",
                    style = Typography.titleLarge,
                    color = HabitaTextPrimary
                )

                location.aspectosARevisar.forEach { aspecto ->
                    AspectoRevisarCardItem(
                        title = aspecto.titulo,
                        detail = aspecto.detalle
                    )
                }
            }

            // Section "Medición dentro de la vivienda"
            Card(
                colors = CardDefaults.cardColors(containerColor = HabitaForestDark),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Medición dentro de la vivienda",
                            style = Typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "PRÓXIMA ETAPA",
                                style = Typography.labelLarge.copy(fontSize = 10.sp),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "Sensor temporal durante 48–72 horas para registrar ruido, temperatura, humedad y luminosidad reales.",
                        style = Typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FortalezaCardItem(title: String, detail: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HabitaSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, Color(0xFFE5EDE8), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = HabitaForestGreen,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = HabitaTextPrimary
                )
            }

            Text(
                text = detail,
                style = Typography.bodyMedium.copy(fontSize = 13.sp),
                color = HabitaTextSecondary
            )
        }
    }
}

@Composable
private fun AspectoRevisarCardItem(title: String, detail: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HabitaWarningBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(HabitaWarningText.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = HabitaWarningText,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = Typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = HabitaWarningText
                )
                Text(
                    text = detail,
                    style = Typography.bodyMedium.copy(fontSize = 13.sp),
                    color = Color(0xFF8C3800)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DetalleEntornoScreenPreview() {
    HabitaTheme {
        DetalleEntornoScreen(location = DemoLocationsRepository.providenciaDemo)
    }
}
