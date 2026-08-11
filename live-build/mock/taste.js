// The taste guard.
//
// This is lever four from the talk, made real: a guardrail that validates what
// an agent produced and can refuse it. It is deliberately deterministic. No
// model is asked whether the page looks good, because "does it look good" is
// exactly the question a model will answer yes to. Instead the rules that a
// designer would actually enforce are written down and checked mechanically.
//
// Rules are the anti-slop set: no em-dashes, eyebrow labels rationed, no three
// identical feature cards, no tiny tagline under the hero CTA, no decorative
// status dots, no scroll cues, no fake version stamps.

// Repairs must never touch a stylesheet or a script.
//
// This is not hypothetical. The emoji repair below collapsed whitespace before
// punctuation across the whole document, which turned the CSS selector
// ".hero .wrap" into ".hero.wrap" and the grid value "1.02fr .98fr" into
// "1.02fr.98fr". Every descendant rule in the hero silently stopped applying and
// the pages had been rendering with a broken layout. A guard that edits markup
// has to know which parts of the markup it is allowed to edit.
function outsideCode(html, fn) {
  const parts = String(html).split(/(<(?:script|style)[^>]*>[\s\S]*?<\/(?:script|style)>)/i);
  return parts.map((p, i) => (i % 2 ? p : fn(p))).join("");
}

