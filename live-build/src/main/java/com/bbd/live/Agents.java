package com.bbd.live;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * The crew, as LangChain4j AI Services. No implementations, the annotations are
 * the program. Each is the same local Qwen model wearing a different hat.
 */
public final class Agents {
    private Agents() {}

    /** Turns one idea into structured landing-page copy. */
    public interface Copywriter {
        @SystemMessage("""
            You are a punchy startup copywriter. Given a product idea, write landing-page copy.
            Keep it short and human. The headline is at most 8 words. The subhead is one sentence.
            Write exactly three features, each with a 1-to-3 word title and a one-sentence body.
            No emojis. No hype words like 'revolutionary'.
            """)
        @UserMessage("Product idea: {{it}}")
        Model.Copy write(String idea);
    }

    /** Picks a single visual vibe word. We map it to a real palette in Java. */
    public interface Designer {
        @SystemMessage("""
            You are a brand designer. Given a product idea, answer with ONE word describing the
            visual vibe, chosen only from: warm, techy, calm, bold, playful, fresh.
            Answer with just the word, nothing else.
            """)
        @UserMessage("Product idea: {{it}}")
        String vibe(String idea);
    }

    /** One honest, useful criticism. */
    public interface Skeptic {
        @SystemMessage("""
            You are a sharp but friendly investor. Given a product idea, reply with ONE sentence:
            the single most important risk or hard question the founder must answer. Be specific
            and a little witty. No preamble.
            """)
        @UserMessage("Product idea: {{it}}")
        String critique(String idea);
    }
}
