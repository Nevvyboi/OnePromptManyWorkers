package com.bbd.live;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * The taste guard: lever four from the talk, made real.
 *
 * <p>A guardrail that validates what an agent produced and can refuse it. It is
 * deliberately deterministic. No model is asked whether the page looks good,
 * because "does this look good" is exactly the question a model will answer yes
 * to. Instead the rules a designer would actually enforce are written down and
 * checked mechanically, and the ones that can be repaired safely are repaired.
 *
 * <p>Worth saying on stage: this caught things the human eye missed. The first
 * page that got called finished failed five of these checks.
 */
public final class TasteGuard {
    private TasteGuard() {}

    /** One thing the guard refuses to ship, and why. */
    public record Violation(String id, String detail, String why) {}

    /** The verdict for one page. */
    public record Report(String html, boolean passed, List<Violation> violations, int repaired) {
        public String summary() {
            if (passed) return repaired > 0 ? "passed, " + repaired + " repaired" : "passed";
            return violations.size() + " to fix";
        }
    }

    private record Rule(String id, String why, Pattern find, java.util.function.BiFunction<String, String, String> detail,
                        UnaryOperator<String> fix) {}

    private static String visible(String html) {
        return html.replaceAll("(?s)<(script|style)[^>]*>.*?</\\1>", "").replaceAll("<[^>]+>", " ");
    }

    public static Report audit(String html) {
        String h = html;
        List<Violation> found = new ArrayList<>();
        int repaired = 0;

        // 1. the em-dash: the single most reliable tell that a machine wrote the page
        int dashes = count(visible(h), "[—–]") + count(h, "&[mn]dash;");
        if (dashes > 0) {
            h = h.replace("&mdash;", "&#183;").replace("&ndash;", "-")
                 .replace("—", " - ").replace("–", "-");
            if (count(visible(h), "[—–]") + count(h, "&[mn]dash;") == 0) repaired++;
            else found.add(new Violation("em-dash", dashes + " found",
                    "the em-dash is the most reliable tell that a machine wrote the page"));
        }

        // 2. a small uppercase label above every section is the templated rhythm
        int sections = count(h, "<section") + 1;
        int eyebrows = count(h, "class=\"(eyebrow|note)\"");
        int cap = (int) Math.ceil(sections / 3.0);
        if (eyebrows > cap)
            found.add(new Violation("eyebrow-restraint", eyebrows + " labels, cap is " + cap,
                    "a label above every section is the rhythm every generated page has"));

        // 3. three identical feature cards is the default every model reaches for
        if (Pattern.compile("grid-template-columns:\\s*repeat\\(3,\\s*1fr\\)").matcher(h.replaceAll("\\s+", " ")).find())
            found.add(new Violation("three-equal-cards", "features are three equal columns",
                    "three identical cards in a row is the default every model reaches for"));

        // 4. the hero holds one message; a micro-line under the button is clutter
        if (h.contains("class=\"fine\""))
            found.add(new Violation("hero-tagline", "tagline under the hero call to action",
                    "the hero holds one message"));

        // 5. a coloured dot carrying no state is decoration pretending to be information
        int dots = count(h, "\\.(logo|eyebrow) i\\{");
        if (dots > 0)
            found.add(new Violation("decorative-dots", dots + " decorative dots",
                    "a dot that carries no state is decoration pretending to be information"));

        // 6. someone looking at the hero already knows how to scroll
        if (Pattern.compile("(?i)\\bscroll to (explore|discover)\\b|↓\\s*scroll").matcher(visible(h)).find())
            found.add(new Violation("scroll-cue", "scroll cue", "the reader already knows how to scroll"));

        // 7. build numbers and beta badges are devtool fixtures, not page content
        if (Pattern.compile("(?i)\\bv\\d+\\.\\d+\\.\\d+\\b|\\bBUILD \\d{3,}\\b|\\bINVITE[- ]ONLY\\b").matcher(visible(h)).find())
            found.add(new Violation("version-stamp", "version stamp", "build numbers are not landing page content"));

        // 8. a call to action that wraps to two lines at desktop reads as broken
        var m = Pattern.compile("class=\"btn lg\"[^>]*>([^<]{1,120})<").matcher(h);
        String longest = "";
        while (m.find()) if (m.group(1).trim().length() > longest.length()) longest = m.group(1).trim();
        if (longest.length() > 34) {
            String shortened = longest.split(",")[0].trim();
            if (shortened.length() >= 6 && shortened.length() <= 34) {
                h = h.replace(">" + longest + "<", ">" + shortened + "<");
                repaired++;
            } else {
                found.add(new Violation("cta-wrap-risk", "\"" + longest + "\" is " + longest.length() + " chars",
                        "a call to action that wraps to two lines reads as broken"));
            }
        }

        // 9. the words a model reaches for when it has nothing to say
        var filler = Pattern.compile("(?i)\\b(elevate|seamless|unleash|revolutionis|revolutioniz|next[- ]gen|supercharge)\\w*")
                .matcher(visible(h));
        List<String> hits = new ArrayList<>();
        while (filler.find() && hits.size() < 3) hits.add(filler.group());
        if (!hits.isEmpty())
            found.add(new Violation("filler-verbs", String.join(", ", hits),
                    "filler verbs are what a model writes when it has nothing to say"));

        return new Report(h, found.isEmpty(), found, repaired);
    }

    private static int count(String s, String regex) {
        var m = Pattern.compile(regex).matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }
}
