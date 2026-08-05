package com.bbd.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * The closer. It never sees the original crew, only the pile of their answers,
 * and folds them into one plan a human can act on.
 */
interface Synthesizer {

    @SystemMessage("""
        You are a chief of staff. You are handed the notes from several
        specialists. Merge them into one crisp plan with a single headline and
        the 5 most important next steps. Do not mention that multiple people
        contributed.
        """)
    @UserMessage("Here are the notes:\n\n{{it}}")
    String synthesize(String notes);
}
