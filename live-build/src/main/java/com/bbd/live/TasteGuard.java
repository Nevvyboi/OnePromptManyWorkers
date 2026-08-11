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

    /** Applies a repair to everything except stylesheets and scripts.
     *
     *  <p>Not hypothetical: the emoji repair collapsed whitespace before
     *  punctuation across the whole document, turning the selector
     *  ".hero .wrap" into ".hero.wrap" and the grid value "1.02fr .98fr" into
     *  "1.02fr.98fr". Every descendant rule in the hero stopped applying and the
     *  pages rendered with a broken layout for a while before anyone measured it.
     *  A guard that edits markup has to know which markup it may edit. */
    private static String outsideCode(String html, UnaryOperator<String> fn) {
        var m = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>").matcher(html);
        StringBuilder out = new StringBuilder();
        int at = 0;
        while (m.find()) {
            out.append(fn.apply(html.substring(at, m.start()))).append(m.group());
            at = m.end();
        }
        return out.append(fn.apply(html.substring(at))).toString();
    }

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
            h = outsideCode(h, t -> t.replace("&mdash;", "&#183;").replace("&ndash;", "-")
                                     .replace("—", " - ").replace("–", "-"));
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
                final String lg = longest, sh = shortened;
                h = outsideCode(h, t -> t.replace(">" + lg + "<", ">" + sh + "<"));
                repaired++;
            } else {
                found.add(new Violation("cta-wrap-risk", "\"" + longest + "\" is " + longest.length() + " chars",
                        "a call to action that wraps to two lines reads as broken"));
            }
        }

        // 10. a local model sprinkles emoji into copy; on a product page it reads as unserious
        var emoji = Pattern.compile("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}]");
        if (emoji.matcher(visible(h)).find()) {
            h = outsideCode(h, t -> emoji.matcher(t).replaceAll("")
                    .replaceAll("[ \\t]{2,}", " ")
                    .replaceAll("([A-Za-z0-9)\\]\"'])\\s+([.,!?])", "$1$2"));
            if (!emoji.matcher(visible(h)).find()) repaired++;
            else found.add(new Violation("emoji", "emoji in visible copy",
                    "emoji in product copy reads as unserious"));
        }

        // 9. the words a model reaches for when it has nothing to say
        var filler = Pattern.compile("(?i)\\b(elevate|seamless|unleash|revolutionis|revolutioniz|next[- ]gen|supercharge|maximiz)\\w*")
                .matcher(visible(h));
        List<String> hits = new ArrayList<>();
        while (filler.find() && hits.size() < 3) hits.add(filler.group());
        if (!hits.isEmpty())
            found.add(new Violation("filler-verbs", String.join(", ", hits),
                    "filler verbs are what a model writes when it has nothing to say"));

        // --- rules that require the presence of quality, not the absence of a tell ---

        // 11. a saturated blue-violet is where every model drifts; the room reads
        //     it as generated before it reads a word
        var purple = new ArrayList<String>();
        var hex = Pattern.compile("#[0-9a-fA-F]{6}\\b").matcher(h);
        while (hex.find() && purple.size() < 4) {
            double[] c = hsl(hex.group());
            if (c[0] >= 236 && c[0] <= 288 && c[1] > 0.42 && c[2] > 0.28 && c[2] < 0.78)
                purple.add(hex.group().toLowerCase());
        }
        if (!purple.isEmpty())
            found.add(new Violation("ai-purple", String.join(", ", purple),
                    "a saturated blue-violet is the colour every model drifts to; it reads as generated on sight"));

        // 12. body text below 4.5:1 is unreadable on a projector, and no model checks it
        var low = new ArrayList<String>();
        for (String[] pair : new String[][]{{"ink", "bg"}, {"muted", "bg"}, {"ink", "surface"}}) {
            String f = cssVar(h, pair[0]), b = cssVar(h, pair[1]);
            if (f == null || b == null) continue;
            double cr = ratio(f, b);
            if (cr < 4.5) low.add(String.format("%s on %s is %.1f:1", pair[0], pair[1], cr));
        }
        if (!low.isEmpty())
            found.add(new Violation("contrast", String.join(", ", low),
                    "body text below 4.5:1 disappears on a projector, and no model checks it"));

        // 13. a page whose hero, buttons and artwork are all one hue has no second voice
        String prim = cssVar(h, "primary"), acc = cssVar(h, "accent");
        if (prim != null && acc != null) {
            double[] P = hsl(prim), A = hsl(acc);
            if (P[1] >= 0.35 && A[1] >= 0.35) {
                double d = Math.abs(P[0] - A[0]), sep = Math.min(d, 360 - d);
                if (sep < 24)
                    found.add(new Violation("one-note", Math.round(sep) + " degrees apart",
                            "primary and accent this close leave the page with no second voice"));
            }
        }

        // --- rules learned from a design review of a page that passed all of the above ---

        // 14. a scale is steps; twenty five sizes inside a few hundredths of a rem is drift
        var sizes = new java.util.LinkedHashSet<String>();
        var fs = Pattern.compile("font-size:\\s*([^;}\"]+)").matcher(h);
        while (fs.find()) sizes.add(fs.group(1).trim());
        if (sizes.size() > 8)
            found.add(new Violation("type-drift", sizes.size() + " distinct font sizes",
                    "a hierarchy is a few deliberate steps; twenty of them is drift dressed as craft"));

        // 15. one radius system means one radius system
        var radii = new java.util.LinkedHashSet<String>();
        var rr = Pattern.compile("border-radius:\\s*([^;}\"]+)").matcher(h);
        while (rr.find()) radii.add(rr.group(1).trim());
        if (radii.size() > 4)
            found.add(new Violation("radius-drift", radii.size() + " distinct corner radii",
                    "mixed corner systems read as several pages stitched together"));

        // 16. a button has to be visible against the ground it sits on.
        //     The earlier contrast rule checked label against fill and never fill
        //     against section, so a primary call to action shipped at 2.3:1.
        String fill = cssVar(h, "primary");
        for (String ground : new String[]{"bg", "surface"}) {
            String g = cssVar(h, ground);
            if (fill == null || g == null) continue;
            double cr = ratio(fill, g);
            if (cr < 3.0) {
                found.add(new Violation("cta-on-ground",
                        String.format("the button fill is %.1f:1 against --%s", cr, ground),
                        "a call to action that does not separate from its own background loses to the page"));
                break;
            }
        }

        // 17. an inverted closing band flips the page's loudest moment in dark mode
        if (Pattern.compile("\\.(close|final|cta)[^{]*\\{[^}]*background:\\s*var\\(--ink\\)").matcher(h).find())
            found.add(new Violation("mode-inversion", "a section is painted with --ink",
                    "in the other colour scheme that band becomes the lightest thing on the page"));

        // 18. headings that end on a lone word are the loudest "nobody looked at this" signal
        if (!h.contains("text-wrap:balance"))
            found.add(new Violation("widows", "no balanced headings",
                    "without text-wrap:balance a heading will end on an orphaned word"));

        return new Report(h, found.isEmpty(), found, repaired);
    }

    // --- colour helpers, so the rules above are arithmetic rather than opinion ---

    private static String cssVar(String html, String name) {
        var m = Pattern.compile("--" + name + ":\\s*(#[0-9a-fA-F]{3,6})").matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private static int[] rgb(String hex) {
        int n = Integer.parseInt(hex.substring(1), 16);
        return new int[]{(n >> 16) & 255, (n >> 8) & 255, n & 255};
    }

    /** Returns hue in degrees, saturation and lightness in 0..1. */
    private static double[] hsl(String hex) {
        int[] v = rgb(hex);
        double r = v[0] / 255.0, g = v[1] / 255.0, b = v[2] / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        double hue = 0;
        if (d != 0) {
            if (max == r) hue = ((g - b) / d + 6) % 6;
            else if (max == g) hue = (b - r) / d + 2;
            else hue = (r - g) / d + 4;
            hue *= 60;
        }
        double lum = (max + min) / 2;
        double sat = d == 0 ? 0 : d / (1 - Math.abs(2 * lum - 1));
        return new double[]{hue, sat, lum};
    }

    /** WCAG relative luminance. */
    private static double rel(String hex) {
        int[] v = rgb(hex);
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double x = v[i] / 255.0;
            c[i] = x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    private static double ratio(String a, String b) {
        double x = rel(a), y = rel(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static int count(String s, String regex) {
        var m = Pattern.compile(regex).matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }
}
