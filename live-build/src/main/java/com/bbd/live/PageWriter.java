package com.bbd.live;

import com.bbd.live.Model.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns one crew Result into a real, standalone landing page: nav, hero,
 * features, pricing, FAQ, sign-up and footer, with every style inlined.
 *
 * <p>One file, no frameworks, no external requests. The attendee can open it,
 * keep it, or mail it to themselves, which is the difference between showing
 * someone a mockup and handing them the thing.
 */
public final class PageWriter {
    private PageWriter() {}

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** The Illustrator's artwork, drawn as SVG so no image files are needed. */
    private static String artSvg(Art a) {
        if (a == null) return "";
        List<String> cols = a.colors();
        String c1 = cols != null && cols.size() > 0 ? cols.get(0) : "#F5A524";
        String c2 = cols != null && cols.size() > 1 ? cols.get(1) : "#38BDF8";
        int sd = a.seed();
        StringBuilder in = new StringBuilder();
        switch (a.kind() == null ? "blobs" : a.kind()) {
            case "rings" -> { for (int i = 0; i < 5; i++)
                in.append("<circle cx=\"").append(70 + i * 100).append("\" cy=\"70\" r=\"").append(22 + rnd(sd, i) * 34)
                  .append("\" fill=\"none\" stroke=\"").append(i % 2 == 0 ? c1 : c2).append("\" stroke-width=\"6\" opacity=\".9\"/>"); }
            case "waves" -> { for (int i = 0; i < 3; i++)
                in.append("<path d=\"M0 ").append(42 + i * 26).append(" Q 130 ").append(10 + rnd(sd, i) * 70)
                  .append(" 260 ").append(42 + i * 26).append(" T 540 ").append(42 + i * 26)
                  .append("\" fill=\"none\" stroke=\"").append(i % 2 == 0 ? c1 : c2).append("\" stroke-width=\"6\" opacity=\".85\"/>"); }
            case "grid" -> { for (int i = 0; i < 28; i++)
                in.append("<rect x=\"").append((i % 14) * 38 + 8).append("\" y=\"").append((i / 14) * 66 + 8)
                  .append("\" width=\"30\" height=\"52\" rx=\"7\" fill=\"").append(rnd(sd, i) > .5 ? c1 : c2)
                  .append("\" opacity=\"").append(0.3 + rnd(sd, i) * 0.65).append("\"/>"); }
            case "burst" -> { for (int i = 0; i < 18; i++)
                in.append("<line x1=\"270\" y1=\"70\" x2=\"").append(270 + Math.cos(i / 18.0 * 6.28) * (80 + rnd(sd, i) * 170))
                  .append("\" y2=\"").append(70 + Math.sin(i / 18.0 * 6.28) * (28 + rnd(sd, i) * 48))
                  .append("\" stroke=\"").append(i % 2 == 0 ? c1 : c2).append("\" stroke-width=\"5\" opacity=\".8\"/>");
                in.append("<circle cx=\"270\" cy=\"70\" r=\"18\" fill=\"").append(c1).append("\"/>"); }
            default -> { for (int i = 0; i < 5; i++)
                in.append("<ellipse cx=\"").append(65 + i * 110).append("\" cy=\"").append(70 + (rnd(sd, i) - .5) * 54)
                  .append("\" rx=\"").append(40 + rnd(sd, i) * 38).append("\" ry=\"").append(27 + rnd(sd, i) * 24)
                  .append("\" fill=\"").append(i % 2 == 0 ? c1 : c2).append("\" opacity=\".55\"/>"); }
        }
        return "<svg viewBox=\"0 0 540 140\" xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" aria-label=\"abstract artwork\">"
                + in + "</svg>";
    }

    private static double rnd(int seed, int n) {
        return ((long) seed * (n + 3) * 9301 + 49297) % 233280 / 233280.0;
    }

    /** A filename-safe slug, used for the download. */
    public static String slug(Result r) {
        String n = (r != null && r.product() != null && r.product().name() != null) ? r.product().name() : "page";
        String s = n.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isBlank() ? "page" : s;
    }

