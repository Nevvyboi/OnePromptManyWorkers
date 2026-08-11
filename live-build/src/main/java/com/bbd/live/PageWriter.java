package com.bbd.live;

import com.bbd.live.Model.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns one crew Result into a real, standalone landing page.
 *
 * <p>Design note: this page is not pretending to be an ordinary SaaS site. Its
 * subject is a product that did not exist ten seconds ago, invented by a crew of
 * agents on a laptop in the room, so the provenance is part of the design rather
 * than something to hide. That is what the build receipt near the bottom is for:
 * proof of its own construction, in the crew's own words.
 *
 * <p>No frameworks, no external requests, no build step. One file the attendee
 * can open, keep, or mail to themselves.
 */
public final class PageWriter {
    private PageWriter() {}

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------ icons
    // Real line icons, chosen by what the feature actually says. A coloured
    // square is a placeholder; an icon that matches the words is a decision.
    private static final Map<String, String> ICONS = new LinkedHashMap<>();
    static {
        ICONS.put("bolt", "<path d=\"M13 2 4 14h7l-1 8 10-12h-7z\"/>");
        ICONS.put("pin", "<path d=\"M12 21s7-6.2 7-11a7 7 0 1 0-14 0c0 4.8 7 11 7 11z\"/><circle cx=\"12\" cy=\"10\" r=\"2.6\"/>");
        ICONS.put("shield", "<path d=\"M12 3l8 3v6c0 5-4 8-8 9-4-1-8-4-8-9V6z\"/><path d=\"M9 12l2 2 4-4\"/>");
        ICONS.put("clock", "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M12 7v5l3.5 2\"/>");
        ICONS.put("users", "<circle cx=\"9\" cy=\"8\" r=\"3.2\"/><path d=\"M2.5 20a6.5 6.5 0 0 1 13 0\"/><path d=\"M16 5.5a3.2 3.2 0 0 1 0 6M17.5 20a6.4 6.4 0 0 0-2.2-4.6\"/>");
        ICONS.put("offline", "<path d=\"M2 8.8A16 16 0 0 1 22 8.8M5.5 12.4a11 11 0 0 1 13 0M9 16a6 6 0 0 1 6 0\"/><circle cx=\"12\" cy=\"20\" r=\"1.2\" fill=\"currentColor\" stroke=\"none\"/><path d=\"M3 3l18 18\"/>");
        ICONS.put("tag", "<path d=\"M3 12.5V4a1 1 0 0 1 1-1h8.5L21 11.5 12.5 20z\"/><circle cx=\"7.5\" cy=\"7.5\" r=\"1.4\"/>");
        ICONS.put("spark", "<path d=\"M12 3l1.9 5.6L19.5 10l-5.6 1.9L12 17.5l-1.9-5.6L4.5 10l5.6-1.4z\"/><path d=\"M18.5 16.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z\"/>");
        ICONS.put("download", "<path d=\"M12 3v12\"/><path d=\"M7.5 10.5 12 15l4.5-4.5\"/><path d=\"M4 20h16\"/>");
        ICONS.put("bell", "<path d=\"M18 8.5a6 6 0 1 0-12 0c0 6-2 7-2 7h16s-2-1-2-7z\"/><path d=\"M10.3 20a2 2 0 0 0 3.4 0\"/>");
        ICONS.put("eye", "<path d=\"M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z\"/><circle cx=\"12\" cy=\"12\" r=\"3\"/>");
        ICONS.put("check", "<path d=\"M20 6 9 17l-5-5\"/>");
    }
    private static final String[][] ICON_RULES = {
        {"real[- ]?time|instant|now|immediate|live|alert|notif|warn", "bolt"},
        {"privat|secure|safe|encrypt|your data|data stays|no hostage|belongs", "shield"},
        {"location|area|nearby|map|local|route|gps|suburb|street address", "pin"},
        {"plan|ahead|schedul|calendar|time|remind|ready", "clock"},
        {"shar|family|team|street|togeth|group|everyone", "users"},
        {"offline|no signal|without internet|works anywhere", "offline"},
        {"free|price|cost|card|pay|cheap|budget", "tag"},
        {"smart|clever|learn|habit|automatic|magic", "spark"},
        {"export|download|keep|own|backup", "download"},
        {"nagging|quiet|once|speak|tell you", "bell"},
        {"watch|monitor|keep an eye|track|see", "eye"},
    };
    private static String iconFor(String text) {
        String t = text == null ? "" : text.toLowerCase();
        for (String[] rule : ICON_RULES)
            if (java.util.regex.Pattern.compile(rule[0]).matcher(t).find()) return ICONS.get(rule[1]);
        return ICONS.get("check");
    }
    private static String svgIcon(String text) {
        return "<svg class=\"ic\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.6\""
             + " stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">" + iconFor(text) + "</svg>";
    }
    private static String tick() {
        return "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.2\""
             + " stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">" + ICONS.get("check") + "</svg>";
    }

    // ------------------------------------------------------------ artwork
    private static double rnd(int seed, int n) {
        return ((long) seed * (n + 3) * 9301 + 49297) % 233280 / 233280.0;
    }

    // --- deriving the other colour mode -------------------------------------

    private static int[] rgb(String hex) {
        String h = hex == null ? "#000000" : hex.replace("#", "");
        if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        int n = Integer.parseInt(h, 16);
        return new int[]{(n >> 16) & 255, (n >> 8) & 255, n & 255};
    }
    private static String hex(int r, int g, int b) {
        return String.format("#%02x%02x%02x", clamp(r), clamp(g), clamp(b));
    }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /** Perceived lightness, 0..1. */
    private static double lum(String hex) {
        int[] c = rgb(hex);
        return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255.0;
    }
    private static boolean dark(String bg) { return lum(bg) < 0.42; }

