package cl.habita.mobile.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tokens de las dos interfaces web del equipo, sin colores dinámicos del dispositivo.
val Navy = Color(0xFF062441)
val Blue = Color(0xFF096899)
val Teal = Color(0xFF00B9C3)
val Ink = Color(0xFF102B44)
val Muted = Color(0xFF62798B)
val Line = Color(0xFFDCE8ED)
val Pale = Color(0xFFEAF9FA)
val Background = Color(0xFFF3F7F9)
val Amber = Color(0xFF855900)

@Composable fun HabitaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White,
            primaryContainer = Pale, onPrimaryContainer = Navy,
            secondary = Blue, onSecondary = Color.White,
            secondaryContainer = Pale, onSecondaryContainer = Navy,
            tertiary = Teal, background = Background, onBackground = Ink,
            surface = Color.White, onSurface = Ink, surfaceVariant = Pale,
            onSurfaceVariant = Muted, outline = Line, error = Color(0xFF9A3525)),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-.7).sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 30.sp, letterSpacing = (-.4).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
            labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 14.sp),
        ), content = content,
    )
}