    public static String render(Idea idea) {
        Result r = idea.result;
        Copy c = r.copy();
        Palette p = r.palette();
        String name = r.product() != null && r.product().name() != null ? r.product().name() : "This";
        String who = idea.name == null || idea.name.isBlank() ? "someone in the room" : idea.name;
        String cta = c.cta() == null ? "Get started" : c.cta();

        String feats = c.features() == null ? "" : c.features().stream()
                .map(f -> "<div class=\"feat\"><div class=\"ic\"></div><h3>" + esc(f.title()) + "</h3><p>" + esc(f.body()) + "</p></div>")
                .collect(Collectors.joining("\n      "));

        String tiers = r.pricing() == null ? "" : r.pricing().stream().map(t ->
                "<div class=\"tier" + (t.featured() ? " featured" : "") + "\">"
              + "<div class=\"tag\">" + (t.featured() ? "most people pick this" : "") + "</div>"
              + "<h3>" + esc(t.name()) + "</h3>"
              + "<div class=\"price\">" + esc(t.price()) + "</div>"
              + "<div class=\"per\">" + esc(t.per()) + "</div>"
              + "<p class=\"blurb\">" + esc(t.blurb()) + "</p>"
              + "<ul>" + (t.lines() == null ? "" : t.lines().stream().map(l -> "<li>" + esc(l) + "</li>").collect(Collectors.joining())) + "</ul>"
              + "<a class=\"pick\" href=\"#get\">" + esc(cta) + "</a></div>")
                .collect(Collectors.joining("\n      "));

        String faq = r.faq() == null ? "" : r.faq().stream()
                .map(q -> "<details><summary>" + esc(q.q()) + "</summary><p>" + esc(q.a()) + "</p></details>")
                .collect(Collectors.joining("\n      "));

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>%s &mdash; %s</title>
<meta name="description" content="%s">
<style>
  :root{--bg:%s;--surface:%s;--ink:%s;--muted:%s;--primary:%s;--accent:%s;--font:%s;}
  *{box-sizing:border-box;margin:0;padding:0}
  body{background:var(--bg);color:var(--ink);font-family:var(--font);line-height:1.5;-webkit-font-smoothing:antialiased}
  .wrap{max-width:1080px;margin:0 auto;padding:0 clamp(1.2rem,4vw,2.4rem)}
  a{color:inherit}
  nav{display:flex;align-items:center;gap:1.6rem;padding:1.4rem 0;flex-wrap:wrap}
  nav .logo{font-weight:800;letter-spacing:-.02em;font-size:1.15rem;margin-right:auto}
  nav .logo span{color:var(--primary)}
  nav a{text-decoration:none;color:var(--muted);font-size:.94rem}
  nav a:hover{color:var(--ink)}
  nav .btn{background:var(--primary);color:#12100a;padding:.55rem 1.1rem;border-radius:9px;font-weight:700;font-size:.9rem}
  .hero{padding:clamp(2rem,6vw,4.5rem) 0 clamp(1.5rem,4vw,3rem)}
  .hero .art{margin-bottom:2rem}
  .hero .art svg{width:100%%;max-height:140px;display:block}
  .badge{display:inline-block;font-size:.7rem;letter-spacing:.18em;text-transform:uppercase;color:var(--primary);
         border:1px solid var(--primary);border-radius:20px;padding:.3rem .85rem;margin-bottom:1.2rem}
  h1{font-size:clamp(2.1rem,5.5vw,3.8rem);line-height:1.04;letter-spacing:-.025em;font-weight:800;max-width:18ch}
  .sub{font-size:clamp(1.05rem,1.9vw,1.4rem);color:var(--muted);margin-top:1.1rem;max-width:46ch}
  .cta{display:inline-block;margin-top:1.9rem;background:var(--primary);color:#12100a;font-weight:700;
       padding:.95rem 1.9rem;border-radius:12px;font-size:1.08rem;text-decoration:none}
  .note{font-size:.82rem;color:var(--muted);margin-top:.8rem}
  section{padding:clamp(2rem,5vw,3.6rem) 0}
  h2{font-size:clamp(1.5rem,3vw,2.2rem);letter-spacing:-.02em;font-weight:800;margin-bottom:1.6rem}
  .feats{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:1.1rem}
  .feat{background:var(--surface);border-radius:14px;padding:1.5rem 1.4rem}
  .feat .ic{width:38px;height:38px;border-radius:10px;background:var(--accent);opacity:.9;margin-bottom:.9rem}
  .feat h3{font-size:1.18rem;font-weight:700;margin-bottom:.45rem}
  .feat p{color:var(--muted);font-size:.97rem}
  .tiers{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:1.1rem}
  .tier{background:var(--surface);border-radius:16px;padding:1.7rem 1.5rem;border:1px solid transparent;display:flex;flex-direction:column}
  .tier.featured{border-color:var(--primary)}
  .tier .tag{font-size:.66rem;letter-spacing:.16em;text-transform:uppercase;color:var(--primary);margin-bottom:.5rem;min-height:1rem}
  .tier h3{font-size:1.05rem;font-weight:700;color:var(--muted);margin-bottom:.5rem}
  .tier .price{font-size:2.3rem;font-weight:800;letter-spacing:-.02em}
  .tier .per{font-size:.85rem;color:var(--muted);margin-bottom:.9rem}
  .tier .blurb{font-size:.92rem;color:var(--muted);margin-bottom:1.1rem}
  .tier ul{list-style:none;display:flex;flex-direction:column;gap:.5rem;margin-bottom:1.4rem}
  .tier li{font-size:.93rem;padding-left:1.4rem;position:relative}
  .tier li::before{content:"";position:absolute;left:0;top:.45em;width:8px;height:8px;border-radius:2px;background:var(--accent)}
  .tier .pick{margin-top:auto;display:block;text-align:center;text-decoration:none;padding:.75rem;border-radius:10px;
              font-weight:700;font-size:.95rem;background:transparent;border:1px solid var(--primary);color:var(--primary)}
  .tier.featured .pick{background:var(--primary);color:#12100a}
  .faq{display:flex;flex-direction:column;gap:.7rem;max-width:760px}
  details{background:var(--surface);border-radius:12px;padding:1.05rem 1.3rem}
  summary{cursor:pointer;font-weight:700;font-size:1.02rem;list-style:none}
  summary::-webkit-details-marker{display:none}
  summary::after{content:"+";float:right;color:var(--primary);font-weight:800}
  details[open] summary::after{content:"\\2212"}
  details p{color:var(--muted);margin-top:.7rem;font-size:.97rem}
  .signup{background:var(--surface);border-radius:18px;padding:clamp(1.6rem,4vw,2.6rem);text-align:center}
  .signup h2{margin-bottom:.6rem}
  .signup p{color:var(--muted);margin-bottom:1.4rem}
  .signup form{display:flex;gap:.6rem;justify-content:center;flex-wrap:wrap}
  .signup input{padding:.85rem 1.1rem;border-radius:10px;border:1px solid var(--muted);background:var(--bg);
                color:var(--ink);font-size:1rem;font-family:inherit;min-width:min(320px,100%%)}
  .signup button{padding:.85rem 1.6rem;border-radius:10px;border:none;background:var(--primary);color:#12100a;
                 font-weight:700;font-size:1rem;font-family:inherit;cursor:pointer}
  .ok{color:var(--accent);margin-top:1rem;font-size:.95rem;min-height:1.3rem}
  footer{border-top:1px solid var(--surface);margin-top:2rem;padding:2rem 0 3rem;color:var(--muted);font-size:.88rem;
         display:flex;justify-content:space-between;gap:1rem;flex-wrap:wrap}
  .made{font-size:.8rem;opacity:.85}
  .made b{color:var(--ink)}
</style>
</head>
<body>
<div class="wrap">
  <nav>
    <div class="logo">%s<span>.</span></div>
    <a href="#features">Features</a>
    <a href="#pricing">Pricing</a>
    <a href="#faq">FAQ</a>
    <a class="btn" href="#get">%s</a>
  </nav>

  <header class="hero">
    <div class="art">%s</div>
    <span class="badge">%s</span>
    <h1>%s</h1>
    <p class="sub">%s</p>
    <a class="cta" href="#get">%s</a>
    <p class="note">No card. Runs on your own machine.</p>
  </header>

  <section id="features">
    <h2>What it does</h2>
    <div class="feats">
      %s
    </div>
  </section>

  <section id="pricing">
    <h2>Pricing</h2>
    <div class="tiers">
      %s
    </div>
  </section>

  <section id="faq">
    <h2>Questions</h2>
    <div class="faq">
      %s
    </div>
  </section>

  <section id="get">
    <div class="signup">
      <h2>%s</h2>
      <p>Leave an address and we will tell you the moment it is ready.</p>
      <form onsubmit="event.preventDefault();document.getElementById('ok').textContent='Thanks. Nothing was actually sent, this page is a demo.';">
        <input type="email" required placeholder="you@example.com" aria-label="Your email">
        <button type="submit">%s</button>
      </form>
      <div class="ok" id="ok"></div>
    </div>
  </section>

  <footer>
    <span>&copy; %s. A page that did not exist a few minutes ago.</span>
    <span class="made">idea by <b>%s</b> &middot; built by seven agents on one laptop</span>
  </footer>
</div>
</body>
</html>
""".formatted(
                esc(name), esc(c.headline()), esc(c.subhead()),
                p.bg(), p.surface(), p.ink(), p.muted(), p.primary(), p.accent(), p.font(),
                esc(name), esc(cta),
                artSvg(r.art()), esc(c.badge()), esc(c.headline()), esc(c.subhead()), esc(cta),
                feats, tiers, faq,
                esc(cta), esc(cta),
                esc(name), esc(who));
    }
}
