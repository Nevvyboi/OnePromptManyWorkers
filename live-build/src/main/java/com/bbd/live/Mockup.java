package com.bbd.live;

import com.bbd.live.Model.Art;
import com.bbd.live.Model.Palette;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Product mockups.
 *
 * <p>The illustrator used to draw abstract patterns: rings, waves, a field of
 * dots. Every page got decoration, and decoration is its own kind of slop. A
 * real landing page shows the product. There is no image model here and nothing
 * may leave the laptop, so the product is drawn as SVG: a small, honest
 * interface for whatever the idea actually is.
 *
 * <p>Nine archetypes cover essentially any idea a room will type in. The
 * Illustrator agent proposes one; this class decides.
 */
public final class Mockup {
    private Mockup() {}

    public static final List<String> ARCHETYPES = List.of(
            "calendar", "timer", "ledger", "chart", "checklist", "route", "inbox", "catalog", "dashboard");

    /**
     * Each archetype's vocabulary. An idea is scored against all of them and the
     * strongest signal wins, because a first-match rule breaks the moment two
     * signals appear: "a braai timer that syncs with the load-shedding schedule"
     * matched "schedule" and drew a calendar for something that is plainly a timer.
     */
    private static final Map<String, Pattern> VOCAB = Map.of(
        "calendar",  p("book|booking|booked|reserve|reservation|court|courts|venue|venues|slot|slots|appointment|appointments|calendar|shift|shifts|availability|diary|table|schedule|scheduling"),
        "timer",     p("timer|countdown|alarm|stopwatch|remind|reminder|remember|forget|forgets|minutes|braai|barbecue|cook|cooking|bake|baking|brew|brewing|oven|boil|water|feed|interval|daily"),
        "ledger",    p("money|payment|payments|pay|paid|save|saving|savings|budget|stokvel|invoice|expense|expenses|split|splitting|bill|bills|cost|costs|wallet|fee|fees|subscription|rand|debt|contribution|owe"),
        "chart",     p("monitor|monitoring|sensor|temperature|pressure|level|levels|usage|using|meter|geyser|alert|alerts|threshold|leak|voltage|electricity|power|kwh|watt|consumption|reading|readings|detect|warns?|disk|disks|storage|capacity|space|full|filling|quota|bandwidth|cpu|memory|server|servers|battery|fuel|stock level|spike|trend|fridge"),
        "checklist", p("todo|checklist|task|tasks|chore|chores|rota|packing|grocery|groceries|steps|tick|done|complete|habit|routine|revision|assign|turn|whose|list|lists|reading|read"),
        "route",     p("route|routes|lift|lifts|commute|travel|trip|delivery|deliver|drive|driver|driving|map|maps|pickup|dropoff|taxi|traffic|journey|distance|walk|cycle"),
        "inbox",     p("chat|message|messages|inbox|mail|email|group|community|forum|thread|notify|notification|social|neighbour|neighbor|announce|reply|dm|post|posts"),
        "catalog",   p("shop|store|order|orders|menu|buy|cart|product|products|catalogue|catalog|coffee|beans|food|meal|meals|drink|drinks|stock|inventory|browse|ship|ships|shipped"),
        "dashboard", p("track|tracker|tracking|dashboard|report|reports|analytic|analytics|metric|metrics|stat|stats|progress|score|scores|overview|insight|insights|leaderboard"));

    /** A few words settle it on their own, and outweigh a pile of vague matches. */
    private static final Map<String, Pattern> STRONG = Map.of(
        "timer",     p("timer|countdown|stopwatch|alarm|remind|remember"),
        "calendar",  p("book|booking|reserve|reservation|appointments?|court"),
        "ledger",    p("stokvels?|invoices?|budgets?|savings|payments?|expenses?"),
        "chart",     p("monitors?|sensors?|thresholds?|leaks?|electricity|usage"),
        "route",     p("routes?|commutes?|deliver(y|ies)|taxis?|lift club"),
        "inbox",     p("chat|inbox|message"),
        "catalog",   p("shop|store|menu|cart|subscription"),
        "checklist", p("checklists?|todos?|chores?|rotas?"),
        "dashboard", p("dashboard|leaderboard|analytics"));

