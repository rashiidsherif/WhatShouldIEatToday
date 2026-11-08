package com.example.whatcanicook.ai

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/** An assistant the kitchen can be handed off to. */
enum class AiTarget(
    val label: String,
    val packageName: String,
    private val urlPrefix: String,
) {
    CLAUDE("Ask Claude", "com.anthropic.claude", "https://claude.ai/new?q="),
    CHATGPT("Ask ChatGPT", "com.openai.chatgpt", "https://chatgpt.com/?q="),
    ;

    fun url(prompt: String): Uri =
        Uri.parse(urlPrefix + URLEncoder.encode(prompt, "UTF-8"))
}

/** How the hand-off actually resolved, so the UI can say something useful. */
enum class HandoffResult { OPENED_APP, SHARED, COPIED }

/**
 * Opens an assistant with the prompt already filled in.
 *
 * Three strategies, in order:
 *
 *  1. A deep link with the prompt in the query string, aimed at the installed
 *     app. Cleanest, but URLs have practical length limits.
 *  2. An `ACTION_SEND` share to that app. No length limit, and both assistants
 *     register as text share targets.
 *  3. Copy to the clipboard, so a missing app is still a one-paste job.
 *
 * The prompt is prefilled, never auto-submitted, so it can be edited first.
 *
 * Seeing whether an app is installed needs a `<queries>` entry in the
 * manifest; Android 11+ hides other packages otherwise.
 */
object AiHandoff {

    fun launch(context: Context, target: AiTarget, prompt: String): HandoffResult {
        if (prompt.length <= MealPlanPrompt.MAX_URL_LENGTH &&
            tryDeepLink(context, target, prompt)
        ) {
            return HandoffResult.OPENED_APP
        }
        if (trySend(context, target, prompt)) return HandoffResult.SHARED
        if (tryChooser(context, prompt)) return HandoffResult.SHARED
        copyToClipboard(context, prompt)
        return HandoffResult.COPIED
    }

    private fun tryDeepLink(context: Context, target: AiTarget, prompt: String): Boolean =
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, target.url(prompt)).apply {
                    setPackage(target.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }

    private fun trySend(context: Context, target: AiTarget, prompt: String): Boolean =
        try {
            context.startActivity(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, prompt)
                    setPackage(target.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }

    /** No specific app: let the user pick from whatever can take text. */
    private fun tryChooser(context: Context, prompt: String): Boolean =
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, prompt)
            }
            context.startActivity(
                Intent.createChooser(send, "Send your kitchen to…").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            true
        } catch (e: Exception) {
            false
        }

    fun copyToClipboard(context: Context, prompt: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Kitchen prompt", prompt))
    }
}
