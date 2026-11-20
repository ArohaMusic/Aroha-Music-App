package com.arohamusic.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** Material controls retain their semantics and ripple while sharing Aroha's touch physics. */
@Composable
internal fun MotionButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Button(onClick = onClick, modifier = modifier.pressFeedback(source, .96f, enabled), enabled = enabled,
        shape = shape, colors = colors, elevation = elevation, border = border,
        contentPadding = contentPadding, interactionSource = source, content = content)
}

@Composable
internal fun MotionFilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    FilledTonalButton(onClick = onClick, modifier = modifier.pressFeedback(source, .96f, enabled), enabled = enabled,
        shape = shape, colors = colors, elevation = elevation, border = border,
        contentPadding = contentPadding, interactionSource = source, content = content)
}

@Composable
internal fun MotionOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    OutlinedButton(onClick = onClick, modifier = modifier.pressFeedback(source, .96f, enabled), enabled = enabled,
        shape = shape, colors = colors, elevation = elevation, border = border,
        contentPadding = contentPadding, interactionSource = source, content = content)
}

@Composable
internal fun MotionTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    TextButton(onClick = onClick, modifier = modifier.pressFeedback(source, .96f, enabled), enabled = enabled,
        shape = shape, colors = colors, elevation = elevation, border = border,
        contentPadding = contentPadding, interactionSource = source, content = content)
}

@Composable
internal fun MotionIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    IconButton(onClick = onClick, modifier = modifier.pressFeedback(source, .9f, enabled), enabled = enabled,
        colors = colors, interactionSource = source, content = content)
}

@Composable
internal fun MotionFilledIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    FilledIconButton(onClick = onClick, modifier = modifier.pressFeedback(source, .9f, enabled), enabled = enabled,
        colors = colors, interactionSource = source, content = content)
}

@Composable
internal fun MotionFilledTonalIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    FilledTonalIconButton(onClick = onClick, modifier = modifier.pressFeedback(source, .9f, enabled), enabled = enabled,
        colors = colors, interactionSource = source, content = content)
}

@Composable
internal fun MotionOutlinedIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.outlinedIconButtonColors(),
    interactionSource: MutableInteractionSource? = null, border: BorderStroke? = IconButtonDefaults.outlinedIconButtonBorder(enabled), content: @Composable () -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    OutlinedIconButton(onClick = onClick, modifier = modifier.pressFeedback(source, .9f, enabled), enabled = enabled,
        colors = colors, interactionSource = source, border = border, content = content)
}
