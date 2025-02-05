package de.gematik.ti.erp.app.profiles.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.gematik.ti.erp.app.utils.firstCharOfForeNameSurName

@Composable
fun Avatar(
    name: String,
    profileColor: ProfileColor,
    ssoStatusColor: Color?,
    active: Boolean = false
) {
    Box {
        val text = remember(name) { firstCharOfForeNameSurName(name) }
        Box(modifier = Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
            CircleBox(
                36.dp,
                profileColor.backGroundColor,
                border = if (active) BorderStroke(2.dp, profileColor.borderColor) else null
            )
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.body2,
                color = profileColor.textColor,
                textAlign = TextAlign.Center
            )
        }
        if (ssoStatusColor != null) {
            CircleBox(
                size = 16.dp,
                backgroundColor = ssoStatusColor,
                border = BorderStroke(2.dp, MaterialTheme.colors.background),
                modifier = Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp)
            )
        }
    }
}

@Composable
private fun CircleBox(
    size: Dp,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                border?.let { Modifier.border(border, CircleShape) } ?: Modifier
            )
    )
}
