package com.habita.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun ResultadoAnalisisScreen(
    location: PropertyLocation,
    onBack: () -> Unit = {},
    onViewDetail: () -> Unit = {}
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
                        text = "Resultado del análisis",
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

            // Address Header
            Column {
                Text(
                    text = location.direccion,
                    style = Typography.headlineLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
                    color = HabitaTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${location.comuna}, ${location.ciudad}",
                    style = Typography.bodyLarge,
                    color = HabitaTextSecondary
                )
            }

            // Big Hero Habitability Score Card (Dark Green)
            Card(
                colors = CardDefaults.cardColors(containerColor = HabitaForestGreen),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // White Circle Badge
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$score",
                                style = Typography.headlineLarge.copy(
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = HabitaForestGreen
                            )
                            Text(
                                text = "de 100",
                                style = Typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = HabitaForestGreen.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Score Text details
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "ÍNDICE HABITA",
                            style = Typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = result.clasificacion,
                            style = Typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 24.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = result.descripcionClasificacion,
                            style = Typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Variables section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Variables del entorno",
                    style = Typography.titleLarge,
                    color = HabitaTextPrimary
                )
                Text(
                    text = "0—100",
                    style = Typography.bodyMedium,
                    color = HabitaTextMuted
                )
            }

            // 2x2 Sub-scores Grid
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VariableScoreCard(
                        title = "Ruido",
                        score = result.subScoresSummary.ruido,
                        barColor = HabitaRuidoOrange,
                        modifier = Modifier.weight(1f)
                    )
                    VariableScoreCard(
                        title = "Ambiente",
                        score = result.subScoresSummary.ambiente,
                        barColor = HabitaAmbienteGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VariableScoreCard(
                        title = "Seguridad",
                        score = result.subScoresSummary.seguridad,
                        barColor = HabitaSeguridadBlue,
                        modifier = Modifier.weight(1f)
                    )
                    VariableScoreCard(
                        title = "Movilidad",
                        score = result.subScoresSummary.movilidad,
                        barColor = HabitaMovilidadGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Button to View Detailed Breakdown
            Button(
                onClick = onViewDetail,
                colors = ButtonDefaults.buttonColors(containerColor = HabitaForestGreen),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "Ver detalle del entorno",
                    style = Typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VariableScoreCard(
    title: String,
    score: Int,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    var animatedProgress by remember { mutableFloatStateOf(0f) }
    val progressAnimation by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = 800)
    )

    LaunchedEffect(score) {
        animatedProgress = (score / 100f).coerceIn(0f, 1f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = HabitaSurface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = Typography.titleMedium.copy(fontSize = 15.sp),
                    color = HabitaTextPrimary
                )
                Text(
                    text = "$score",
                    style = Typography.headlineMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    color = barColor
                )
            }

            // Custom Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8EFEB))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressAnimation)
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ResultadoAnalisisScreenPreview() {
    HabitaTheme {
        ResultadoAnalisisScreen(location = DemoLocationsRepository.providenciaDemo)
    }
}
