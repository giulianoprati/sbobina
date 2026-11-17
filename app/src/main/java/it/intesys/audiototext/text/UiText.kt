package it.intesys.audiototext.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * User-visible text that is not translated yet. ViewModels, exceptions and formatters build these
 * (JVM-testable, no Context), and the UI resolves them against the current locale at display time.
 * [args] may themselves be [UiText].
 */
sealed interface UiText {
    data class Plain(val value: String) : UiText

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val quantity: Int, val args: List<Any> = emptyList()) : UiText

    fun resolve(context: Context): String = when (this) {
        is Plain -> value
        is Res -> context.getString(id, *resolveArgs(context, args))
        is Plural -> context.resources.getQuantityString(id, quantity, *resolveArgs(context, args))
    }
}

/** Shorthand for [UiText.Res] with positional arguments. */
fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

private fun resolveArgs(context: Context, args: List<Any>): Array<Any> =
    Array(args.size) { (args[it] as? UiText)?.resolve(context) ?: args[it] }

/** An error whose message is meant for the user and must follow the app language. */
open class LocalizedException(val uiText: UiText, cause: Throwable? = null) : Exception(cause)
