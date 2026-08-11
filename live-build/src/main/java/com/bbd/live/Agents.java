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

    /**
     * The Insight agent: what is actually going on, before anyone writes copy.
     *
     * <p>Nine agents produced a page that said "Finally, a chore rota that
     * actually works for housemates." Fluent, and about nothing. No worker in the
     * crew was ever asked WHY the problem persists, so nothing on the page had a
     * point of view. This one runs first and hands the Copywriter a thesis.
     */
    public interface Insight {
        @SystemMessage("""
            You find the real reason a everyday problem never gets solved.
            Given a product idea, write ONE sentence naming the human tension underneath
            it. Not what the product does. Why the problem survives.
            Be specific and slightly blunt. Good: "Nobody in the house is lazy, everybody
            genuinely thinks they did it last." Bad: "Managing chores can be difficult."
            At most 18 words. No em-dash. Reply with just the sentence.
            """)
        @UserMessage("Product idea: {{it}}")
        String tension(String idea);

        // The Insight agent finishes first and would otherwise idle for the whole
        // build. It comes back at the end to help the Critic: does the final
        // headline actually answer the tension we named?
        @SystemMessage("""
            You check whether a headline answers a known tension. Reply in ONE short
            line starting with YES or NO, then why in a few words.
            "YES, it names the exact fear the tension is about."
            "NO, it describes the product instead of the feeling."
            """)
        @UserMessage("The tension: {{tension}}\nThe headline: {{headline}}")
        String answers(@V("tension") String tension, @V("headline") String headline);
    }

    /**
     * The Researcher: the concrete detail that proves someone knows the domain.
     *
     * <p>Generic copy is not a style problem, it is a knowledge problem. "Share
     * chores fairly" could be written by someone who has never lived in a share
     * house. "Bins go out Tuesday night, and the recycling only every second week"
     * could not. This agent supplies the specifics the Copywriter can lean on.
     */
    public interface Researcher {
        @SystemMessage("""
            You know how ordinary things actually work. Given a product idea, list the
            3 most concrete, checkable details someone who really lives with this
            problem would know.
            Real specifics only: days, amounts, sequences, names of things, the bit
            that always goes wrong. No advice, no benefits, no marketing.
            Good: "Bins go out Tuesday night. Recycling is every second week. The
            green bin is council, the black one is not."
            Bad: "Chore management improves household harmony."
            Reply as 3 short lines separated by semicolons, nothing else.
            """)
        @UserMessage("Product idea: {{it}}")
        String facts(String idea);

        // The Researcher finishes early, so while the checkers read the page it
        // keeps working: the single most useful real number this product could cite.
        @SystemMessage("""
            You find the one concrete, checkable number a product like this could
            honestly put on its page. A median, a typical delay, a common amount.
            Real and specific, phrased in under 12 words. No marketing.
            "Freelancers wait a median 34 days past terms to be paid."
            Reply with just that one line.
            """)
        @UserMessage("Product idea: {{it}}")
        String gap(String idea);
    }

    /** Turns one idea into structured landing-page copy. */
    public interface Copywriter {
        @SystemMessage("""
            You are a punchy startup copywriter. Given a product idea, write landing-page copy.
            Keep it short and human. The headline is at most 8 words. The subhead is one sentence.
            Write exactly three features, each with a 1-to-3 word title and a one-sentence body.
            No emojis. Ban these tired words: revolutionary, seamless, effortless, elevate,
            unlock, empower, game-changing, cutting-edge, transform, supercharge. Be specific
            to THIS idea: a stranger should be able to guess the product from the headline alone.
            """)
        @UserMessage("Product idea: {{it}}")
        Model.Copy write(String idea);

        // A local model settles into a groove: ask it for four landing pages and
        // three of them open with the same four words. The harness detects the
        // repeat and sends the work back with the used openings named.
        @SystemMessage("""
            You are a punchy startup copywriter. Given a product idea, write landing-page copy.
            Keep it short and human. The headline is at most 8 words. The subhead is one sentence.
            Write exactly three features, each with a 1-to-3 word title and a one-sentence body.
            No emojis. Ban these tired words: revolutionary, seamless, effortless, elevate,
            unlock, empower, game-changing, cutting-edge, transform, supercharge. Be specific
            to THIS idea: a stranger should be able to guess the product from the headline alone.
            Your headline must NOT begin with any of the openings listed as already used.
            Find a different angle: a question, a flat statement of fact, or a promise.
            """)
        @UserMessage("Product idea: {{idea}}\nOpenings already used, do not reuse: {{used}}")
        Model.Copy rewrite(@V("idea") String idea, @V("used") String used);

        // A 3b model asked for a nested JSON object puts an object where a string
        // belongs and the whole reply is lost. Asked for five flat lines it lands
        // nearly every time. The schema is a control lever, same as the prompt.
        @SystemMessage("""
            You are a punchy startup copywriter. Given a product idea, write landing-page copy.
            Reply as plain text in EXACTLY five lines, using these labels and nothing else:
            BADGE: two words at most
            HEADLINE: eight words at most
            SUBHEAD: one sentence
            CTA: four words at most
            FEATURES: title | one sentence ;; title | one sentence ;; title | one sentence
            No emojis. Ban these tired words: revolutionary, seamless, effortless, elevate,
            unlock, empower, game-changing, cutting-edge, transform, supercharge. Be specific
            to THIS idea: a stranger should be able to guess the product from the headline alone. No quotes around anything.
            """)
        @UserMessage("Product idea: {{it}}")
        String writeFlat(String idea);

        // Same flat schema, but handed the Insight agent's thesis. Copy written
        // against a stated tension argues something; copy written against a
        // feature list describes something.
        @SystemMessage("""
            You are a punchy startup copywriter. You are given a product idea, the real
            tension underneath it, and some concrete details that are actually true of
            this domain. Write landing-page copy that answers that tension directly and
            uses at least one of the concrete details. Specifics are what make copy
            sound like a person wrote it. The headline should feel like it was written by someone
            who has lived the problem, not summarised it.
            Reply as plain text in EXACTLY five lines, using these labels and nothing else:
            BADGE: two words at most
            HEADLINE: eight words at most
            SUBHEAD: one sentence
            CTA: a verb and the thing, two or three words
            FEATURES: title | one sentence ;; title | one sentence ;; title | one sentence
            Each feature title is 2 to 4 words and says what happens, not what it is
            called. "Nobody gets skipped", not "Rotation". "Told the night before", not
            "Notifications".
            No emojis. Ban these tired words: revolutionary, seamless, effortless, elevate,
            unlock, empower, game-changing, cutting-edge, transform, supercharge. Be specific
            to THIS idea: a stranger should be able to guess the product from the headline alone. No quotes around anything.
            """)
        @UserMessage("Product idea: {{idea}}\nThe real tension: {{tension}}\nThings that are actually true here: {{facts}}")
        String writeWithInsight(@V("idea") String idea, @V("tension") String tension, @V("facts") String facts);

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

    /**
     * The Critic: the only agent that sees the finished page rather than a field.
     *
     * <p>Every other agent works on its own slice, so nothing in the crew ever
     * reads the assembled result. That is how three pages shipped with copy that
     * spliced a raw noun phrase into a template. The Critic reads the whole thing
     * and sends exactly one line back, which is the smallest useful unit of
     * criticism: "find everything wrong" gets you a list nobody acts on.
     */
    public interface Critic {
        @SystemMessage("""
            You are a hard to please editor reviewing finished landing page copy.
            Find the ONE weakest line and rewrite it. Prefer lines that are vague,
            that repeat a noun awkwardly, or that could describe any product at all.
            A feature titled with a single abstract noun is always a candidate:
            "Rotation", "Scheduling", "Notifications" name a category instead of making
            a promise. Rewrite those as "Nobody gets skipped" or "Told the night before".
            When you rewrite a feature, reply with the new title, then a colon, then the
            sentence.
            Reply in EXACTLY two lines and nothing else:
            FIELD: one of headline, subhead, cta, feature1, feature2, feature3
            REPLACEMENT: the rewritten line
            The replacement must be plain, specific and no longer than the original.
            Never use an em-dash, an emoji, or the words elevate, seamless, unleash,
            supercharge or next-gen. No quotes around the replacement.
            """)
        @UserMessage("""
            Product: {{name}}
            Idea: {{idea}}

            headline: {{headline}}
            subhead: {{subhead}}
            cta: {{cta}}
            feature1: {{f1}}
            feature2: {{f2}}
            feature3: {{f3}}

            Already rewritten, do not choose these again: {{done}}
            """)
        String review(@V("name") String name, @V("idea") String idea, @V("headline") String headline,
                      @V("subhead") String subhead, @V("cta") String cta,
                      @V("f1") String f1, @V("f2") String f2, @V("f3") String f3,
                      @V("done") String done);
    }

    /**
     * The Strategist: decides the ONE thing the page should lead with.
     *
     * <p>The crew's default is three equal features, which is exactly the shape
     * that reads as generated. A page has a hook, not a list. Given the features,
     * the Strategist names the strongest one so the builder can lead with it.
     */
    public interface Strategist {
        @SystemMessage("""
            You decide what a landing page should lead with. Given a product and its
            three features, reply with ONLY the number, 1, 2 or 3, of the single
            feature that is the real reason someone would use this. Not the most
            generic one, the most specific and convincing one. Reply with just the digit.
            """)
        @UserMessage("Product: {{idea}}\n1: {{f1}}\n2: {{f2}}\n3: {{f3}}")
        String lead(@V("idea") String idea, @V("f1") String f1, @V("f2") String f2, @V("f3") String f3);

        @SystemMessage("""
            You decide whether a page should sell a paid product or just present an idea.
            Many ideas are free tools, personal projects, community things, hobby ideas or
            public services that look ridiculous with pricing tiers stapled on. Only a real
            commercial product that a normal person would open their wallet for should show
            pricing. Given the idea, reply with ONE word: "paid" if someone would genuinely
            expect to pay money to use this, or "free" if not. Just the one word.
            """)
        @UserMessage("Idea: {{idea}}")
        String priced(@V("idea") String idea);

        @SystemMessage("""
            You are a ruthless headline editor. You are given a product idea and three
            candidate headlines. Pick the ONE that is sharpest: specific over generic,
            confident over hedged, human over corporate. Reject anything with tired words
            like seamless, effortless, elevate, unlock, revolutionise, or empower. Reply
            with ONLY the digit 1, 2 or 3 of the best one. Just the digit.
            """)
        @UserMessage("Idea: {{idea}}\n1: {{a}}\n2: {{b}}\n3: {{c}}")
        String sharpest(@V("idea") String idea, @V("a") String a, @V("b") String b, @V("c") String c);

        @SystemMessage("""
            You pick product names. Given an idea and three candidate names, choose the ONE
            that is most memorable and brandable: easy to say out loud, not a generic word
            with a suffix bolted on, and evocative of the moment the product matters. Reply
            with ONLY the digit 1, 2 or 3 of the best one. Just the digit.
            """)
        @UserMessage("Idea: {{idea}}\n1: {{a}}\n2: {{b}}\n3: {{c}}")
        String bestName(@V("idea") String idea, @V("a") String a, @V("b") String b, @V("c") String c);
    }

    /**
     * The Proofreader: the last human-feeling pass before the page ships.
     *
     * <p>A local model produces the occasional broken fragment: "Who s done what",
     * a feature titled "Sentence", a subhead missing a word. The other agents work
     * on their own slice and never read it as prose. This one does.
     */
    public interface Proofreader {
        @SystemMessage("""
            You are a proofreader. You are given one line of finished website copy.
            If it reads cleanly, reply with it exactly as is. If it has a typo, a
            missing word, a broken fragment, or awkward grammar, reply with the fixed
            line and nothing else. Never add hype, never change the meaning, never add
            quotes. Keep it the same length or shorter.
            """)
        @UserMessage("Line: {{line}}")
        String fix(@V("line") String line);
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

        // The one genuinely visual judgement the crew makes. Palette and artwork
        // can both be recovered from keywords; "is this a sentence people need to
        // read, or a thing people need to see" cannot. So the Designer owns it.
        @SystemMessage("""
            You lay out landing pages. Given a product idea, choose how the top of
            the page should be built, from exactly these three:
            editorial - the promise is the whole pitch and the words carry it. The
              headline fills the screen and the product sits below.
            split - the product is easy to picture and belongs beside the words.
            band - the product is the hook and should be seen before it is read.
            Answer with just that single word, nothing else.
            """)
        @UserMessage("Product idea: {{it}}")
        String layout(String idea);

        // The Designer finishes early and comes back to help the Illustrator: given
        // the interface it is drawing, the Designer says which colour should lead.
        @SystemMessage("""
            You are guiding an illustration. Given a product and the kind of interface
            being drawn, answer with ONE word, either "primary" or "accent", for which
            colour should dominate the picture. A calm interface leads with primary; a
            lively one leads with accent. Answer with just the word.
            """)
        @UserMessage("Product idea: {{idea}}\nInterface: {{kind}}")
        String emphasis(@V("idea") String idea, @V("kind") String kind);
    }

    /** Invents a short product name for the idea. */
    public interface Namer {
        @SystemMessage("""
            You name products. Given an idea, give ONE name of 4 to 14 letters, easy to
            say out loud, one or two words.
            Do NOT bolt a suffix onto a word from the idea. Never end the name with
            ly, ify, io, ery, Hub, Kit, Pro, App, Sync, Wise or Flow. A name built that
            way tells the reader nothing.
            Name it after the moment the product matters, the object it replaces, or
            the day it happens. A bin rota is called Wednesday. A braai timer is called
            Coalfall. A lift club is called Backseat.
            Reply with just the name, nothing else.
            """)
        @UserMessage("Product idea: {{it}}")
        String name(String idea);

        // The Namer finishes first, so it comes back to do real work: given the
        // finished name and headline, it writes the short tagline the share card
        // and the browser tab use. This is help that takes time, not an instant handoff.
        @SystemMessage("""
            You write the one-line tagline that sits under a product's name. Given the
            name and the headline, write 3 to 6 words that a person would remember.
            Not a sentence, not the headline again. "Chores, minus the argument."
            Reply with just the tagline.
            """)
        @UserMessage("Name: {{name}}\nHeadline: {{headline}}")
        String tagline(@V("name") String name, @V("headline") String headline);

        // Asked once more, with the rejected name named. A local model will hand back
        // the same suffix pattern unless it is told which one was refused.
        @SystemMessage("""
            You name products. Your previous name was refused for being a word from the
            idea with a suffix bolted on. Give ONE different name, 4 to 14 letters, one
            or two words.
            Never end the name with ly, ify, io, ery, Hub, Kit, Pro, App, Sync, Wise or
            Flow, and do not simply reuse a noun from the idea.
            Name it after the moment it matters, the object it replaces, or the day it
            happens.
            Reply with just the name, nothing else.
            """)
        @UserMessage("Product idea: {{idea}}\nRefused: {{refused}}")
        String rename(@V("idea") String idea, @V("refused") String refused);
    }

    /** Chooses the style of the hero artwork. The browser draws it as SVG. */
    public interface Illustrator {
        @SystemMessage("""
            You decide what the product screenshot should show. Given a product idea,
            choose the ONE interface that best represents it, from exactly this list:
            calendar, timer, ledger, chart, checklist, route, inbox, catalog, dashboard.
            calendar for anything booked or reserved. timer for anything counted down.
            ledger for money. chart for anything measured over time. checklist for
            tasks and rotas. route for anything travelled. inbox for messages.
            catalog for things browsed or bought. dashboard when nothing else fits.
            Reply with just that single word, nothing else.
            """)
        @UserMessage("Product idea: {{it}}")
        String style(String idea);

        /**
         * What is actually inside the screenshot.
         *
         * <p>The mockup drew four grey placeholder bars for every idea, so a chore
         * rota and a revision planner produced the same picture. A real landing page
         * shows the product with the user's own content in it.
         */
        @SystemMessage("""
            You write the sample content shown inside a product screenshot.
            Given a product idea, list exactly 4 short rows that would really appear on
            that screen. 1 to 4 words each, separated by a semicolon.
            For a chore rota: Bins out, Tuesday; Kitchen deep clean; Bathroom; Recycling
            For a stokvel: Thandi paid R250; Sipho paid R250; Monthly payout; Ayanda paid R500
            Write the content, never the feature name. Not "Reminders", not "Scheduling".
            Reply with just the 4 rows separated by semicolons.
            """)
        @UserMessage("Product idea: {{idea}}\nThe screen shows a {{kind}}")
        String rows(@V("idea") String idea, @V("kind") String kind);

        /**
         * The photograph the page should carry.
         *
         * <p>A landing page made only of drawn shapes reads as a diagram. One real
         * photograph of the world the product lives in does more for credibility
         * than any amount of vector polish.
         */
        @SystemMessage("""
            You choose the one photograph a landing page should carry.
            Given a product idea, describe the scene in 2 to 4 plain words: a real
            place or object someone could photograph, never an abstract concept.
            Good: "wheelie bins street", "padel court evening", "coffee beans roasting"
            Bad: "teamwork", "productivity", "harmony", "innovation"
            Reply with just those words.
            """)
        @UserMessage("Product idea: {{it}}")
        String scene(String idea);
    }

    /** Reads the finished page and sends back one concrete improvement. */
    public interface Reviewer {
        @SystemMessage("""
            You are a senior designer reviewing a landing page. The call to action is
            weak. Write ONE stronger call to action.
            It must be a verb followed by the thing itself, 2 or 3 words, no more.
            "Start a rota". "Book a court". "Set the timer". "Split the bill".
            Never write a mood: not "Simplify chores together now", not "Get started
            today", not "Join the movement". No exclamation marks.
            Reply with just those words.
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

    /**
     * Decides the shape of the page. The old harness shipped one fixed template for
     * every idea, so a food truck and a payroll tool came out structurally identical.
     * This picks the sections the idea actually needs.
     */
    public interface Architect {
        @SystemMessage("""
            You decide what sections a landing page needs. Choose ONLY from this list:
              how     - three concrete steps showing how it works
              catalog - a list of real named items (a menu, services, packages, products)
              story   - a short origin story about the people behind it
              proof   - short quotes from the kind of people who would use it
            Pick the 2 or 3 that this idea genuinely needs. A restaurant or shop needs
            catalog. A tool or app needs how. A craft or family business needs story.
            Something people judge socially needs proof. Do not pick all four.
            Reply as exactly two lines and nothing else:
            SECTIONS: comma separated names from the list
            HERO: one of photo, mockup, editorial
            Choose HERO photo when the thing is physical, visual or a place; mockup when
            it is software with a screen; editorial when the idea is a statement.
            """)
        @UserMessage("Product idea: {{it}}")
        String plan(String idea);
    }

    /** Writes the content for the sections the Architect chose. */
    public interface Sections {
        @SystemMessage("""
            You write the short origin story behind a product, in two or three sentences.
            Invent believable specifics: a person, a reason they started, what they
            refused to compromise on. Warm and plain, never corporate. No hype words.
            Reply with just the story.
            """)
        @UserMessage("Product: {{name}}\nIdea: {{idea}}")
        String story(@V("name") String name, @V("idea") String idea);

        @SystemMessage("""
            You explain how something works in exactly three steps. Each step is a 1-to-3
            word title and one short sentence. Be concrete and specific to this idea.
            Reply as one line, nothing else:
            title | sentence ;; title | sentence ;; title | sentence
            """)
        @UserMessage("Product: {{name}}\nIdea: {{idea}}")
        String steps(@V("name") String name, @V("idea") String idea);

        @SystemMessage("""
            You write the real list of things a business offers: menu dishes, services,
            packages or products. Give FIVE items. Each has a short specific name and a
            one sentence description that makes it feel real and appetising or useful.
            Never generic: name the ingredients, the materials, the actual job.
            Reply as one line, nothing else:
            name | description ;; name | description ;; name | description ;; name | description ;; name | description
            """)
        @UserMessage("Product: {{name}}\nIdea: {{idea}}")
        String catalog(@V("name") String name, @V("idea") String idea);

        @SystemMessage("""
            You write two short quotes from the kind of people who would really use this.
            Each is a first name and a one sentence quote in their own plain voice, saying
            something specific they noticed. Never marketing speak, no exclamation marks.
            Reply as one line, nothing else:
            name | quote ;; name | quote
            """)
        @UserMessage("Product: {{name}}\nIdea: {{idea}}")
        String proof(@V("name") String name, @V("idea") String idea);
    }

    /**
     * The page used to ship a hardcoded FAQ that asked "does it really work for
     * software?" and promised no cloud account on a product whose own feature was a
     * phone app. Real questions, written for this idea, end that contradiction.
     */
    public interface Faq {
        @SystemMessage("""
            You write the three questions a careful person would really ask before using
            this, and their honest answers. Ask about the actual doubts this idea raises,
            not generic ones. Never claim anything about pricing, data storage or the
            cloud unless the idea itself implies it. Each answer is one or two sentences.
            Reply as one line, nothing else:
            question | answer ;; question | answer ;; question | answer
            """)
        @UserMessage("Product: {{name}}\nIdea: {{idea}}")
        String write(@V("name") String name, @V("idea") String idea);

        @SystemMessage("""
            You write the one line description under each of three pricing tiers, for a
            small, a middle and a large customer of THIS product. Say who each tier is
            for in the product's own terms. Never mention blocks, studios or things.
            Reply as one line, nothing else:
            small | middle | large
            """)
        @UserMessage("Product: {{name}}\nIdea: {{idea}}")
        String tiers(@V("name") String name, @V("idea") String idea);
    }
}
