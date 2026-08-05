package com.bbd.live;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

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

        // The net closes here: the copywriter takes the skeptic's critique and
        // revises its own earlier work. Agent output feeding another agent.
        @SystemMessage("""
            You are a punchy startup copywriter revising your own headline after honest
            feedback. Write ONE sharper headline, at most 8 words, that quietly answers
            the critique. Reply with just the new headline, nothing else. No quotes.
            """)
        @UserMessage("Product idea: {{idea}}\nCurrent headline: {{headline}}\nThe critique: {{critique}}")
        String revise(@V("idea") String idea, @V("headline") String headline, @V("critique") String critique);
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

    /** Invents a short product name for the idea. */
    public interface Namer {
        @SystemMessage("""
            You name products. Given an idea, invent ONE short brandable name: a single
            word, 4 to 12 letters, no spaces, no punctuation, easy to say out loud.
            Reply with just the name, nothing else.
            """)
        @UserMessage("Product idea: {{it}}")
        String name(String idea);
    }

    /** Chooses the style of the hero artwork. The browser draws it as SVG. */
    public interface Illustrator {
        @SystemMessage("""
            You art-direct hero graphics. Given a product idea, choose ONE abstract
            style from exactly this list: blobs, rings, waves, grid, burst.
            Reply with just that single word, nothing else.
            """)
        @UserMessage("Product idea: {{it}}")
        String style(String idea);
    }

    /** Reads the finished page and sends back one concrete improvement. */
    public interface Reviewer {
        @SystemMessage("""
            You are a senior designer reviewing a landing page. The call to action is
            weak. Write ONE stronger call to action: at most 5 words, plain and
            specific, no exclamation marks. Reply with just those words.
            """)
        @UserMessage("Product idea: {{idea}}\nCurrent call to action: {{cta}}")
        String polish(@V("idea") String idea, @V("cta") String cta);
    }

    /** Invents the middle price. We build the three tiers around it in Java. */
    public interface Pricer {
        @SystemMessage("""
            You price consumer software for a South African audience. Given a product idea,
            reply with ONE monthly price for the middle tier, in rands, in the form R49 or
            R120. Nothing else, no words, no range.
            """)
        @UserMessage("Product idea: {{it}}")
        String midPrice(String idea);
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
