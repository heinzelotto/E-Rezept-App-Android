package de.gematik.ti.erp.app.utils.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.flowlayout.MainAxisAlignment
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.insets.imePadding
import de.gematik.ti.erp.app.theme.PaddingDefaults
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import java.util.UUID

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    buttons: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(PaddingDefaults.Large),
    backgroundColor: Color = MaterialTheme.colors.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    properties: DialogProperties = DialogProperties()
) {
    val dismissModifier = if (properties.dismissOnClickOutside) {
        Modifier.clickable(
            onClick = onDismissRequest,
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        )
    } else {
        Modifier.pointerInput(Unit) { }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Box(
            Modifier
                .semantics(false) { }
                .fillMaxSize()
                .then(dismissModifier)
                .background(SolidColor(Color.Black), alpha = 0.5f)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = modifier
                    .wrapContentHeight()
                    .fillMaxWidth(0.78f),
                color = backgroundColor,
                contentColor = contentColor,
                shape = shape,
                elevation = 8.dp
            ) {
                Column(Modifier.padding(PaddingDefaults.Large)) {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.h6
                    ) {
                        title?.let {
                            title()
                            SpacerMedium()
                        }
                    }
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.body2
                    ) {
                        text?.let {
                            text()
                            SpacerMedium()
                        }
                    }
                    FlowRow(
                        modifier = Modifier
                            .wrapContentWidth()
                            .align(Alignment.End),
                        mainAxisAlignment = MainAxisAlignment.End,
                        content = buttons
                    )
                }
            }
        }
    }
}

@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    BackHandler {
        if (properties.dismissOnBackPress) {
            onDismissRequest()
        }
    }

    val key = remember { UUID.randomUUID() }
    var stack by LocalDialogHostState.current.stack

    DisposableEffect(Unit) {
        stack = stack + (key to content)
        onDispose {
            stack = stack - key
        }
    }
}

@Composable
fun DialogHost(
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalDialogHostState provides DialogHostState()
        ) {
            content()

            val stack by LocalDialogHostState.current.stack

            var previous by remember { mutableStateOf<Map.Entry<Any, @Composable () -> Unit>?>(null) }

            previous?.let { p ->
                AnimatedVisibility(
                    visible = stack.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    key(p.key) { p.value() }
                }
            }

            LaunchedEffect(Unit) {
                snapshotFlow {
                    stack.entries
                }
                    .filter {
                        it.isNotEmpty()
                    }
                    .collect {
                        previous = it.first()
                    }
            }
        }
    }
}

class DialogHostState {
    val stack = mutableStateOf<Map<Any, @Composable () -> Unit>>(emptyMap())
}

val LocalDialogHostState = compositionLocalOf<DialogHostState> { error("no dialog host state provided") }
