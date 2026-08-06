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

const RULES = [
  {
    id: "em-dash",
    why: "the em-dash is the single most reliable tell that a machine wrote the page",
    check(h, text) {
      const n = (text.match(/[—–]/g) || []).length + (h.match(/&[mn]dash;/g) || []).length;
      return n ? { count: n, detail: `${n} found` } : null;
    },
    // deterministic enough to repair rather than merely report
    fix: h => h.replace(/&mdash;/g, "&#183;").replace(/&ndash;/g, "-").replace(/—/g, " - ").replace(/–/g, "-"),
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
      return h.replace(/>([^<>]{35,120})</g, (whole, label) => {
        if (!/^[A-Z]/.test(label.trim()) || label.includes(".")) return whole;
        const short = label.split(",")[0].trim();
        return short.length >= 6 && short.length <= 34 ? ">" + short + "<" : whole;
      });
    },
  },
  {
    id: "emoji",
    why: "a model sprinkles emoji into copy; on a product page it reads as unserious",
    check(h, text) {
      const n = (text.match(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]/gu) || []).length;
      return n ? { count: n, detail: `${n} in visible copy` } : null;
    },
    fix: h => h.replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]/gu, "").replace(/[ \t]{2,}/g, " ").replace(/\s+([.,!?])/g, "$1"),
  },
  {
    id: "filler-verbs",
    why: "elevate, seamless, unleash and revolutionise are the words models reach for when they have nothing to say",
    check(h, text) {
      const bad = (text.match(/\b(elevate|seamless(ly)?|unleash|revolutionis|revolutioniz|next[- ]gen|supercharge)\w*/gi) || []);
      return bad.length ? { count: bad.length, detail: bad.slice(0, 3).join(", ") } : null;
    },
  },
];

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
