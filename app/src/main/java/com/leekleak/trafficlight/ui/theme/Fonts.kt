package com.leekleak.trafficlight.ui.theme

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.leekleak.trafficlight.R

@Composable
fun vazirFont(): FontFamily {
    return FontFamily(
        Font(
            R.font.vazir
        ),
    )
}

@Composable
fun carrierFont(): FontFamily = FontFamily(
    Font(
        R.font.vazir
    ),
)

@Composable
fun doHyeonFont(): FontFamily {
    return FontFamily(
        Font(
            R.font.vazir
        ),
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun robotoFlex(
    @FloatRange(-10.0, 0.0) slant: Float,
    @FloatRange(25.0, 151.0) width: Float,
    @FloatRange(100.0, 1000.0) weight: Float
): FontFamily {
    return FontFamily(
        Font(
            R.font.vazir
        ),
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun jetbrainsMono(
    @FloatRange(100.0, 800.0) weight: Float = 400f
): FontFamily {
    return FontFamily(
        Font(
            R.font.vazir
        ),
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun outfit(
    @FloatRange(100.0, 900.0) weight: Float = 500f
): FontFamily {
    return FontFamily(
        Font(
            R.font.vazir
        ),
    )
}