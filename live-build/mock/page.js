// Turns one crew Result into a real, standalone landing page.
//
// Design note: this page is not pretending to be an ordinary SaaS site. Its
// subject is a product that did not exist ten seconds ago, invented by a crew
// of agents on a laptop in the room, so the provenance is part of the design
// rather than something to hide. That is what the build receipt near the
// bottom is for: proof of its own construction, in the crew's own words.
//
// No frameworks, no external requests, no build step. One file you can open,
// keep, or mail to yourself. The Java server produces the same shape.

const { mockupSvg } = require("./mockup.js");

const esc = s => String(s == null ? "" : s).replace(/[&<>"]/g, m => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[m]));

// ---------------------------------------------------------------- icons
// Real line icons, chosen by what the feature actually says. A colored square
// is a placeholder; an icon that matches the words is a decision.
const ICONS = {
  bolt:     '<path d="M13 2 4 14h7l-1 8 10-12h-7z"/>',
  pin:      '<path d="M12 21s7-6.2 7-11a7 7 0 1 0-14 0c0 4.8 7 11 7 11z"/><circle cx="12" cy="10" r="2.6"/>',
  shield:   '<path d="M12 3l8 3v6c0 5-4 8-8 9-4-1-8-4-8-9V6z"/><path d="M9 12l2 2 4-4"/>',
  clock:    '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
  users:    '<circle cx="9" cy="8" r="3.2"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 5.5a3.2 3.2 0 0 1 0 6M17.5 20a6.4 6.4 0 0 0-2.2-4.6"/>',
  offline:  '<path d="M2 8.8A16 16 0 0 1 22 8.8M5.5 12.4a11 11 0 0 1 13 0M9 16a6 6 0 0 1 6 0"/><circle cx="12" cy="20" r="1.2" fill="currentColor" stroke="none"/><path d="M3 3l18 18"/>',
  tag:      '<path d="M3 12.5V4a1 1 0 0 1 1-1h8.5L21 11.5 12.5 20z"/><circle cx="7.5" cy="7.5" r="1.4"/>',
  spark:    '<path d="M12 3l1.9 5.6L19.5 10l-5.6 1.9L12 17.5l-1.9-5.6L4.5 10l5.6-1.4z"/><path d="M18.5 16.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z"/>',
  download: '<path d="M12 3v12"/><path d="M7.5 10.5 12 15l4.5-4.5"/><path d="M4 20h16"/>',
  bell:     '<path d="M18 8.5a6 6 0 1 0-12 0c0 6-2 7-2 7h16s-2-1-2-7z"/><path d="M10.3 20a2 2 0 0 0 3.4 0"/>',
  eye:      '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
  check:    '<path d="M20 6 9 17l-5-5"/>',
};
const ICON_RULES = [
  [/real[- ]?time|instant|now|immediate|live|alert|notif|warn/i, "bolt"],
  [/privat|secure|safe|encrypt|your data|data stays|no hostage|belongs/i, "shield"],
  [/location|area|nearby|map|local|route|gps|suburb|street address/i, "pin"],
  [/plan|ahead|schedul|calendar|time|remind|ready/i, "clock"],
  [/shar|family|team|street|togeth|group|everyone/i, "users"],
  [/offline|no signal|without internet|works anywhere/i, "offline"],
  [/free|price|cost|card|pay|cheap|budget/i, "tag"],
  [/smart|clever|learn|habit|automatic|magic/i, "spark"],
  [/export|download|keep|own|backup/i, "download"],
  [/nagging|quiet|once|speak|tell you/i, "bell"],
  [/watch|monitor|keep an eye|track|see/i, "eye"],
];
function iconFor(text) {
  for (const [re, name] of ICON_RULES) if (re.test(text || "")) return ICONS[name];
  return ICONS.check;
}
const svgIcon = (text, cls = "ic") =>
  `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${iconFor(text)}</svg>`;

// ---------------------------------------------------------------- artwork
// The Illustrator's choice, drawn large and soft behind the hero rather than
// squashed into a decorative strip.
function artSvg(a, layout) {
  if (!a) return "";
  const [c1, c2] = a.colors || ["#F5A524", "#38BDF8"];
  const sd = a.seed || 7, r = n => ((sd * (n + 3) * 9301 + 49297) % 233280) / 233280;
  // strips get a wide canvas; the panel gets a tall one, so nothing is cropped to mush
  const rail = layout === "editorial";
  const strip = rail || layout === "band";
  const W = strip ? 1200 : 860, H = rail ? 130 : strip ? 280 : 640;
  const cx = W * 0.5, cy = H * 0.5;
  let inner;

  // A 9:1 rail cannot hold a centred composition: any focal mark floats in it as
  // a speck. The rail gets its own horizontal rhythm that spans the full width.
  if (rail) {
    const bars = ["rings", "burst", "blobs"].includes(a.kind);
    const n = bars ? 34 : 7;
    const marks = Array.from({ length: n }, (_, i) => {
      const t = i / n;
      if (bars) {
        const h = H * (.22 + .62 * Math.abs(Math.sin(t * 9 + sd)) * (.45 + r(i) * .55));
        const hot = i % 7 === 3;
        return `<rect x="${t * W}" y="${H - h}" width="${W / n * .42}" height="${h}" rx="${W / n * .18}" fill="${hot ? c1 : c2}" opacity="${hot ? .95 : .5}"/>`;
      }
      const y = H * .16 + i * (H * .11), len = W * (.34 + r(i) * .62);
      return `<rect x="0" y="${y}" width="${len}" height="${i % 4 === 1 ? 5 : 2.5}" rx="2" fill="${i % 4 === 1 ? c1 : c2}" opacity="${i % 4 === 1 ? .9 : .45}"/>`;
    }).join("");
    return `<svg class="field" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid slice" aria-hidden="true">${marks}</svg>`;
  }

  if (a.kind === "rings") {
    // concentric, one solid anchor, weight growing outward: a composition, not scatter
    const step = Math.min(W, H) / 9;
    inner = `<circle cx="${cx}" cy="${cy}" r="${step * .85}" fill="${c1}" opacity=".9"/>`
      + [1, 2, 3, 4, 5, 6].map(i =>
          `<circle cx="${cx}" cy="${cy}" r="${step * (.85 + i * .95)}" fill="none" stroke="${i % 2 ? c2 : c1}" stroke-width="${1 + i * 1.6}" opacity="${.62 - i * .07}"/>`
        ).join("");
  } else if (a.kind === "waves") {
    // parallel contour lines, evenly phased, thickening downward, with a solid base band
    const n = 6;
    inner = Array.from({ length: n }, (_, i) => {
      const y = H * .2 + i * (H * .11), amp = H * .13 * (1 - i / (n * 1.7));
      return `<path d="M0 ${y} C ${W * .28} ${y - amp} ${W * .52} ${y + amp} ${W} ${y - amp * .5}" fill="none" stroke="${i % 3 === 0 ? c1 : c2}" stroke-width="${2 + i * .9}" opacity="${.42 + i * .08}" stroke-linecap="round"/>`;
    }).join("")
      + `<path d="M0 ${H} L0 ${H * .86} C ${W * .3} ${H * .78} ${W * .6} ${H * .95} ${W} ${H * .82} L${W} ${H} Z" fill="${c1}" opacity=".18"/>`;
  } else if (a.kind === "grid") {
    // a real modular grid: hairline cells, four filled deliberately, one spanning two columns
    const cols = strip ? 12 : 7, rows = rail ? 2 : strip ? 3 : 6;
    const gx = W / (cols + 1), gy = H / (rows + 1), pad = gx * .16;
    const hot = new Set([3, 11, 17, 26].map(k => k % (cols * rows)));
    inner = Array.from({ length: cols * rows }, (_, i) => {
      const x = (i % cols) * gx + gx * .5, y = Math.floor(i / cols) * gy + gy * .5;
      return hot.has(i)
        ? `<rect x="${x}" y="${y}" width="${gx - pad}" height="${gy - pad}" rx="${gx * .18}" fill="${i % 2 ? c1 : c2}" opacity=".92"/>`
        : `<rect x="${x}" y="${y}" width="${gx - pad}" height="${gy - pad}" rx="${gx * .18}" fill="none" stroke="${c2}" stroke-width="1.6" opacity=".42"/>`;
    }).join("");
  } else if (a.kind === "burst") {
    // rays on a fixed angular rhythm, length shaped by a sine so the silhouette reads as a form
    const ax = W * .34, ay = H * .52, R = Math.min(W, H) * .62;
    inner = Array.from({ length: 24 }, (_, i) => {
      const t = i / 24 * 6.283, len = R * (.45 + .55 * Math.abs(Math.sin(t * 1.5)));
      return `<line x1="${ax}" y1="${ay}" x2="${ax + Math.cos(t) * len}" y2="${ay + Math.sin(t) * len}" stroke="${i % 3 ? c2 : c1}" stroke-width="${i % 3 ? 2 : 5}" opacity="${i % 3 ? .4 : .8}" stroke-linecap="round"/>`;
    }).join("")
      + `<circle cx="${ax}" cy="${ay}" r="${R * .17}" fill="${c1}"/>`;
  } else {
    // stacked arcs at real scale: three shapes, not nine, each carrying weight
    inner = `<path d="M0 ${H} A ${W * .55} ${H * .8} 0 0 1 ${W * .78} ${H * .1}" fill="none" stroke="${c1}" stroke-width="${H * .045}" opacity=".75" stroke-linecap="round"/>`
      + `<path d="M${W * .22} ${H} A ${W * .5} ${H * .72} 0 0 1 ${W} ${H * .26}" fill="none" stroke="${c2}" stroke-width="${H * .03}" opacity=".55" stroke-linecap="round"/>`
      + `<circle cx="${W * .74}" cy="${H * .3}" r="${H * .11}" fill="${c2}" opacity=".85"/>`;
  }
  return `<svg class="field" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid slice" aria-hidden="true">${inner}</svg>`;
}

// A serif palette gets a serif display; the rest get a tight, heavy sans.
// The pairing is what stops six palettes looking like one template.
function fonts(p, headline) {
  const n = (headline || "").length;
  // longer headline, smaller type and a wider measure, so it lands in two lines
  const h1size = n > 46 ? "clamp(1.9rem,4vw,2.9rem)"
               : n > 30 ? "clamp(2.2rem,5.4vw,3.8rem)"
               :          "clamp(2.5rem,7.2vw,5.2rem)";
  const h1measure = n > 46 ? "24ch" : n > 30 ? "19ch" : "15ch";
  const body = p.font || "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif";
  const serif = /georgia|times|palatino|serif/i.test(body);
  return {
    h1size, h1measure,
    body: serif ? "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif" : body,
    display: serif ? body : body,
    tracking: serif ? "-.012em" : "-.035em",
    weight: serif ? "600" : "800",
  };
}

function renderPage(item) {
  const R = item.result || {}, c = R.copy || {}, p = R.palette || {};
  const name = (R.product && R.product.name) || "This";
  const who = (item.name || "").trim();
  const tiers = R.pricing || [], faq = R.faq || [], f = fonts(p, c.headline);
  const layout = R.layout || "split";
  const secs = item.ms ? (item.ms / 1000).toFixed(1) + "s" : "";

  // the crew's own account of what it made, in their words
  const receipt = [
    ["namer", "the name", name],
    ["copywriter", "the words", (c.headline || "").slice(0, 44)],
    ["designer", "the palette", (p.primary || "") + " / " + (p.accent || "")],
    ["illustrator", "the artwork", (R.art && R.art.kind) || ""],
    ["pricer", "three tiers", tiers.map(t => t.price).join(" · ")],
    ["reviewer", "sharpened the button", c.cta || ""],
    ["skeptic", "the hard question", (R.skeptic || "").slice(0, 52) + "…"],
  ];

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${esc(name)} &#183; ${esc(c.headline || "")}</title>
<meta name="description" content="${esc(c.subhead || "")}">
<style>
  :root{
    --bg:${p.bg || "#0e1016"}; --surface:${p.surface || "#171a22"};
    --ink:${p.ink || "#ECE7DE"}; --muted:${p.muted || "#9aa0ae"};
    --primary:${p.primary || "#F5A524"}; --accent:${p.accent || "#38BDF8"};
    --body:${f.body}; --display:${f.display};
    --mono:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;
    --line:color-mix(in srgb, var(--ink) 12%, transparent);
    --lift:color-mix(in srgb, var(--ink) 4%, transparent);
    --measure:64ch;
  }
  *{box-sizing:border-box;margin:0;padding:0}
  html{scroll-behavior:smooth}
  body{background:var(--bg);color:var(--ink);font-family:var(--body);line-height:1.55;
       -webkit-font-smoothing:antialiased;overflow-x:hidden}
  /* a little grain, so large flat areas do not read as a default template */
  body::after{content:"";position:fixed;inset:0;pointer-events:none;z-index:2;opacity:.035;
    background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='3'/%3E%3C/filter%3E%3Crect width='140' height='140' filter='url(%23n)'/%3E%3C/svg%3E")}
  .wrap{width:min(1120px,100%);margin-inline:auto;padding-inline:clamp(1.15rem,4vw,2.6rem)}
  a{color:inherit}
  :focus-visible{outline:2px solid var(--accent);outline-offset:3px;border-radius:4px}

  /* ---------- nav ---------- */
  .nav{position:sticky;top:0;z-index:5;backdrop-filter:blur(14px);
       background:color-mix(in srgb, var(--bg) 78%, transparent);border-bottom:1px solid transparent;
       transition:border-color .3s}
  .nav.stuck{border-bottom-color:var(--line)}
  .nav .row{display:flex;align-items:center;gap:clamp(.9rem,2.4vw,2rem);padding:.95rem 0}
  .logo{font-family:var(--display);font-weight:${f.weight};letter-spacing:${f.tracking};
        font-size:1.12rem;margin-right:auto;display:flex;align-items:baseline;gap:.12rem}
  .nav a.lnk{text-decoration:none;color:var(--muted);font-size:.9rem}
  .nav a.lnk:hover{color:var(--ink)}
  @media(max-width:640px){.nav a.lnk{display:none}}
  .btn{display:inline-block;text-decoration:none;font-weight:700;border-radius:999px;
       background:var(--primary);color:var(--bg);padding:.6rem 1.15rem;font-size:.88rem;
       transition:transform .18s cubic-bezier(.2,.7,.2,1),filter .18s}
  .btn:hover{transform:translateY(-1px);filter:brightness(1.07)}

  /* ---------- hero ---------- */
  /* the glow and the artwork are decoration: they must not widen the page */
  .hero{position:relative;overflow:hidden;padding:clamp(3.2rem,10vh,7rem) 0 clamp(2.6rem,7vh,5rem)}
  .hero .wrap{position:relative;z-index:1}
  .hero-art{position:relative}
  .field{display:block;width:100%;height:100%}

  /* split: copy left, the product on the right */
  .hero-split .wrap{display:grid;grid-template-columns:1.02fr .98fr;gap:clamp(2rem,5vw,4.5rem);align-items:center}
  .hero-split .hero-art{border:1px solid var(--line);border-radius:20px;padding:clamp(.9rem,2vw,1.5rem);
       background:linear-gradient(150deg,color-mix(in srgb,var(--primary) 12%,var(--surface)),var(--surface));
       box-shadow:0 40px 80px -60px color-mix(in srgb,var(--primary) 70%,transparent)}
  @media(max-width:820px){.hero-split .wrap{grid-template-columns:1fr}}

  /* editorial: the sentence is the whole hero, the product gets its own band below */
  .hero-editorial .hero-copy{max-width:min(100%,54rem)}
  .hero-editorial h1{max-width:15ch}
  .hero-editorial .lede{max-width:48ch}
  .band.shot{padding-top:0}
  .shot-frame{border:1px solid var(--line);border-radius:22px;padding:clamp(1rem,2.4vw,2rem);
       background:linear-gradient(160deg,color-mix(in srgb,var(--accent) 12%,var(--surface)),var(--surface));
       box-shadow:0 50px 90px -70px color-mix(in srgb,var(--accent) 80%,transparent)}
  .shot-frame .field{max-height:min(46vh,420px);margin:0 auto}

  /* the one place the page argues something, so it is a statement not a card */
  .band.tension{background:var(--surface);border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
  .band.tension p{font-size:clamp(1.35rem,2.9vw,2.05rem);line-height:1.32;letter-spacing:-.022em;
       color:var(--ink);max-width:26ch;font-weight:600;margin:0}

  /* band: the product above, the copy beneath it */
  .hero-band .wrap{display:flex;flex-direction:column-reverse;gap:clamp(1.6rem,4vw,3rem)}
  .hero-band .hero-art{border:1px solid var(--line);border-radius:20px;padding:clamp(.9rem,2vw,1.4rem);
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
           font-size:.66rem;letter-spacing:.22em;text-transform:uppercase;color:var(--primary);
           border:1px solid color-mix(in srgb,var(--primary) 45%,transparent);
           border-radius:999px;padding:.34rem .8rem;margin-bottom:1.5rem}
  /* a four line hero headline is a type-scale error, not a copy error, so the
     scale is set from the length the copywriter actually produced */
  h1{font-family:var(--display);font-weight:${f.weight};letter-spacing:${f.tracking};
     font-size:${f.h1size};line-height:1.02;max-width:${f.h1measure};text-wrap:balance}
  .lede{font-size:clamp(1.06rem,1.7vw,1.36rem);color:var(--muted);margin-top:1.35rem;
        max-width:44ch;text-wrap:pretty}
  .actions{display:flex;align-items:center;gap:1.1rem;flex-wrap:wrap;margin-top:2.1rem}
  .btn.lg{font-size:1.02rem;padding:.92rem 1.7rem}
  .fine{font-family:var(--mono);font-size:.72rem;color:var(--muted);letter-spacing:.02em}

  /* ---------- shared section furniture ---------- */
  .band{padding:clamp(3rem,8vh,5.5rem) 0;border-top:1px solid var(--line)}
  .head{display:flex;align-items:baseline;gap:1rem;margin-bottom:2.4rem;flex-wrap:wrap}
  .head h2{font-family:var(--display);font-weight:${f.weight};letter-spacing:${f.tracking};
           font-size:clamp(1.6rem,3.4vw,2.4rem);line-height:1.08}
  .head .note{font-family:var(--mono);font-size:.68rem;letter-spacing:.16em;text-transform:uppercase;
              color:var(--muted);margin-left:auto}

  /* features: a list, not three identical boxes */
  .feature{display:grid;grid-template-columns:auto 1fr;gap:1.15rem;align-items:start;
           padding:1.35rem 0;border-top:1px solid var(--line)}
  .feature:first-child{border-top:0}
  .feature .badge{width:44px;height:44px;border-radius:13px;display:grid;place-items:center;
                  background:var(--lift);border:1px solid var(--line);color:var(--accent);flex:none}
  .feature .ic{width:21px;height:21px}
  .feature h3{font-family:var(--display);font-size:1.16rem;font-weight:${f.weight};
              letter-spacing:-.01em;margin-bottom:.25rem}
  .feature p{color:var(--muted);max-width:56ch}
  /* deliberately not three equal columns: the first feature leads, the other
     two sit beside it, so the eye has somewhere to start */
  @media(min-width:860px){
    .features{display:grid;grid-template-columns:1.35fr 1fr;gap:2.4rem 3rem;align-items:start}
    .feature{border-top:0;padding:0;display:block}
    .feature .badge{margin-bottom:1rem}
    .feature:first-child{grid-row:span 2;align-self:stretch;
      border-right:1px solid var(--line);padding-right:3rem;
      display:flex;flex-direction:column;justify-content:center}
    .feature:first-child h3{font-size:1.5rem}
    .feature:first-child p{font-size:1.05rem}
  }

  /* pricing */
  .tiers{display:grid;grid-template-columns:repeat(auto-fit,minmax(238px,1fr));gap:1rem;align-items:start}
  .tier{border:1px solid var(--line);border-radius:18px;padding:1.7rem 1.5rem;display:flex;
        flex-direction:column;background:var(--lift);transition:transform .22s cubic-bezier(.2,.7,.2,1)}
  .tier:hover{transform:translateY(-3px)}
  .tier.pick{border-color:color-mix(in srgb,var(--primary) 60%,transparent);
             background:color-mix(in srgb,var(--primary) 7%,var(--lift));
             box-shadow:0 26px 60px -40px color-mix(in srgb,var(--primary) 70%,transparent)}
  .tier .tier-tag{font-family:var(--mono);font-size:.6rem;letter-spacing:.16em;text-transform:uppercase;
                  color:var(--primary);min-height:1.1em;margin-bottom:.7rem}
  .tier h3{font-size:.94rem;color:var(--muted);font-weight:600;letter-spacing:.01em}
  .price{font-family:var(--display);font-weight:${f.weight};letter-spacing:${f.tracking};
         font-size:2.5rem;line-height:1.1;margin-top:.25rem}
  .per{font-family:var(--mono);font-size:.72rem;color:var(--muted);margin-bottom:1rem}
  .tier .blurb{font-size:.93rem;color:var(--muted);margin-bottom:1.2rem}
  .tier ul{list-style:none;display:grid;gap:.55rem;margin-bottom:1.5rem}
  .tier li{font-size:.92rem;display:grid;grid-template-columns:auto 1fr;gap:.6rem;align-items:start}
  .tier li svg{width:15px;height:15px;color:var(--accent);margin-top:.25em}
  .tier .pick-btn{margin-top:auto;text-align:center;text-decoration:none;border-radius:999px;
                  padding:.72rem;font-weight:700;font-size:.92rem;border:1px solid var(--primary);
                  color:var(--primary);transition:background .2s,color .2s}
  .tier .pick-btn:hover{background:var(--primary);color:var(--bg)}
  .tier.pick .pick-btn{background:var(--primary);color:var(--bg)}

  /* questions */
  .qs{display:grid;gap:.6rem;max-width:var(--measure)}
  details{border:1px solid var(--line);border-radius:14px;background:var(--lift);overflow:hidden}
  summary{cursor:pointer;list-style:none;padding:1.05rem 1.3rem;font-weight:650;font-size:1.01rem;
          display:flex;align-items:center;gap:1rem}
  summary::-webkit-details-marker{display:none}
  summary::after{content:"";width:9px;height:9px;margin-left:auto;flex:none;
                 border-right:2px solid var(--primary);border-bottom:2px solid var(--primary);
                 transform:rotate(45deg) translateY(-2px);transition:transform .25s}
  details[open] summary::after{transform:rotate(225deg) translateY(-2px)}
  details p{padding:0 1.3rem 1.2rem;color:var(--muted);max-width:60ch}

  /* closing call */
  .close-band{border:1px solid var(--line);border-radius:24px;padding:clamp(1.8rem,5vw,3.2rem);
              background:linear-gradient(160deg,color-mix(in srgb,var(--primary) 11%,var(--lift)),var(--lift));
              text-align:center}
  .close-band h2{font-family:var(--display);font-weight:${f.weight};letter-spacing:${f.tracking};
                 font-size:clamp(1.6rem,3.6vw,2.5rem);margin-bottom:.6rem}
  .close-band p{color:var(--muted);margin-bottom:1.7rem}
  form{display:flex;gap:.6rem;justify-content:center;flex-wrap:wrap}
  input[type=email]{flex:1 1 300px;max-width:360px;padding:.85rem 1.1rem;border-radius:999px;
        border:1px solid var(--line);background:var(--bg);color:var(--ink);font:inherit;font-size:1rem}
  input[type=email]::placeholder{color:var(--muted)}
  button{border:0;cursor:pointer;font:inherit}
  .said{margin-top:1.1rem;font-family:var(--mono);font-size:.82rem;color:var(--accent);min-height:1.3em}

  /* the signature: this page's own build receipt */
  .receipt{border-top:1px solid var(--line);margin-top:clamp(3rem,8vh,5rem);padding-top:1.6rem}
  .receipt .rh{font-family:var(--mono);font-size:.62rem;letter-spacing:.2em;text-transform:uppercase;
               color:var(--muted);margin-bottom:1rem;display:flex;gap:.7rem;flex-wrap:wrap}
  .receipt .rh b{color:var(--primary);font-weight:600}
  .receipt dl{display:grid;grid-template-columns:auto auto 1fr;gap:.42rem 1rem;
              font-family:var(--mono);font-size:.72rem;align-items:baseline}
  .receipt dt{color:var(--primary)}
  .receipt .what{color:var(--muted)}
  .receipt dd{color:var(--ink);opacity:.85;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  @media(max-width:620px){.receipt dl{grid-template-columns:1fr;gap:.1rem}.receipt dd{margin-bottom:.6rem}}

  footer{padding:2rem 0 3.4rem;color:var(--muted);font-size:.85rem;
         display:flex;justify-content:space-between;gap:1rem;flex-wrap:wrap}
  footer b{color:var(--ink);font-weight:600}

  /* motion, kept quiet and switched off on request */
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

<nav class="nav" id="nav">
  <div class="wrap row">
    <span class="logo">${esc(name)}</span>
    <a class="lnk" href="#what">What it does</a>
    <a class="lnk" href="#pricing">Pricing</a>
    <a class="lnk" href="#questions">Questions</a>
    <a class="btn" href="#get">${esc(c.cta || "Get started")}</a>
  </div>
</nav>

<header class="hero hero-${layout}">
  <div class="glow"></div>
  <div class="wrap">
    <div class="hero-copy">
      <span class="eyebrow">${esc(c.badge || "introducing")}</span>
      <h1>${esc(c.headline || "")}</h1>
      <p class="lede">${esc(c.subhead || "")}</p>
      <div class="actions">
        <a class="btn lg" href="#get">${esc(c.cta || "Get started")}</a>
      </div>
    </div>
    ${layout === "editorial" ? "" : `<div class="hero-art">${mockupSvg(R.art, p, layout)}</div>`}
  </div>
</header>

${layout === "editorial" ? `<section class="band shot">
  <div class="wrap"><div class="shot-frame">${mockupSvg(R.art, p, layout)}</div></div>
</section>` : ""}

${R.insight ? `<section class="band tension"><div class="wrap"><p>${esc(R.insight)}</p></div></section>` : ""}

<section class="band" id="what">
  <div class="wrap">
    <div class="head"><h2>What it does</h2></div>
    <div class="features">
      ${(c.features || []).map(x => `<article class="feature rise">
        <span class="badge">${svgIcon(x.title + " " + x.body)}</span>
        <div><h3>${esc(x.title)}</h3><p>${esc(x.body)}</p></div>
      </article>`).join("\n      ")}
    </div>
  </div>
</section>

<section class="band" id="pricing">
  <div class="wrap">
    <div class="head"><h2>Pricing</h2></div>
    <div class="tiers">
      ${tiers.map(t => `<article class="tier rise${t.featured ? " pick" : ""}">
        <div class="tier-tag">${t.featured ? "most people pick this" : ""}</div>
        <h3>${esc(t.name)}</h3>
        <div class="price">${esc(t.price)}</div>
        <div class="per">${esc(t.per)}</div>
        <p class="blurb">${esc(t.blurb)}</p>
        <ul>${(t.lines || []).map(l => `<li><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS.check}</svg><span>${esc(l)}</span></li>`).join("")}</ul>
        <a class="pick-btn" href="#get">Choose ${esc(t.name)}</a>
      </article>`).join("\n      ")}
    </div>
  </div>
</section>

<section class="band" id="questions">
  <div class="wrap">
    <div class="head"><h2>Questions</h2></div>
    <div class="qs">
      ${faq.map(q => `<details class="rise"><summary>${esc(q.q)}</summary><p>${esc(q.a)}</p></details>`).join("\n      ")}
    </div>
  </div>
</section>

<section class="band" id="get">
  <div class="wrap">
    <div class="close-band rise">
      <h2>${esc(c.cta || "Get started")}</h2>
      <p>Leave an address and we will tell you the moment it is ready.</p>
      <form id="f">
        <input type="email" required placeholder="you@example.com" aria-label="Your email address">
        <button class="btn lg" type="submit">${esc(c.cta || "Get started")}</button>
      </form>
      <p class="said" id="said" role="status"></p>
    </div>

    <div class="receipt rise">
      <div class="rh"><span>Built by <b>seven agents</b> on one laptop${secs ? `, in <b>${secs}</b>` : ""}</span>
        <span style="margin-left:auto">no cloud, nothing left the room</span></div>
      <dl>
        ${receipt.filter(r => r[2]).map(r => `<dt>${esc(r[0])}</dt><span class="what">${esc(r[1])}</span><dd>${esc(r[2])}</dd>`).join("\n        ")}
      </dl>
    </div>

    <footer>
      <span>&copy; ${esc(name)}. A page that did not exist a few minutes ago.</span>
      <span>idea by <b>${esc(who || "someone in the room")}</b></span>
    </footer>
  </div>
</section>

<script>
  // the nav earns its hairline only once you have scrolled past the hero
  const nav = document.getElementById("nav");
  addEventListener("scroll", () => nav.classList.toggle("stuck", scrollY > 12), { passive: true });

  // one quiet reveal per element, then the observer lets go
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
</html>`;
}

module.exports = { renderPage };