    private static Pattern p(String body) {
        return Pattern.compile("(?i)\\b(" + body + ")\\b");
    }

    /**
     * Picks the archetype for an idea. The model may propose; this decides.
     *
     * <p>The model's word is only taken when the words in the idea are not
     * decisive. Given "a stokvel tracker for the whole street", Qwen answered
     * "dashboard" and the page showed a stat panel for something that is plainly
     * a ledger. When the vocabulary is clear, the vocabulary wins.
     */
    /**
     * Phrases where a keyword means something else entirely: "my book club" is
     * not a booking and "a reading list" is not a diary.
     *
     * <p>They are rewritten rather than deleted. Blanking them threw the signal
     * away too, and "a reading list I can share with my book club" scored nothing
     * at all once both phrases were gone.
     */
    private static final List<String[]> DECOYS = List.of(
            new String[]{"(?i)\\breading list\\b", "checklist"},
            new String[]{"(?i)\\bbook (club|shop|store)\\b", "club"},
            new String[]{"(?i)\\bbookshop\\b|(?i)\\bbookstore\\b", "shop"},
            new String[]{"(?i)\\btable tennis\\b", "tennis"});

    private static String undecoy(String t) {
        for (String[] d : DECOYS) t = t.replaceAll(d[0], d[1]);
        return t;
    }

    public static String archetypeFor(String idea, String proposed) {
        String prop = proposed == null ? "" : proposed.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        idea = idea == null ? "" : undecoy(idea);
        Scored best = score(idea);
        if (best.confident) return best.kind;
        if (ARCHETYPES.contains(prop)) return prop;
        return best.kind;
    }

    private record Scored(String kind, double top, boolean confident) {}

    private static Scored score(String idea) {
        String t = idea == null ? "" : idea;
        String best = "dashboard";
        double top = 0;
        for (String kind : ARCHETYPES) {
            var m = VOCAB.get(kind).matcher(t);
            int hits = 0;
            while (m.find()) hits++;
            if (hits == 0) continue;
            // a decisive word is worth more than a couple of vague ones
            double sc = hits + (STRONG.containsKey(kind) && STRONG.get(kind).matcher(t).find() ? 2.5 : 0);
            if (sc > top) { top = sc; best = kind; }
        }
        // a decisive word, or several supporting ones, beats whatever the model said
        return new Scored(best, top, top >= 3.0);
    }

    // --- small drawing helpers, so each archetype reads as one idea ---

