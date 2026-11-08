package dev.kstate.feature.inspector.domain

import dev.kstate.core.model.CommandOutcome

/** What a command's outcome means, in words the person reads. */
fun outcomeText(outcome: CommandOutcome): String =
    when (outcome) {
        CommandOutcome.Ok -> {
            "Done."
        }

        is CommandOutcome.Refused -> {
            outcome.path?.let { "Refused at $it: ${outcome.error}" }
                ?: "Refused: ${outcome.error}"
        }

        CommandOutcome.TimedOut -> {
            "The app didn't answer within 3 seconds."
        }

        CommandOutcome.NotConnected -> {
            "No app is connected."
        }

        CommandOutcome.Cancelled -> {
            "Cancelled."
        }

        CommandOutcome.NotAllowed -> {
            "Your role doesn't allow that. Ask the owner."
        }
    }