const RULES = [
  {
    id: "em-dash",
    why: "the em-dash is the single most reliable tell that a machine wrote the page",
    check(h, text) {
      const n = (text.match(/[—–]/g) || []).length + (h.match(/&[mn]dash;/g) || []).length;
      return n ? { count: n, detail: `${n} found` } : null;
    },
    // deterministic enough to repair rather than merely report
    fix: h => outsideCode(h, t => t
      .replace(/&mdash;/g, "&#183;").replace(/&ndash;/g, "-")
      .replace(/—/g, " - ").replace(/–/g, "-")),
  },
  {
    id: "eyebrow-restraint",
    why: "a small uppercase label above every section is the templated rhythm every generated page has",
    check(h) {
      const sections = (h.match(/<section/g) || []).length + 1;
      const eyebrows = (h.match(/class="(eyebrow|note)"/g) || []).length;
      const cap = Math.ceil(sections / 3);
      return eyebrows > cap ? { count: eyebrows, detail: `${eyebrows} labels, cap is ${cap}` } : null;
    },
  },
  {
    id: "three-equal-cards",
    why: "three identical feature cards in a row is the default every model reaches for",
    check(h) {
      return /grid-template-columns:\s*repeat\(3,\s*1fr\)/.test(h.replace(/\s+/g, " "))
        ? { detail: "features laid out as three equal columns" } : null;
    },
  },
  {
    id: "hero-tagline",
    why: "the hero holds one message; a micro-line under the button is clutter",
    check(h) { return /class="fine"/.test(h) ? { detail: "tagline under the hero CTA" } : null; },
  },
  {
    id: "decorative-dots",
    why: "a coloured dot that carries no state is decoration pretending to be information",
    check(h) {
      const n = (h.match(/\.(logo|eyebrow) i\{/g) || []).length;
      return n ? { count: n, detail: `${n} decorative dots` } : null;
    },
  },
  {
    id: "scroll-cue",
    why: "someone looking at the hero already knows how to scroll",
    check(h, text) { return /\bscroll to (explore|discover)\b|↓\s*scroll/i.test(text) ? { detail: "scroll cue" } : null; },
  },
  {
    id: "version-stamp",
    why: "build numbers and beta badges are devtool fixtures, not landing page content",
    check(h, text) {
      return /\bv\d+\.\d+\.\d+\b|\bBUILD \d{3,}\b|\bINVITE[- ]ONLY\b/i.test(text) ? { detail: "version stamp" } : null;
    },
  },
  {
    id: "cta-wrap-risk",
    why: "a call to action that wraps to two lines at desktop reads as broken",
    check(h) {
      const m = [...h.matchAll(/class="btn lg"[^>]*>([^<]{1,120})</g)].map(x => x[1].trim());
      const longest = m.sort((a, b) => b.length - a.length)[0] || "";
      return longest.length > 34 ? { detail: `"${longest}" is ${longest.length} chars` } : null;
    },
    // keep the first clause, which is nearly always the actual instruction
    fix(h) {
      return outsideCode(h, t => t.replace(/>([^<>]{35,120})</g, (whole, label) => {
        if (!/^[A-Z]/.test(label.trim()) || label.includes(".")) return whole;
        const short = label.split(",")[0].trim();
        return short.length >= 6 && short.length <= 34 ? ">" + short + "<" : whole;
      }));
    },
  },
  {
    id: "emoji",
    why: "a model sprinkles emoji into copy; on a product page it reads as unserious",
    check(h, text) {
      const n = (text.match(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]/gu) || []).length;
      return n ? { count: n, detail: `${n} in visible copy` } : null;
    },
    fix: h => outsideCode(h, t => t
      .replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]/gu, "")
      .replace(/[ \t]{2,}/g, " ")
      .replace(/([A-Za-z0-9)\]"'])\s+([.,!?])/g, "$1$2")),
  },
  {
    id: "filler-verbs",
    why: "elevate, seamless, unleash and revolutionise are the words models reach for when they have nothing to say",
    check(h, text) {
      const bad = (text.match(/\b(elevate|seamless(ly)?|unleash|revolutionis|revolutioniz|next[- ]gen|supercharge|maximiz)\w*/gi) || []);
      return bad.length ? { count: bad.length, detail: bad.slice(0, 3).join(", ") } : null;
    },
  },
  // --- rules that require the presence of quality, not the absence of a tell ---
  {
    id: "ai-purple",
    why: "a saturated blue-violet is the colour every image and page model drifts to; it reads as generated before a word is read",
    check(h) {
      const bad = hexes(h).filter(x => {
        const { hue, sat, lum } = hsl(x);
        return hue >= 236 && hue <= 288 && sat > 0.42 && lum > 0.28 && lum < 0.78;
      });
      return bad.length ? { count: bad.length, detail: bad.slice(0, 3).join(", ") } : null;
    },
  },
  {
    id: "contrast",
    why: "body text below 4.5:1 is unreadable on a projector, and no model checks it",
    check(h) {
      const v = name => (h.match(new RegExp("--" + name + ":\\s*(#[0-9a-fA-F]{3,6})")) || [])[1];
      const pairs = [["ink", "bg"], ["muted", "bg"], ["ink", "surface"]];
      const bad = pairs
        .map(([f, b]) => [f, b, v(f) && v(b) ? ratio(v(f), v(b)) : null])
        .filter(([, , r]) => r !== null && r < 4.5)
        .map(([f, b, r]) => `${f} on ${b} is ${r.toFixed(1)}:1`);
      return bad.length ? { count: bad.length, detail: bad.join(", ") } : null;
    },
  },
  {
    id: "one-note",
    why: "a page whose hero, buttons and artwork are all one hue has no second voice; the accent has to do some work",
    check(h) {
      const p = (h.match(/--primary:\s*(#[0-9a-fA-F]{6})/) || [])[1];
      const a = (h.match(/--accent:\s*(#[0-9a-fA-F]{6})/) || [])[1];
      if (!p || !a) return null;
      const [P, A] = [hsl(p), hsl(a)];
      if (P.sat < 0.35 || A.sat < 0.35) return null;
      const d = Math.abs(P.hue - A.hue);
      const sep = Math.min(d, 360 - d);
      return sep < 24 ? { detail: `primary and accent are ${Math.round(sep)} degrees apart` } : null;
    },
  },
];

// --- colour helpers, so the rules above are arithmetic rather than opinion ---
const hexes = h => [...String(h).matchAll(/#[0-9a-fA-F]{6}\b/g)].map(m => m[0].toLowerCase());

function rgb(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function hsl(hex) {
  const [r, g, b] = rgb(hex).map(v => v / 255);
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let hue = 0;
  if (d) {
    if (max === r) hue = ((g - b) / d + 6) % 6;
    else if (max === g) hue = (b - r) / d + 2;
    else hue = (r - g) / d + 4;
    hue *= 60;
  }
  const lum = (max + min) / 2;
  return { hue, sat: d === 0 ? 0 : d / (1 - Math.abs(2 * lum - 1)), lum };
}

// WCAG relative luminance and contrast ratio
function rel(hex) {
  const [r, g, b] = rgb(hex).map(v => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
const ratio = (a, b) => {
  const [x, y] = [rel(a), rel(b)].sort((m, n) => n - m);
  return (x + 0.05) / (y + 0.05);
};

/** Runs every rule. Returns {passed, violations[], fixed} and a repaired page. */
function audit(html) {
  let h = html;
  const violations = [];
  let fixed = 0;

  for (const rule of RULES) {
    const text = String(h).replace(/<(script|style)[^>]*>[\s\S]*?<\/\1>/g, "").replace(/<[^>]+>/g, " ");
    const hit = rule.check(h, text);
    if (!hit) continue;
    if (rule.fix) {
      h = rule.fix(h);
      const after = rule.check(h, String(h).replace(/<(script|style)[^>]*>[\s\S]*?<\/\1>/g, "").replace(/<[^>]+>/g, " "));
      if (!after) { fixed++; continue; }
    }
    violations.push({ id: rule.id, why: rule.why, ...hit });
  }
  return { html: h, passed: violations.length === 0, violations, fixed };
}

module.exports = { audit, RULES };
