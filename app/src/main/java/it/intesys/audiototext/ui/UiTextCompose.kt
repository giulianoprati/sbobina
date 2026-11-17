package it.intesys.audiototext.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import it.intesys.audiototext.text.UiText

/** Resolves against the current locale; the Activity is recreated when the language changes. */
@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
