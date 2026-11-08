package com.example.whatcanicook.ai

import com.example.whatcanicook.domain.FoodCategory
import com.example.whatcanicook.domain.KitchenSnapshot

/**
 * Builds the prompt handed to ChatGPT or Claude.
 *
 * This is the app's "skill": the user shouldn't have to retype the framing
 * every time, and the answer should be about **today** rather than a week of
 * batch cooking.
 *
 * Items are listed comma-separated per category rather than as bullets, which
 * keeps the text short enough to survive being passed in a URL.
 */
object MealPlanPrompt {

    /** Above this, a URL becomes unreliable and we share the text instead. */
    const val MAX_URL_LENGTH = 1200

    fun build(snapshot: KitchenSnapshot): String {
        val groups = snapshot.byCategory
            .filterNot { (category, _) -> category == FoodCategory.NON_FOOD }

        if (groups.isEmpty()) {
            return "My kitchen is empty. Suggest a short healthy shopping list " +
                "for one day of good eating."
        }

        val inventory = groups.joinToString("\n") { (category, items) ->
            // Collapse duplicates: two identical tins read better as "x2".
            val counted = items.groupingBy { it.name.trim() }.eachCount()
            val rendered = counted.entries.joinToString(", ") { (name, count) ->
                if (count > 1) "$name x$count" else name
            }
            "${category.label}: $rendered"
        }

        return buildString {
            append("I'm trying to eat really healthily. ")
            append("Here is everything in my kitchen right now.\n\n")
            append(inventory)
            append("\n\n")
            append("Using only what's listed, tell me what to eat TODAY — ")
            append("breakfast, lunch and dinner. Just today, not a week plan ")
            append("and no batch cooking. Keep it simple and quick to make, ")
            append("say roughly how long each one takes, and tell me if ")
            append("something is missing that I'd really need.")
        }
    }
}