    /**
     * Mirrors a ground or ink colour into the other mode, keeping its hue.
     *
     * <p>A straight inversion turns a warm paper into a cold blue, which loses the
     * palette. Reflecting lightness around the midpoint and keeping the channel
     * relationships keeps a forest page recognisably forest in both modes.
     */
    private static String flip(String c, boolean wasDark) {
        int[] v = rgb(c);
        int max = Math.max(v[0], Math.max(v[1], v[2])), min = Math.min(v[0], Math.min(v[1], v[2]));
        int mid = (max + min) / 2;
        // reflect around the midpoint, then pull toward the new ground
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            int reflected = 255 - v[i];
            int chroma = v[i] - mid;                       // keep the colour's own tilt
            out[i] = reflected + (int) Math.round(chroma * 0.55);
        }
        // never pure black or pure white: both kill depth
        double l = (0.299 * out[0] + 0.587 * out[1] + 0.114 * out[2]) / 255.0;
        if (wasDark && l > 0.97) for (int i = 0; i < 3; i++) out[i] -= 8;
        if (!wasDark && l < 0.03) for (int i = 0; i < 3; i++) out[i] += 12;
        return hex(out[0], out[1], out[2]);
    }

    /** Keeps an accent recognisable but readable against the opposite ground. */
    private static String readable(String c, boolean wasDark) {
        int[] v = rgb(c);
        double l = lum(c);
        // moving to a light ground: darken a pale accent. Moving to dark: lighten a deep one.
        double target = wasDark ? 0.46 : 0.62;
        if (wasDark ? l < 0.62 : l > 0.42) return c;       // already fine, leave the brand alone
        double k = target / Math.max(0.04, l);
        return hex((int) (v[0] * k), (int) (v[1] * k), (int) (v[2] * k));
    }

    /** Trims the float noise that would otherwise fill the markup with 17 decimals. */
    private static String f(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v))
                : String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** A monogram favicon in the product's own colours, inline so nothing is fetched. */
    private static String favicon(Palette p, String name) {
        String letter = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'>"
                + "<rect width='64' height='64' rx='14' fill='" + nz(p.primary(), "#111") + "'/>"
                + "<text x='32' y='45' font-family='system-ui,sans-serif' font-size='38' font-weight='700'"
                + " text-anchor='middle' fill='" + nz(p.bg(), "#fff") + "'>" + esc(letter) + "</text></svg>";
        return java.net.URLEncoder.encode(svg, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Enough structured data for a search result and a share card to be correct. */
    private static String jsonLd(String name, String headline, String desc, List<Tier> tiers) {
        StringBuilder offers = new StringBuilder();
        for (Tier t : tiers) {
            String price = t.price() == null ? "" : t.price().replaceAll("[^0-9.]", "");
            if (offers.length() > 0) offers.append(",");
            offers.append("{\"@type\":\"Offer\",\"name\":\"").append(j(t.name()))
                  .append("\",\"price\":\"").append(price.isBlank() ? "0" : price)
                  .append("\",\"priceCurrency\":\"ZAR\"}");
        }
        return "{\"@context\":\"https://schema.org\",\"@type\":\"SoftwareApplication\","
             + "\"name\":\"" + j(name) + "\",\"headline\":\"" + j(headline) + "\","
             + "\"description\":\"" + j(desc) + "\",\"applicationCategory\":\"BusinessApplication\","
             + "\"offers\":[" + offers + "]}";
    }

    /** Escapes a string for embedding inside JSON. */
    private static String j(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replaceAll("[\\r\\n]+", " ");
    }

    /** A filename-safe slug, used for the download. */
    public static String slug(Result r) {
        String n = (r != null && r.product() != null && r.product().name() != null) ? r.product().name() : "page";
        String s = n.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isBlank() ? "page" : s;
    }

    public static String render(Idea idea) { return render(idea, ""); }

    public static String render(Idea idea, String guardSummary) {
        Result r = idea.result;
        Copy c = r.copy();
        Palette p = r.palette();
        String name = r.product() != null && r.product().name() != null ? r.product().name() : "This";
        String who = idea.name == null || idea.name.isBlank() || !idea.showName ? "someone in the room" : idea.name;
        String cta = c.cta() == null ? "Get started" : c.cta();
        List<Tier> tiers = r.pricing() == null ? List.of() : r.pricing();
        String secs = idea.ms > 0 ? String.format("%.1fs", idea.ms / 1000.0) : "";

        // a serif palette gets a serif display; the rest get a tight, heavy sans
        String bodyFont = p.font() == null ? "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif" : p.font();
        boolean serif = bodyFont.toLowerCase().matches(".*(georgia|times|palatino|serif).*");
        String display = bodyFont;
        if (serif) bodyFont = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif";
        String tracking = serif ? "-.012em" : "-.035em";
        // longer headline, smaller type and a wider measure, so it lands in two lines
        int hl = c.headline() == null ? 0 : c.headline().length();
        String h1size = hl > 46 ? "clamp(1.9rem,4vw,2.9rem)"
                      : hl > 30 ? "clamp(2.2rem,5.4vw,3.8rem)"
                      :           "clamp(2.5rem,7.2vw,5.2rem)";
        String h1measure = hl > 46 ? "24ch" : hl > 30 ? "19ch" : "15ch";
        String weight = serif ? "600" : "800";

        String feats = c.features() == null ? "" : c.features().stream().map(x ->
                "<article class=\"feature rise\"><span class=\"badge\">" + svgIcon(x.title() + " " + x.body())
              + "</span><div><h3>" + esc(x.title()) + "</h3><p>" + esc(x.body()) + "</p></div></article>")
                .collect(Collectors.joining("\n      "));

        String tierHtml = tiers.stream().map(t ->
                "<article class=\"tier rise" + (t.featured() ? " pick" : "") + "\">"
              + "<div class=\"tier-tag\">" + (t.featured() ? "most people pick this" : "") + "</div>"
              + "<h3>" + esc(t.name()) + "</h3>"
              + "<div class=\"price\">" + esc(t.price()) + "</div>"
              + "<div class=\"per\">" + esc(t.per()) + "</div>"
              + "<p class=\"blurb\">" + esc(t.blurb()) + "</p>"
              + "<ul>" + (t.lines() == null ? "" : t.lines().stream()
                    .map(l -> "<li>" + tick() + "<span>" + esc(l) + "</span></li>").collect(Collectors.joining())) + "</ul>"
              + "<a class=\"pick-btn\" href=\"#get\">Choose " + esc(t.name()) + "</a></article>")
                .collect(Collectors.joining("\n      "));

        // The Strategist decides if this idea is really a paid product. Only then does
        // the page carry a pricing section and nav link; everything else stays a simple
        // idea page, so a free tool or a community project is never dressed up for sale.
        boolean showPricing = r.showPricing() && !tiers.isEmpty();
        String pricingNav = showPricing ? "<a class=\"lnk\" href=\"#pricing\">Pricing</a>" : "";
        String pricingSection = showPricing
                ? "<section class=\"band\" id=\"pricing\">\n  <div class=\"wrap\">\n"
                  + "    <div class=\"head\"><h2>Pricing</h2></div>\n    <div class=\"tiers\">\n      "
                  + tierHtml + "\n    </div>\n  </div>\n</section>"
                : "";

        // The sections the Architect chose for this idea, rendered in the order it picked.
        // Each kind gets its own shape, so a menu never looks like a list of steps.
        StringBuilder sectionHtml = new StringBuilder();
        List<Section> pageSecs = r.sections() == null ? List.of() : r.sections();
        for (Section s : pageSecs) {
            String items = s.items() == null ? "" : s.items().stream().map(x -> switch (s.kind()) {
                case "how" -> "<li class=\"step rise\"><h3>" + esc(x.title()) + "</h3><p>" + esc(x.body()) + "</p></li>";
                case "catalog" -> "<article class=\"cat-item rise\"><h3>" + esc(x.title()) + "</h3><p>" + esc(x.body()) + "</p></article>";
                case "proof" -> "<figure class=\"quote rise\"><blockquote>" + esc(x.body()) + "</blockquote>"
                        + "<figcaption>" + esc(x.title()) + "</figcaption></figure>";
                default -> "";
            }).collect(Collectors.joining("\n      "));
            String inner = switch (s.kind()) {
                case "how" -> "<ol class=\"steps\">\n      " + items + "\n    </ol>";
                case "catalog" -> "<div class=\"catalog\">\n      " + items + "\n    </div>";
                case "proof" -> "<div class=\"quotes\">\n      " + items + "\n    </div>";
                case "story" -> "<div class=\"story rise\"><p>" + esc(s.intro()) + "</p></div>";
                default -> "";
            };
            if (inner.isBlank()) continue;
            sectionHtml.append("\n<section class=\"band sec-").append(esc(s.kind())).append("\" id=\"sec-").append(esc(s.kind())).append("\">\n")
                    .append("  <div class=\"wrap\">\n    <div class=\"head\"><h2>").append(esc(s.heading())).append("</h2></div>\n    ")
                    .append(inner).append("\n  </div>\n</section>\n");
        }
        String secNav = pageSecs.stream().limit(2)
                .map(s -> "<a class=\"lnk\" href=\"#sec-" + esc(s.kind()) + "\">" + esc(s.heading()) + "</a>")
                .collect(Collectors.joining("\n    "));

        String faqHtml = r.faq() == null ? "" : r.faq().stream()
                .map(q -> "<details class=\"rise\"><summary>" + esc(q.q()) + "</summary><p>" + esc(q.a()) + "</p></details>")
                .collect(Collectors.joining("\n      "));

        // the crew's own account of what it made
        String[][] receipt = {
            {"namer", "the name", name},
            {"copywriter", "the words", cut(c.headline(), 44)},
            {"designer", "the palette", p.primary() + " / " + p.accent()},
            {"illustrator", "the artwork", r.art() == null ? "" : r.art().kind()},
            showPricing
                ? new String[]{"pricer", "three tiers", tiers.stream().map(Tier::price).collect(Collectors.joining(" · "))}
                : new String[]{"pricer", "weighed pricing", "left off — the strategist read this as not a paid product"},
            {"reviewer", "sharpened the button", cta},
            {"skeptic", "the hard question", cut(r.skeptic(), 52) + "…"},
        };
        // the hero composition the harness picked, defaulting for older results
        String layout = r.layout() == null || r.layout().isBlank() ? "split" : r.layout();
        // Phase three: when the Architect says this idea is physical or a place, the
        // hero becomes a full bleed photograph instead of a device mockup. A ramen
        // truck deserves a bowl of ramen, not a rectangle pretending to be an app.
        boolean photoHero = "photo".equals(r.heroStyle());

        // The mockup used to draw four grey bars whatever the idea was. Feeding it
        // the feature titles makes the picture belong to this product: a chore rota
        // shows the chores, a stokvel shows the contributions.
        List<String> mockRows = new java.util.ArrayList<>();
        if (r.art() != null && r.art().rows() != null) mockRows.addAll(r.art().rows());
        // fall back to the feature titles rather than to grey bars
        if (mockRows.isEmpty())
            for (Feature f : c.features()) if (f.title() != null && !f.title().isBlank()) mockRows.add(f.title());
        Mockup.Rows rows = new Mockup.Rows(name, cut(c.headline(), 30), "", mockRows);

        // One real photograph of the world the product lives in. It is embedded as
        // bytes, fetched during the background build, so serving it touches nothing.
        String sceneAlt = r.art() == null || r.art().scene() == null ? "" : r.art().scene();
        Photos.Shot shot = Photos.find(sceneAlt, true);
        String photo = shot.dataUri();
        if (photoHero && !photo.isBlank()) layout = "photo";

        StringBuilder rec = new StringBuilder();
        for (String[] row : receipt) {
            if (row[2] == null || row[2].isBlank()) continue;
            rec.append("<dt>").append(esc(row[0])).append("</dt><span class=\"what\">").append(esc(row[1]))
               .append("</span><dd>").append(esc(row[2])).append("</dd>\n        ");
        }

        return CSS_AND_BODY
                .replace("__OTHERMODE__", dark(p.bg()) ? "light" : "dark")
                .replace("__ALT_BG__", flip(p.bg(), dark(p.bg())))
                .replace("__ALT_SURFACE__", flip(p.surface(), dark(p.bg())))
                .replace("__ALT_INK__", flip(p.ink(), dark(p.bg())))
                .replace("__ALT_MUTED__", flip(p.muted(), dark(p.bg())))
                .replace("__ALT_PRIMARY__", readable(p.primary(), dark(p.bg())))
                .replace("__ALT_ACCENT__", readable(p.accent(), dark(p.bg())))
                .replace("__BG__", nz(p.bg(), "#0e1016"))
                .replace("__SURFACE__", nz(p.surface(), "#171a22"))
                .replace("__INK__", nz(p.ink(), "#ECE7DE"))
                .replace("__MUTED__", nz(p.muted(), "#9aa0ae"))
                .replace("__PRIMARY__", nz(p.primary(), "#F5A524"))
                .replace("__ACCENT__", nz(p.accent(), "#38BDF8"))
                .replace("__BODYFONT__", bodyFont)
                .replace("__DISPLAY__", display)
                .replace("__TRACKING__", tracking)
                .replace("__H1SIZE__", h1size)
                .replace("__H1MEASURE__", h1measure)
                .replace("__WEIGHT__", weight)
                .replace("__TITLE__", esc(name) + " &#183; "
                        + esc(r.product() != null && r.product().tagline() != null
                              && !r.product().tagline().isBlank() ? r.product().tagline() : c.headline()))
                .replace("__CANON__", "https://" + slug(r) + ".local/")
                .replace("__FAVICON__", favicon(p, name))
                .replace("__JSONLD__", jsonLd(name, c.headline(), c.subhead(), showPricing ? tiers : List.of()))
                .replace("__TAGLINE__", r.product() != null && r.product().tagline() != null
                        && !r.product().tagline().isBlank() ? esc(r.product().tagline()) : "")
                .replace("__DESC__", esc(c.subhead()))
                .replace("__NAVTAG__", r.product() != null && r.product().tagline() != null
                        && !r.product().tagline().isBlank()
                        ? "<span class=\"navtag\">" + esc(r.product().tagline()) + "</span>" : "")
                .replace("__NAME__", esc(name))
                .replace("__CTA__", esc(cta))
                .replace("__BADGE__", esc(c.badge() == null ? "introducing" : c.badge()))
                .replace("__HEADLINE__", esc(c.headline()))
                .replace("__SUBHEAD__", esc(c.subhead()))
                .replace("__HEROBG__", "photo".equals(layout)
                        ? "<img class=\"hero-bg\" src=\"" + photo + "\" alt=\"" + esc(sceneAlt) + "\">"
                          + "<div class=\"hero-scrim\"></div>" : "")
                .replace("__HEROART__", "editorial".equals(layout) || "photo".equals(layout) ? ""
                        : "<div class=\"hero-art\">" + Mockup.svg(r.art(), p, layout, rows) + "</div>")
                .replace("__SHOT__", "editorial".equals(layout)
                        ? "\n<section class=\"band shot\"><div class=\"wrap\"><div class=\"shot-frame\">"
                          + Mockup.svg(r.art(), p, layout, rows) + "</div></div></section>\n"
                        : "")
                .replace("__PHOTO__", photo.isBlank() || "photo".equals(layout) ? ""
                        : "\n<section class=\"band photo\"><div class=\"wrap\"><figure>"
                          + "<img src=\"" + photo + "\" alt=\"" + esc(sceneAlt) + "\" loading=\"lazy\" decoding=\"async\">"
                          + (shot.credit().isBlank() ? ""
                             : "<figcaption>" + esc(shot.credit()) + "</figcaption>")
                          + "</figure></div></section>\n")
                .replace("__TENSION__", r.insight() == null || r.insight().isBlank() ? ""
                        : "\n<section class=\"band tension\"><div class=\"wrap\"><p>"
                          + esc(r.insight()) + "</p></div></section>\n")
                .replace("__LAYOUT__", layout)
                .replace("__FEATURES__", feats)
                .replace("__SECTIONS__", sectionHtml.toString())
                .replace("__SECNAV__", secNav)
                .replace("__PRICING_NAV__", pricingNav)
                .replace("__PRICING_SECTION__", pricingSection)
                .replace("__FAQ__", faqHtml)
                .replace("__RECEIPT__", rec.toString())
                .replace("__SECS__", secs.isBlank() ? "" : ", in <b>" + secs + "</b>")
                .replace("__WHO__", esc(who))
                .replace("__GUARD__", esc(guardSummary.isBlank() ? "not run" : guardSummary));
    }

    private static String nz(String v, String d) { return v == null || v.isBlank() ? d : v; }
    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    // The page itself. Kept as one text block so it stays readable and so the
    // Node mock and this server cannot drift apart by accident.
    private static final String CSS_AND_BODY = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>__TITLE__</title>
<meta name="description" content="__DESC__">
<meta name="theme-color" content="__BG__">
<meta name="color-scheme" content="light dark">
<link rel="canonical" href="__CANON__">
<link rel="icon" href="data:image/svg+xml,__FAVICON__">
<meta property="og:type" content="website">
<meta property="og:title" content="__NAME__ · __HEADLINE__">
<meta property="og:description" content="__DESC__">
<meta property="og:site_name" content="__NAME__">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="__NAME__ · __HEADLINE__">
<meta name="twitter:description" content="__DESC__">
<script type="application/ld+json">__JSONLD__</script>
<style>
  :root{
    --bg:__BG__; --surface:__SURFACE__; --ink:__INK__; --muted:__MUTED__;
    --primary:__PRIMARY__; --accent:__ACCENT__;
    --body:__BODYFONT__; --display:__DISPLAY__;
    --mono:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;
    /* five steps and two mono sizes. Nothing lives between them. */
    --t2:clamp(1.75rem,3.4vw,2.6rem); --t3:clamp(1.1rem,1.7vw,1.32rem);
    --t4:1rem; --t5:.875rem; --mono-sm:.72rem; --mono-md:.8rem;
    /* three tiers of pace, because every section at one rhythm reads as a list */
    --pad-tight:clamp(2.4rem,5vh,3.4rem); --pad-base:clamp(3.4rem,8vh,5.5rem);
    --pad-loud:clamp(4.5rem,12vh,8rem);
    --r:14px; --r-sm:5px; --pill:999px;
    --shadow:12,12,10;
    --line:color-mix(in srgb, var(--ink) 12%, transparent);
    --lift:color-mix(in srgb, var(--ink) 4%, transparent);
  }
  /* The other mode. Derived from the same palette, so the brand survives the
     flip: the accent stays the accent, only the ground and the ink swap. */
  @media (prefers-color-scheme: __OTHERMODE__){
    :root{
      --bg:__ALT_BG__; --surface:__ALT_SURFACE__; --ink:__ALT_INK__; --muted:__ALT_MUTED__;
      --primary:__ALT_PRIMARY__; --accent:__ALT_ACCENT__;
    }
  }
  *{box-sizing:border-box;margin:0;padding:0}
  .skip{position:absolute;left:-9999px;top:0;z-index:100;background:var(--primary);color:var(--bg);
        padding:.7rem 1.1rem;border-radius:var(--r-sm);font-size:var(--t5);font-weight:600}
  .skip:focus{left:1rem;top:1rem}
  h1,h2,h3{text-wrap:balance}
  p,li{text-wrap:pretty}
  html{scroll-behavior:smooth}
  body{background:var(--bg);color:var(--ink);font-family:var(--body);line-height:1.55;
       -webkit-font-smoothing:antialiased;overflow-x:hidden}
  body::after{content:"";position:fixed;inset:0;pointer-events:none;z-index:2;opacity:.035;
    background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='3'/%3E%3C/filter%3E%3Crect width='140' height='140' filter='url(%23n)'/%3E%3C/svg%3E")}
  .wrap{width:min(1120px,100%);margin-inline:auto;padding-inline:clamp(1.15rem,4vw,2.6rem)}
  a{color:inherit}
  :focus-visible{outline:2px solid var(--accent);outline-offset:3px;border-radius:var(--r-sm)}

  .nav{position:sticky;top:0;z-index:5;backdrop-filter:blur(14px);
       background:color-mix(in srgb, var(--bg) 78%, transparent);border-bottom:1px solid transparent;
       transition:border-color .3s}
  .nav.stuck{border-bottom-color:var(--line)}
  .nav .row{display:flex;align-items:center;gap:clamp(.9rem,2.4vw,2rem);padding:.95rem 0}
  .navtag{font-size:var(--t5);color:var(--muted);margin-left:.7rem;padding-left:.7rem;border-left:1px solid var(--line)}
  @media(max-width:640px){.navtag{display:none}}
  .logo{font-family:var(--display);font-weight:__WEIGHT__;letter-spacing:__TRACKING__;
        font-size:var(--t3);margin-right:auto;display:flex;align-items:baseline;gap:.12rem}
  .nav a.lnk{text-decoration:none;color:var(--muted);font-size:var(--t5)}
  .nav a.lnk:hover{color:var(--ink)}
  @media(max-width:640px){.nav a.lnk{display:none}}
  .btn{display:inline-block;text-decoration:none;font-weight:700;border-radius:var(--pill);
       background:var(--primary);color:var(--bg);padding:.6rem 1.15rem;font-size:var(--t5);
       transition:transform .18s cubic-bezier(.2,.7,.2,1),filter .18s}
  .btn:hover{transform:translateY(-1px);filter:brightness(1.07)}

  /* the glow and the artwork are decoration: they must not widen the page */
  .hero{position:relative;overflow:hidden;padding:clamp(3.2rem,10vh,7rem) 0 clamp(2.6rem,7vh,5rem)}
  .hero .wrap{position:relative;z-index:1}
  .hero-art{position:relative}
  .field{display:block;width:100%;height:100%}

  /* split: copy left, the product on the right */
  .hero-photo{min-height:min(72vh,680px);display:flex;align-items:flex-end;padding-bottom:clamp(2rem,6vh,3.6rem)}
  .hero-photo .hero-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:0}
  .hero-photo .hero-scrim{position:absolute;inset:0;z-index:1;
        background:linear-gradient(180deg,color-mix(in srgb,var(--bg) 55%,transparent) 0%,
        color-mix(in srgb,var(--bg) 30%,transparent) 38%,color-mix(in srgb,var(--bg) 92%,transparent) 100%)}
  .hero-photo .glow{display:none}
  .hero-photo .wrap{position:relative;z-index:2}
  .hero-photo .hero-copy{max-width:none}
  .hero-photo .eyebrow{white-space:nowrap}
  .hero-photo h1{font-size:clamp(2.1rem,5.2vw,4rem);max-width:16ch}
  .hero-photo h1{text-shadow:0 2px 26px color-mix(in srgb,var(--bg) 70%,transparent)}
  .hero-photo .lede{max-width:38ch}
  .hero-split .wrap{display:grid;grid-template-columns:1.02fr .98fr;gap:clamp(2rem,5vw,4.5rem);align-items:center}
  .hero-split .hero-art{border:1px solid var(--line);border-radius:var(--r);padding:clamp(.9rem,2vw,1.5rem);
       background:linear-gradient(150deg,color-mix(in srgb,var(--primary) 12%,var(--surface)),var(--surface));
       box-shadow:0 40px 80px -60px color-mix(in srgb,var(--primary) 70%,transparent)}
  @media(max-width:820px){.hero-split .wrap{grid-template-columns:1fr}}

  /* editorial: the sentence is the whole hero, the product gets its own band below */
  .hero-editorial .hero-copy{max-width:min(100%,54rem)}
  .hero-editorial h1{max-width:15ch}
  .hero-editorial .lede{max-width:48ch}
  .band.shot{padding-top:0}
  .shot-frame{border:1px solid var(--line);border-radius:var(--r);padding:clamp(1rem,2.4vw,2rem);
       background:linear-gradient(160deg,color-mix(in srgb,var(--accent) 12%,var(--surface)),var(--surface));
       box-shadow:0 50px 90px -70px color-mix(in srgb,var(--accent) 80%,transparent)}
  .shot-frame .field{max-height:min(46vh,420px);margin:0 auto}

  /* The photograph. A page made only of drawn shapes reads as a diagram, and a
     landing page has to look like it belongs to somewhere real. */
  .band.photo{padding-top:0;padding-bottom:0}
  .band.photo figure{margin:0;border-radius:var(--r);overflow:hidden;border:1px solid var(--line);
       aspect-ratio:21/9;background:var(--surface)}
  .band.photo img{width:100%;height:100%;object-fit:cover;display:block;
       filter:saturate(.92) contrast(1.03)}
  @media(max-width:760px){.band.photo figure{aspect-ratio:16/10}}
  /* openly licensed work gets its credit, quietly */
  .band.photo figcaption{font-family:var(--mono);font-size:var(--mono-sm);letter-spacing:.06em;
       color:var(--muted);padding:.55rem .2rem 0;text-transform:none}

  /* The tension band. The Insight agent's sentence, set as a statement rather
     than a card, because it is the one place the page makes an argument. */
  .band.tension{background:var(--surface);border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
  .band.tension p{font-size:var(--t2);line-height:1.32;letter-spacing:-.022em;
       color:var(--ink);max-width:26ch;font-weight:600;margin:0}

  /* band: the product above, the copy beneath it */
  .hero-band .wrap{display:flex;flex-direction:column-reverse;gap:clamp(1.6rem,4vw,3rem)}
  .hero-band .hero-art{border:1px solid var(--line);border-radius:var(--r);padding:clamp(.9rem,2vw,1.4rem);
       background:linear-gradient(120deg,color-mix(in srgb,var(--accent) 14%,var(--surface)),var(--surface));
       box-shadow:0 40px 80px -60px color-mix(in srgb,var(--accent) 80%,transparent)}
  .hero-band .hero-art .field{max-height:min(34vh,300px);margin:0 auto}
  .hero-band h1{max-width:20ch}

  .glow{position:absolute;top:-30%;right:-16%;width:54vw;height:54vw;max-width:700px;max-height:700px;
        background:radial-gradient(circle,color-mix(in srgb,var(--primary) 13%,transparent) 0%,transparent 64%);
        z-index:0;pointer-events:none}
  .hero-editorial .glow,.hero-band .glow{display:none}
  .hero .wrap{position:relative;z-index:1}
  .eyebrow{display:inline-flex;align-items:center;gap:.55rem;font-family:var(--mono);
           font-size:var(--mono-sm);letter-spacing:.22em;text-transform:uppercase;color:var(--primary);
           border:1px solid color-mix(in srgb,var(--primary) 45%,transparent);
           border-radius:var(--pill);padding:.34rem .8rem;margin-bottom:1.5rem}
  /* a four line hero headline is a type-scale error, not a copy error, so the
     scale is set from the length the copywriter actually produced */
  h1{font-family:var(--display);font-weight:__WEIGHT__;letter-spacing:__TRACKING__;
     font-size:__H1SIZE__;line-height:1.02;max-width:__H1MEASURE__;text-wrap:balance}
  .lede{font-size:var(--t3);color:var(--muted);margin-top:1.35rem;
        max-width:44ch;text-wrap:pretty}
  .actions{display:flex;align-items:center;gap:1.1rem;flex-wrap:wrap;margin-top:2.1rem}
  .btn.lg{font-size:var(--t3);padding:.92rem 1.7rem}

  .band{padding:clamp(3rem,8vh,5.5rem) 0;border-top:1px solid var(--line)}
  .head{display:flex;align-items:baseline;gap:1rem;margin-bottom:2.4rem;flex-wrap:wrap}
  .head h2{font-family:var(--display);font-weight:__WEIGHT__;letter-spacing:__TRACKING__;
           font-size:var(--t2);line-height:1.08}
  .head .note{font-family:var(--mono);font-size:var(--mono-sm);letter-spacing:.16em;text-transform:uppercase;
              color:var(--muted);margin-left:auto}

  .feature{display:grid;grid-template-columns:auto 1fr;gap:1.15rem;align-items:start;
           padding:1.35rem 0;border-top:1px solid var(--line)}
  .feature:first-child{border-top:0}
  .feature .badge{width:44px;height:44px;border-radius:var(--r);display:grid;place-items:center;
                  background:var(--lift);border:1px solid var(--line);color:var(--accent);flex:none}
  .feature .ic{width:21px;height:21px}
  .feature h3{font-family:var(--display);font-size:var(--t3);font-weight:__WEIGHT__;
              letter-spacing:-.01em;margin-bottom:.25rem}
  .feature p{color:var(--muted);max-width:56ch}
  /* deliberately not three equal columns: the first feature leads */
  @media(min-width:860px){
    .features{display:grid;grid-template-columns:1.35fr 1fr;gap:2.4rem 3rem;align-items:start}
    .feature{border-top:0;padding:0;display:block}
    .feature .badge{margin-bottom:1rem}
    .feature:first-child{grid-row:span 2;align-self:stretch;
      border-right:1px solid var(--line);padding-right:3rem}
    .feature:first-child h3{font-size:var(--t3)}
    .feature:first-child p{font-size:var(--t3)}
  }

  /* the Architect's sections: each kind has its own rhythm, so a menu never reads
     like a list of steps and a story never reads like a feature grid */
  .steps{list-style:none;counter-reset:s;display:grid;gap:1.6rem;
         grid-template-columns:repeat(auto-fit,minmax(240px,1fr))}
  .steps .step{counter-increment:s;position:relative;padding-top:2.6rem}
  .steps .step::before{content:counter(s);position:absolute;top:0;left:0;
         font-family:var(--mono);font-size:var(--t5);letter-spacing:.1em;color:var(--primary);
         border-top:2px solid var(--primary);padding-top:.5rem;width:1.8rem}
  .steps .step h3{font-size:var(--t3);margin-bottom:.4rem}
  .steps .step p{color:var(--muted);font-size:var(--t4)}
  .catalog{display:grid;gap:0;grid-template-columns:repeat(auto-fit,minmax(280px,1fr))}
  .cat-item{padding:1.15rem 0;border-top:1px solid var(--line)}
  .catalog>.cat-item:nth-child(-n+2){border-top:0}
  @media(max-width:640px){.catalog>.cat-item:nth-child(2){border-top:1px solid var(--line)}}
  .cat-item h3{font-size:var(--t3);letter-spacing:-.01em;margin-bottom:.3rem}
  .cat-item p{color:var(--muted);font-size:var(--t4);max-width:44ch}
  .quotes{display:grid;gap:1.4rem;grid-template-columns:repeat(auto-fit,minmax(280px,1fr))}
  .quote{margin:0;border-left:2px solid var(--accent);padding:.2rem 0 .2rem 1.2rem}
  .quote blockquote{margin:0;font-size:var(--t2);line-height:1.4;text-wrap:balance}
  .quote figcaption{margin-top:.7rem;font-family:var(--mono);font-size:var(--mono-sm);
         letter-spacing:.14em;text-transform:uppercase;color:var(--muted)}
  .story p{font-size:var(--t2);line-height:1.55;max-width:58ch;color:var(--ink);text-wrap:pretty}
  .tiers{display:grid;grid-template-columns:repeat(auto-fit,minmax(238px,1fr));gap:1rem;align-items:start}
  .tier{border:1px solid var(--line);border-radius:var(--r);padding:1.7rem 1.5rem;display:flex;
        flex-direction:column;background:var(--lift);transition:transform .22s cubic-bezier(.2,.7,.2,1)}
  .tier:hover{transform:translateY(-3px)}
  .tier.pick{border-color:color-mix(in srgb,var(--primary) 60%,transparent);
             background:color-mix(in srgb,var(--primary) 7%,var(--lift));
             box-shadow:0 26px 60px -40px color-mix(in srgb,var(--primary) 70%,transparent)}
  .tier .tier-tag{font-family:var(--mono);font-size:var(--mono-sm);letter-spacing:.16em;text-transform:uppercase;
                  color:var(--primary);min-height:1.1em;margin-bottom:.7rem}
  .tier h3{font-size:var(--t4);color:var(--muted);font-weight:600}
  .price{font-family:var(--display);font-weight:__WEIGHT__;letter-spacing:__TRACKING__;
         font-size:var(--t2);line-height:1.1;margin-top:.25rem}
  .per{font-family:var(--mono);font-size:var(--mono-sm);color:var(--muted);margin-bottom:1rem}
  .tier .blurb{font-size:var(--t4);color:var(--muted);margin-bottom:1.2rem}
  .tier ul{list-style:none;display:grid;gap:.55rem;margin-bottom:1.5rem}
  .tier li{font-size:var(--t4);display:grid;grid-template-columns:auto 1fr;gap:.6rem;align-items:start}
  .tier li svg{width:15px;height:15px;color:var(--accent);margin-top:.25em}
  .tier .pick-btn{margin-top:auto;text-align:center;text-decoration:none;border-radius:var(--pill);
                  padding:.72rem;font-weight:700;font-size:var(--t4);border:1px solid var(--primary);
                  color:var(--primary);transition:background .2s,color .2s}
  .tier .pick-btn:hover{background:var(--primary);color:var(--bg)}
  .tier.pick .pick-btn{background:var(--primary);color:var(--bg)}

  .qs{display:grid;gap:.6rem;max-width:64ch}
  details{border:1px solid var(--line);border-radius:var(--r);background:var(--lift);overflow:hidden}
  summary{cursor:pointer;list-style:none;padding:1.05rem 1.3rem;font-weight:650;font-size:var(--t3);
          display:flex;align-items:center;gap:1rem}
  summary::-webkit-details-marker{display:none}
  summary::after{content:"";width:9px;height:9px;margin-left:auto;flex:none;
                 border-right:2px solid var(--primary);border-bottom:2px solid var(--primary);
                 transform:rotate(45deg) translateY(-2px);transition:transform .25s}
  details[open] summary::after{transform:rotate(225deg) translateY(-2px)}
  details p{padding:0 1.3rem 1.2rem;color:var(--muted);max-width:60ch}

  .close-band{border:1px solid var(--line);border-radius:var(--r);padding:clamp(1.8rem,5vw,3.2rem);
              background:linear-gradient(160deg,color-mix(in srgb,var(--primary) 11%,var(--lift)),var(--lift));
              text-align:center}
  .close-band h2{font-family:var(--display);font-weight:__WEIGHT__;letter-spacing:__TRACKING__;
                 font-size:var(--t2);margin-bottom:.6rem}
  .close-band p{color:var(--muted);margin-bottom:1.7rem}
  form{display:flex;gap:.6rem;justify-content:center;flex-wrap:wrap}
  input[type=email]{flex:1 1 300px;max-width:360px;padding:.85rem 1.1rem;border-radius:var(--pill);
        border:1px solid var(--line);background:var(--bg);color:var(--ink);font:inherit;font-size:var(--t4)}
  input[type=email]::placeholder{color:var(--muted)}
  button{border:0;cursor:pointer;font:inherit}
  .said{margin-top:1.1rem;font-family:var(--mono);font-size:var(--t5);color:var(--accent);min-height:1.3em}

  .receipt{border-top:1px solid var(--line);margin-top:clamp(3rem,8vh,5rem);padding-top:1.6rem}
  .receipt .rh{font-family:var(--mono);font-size:var(--mono-sm);letter-spacing:.2em;text-transform:uppercase;
               color:var(--muted);margin-bottom:1rem;display:flex;gap:.7rem;flex-wrap:wrap}
  .receipt .rh b{color:var(--primary);font-weight:600}
  .receipt dl{display:grid;grid-template-columns:auto auto 1fr;gap:.42rem 1rem;
              font-family:var(--mono);font-size:var(--mono-sm);align-items:baseline}
  .receipt dt{color:var(--primary)}
  .receipt .what{color:var(--muted)}
  .receipt dd{color:var(--ink);opacity:.85;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  @media(max-width:620px){.receipt dl{grid-template-columns:1fr;gap:.1rem}.receipt dd{margin-bottom:.6rem}}

  footer{padding:2rem 0 3.4rem;color:var(--muted);font-size:var(--t5);
         display:flex;justify-content:space-between;gap:1rem;flex-wrap:wrap}
  footer b{color:var(--ink);font-weight:600}

  /* motion is an enhancement: without script, everything is simply visible */
  .js .rise{opacity:0;transform:translateY(16px);transition:opacity .6s ease,transform .6s cubic-bezier(.2,.7,.2,1)}
  .js .rise.in{opacity:1;transform:none}
  @media(prefers-reduced-motion:reduce){
    *{animation:none!important;transition:none!important}
    .rise{opacity:1;transform:none}
    html{scroll-behavior:auto}
  }
</style>
</head>
<body>
<a class="skip" href="#what">Skip to content</a>

<nav class="nav" id="nav">
  <div class="wrap row">
    <span class="logo">__NAME__</span>__NAVTAG__
    <a class="lnk" href="#what">What it does</a>
    __SECNAV__
    __PRICING_NAV__
    <a class="lnk" href="#questions">Questions</a>
    <a class="btn" href="#get">__CTA__</a>
  </div>
</nav>

<header class="hero hero-__LAYOUT__">
  __HEROBG__
  <div class="glow"></div>
  <div class="wrap">
    <div class="hero-copy">
      <span class="eyebrow">__BADGE__</span>
      <h1>__HEADLINE__</h1>
      <p class="lede">__SUBHEAD__</p>
      <div class="actions">
        <a class="btn lg" href="#get">__CTA__</a>
      </div>
    </div>
    __HEROART__
  </div>
</header>
__SHOT____PHOTO____TENSION__

<section class="band" id="what">
  <div class="wrap">
    <div class="head"><h2>What it does</h2></div>
    <div class="features">
      __FEATURES__
    </div>
  </div>
</section>

__SECTIONS__
__PRICING_SECTION__

<section class="band" id="questions">
  <div class="wrap">
    <div class="head"><h2>Questions</h2></div>
    <div class="qs">
      __FAQ__
    </div>
  </div>
</section>

<section class="band" id="get">
  <div class="wrap">
    <div class="close-band rise">
      <h2>__CTA__</h2>
      <p>Leave an address and we will tell you the moment it is ready.</p>
      <form id="f">
        <input type="email" required placeholder="you@example.com" aria-label="Your email address">
        <button class="btn lg" type="submit">__CTA__</button>
      </form>
      <p class="said" id="said" role="status"></p>
    </div>

    <div class="receipt rise">
      <div class="rh"><span>Built by <b>seven agents</b> on one laptop__SECS__</span>
        <span style="margin-left:auto">taste guard: <b>__GUARD__</b></span></div>
      <dl>
        __RECEIPT__
      </dl>
    </div>

    <footer>
      <span>&copy; __NAME__. A page that did not exist a few minutes ago.</span>
      <span>idea by <b>__WHO__</b></span>
    </footer>
  </div>
</section>

<script>
  const nav = document.getElementById("nav");
  addEventListener("scroll", () => nav.classList.toggle("stuck", scrollY > 12), { passive: true });

  // only now is it safe to hide things until they scroll into view
  document.documentElement.classList.add("js");

  const io = new IntersectionObserver((es, o) => es.forEach(e => {
    if (e.isIntersecting) { e.target.classList.add("in"); o.unobserve(e.target); }
  }), { rootMargin: "0px 0px -8% 0px" });
  document.querySelectorAll(".rise").forEach((el, i) => {
    el.style.transitionDelay = (i % 3) * 70 + "ms";
    io.observe(el);
  });

  // safety net: if anything goes wrong with the observer, show the page anyway
  setTimeout(() => document.querySelectorAll(".rise:not(.in)").forEach(el => el.classList.add("in")), 2500);

  document.getElementById("f").addEventListener("submit", e => {
    e.preventDefault();
    document.getElementById("said").textContent =
      "Thanks. Nothing was actually sent: this page is a demo built during a talk.";
    e.target.reset();
  });
</script>
</body>
</html>
""";
}