    private static String rect(double x, double y, double w, double h, double r, String fill, String extra) {
        return "<rect x=\"" + f(x) + "\" y=\"" + f(y) + "\" width=\"" + f(Math.max(0, w))
             + "\" height=\"" + f(Math.max(0, h)) + "\" rx=\"" + f(r) + "\" fill=\"" + fill + "\"" + extra + "/>";
    }
    private static String rect(double x, double y, double w, double h, double r, String fill) {
        return rect(x, y, w, h, r, fill, "");
    }
    private static String line(double x1, double y1, double x2, double y2, String stroke, double w, String extra) {
        return "<line x1=\"" + f(x1) + "\" y1=\"" + f(y1) + "\" x2=\"" + f(x2) + "\" y2=\"" + f(y2)
             + "\" stroke=\"" + stroke + "\" stroke-width=\"" + f(w) + "\"" + extra + "/>";
    }
    private static String circle(double cx, double cy, double r, String fill, String extra) {
        return "<circle cx=\"" + f(cx) + "\" cy=\"" + f(cy) + "\" r=\"" + f(r) + "\" fill=\"" + fill + "\"" + extra + "/>";
    }
    private static String text(double x, double y, String s, String fill, double size, int weight, String anchor) {
        return "<text x=\"" + f(x) + "\" y=\"" + f(y) + "\" fill=\"" + fill + "\" font-size=\"" + f(size)
             + "\" font-weight=\"" + weight + "\" text-anchor=\"" + anchor
             + "\" font-family=\"ui-sans-serif,system-ui,-apple-system,Segoe UI,Roboto,sans-serif\">"
             + s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</text>";
    }
    /** A placeholder text run. Mockup convention, and it never renders as lorem. */
    private static String bar(double x, double y, double w, String c, double h) {
        return rect(x, y, w, h, h / 2, c, " opacity=\".28\"");
    }
    private static String f(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v))
                : String.format(Locale.ROOT, "%.2f", v);
    }

    /**
     * Rows the mockup can actually name.
     *
     * <p>Every generated page drew the same four grey placeholder bars, so a chore
     * rota and a revision planner looked identical. A landing page shows the
     * product; a product has content in it. These come from the idea itself.
     */
    public record Rows(String title, String lead, String sub, List<String> items) {
        public String item(int i) { return i < items.size() ? items.get(i) : ""; }
        public boolean any() { return !items.isEmpty(); }
    }

    private static final Rows EMPTY = new Rows("", "", "", List.of());

    /** The finished SVG, sized to whatever box the layout gives it. */
    public static String svg(Art art, Palette pal, String layout) {
        return svg(art, pal, layout, EMPTY);
    }

    public static String svg(Art art, Palette pal, String layout, Rows rows) {
        String kind = art == null || art.kind() == null ? "dashboard" : art.kind();
        if (!ARCHETYPES.contains(kind)) kind = "dashboard";
        int seed = art == null ? 7 : art.seed();
        boolean wide = "band".equals(layout) || "editorial".equals(layout);
        double W = wide ? 900 : 640, H = wide ? 340 : 480;
        return "<svg class=\"field\" viewBox=\"0 0 " + f(W) + " " + f(H)
             + "\" preserveAspectRatio=\"xMidYMid meet\" role=\"img\" aria-label=\"Product interface preview\">"
             + draw(kind, pal, seed, W, H, rows == null ? EMPTY : rows) + "</svg>";
    }

    private static String draw(String kind, Palette pal, int seed, double W, double H, Rows data) {
        String ink = pal.ink(), muted = pal.muted(), prim = pal.primary(), acc = pal.accent(), surf = pal.surface();
        double pad = Math.round(Math.min(W, H) * 0.075);
        double barH = Math.max(26, H * 0.1);

        StringBuilder s = new StringBuilder();
        // the window the interface sits in
        s.append(rect(0, 0, W, H, 14, surf))
         .append(rect(0, 0, W, barH, 14, ink, " opacity=\".05\""))
         .append(line(0, barH, W, barH, ink, 1, " opacity=\".1\""))
         .append(data.title().isBlank()
                 ? rect(pad, barH / 2 - 4, Math.min(120, W * 0.28), 8, 4, ink, " opacity=\".18\"")
                 : text(pad, barH / 2 + 4, clipTo(data.title(), 26), ink, Math.min(13, barH * 0.42), 600, "start"))
         .append(rect(W - pad - 34, barH / 2 - 5, 34, 10, 5, prim, " opacity=\".55\""));

        double x0 = pad, y0 = barH + pad, w = W - pad * 2, h = H - barH - pad * 2;

        switch (kind) {
            case "calendar" -> {
                int cols = 7, rows = 4;
                double cw = w / cols, chh = (h - 18) / rows;
                String[] days = {"M", "T", "W", "T", "F", "S", "S"};
                for (int c = 0; c < cols; c++)
                    s.append(text(x0 + c * cw + cw / 2, y0 + 9, days[c], muted, Math.max(8, cw * 0.3), 600, "middle"));
                var taken = java.util.Set.of(3, 9, 16, 22, 23);
                int mine = 10;
                for (int i = 0; i < cols * rows; i++) {
                    double cx = x0 + (i % cols) * cw, cy = y0 + 18 + (i / cols) * chh;
                    double bw = cw - 4, bh = chh - 4;
                    if (i == mine)
                        s.append(rect(cx, cy, bw, bh, 5, prim))
                         .append(text(cx + bw / 2, cy + bh / 2 + 3, "✓", surf, bh * 0.5, 700, "middle"));
                    else if (taken.contains(i)) s.append(rect(cx, cy, bw, bh, 5, acc, " opacity=\".38\""));
                    else s.append(rect(cx, cy, bw, bh, 5, ink, " opacity=\".06\""));
                }
            }
            case "timer" -> {
                double cx = x0 + w / 2, cy = y0 + h * 0.44, R = Math.min(w, h) * 0.31;
                double frac = 0.68, a0 = -Math.PI / 2, a1 = a0 + frac * Math.PI * 2;
                s.append("<circle cx=\"").append(f(cx)).append("\" cy=\"").append(f(cy)).append("\" r=\"").append(f(R))
                 .append("\" fill=\"none\" stroke=\"").append(ink).append("\" stroke-opacity=\".12\" stroke-width=\"")
                 .append(f(R * 0.17)).append("\"/>")
                 .append("<path d=\"M ").append(f(cx + Math.cos(a0) * R)).append(" ").append(f(cy + Math.sin(a0) * R))
                 .append(" A ").append(f(R)).append(" ").append(f(R)).append(" 0 ").append(frac > 0.5 ? 1 : 0).append(" 1 ")
                 .append(f(cx + Math.cos(a1) * R)).append(" ").append(f(cy + Math.sin(a1) * R))
                 .append("\" fill=\"none\" stroke=\"").append(prim).append("\" stroke-width=\"").append(f(R * 0.17))
                 .append("\" stroke-linecap=\"round\"/>")
                 .append(text(cx, cy + R * 0.16, "12:40", ink, R * 0.46, 700, "middle"))
                 .append(text(cx, cy + R * 0.52, "remaining", muted, R * 0.2, 500, "middle"));
                double bw = w * 0.3, by = y0 + h - 22;
                s.append(rect(cx - bw - 5, by, bw, 20, 10, prim))
                 .append(rect(cx + 5, by, bw, 20, 10, ink, " opacity=\".1\""));
            }
            case "ledger" -> {
                double rowH = Math.min(26, h / 5.4);
                String[] amounts = {"R 250", "R 250", "R 500", "R 250"};
                for (int i = 0; i < 4; i++) {
                    double y = y0 + i * rowH;
                    s.append(circle(x0 + 9, y + rowH / 2, 7, i == 2 ? prim : ink, i == 2 ? "" : " fill-opacity=\".1\""))
                     .append(data.item(i).isBlank()
                             ? bar(x0 + 24, y + rowH / 2 - 3, w * (0.3 + rnd(seed, i) * 0.22), ink, 6)
                             : text(x0 + 24, y + rowH / 2 + 4, clipTo(data.item(i), 26), ink, Math.min(13, rowH * 0.42), 540, "start"))
                     .append(text(x0 + w, y + rowH / 2 + 4, amounts[i], i == 2 ? prim : ink, rowH * 0.42, 600, "end"));
                    if (i < 3) s.append(line(x0, y + rowH, x0 + w, y + rowH, ink, 1, " opacity=\".08\""));
                }
                double ty = y0 + 4 * rowH + 12, th = Math.max(20, h - 4 * rowH - 18);
                // Font must track the ROW size, not the box height: a tall total box was
                // ballooning both strings until "Total this month" and the amount collided.
                double labelFont = Math.min(13, rowH * 0.42);
                double amtFont = Math.min(16, rowH * 0.52);
                double cy = ty + th / 2;
                s.append(rect(x0, ty, w, th, 9, prim, " opacity=\".1\""))
                 .append(text(x0 + 12, cy + labelFont * 0.35, "Total this month", muted, labelFont, 600, "start"))
                 .append(text(x0 + w - 12, cy + amtFont * 0.35, "R 1 250", ink, amtFont, 700, "end"));
            }
            case "chart" -> {
                double gh = h * 0.72, gy = y0 + 4;
                for (int i = 0; i <= 3; i++)
                    s.append(line(x0, gy + (gh / 3) * i, x0 + w, gy + (gh / 3) * i, ink, 1, " opacity=\".08\""));
                StringBuilder d = new StringBuilder();
                double hotX = x0, hotY = Double.MAX_VALUE;
                for (int i = 0; i <= 10; i++) {
                    double x = x0 + (w / 10) * i;
                    double v = 0.52 + Math.sin(i * 0.9 + seed) * 0.22 + (rnd(seed, i) - 0.5) * 0.14;
                    double y = gy + gh - Math.max(0.06, Math.min(0.94, v)) * gh;
                    d.append(i == 0 ? "M " : " L ").append(f(x)).append(" ").append(f(y));
                    if (y < hotY) { hotY = y; hotX = x; }
                }
                double thresh = gy + gh * 0.24;
                s.append(line(x0, thresh, x0 + w, thresh, acc, 1.5, " stroke-dasharray=\"5 4\" opacity=\".8\""))
                 .append("<path d=\"").append(d).append("\" fill=\"none\" stroke=\"").append(prim)
                 .append("\" stroke-width=\"2.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>")
                 .append("<path d=\"").append(d).append(" L ").append(f(x0 + w)).append(" ").append(f(gy + gh))
                 .append(" L ").append(f(x0)).append(" ").append(f(gy + gh)).append(" Z\" fill=\"").append(prim).append("\" opacity=\".1\"/>")
                 .append(circle(hotX, hotY, 5, surf, " stroke=\"" + prim + "\" stroke-width=\"2.6\""));
                double ly = gy + gh + 14, lh = Math.max(16, h - gh - 20);
                s.append(rect(x0, ly, w, lh, 8, acc, " opacity=\".14\""))
                 .append(circle(x0 + 14, ly + lh / 2, 4, acc, ""))
                 .append(bar(x0 + 26, ly + lh / 2 - 3, w * 0.46, ink, 6));
            }
            case "checklist" -> {
                int rows = 4;
                double rowH = Math.min(30, (h - 26) / rows);
                for (int i = 0; i < rows; i++) {
                    double y = y0 + i * rowH, bs = rowH * 0.5;
                    boolean done = i < 2;
                    s.append(done
                        ? rect(x0, y + (rowH - bs) / 2, bs, bs, 5, prim)
                          + text(x0 + bs / 2, y + rowH / 2 + bs * 0.2, "✓", surf, bs * 0.72, 700, "middle")
                        : rect(x0, y + (rowH - bs) / 2, bs, bs, 5, "none",
                               " stroke=\"" + ink + "\" stroke-opacity=\".22\" stroke-width=\"1.6\""));
                    double bw = w * (0.36 + rnd(seed, i) * 0.3);
                    String label = data.item(i);
                    if (label.isBlank()) {
                        s.append(bar(x0 + bs + 12, y + rowH / 2 - 3, bw, ink, done ? 6 : 7));
                        if (done) s.append(line(x0 + bs + 12, y + rowH / 2, x0 + bs + 12 + bw, y + rowH / 2, ink, 1.4, " opacity=\".32\""));
                    } else {
                        s.append(text(x0 + bs + 12, y + rowH / 2 + 4, clipTo(label, 30),
                                done ? muted : ink, Math.min(13, rowH * 0.42), done ? 500 : 560, "start"));
                    }
                    // whose turn it is, which is the whole point of a rota
                    s.append(circle(x0 + w - 11, y + rowH / 2, 9, i % 2 == 1 ? acc : prim,
                            " opacity=\"" + (done ? ".35" : ".85") + "\""));
                }
                double ay = y0 + rows * rowH + 6, ah = Math.max(20, Math.min(26, h - rows * rowH - 8));
                s.append(rect(x0, ay, w * 0.36, ah, ah / 2, prim, " opacity=\".14\""))
                 .append(text(x0 + 14, ay + ah / 2 + ah * 0.17, "+ Add a task", prim, ah * 0.42, 600, "start"));
            }
            case "route" -> {
                // a bare curve reads as a chart, so this gets the things that make a
                // map legible: a faint street grid, a labelled start and end, and
                // stops big enough to count.
                for (int gx = 1; gx < 5; gx++)
                    s.append(line(x0 + (w / 5) * gx, y0, x0 + (w / 5) * gx, y0 + h, ink, 1, " opacity=\".07\""));
                for (int gy2 = 1; gy2 < 4; gy2++)
                    s.append(line(x0, y0 + (h / 4) * gy2, x0 + w, y0 + (h / 4) * gy2, ink, 1, " opacity=\".07\""));
                s.append(rect(x0 + w * 0.06, y0 + h * 0.08, w * 0.2, h * 0.22, 6, ink, " opacity=\".05\""))
                 .append(rect(x0 + w * 0.62, y0 + h * 0.62, w * 0.26, h * 0.28, 6, ink, " opacity=\".05\""));

                double[][] stops = {{0.1, 0.78}, {0.33, 0.44}, {0.58, 0.62}, {0.84, 0.26}};
                double[][] pts = new double[stops.length][2];
                for (int i = 0; i < stops.length; i++) { pts[i][0] = x0 + stops[i][0] * w; pts[i][1] = y0 + stops[i][1] * h; }
                StringBuilder d = new StringBuilder("M " + f(pts[0][0]) + " " + f(pts[0][1]));
                for (int i = 1; i < pts.length; i++) {
                    double px = pts[i - 1][0], py = pts[i - 1][1], cx = pts[i][0], cy = pts[i][1];
                    d.append(" C ").append(f(px + (cx - px) * 0.55)).append(" ").append(f(py))
                     .append(" ").append(f(px + (cx - px) * 0.45)).append(" ").append(f(cy))
                     .append(" ").append(f(cx)).append(" ").append(f(cy));
                }
                s.append("<path d=\"").append(d).append("\" fill=\"none\" stroke=\"").append(prim)
                 .append("\" stroke-width=\"").append(f(Math.max(5, h * 0.022)))
                 .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\" opacity=\".28\"/>")
                 .append("<path d=\"").append(d).append("\" fill=\"none\" stroke=\"").append(prim)
                 .append("\" stroke-width=\"").append(f(Math.max(2.5, h * 0.011)))
                 .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>");

                for (int i = 0; i < pts.length; i++) {
                    double cx = pts[i][0], cy = pts[i][1];
                    boolean first = i == 0, last = i == pts.length - 1;
                    double rr = first || last ? Math.max(8, h * 0.036) : Math.max(5, h * 0.022);
                    if (last) {
                        // the destination gets a pin, which is what says "map" at a glance
                        s.append("<path d=\"M ").append(f(cx)).append(" ").append(f(cy + rr * 1.5))
                         .append(" C ").append(f(cx - rr * 1.3)).append(" ").append(f(cy + rr * 0.2))
                         .append(" ").append(f(cx - rr)).append(" ").append(f(cy - rr))
                         .append(" ").append(f(cx)).append(" ").append(f(cy - rr))
                         .append(" C ").append(f(cx + rr)).append(" ").append(f(cy - rr))
                         .append(" ").append(f(cx + rr * 1.3)).append(" ").append(f(cy + rr * 0.2))
                         .append(" ").append(f(cx)).append(" ").append(f(cy + rr * 1.5)).append(" Z\" fill=\"").append(prim).append("\"/>")
                         .append(circle(cx, cy - rr * 0.15, rr * 0.36, surf, ""));
                    } else {
                        s.append(circle(cx, cy, rr, surf, " stroke=\"" + (first ? prim : ink) + "\" stroke-opacity=\""
                                + (first ? "1" : "0.4") + "\" stroke-width=\"" + f(Math.max(2, h * 0.009)) + "\""));
                        if (first) s.append(circle(cx, cy, rr * 0.42, prim, ""));
                    }
                }
                double ph = Math.max(22, h * 0.13), pw = Math.min(w * 0.42, 150);
                s.append(rect(x0, y0 + h - ph, pw, ph, ph / 2, surf))
                 .append(rect(x0, y0 + h - ph, pw, ph, ph / 2, ink, " opacity=\".07\""))
                 .append(text(x0 + 12, y0 + h - ph / 2 + ph * 0.18, "18 min", ink, ph * 0.42, 700, "start"))
                 .append(text(x0 + 12 + ph * 1.9, y0 + h - ph / 2 + ph * 0.16, "4 stops", muted, ph * 0.32, 500, "start"));
            }
            case "inbox" -> {
                double[][] rows = {{0.62, 0}, {0.44, 1}, {0.72, 0}, {0.5, 1}};
                double rowH = Math.min(30, h / 4.5);
                for (int i = 0; i < rows.length; i++) {
                    double y = y0 + i * rowH, bw = w * rows[i][0];
                    boolean mine = rows[i][1] == 1;
                    double bx = mine ? x0 + w - bw : x0;
                    s.append(rect(bx, y, bw, rowH - 7, 9, mine ? prim : ink, mine ? " opacity=\".9\"" : " opacity=\".07\""))
                     .append(data.item(i).isBlank()
                             ? bar(bx + 10, y + (rowH - 7) / 2 - 3, bw - 24, mine ? surf : ink, 5)
                             : text(bx + 11, y + (rowH - 7) / 2 + 4, clipTo(data.item(i), 28),
                                    mine ? surf : ink, Math.min(12, (rowH - 7) * 0.46), 520, "start"));
                }
                double cy = y0 + 4 * rowH + 4, chh = Math.max(20, h - 4 * rowH - 8);
                s.append(rect(x0, cy, w, chh, chh / 2, ink, " opacity=\".06\""))
                 .append(bar(x0 + 14, cy + chh / 2 - 3, w * 0.4, ink, 6))
                 .append(circle(x0 + w - chh / 2 - 4, cy + chh / 2, chh * 0.3, prim, ""));
            }
            case "catalog" -> {
                int cols = 3;
                double gap = 10, cw = (w - gap * (cols - 1)) / cols, chh = Math.min(h * 0.6, cw * 1.1);
                for (int i = 0; i < cols; i++) {
                    double cx = x0 + i * (cw + gap);
                    s.append(rect(cx, y0, cw, chh, 10, ink, " opacity=\".06\""))
                     .append(rect(cx, y0, cw, chh * 0.58, 10, i == 1 ? prim : acc, i == 1 ? " opacity=\".85\"" : " opacity=\".4\""))
                     .append(bar(cx + 8, y0 + chh * 0.68, cw - 26, ink, 6))
                     .append(bar(cx + 8, y0 + chh * 0.82, cw * 0.4, ink, 5));
                }
                double by = y0 + chh + 12, bh = Math.max(20, h - chh - 16);
                s.append(rect(x0, by, w, bh, bh / 2, ink, " opacity=\".06\""))
                 .append(bar(x0 + 14, by + bh / 2 - 3, w * 0.34, ink, 6))
                 .append(rect(x0 + w - 78, by + 4, 70, bh - 8, (bh - 8) / 2, prim));
            }
            default -> { // dashboard
                double gap = 10;
                int cols = 3;
                double cw = (w - gap * (cols - 1)) / cols, chh = h * 0.36;
                String[] vals = {"128", "94%", "12"};
                for (int i = 0; i < cols; i++) {
                    double cx = x0 + i * (cw + gap);
                    s.append(rect(cx, y0, cw, chh, 10, i == 0 ? prim : ink, i == 0 ? " opacity=\".12\"" : " opacity=\".06\""))
                     .append(text(cx + 10, y0 + chh * 0.56, vals[i], i == 0 ? prim : ink, chh * 0.42, 700, "start"))
                     .append(bar(cx + 10, y0 + chh * 0.74, cw * 0.5, ink, 5));
                }
                double gy = y0 + chh + 12, gh = h - chh - 16;
                s.append(rect(x0, gy, w, gh, 10, ink, " opacity=\".05\""));
                int bars = 12;
                double bw = (w - 24) / bars;
                for (int i = 0; i < bars; i++) {
                    double bh2 = (gh - 20) * (0.28 + Math.abs(Math.sin(i * 0.8 + seed)) * 0.66);
                    s.append(rect(x0 + 12 + i * bw, gy + gh - 10 - bh2, bw - 4, bh2, 3,
                            i == 8 ? prim : acc, i == 8 ? "" : " opacity=\".45\""));
                }
            }
        }
        return s.toString();
    }

    /** Trims to a word boundary so a label never ends mid-word inside the frame. */
    private static String clipTo(String s, int max) {
        String t = s.strip();
        if (t.length() <= max) return t;
        int cut = t.lastIndexOf(' ', max);
        return (cut > 6 ? t.substring(0, cut) : t.substring(0, max)).strip();
    }

    private static double rnd(int seed, int i) {
        return ((long) seed * (i + 3) * 9301 + 49297) % 233280 / 233280.0;
    }
}
