package com.habita.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habita.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequisitosValidarScreen(
    onBack: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = HabitaBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Proyecto HABITA",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Main Goal Header Card (Dark Green)
            Card(
                colors = CardDefaults.cardColors(containerColor = HabitaForestDark),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Requisitos por validar",
                        style = Typography.headlineMedium.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Objetivo: salir de la reunión con alcance cerrado para 2 semanas.",
                        style = Typography.bodyMedium.copy(fontSize = 14.sp),
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // Banner badge
            Card(
                colors = CardDefaults.cardColors(containerColor = HabitaLightGreenBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "SUPUESTOS PARA AVANZAR",
                        style = Typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.5.sp),
                        color = HabitaForestGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "App móvil · sin login · 1 dirección demo · datos simulados",
                        style = Typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = HabitaTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Item 01
            RequirementNumberCard(
                number = "01",
                title = "Usuario y decisión",
                description = "¿El usuario principal es comprador, arrendatario o corredora? Definir una sola decisión central para la beta."
            )

            // Item 02
            RequirementNumberCard(
                number = "02",
                title = "Datos territoriales",
                description = "Acordar comuna piloto, fuentes, cobertura, actualización y permisos. ¿Qué datos reales estarán disponibles?"
            )

            // Item 03
            RequirementNumberCard(
                number = "03",
                title = "Índice HABITA",
                description = "Definir variables, ponderaciones, umbrales y explicación del puntaje. ¿Será único o ajustable por perfil?"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RequirementNumberCard(
    number: String,
    title: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HabitaSurface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Number Circle Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(HabitaForestGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    style = Typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = Typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = HabitaTextPrimary
                )
                Text(
                    text = description,
                    style = Typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = HabitaTextSecondary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RequisitosValidarScreenPreview() {
    HabitaTheme {
        RequisitosValidarScreen()
    }
}
