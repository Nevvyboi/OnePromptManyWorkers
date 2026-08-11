// Product mockups.
//
// The illustrator used to draw abstract patterns: rings, waves, a field of dots.
// Every page got decoration, and decoration is its own kind of slop. A real
// landing page shows the product. There is no image model here and nothing may
// leave the laptop, so the product is drawn as SVG: a small, honest interface
// for whatever the idea actually is.
//
// Nine archetypes cover essentially any idea a room will type in. The Illustrator
// agent picks one; if it picks badly, keyword rules pick for it.

const ARCHETYPES = [
  "calendar", "timer", "ledger", "chart", "checklist", "route", "inbox", "catalog", "dashboard",
];

// Every archetype's vocabulary. An idea is scored against all of them and the
// strongest signal wins, because a first-match rule breaks the moment two
// signals appear: "a braai timer that syncs with the load-shedding schedule"
// matched "schedule" and drew a calendar for something that is plainly a timer.
const VOCAB = {
  calendar:  /\b(book|booking|booked|reserve|reservation|court|courts|venue|venues|slot|slots|appointment|appointments|calendar|shift|shifts|availability|diary|table|schedule|scheduling)\b/gi,
  timer:     /\b(timer|countdown|alarm|stopwatch|remind|reminder|remember|forget|forgets|minutes|braai|barbecue|cook|cooking|bake|baking|brew|brewing|oven|boil|water|feed|interval|every day|daily)\b/gi,
  ledger:    /\b(money|payment|payments|pay|paid|save|saving|savings|budget|stokvel|invoice|expense|expenses|split|splitting|bill|bills|cost|costs|wallet|fee|fees|subscription|rand|debt|contribution|owe)\b/gi,
  chart:     /\b(monitor|monitoring|sensor|temperature|pressure|level|levels|usage|using|meter|geyser|alert|alerts|threshold|leak|voltage|electricity|power|kwh|watt|consumption|reading|readings|detect|warns?|disk|disks|storage|capacity|space|full|filling|quota|bandwidth|cpu|memory|server|servers|battery|fuel|stock level|spike|trend|fridge)\b/gi,
  checklist: /\b(todo|checklist|task|tasks|chore|chores|rota|packing|grocery|groceries|steps|tick|done|complete|habit|routine|revision|assign|turn|whose|list|lists|reading|read)\b/gi,
  route:     /\b(route|routes|lift|lifts|commute|travel|trip|delivery|deliver|drive|driver|driving|map|maps|pickup|dropoff|taxi|traffic|journey|distance|walk|cycle)\b/gi,
  inbox:     /\b(chat|message|messages|inbox|mail|email|group|community|forum|thread|notify|notification|social|neighbour|neighbor|announce|reply|dm|post|posts)\b/gi,
  catalog:   /\b(shop|store|order|orders|menu|buy|cart|product|products|catalogue|catalog|coffee|beans|food|meal|meals|drink|drinks|stock|inventory|browse|ship|ships|shipped)\b/gi,
  dashboard: /\b(track|tracker|tracking|dashboard|report|reports|analytic|analytics|metric|metrics|stat|stats|progress|score|scores|overview|insight|insights|leaderboard)\b/gi,
};

// A few words settle it on their own, and outweigh a pile of vague matches.
const STRONG = {
  timer:     /\b(timer|countdown|stopwatch|alarm|remind|remember)\b/i,
  calendar:  /\b(book|booking|reserve|reservation|appointments?|court)\b/i,
  ledger:    /\b(stokvels?|invoices?|budgets?|savings|payments?|expenses?)\b/i,
  chart:     /\b(monitors?|sensors?|thresholds?|leaks?|electricity|usage)\b/i,
  route:     /\b(routes?|commutes?|deliver(y|ies)|taxis?|lift club)\b/i,
  inbox:     /\b(chat|inbox|message)\b/i,
  catalog:   /\b(shop|store|menu|cart|subscription)\b/i,
  checklist: /\b(checklists?|todos?|chores?|rotas?)\b/i,
  dashboard: /\b(dashboard|leaderboard|analytics)\b/i,
};

/** Picks the archetype for an idea. The model may propose; this decides. */
// Phrases where a keyword means something else entirely: "my book club" is not
// a booking, and "a reading list" is not a diary.
const DECOYS = [
  [/\breading list\b/gi, "checklist"],       // a list, not a diary
  [/\bbook (club|shop|store)\b/gi, "club"],  // "book" here is a noun
  [/\bbookshop\b|\bbookstore\b/gi, "shop"],
  [/\btable tennis\b/gi, "tennis"],          // not a table to reserve
];
const undecoy = t => DECOYS.reduce((s, [re, to]) => s.replace(re, to), t);

function archetypeFor(idea, proposed) {
  const p = String(proposed || "").trim().toLowerCase();
  const t = undecoy(String(idea || ""));
  let best = "dashboard", top = 0;
  for (const kind of ARCHETYPES) {
    const hits = (t.match(VOCAB[kind]) || []).length;
    if (!hits) continue;
    // a decisive word is worth more than a couple of vague ones
    const score = hits + (STRONG[kind] && STRONG[kind].test(t) ? 2.5 : 0);
    if (score > top) { top = score; best = kind; }
  }
  // The model's word is only taken when the idea's own words are not decisive.
  // Given "a stokvel tracker for the whole street", Qwen answered "dashboard" and
  // the page showed a stat panel for something that is plainly a ledger.
  if (top >= 3) return best;
  if (ARCHETYPES.includes(p)) return p;
  return best;
}

// --- small drawing helpers, so each archetype reads as one idea not a pile of tags ---

const rect = (x, y, w, h, r, fill, extra = "") =>
  `<rect x="${n(x)}" y="${n(y)}" width="${n(Math.max(0, w))}" height="${n(Math.max(0, h))}" rx="${n(r)}" fill="${fill}"${extra}/>`;

const line = (x1, y1, x2, y2, stroke, w = 1, extra = "") =>
  `<line x1="${n(x1)}" y1="${n(y1)}" x2="${n(x2)}" y2="${n(y2)}" stroke="${stroke}" stroke-width="${n(w)}"${extra}/>`;

const text = (x, y, s, fill, size, weight = 500, anchor = "start") =>
  `<text x="${n(x)}" y="${n(y)}" fill="${fill}" font-size="${n(size)}" font-weight="${weight}" text-anchor="${anchor}" font-family="ui-sans-serif,system-ui,-apple-system,Segoe UI,Roboto,sans-serif">${esc(s)}</text>`;

const n = v => (Math.abs(v - Math.round(v)) < 1e-9 ? String(Math.round(v)) : v.toFixed(2));
const esc = s => String(s).replace(/[&<>]/g, m => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[m]));

/** A placeholder text run. Mockup convention, and it never renders as lorem. */
const bar = (x, y, w, c, h = 6) => rect(x, y, w, h, h / 2, c, ' opacity=".28"');

/**
 * Draws the product.
 *
 * @param kind      one of ARCHETYPES
 * @param p         the palette, so the mockup is the same product as the page
 * @param seed      keeps one idea's mockup stable across rebuilds
 * @param W,H       the canvas the caller has room for
 */
function mockup(kind, p, seed, W, H) {
  const r = i => ((seed * (i + 3) * 9301 + 49297) % 233280) / 233280;
  const ink = p.ink, muted = p.muted, prim = p.primary, acc = p.accent, surf = p.surface;
  const pad = Math.round(Math.min(W, H) * 0.075);

  // the window the interface sits in
  const barH = Math.max(26, H * 0.1);
  let s = rect(0, 0, W, H, 14, surf)
        + rect(0, 0, W, barH, 14, ink, ' opacity=".05"')
        + line(0, barH, W, barH, ink, 1, ' opacity=".1"')
        + rect(pad, barH / 2 - 4, Math.min(120, W * 0.28), 8, 4, ink, ' opacity=".18"')
        + rect(W - pad - 34, barH / 2 - 5, 34, 10, 5, prim, ' opacity=".55"');

  const x0 = pad, y0 = barH + pad, w = W - pad * 2, h = H - barH - pad * 2;

  switch (kind) {
    case "calendar": {
      const cols = 7, rows = 4;
      const cw = w / cols, chh = (h - 18) / rows;
      const days = ["M", "T", "W", "T", "F", "S", "S"];
      for (let c = 0; c < cols; c++) s += text(x0 + c * cw + cw / 2, y0 + 9, days[c], muted, Math.max(8, cw * 0.3), 600, "middle");
      const taken = new Set([3, 9, 10, 16, 22, 23].map(k => k % (cols * rows)));
      const mine = 10 % (cols * rows);
      for (let i = 0; i < cols * rows; i++) {
        const cx = x0 + (i % cols) * cw, cy = y0 + 18 + Math.floor(i / cols) * chh;
        const bw = cw - 4, bh = chh - 4;
        if (i === mine) s += rect(cx, cy, bw, bh, 5, prim) + text(cx + bw / 2, cy + bh / 2 + 3, "✓", surf, bh * 0.5, 700, "middle");
        else if (taken.has(i)) s += rect(cx, cy, bw, bh, 5, acc, ' opacity=".38"');
        else s += rect(cx, cy, bw, bh, 5, ink, ' opacity=".06"');
      }
      break;
    }

    case "timer": {
      const cx = x0 + w / 2, cy = y0 + h * 0.44, R = Math.min(w, h) * 0.31;
      const frac = 0.68, a0 = -Math.PI / 2, a1 = a0 + frac * Math.PI * 2;
      const big = frac > 0.5 ? 1 : 0;
      s += `<circle cx="${n(cx)}" cy="${n(cy)}" r="${n(R)}" fill="none" stroke="${ink}" stroke-opacity=".12" stroke-width="${n(R * 0.17)}"/>`
         + `<path d="M ${n(cx + Math.cos(a0) * R)} ${n(cy + Math.sin(a0) * R)} A ${n(R)} ${n(R)} 0 ${big} 1 ${n(cx + Math.cos(a1) * R)} ${n(cy + Math.sin(a1) * R)}" fill="none" stroke="${prim}" stroke-width="${n(R * 0.17)}" stroke-linecap="round"/>`
         + text(cx, cy + R * 0.16, "12:40", ink, R * 0.46, 700, "middle")
         + text(cx, cy + R * 0.52, "remaining", muted, R * 0.2, 500, "middle");
      const bw = w * 0.3, by = y0 + h - 22;
      s += rect(cx - bw - 5, by, bw, 20, 10, prim)
         + rect(cx + 5, by, bw, 20, 10, ink, ' opacity=".1"');
      break;
    }

    case "ledger": {
      const rowH = Math.min(26, h / 5.4);
      const amounts = ["R 250", "R 250", "R 500", "R 250"];
      for (let i = 0; i < 4; i++) {
        const y = y0 + i * rowH;
        s += `<circle cx="${n(x0 + 9)}" cy="${n(y + rowH / 2)}" r="7" fill="${i === 2 ? prim : ink}" fill-opacity="${i === 2 ? 1 : 0.1}"/>`
           + bar(x0 + 24, y + rowH / 2 - 3, w * (0.3 + r(i) * 0.22), ink)
           + text(x0 + w, y + rowH / 2 + 4, amounts[i], i === 2 ? prim : ink, rowH * 0.42, 600, "end");
        if (i < 3) s += line(x0, y + rowH, x0 + w, y + rowH, ink, 1, ' opacity=".08"');
      }
      const ty = y0 + 4 * rowH + 12, th = Math.max(20, h - (4 * rowH) - 18);
      s += rect(x0, ty, w, th, 9, prim, ' opacity=".1"')
         + text(x0 + 12, ty + th / 2 + 5, "Total this month", muted, th * 0.3, 500)
         + text(x0 + w - 12, ty + th / 2 + 5, "R 1 250", ink, th * 0.36, 700, "end");
      break;
    }

    case "chart": {
      const gh = h * 0.72, gy = y0 + 4;
      for (let i = 0; i <= 3; i++) s += line(x0, gy + (gh / 3) * i, x0 + w, gy + (gh / 3) * i, ink, 1, ' opacity=".08"');
      const pts = Array.from({ length: 11 }, (_, i) => {
        const x = x0 + (w / 10) * i;
        const v = 0.52 + Math.sin(i * 0.9 + seed) * 0.22 + (r(i) - 0.5) * 0.14;
        return [x, gy + gh - Math.max(0.06, Math.min(0.94, v)) * gh];
      });
      const thresh = gy + gh * 0.24;
      s += line(x0, thresh, x0 + w, thresh, acc, 1.5, ' stroke-dasharray="5 4" opacity=".8"')
         + `<path d="M ${pts.map(q => n(q[0]) + " " + n(q[1])).join(" L ")}" fill="none" stroke="${prim}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>`
         + `<path d="M ${pts.map(q => n(q[0]) + " " + n(q[1])).join(" L ")} L ${n(x0 + w)} ${n(gy + gh)} L ${n(x0)} ${n(gy + gh)} Z" fill="${prim}" opacity=".1"/>`;
      const hot = pts.reduce((a, b) => (b[1] < a[1] ? b : a));
      s += `<circle cx="${n(hot[0])}" cy="${n(hot[1])}" r="5" fill="${surf}" stroke="${prim}" stroke-width="2.6"/>`;
      const ly = gy + gh + 14, lh = Math.max(16, h - gh - 20);
      s += rect(x0, ly, w, lh, 8, acc, ' opacity=".14"')
         + `<circle cx="${n(x0 + 14)}" cy="${n(ly + lh / 2)}" r="4" fill="${acc}"/>`
         + bar(x0 + 26, ly + lh / 2 - 3, w * 0.46, ink);
      break;
    }

    case "checklist": {
      const rows = 4, rowH = Math.min(30, (h - 26) / rows);
      for (let i = 0; i < rows; i++) {
        const y = y0 + i * rowH, done = i < 2, bs = rowH * 0.5;
        s += done
          ? rect(x0, y + (rowH - bs) / 2, bs, bs, 5, prim) + text(x0 + bs / 2, y + rowH / 2 + bs * 0.2, "\u2713", surf, bs * 0.72, 700, "middle")
          : rect(x0, y + (rowH - bs) / 2, bs, bs, 5, "none", ` stroke="${ink}" stroke-opacity=".22" stroke-width="1.6"`);
        const bw = w * (0.36 + r(i) * 0.3);
        s += bar(x0 + bs + 12, y + rowH / 2 - 3, bw, ink, done ? 6 : 7);
        if (done) s += line(x0 + bs + 12, y + rowH / 2, x0 + bs + 12 + bw, y + rowH / 2, ink, 1.4, ' opacity=".32"');
        // whose turn it is, which is the whole point of a rota
        s += `<circle cx="${n(x0 + w - 11)}" cy="${n(y + rowH / 2)}" r="9" fill="${i % 2 ? acc : prim}" opacity="${done ? 0.35 : 0.85}"/>`;
      }
      const ay = y0 + rows * rowH + 6, ah = Math.max(20, Math.min(26, h - rows * rowH - 8));
      s += rect(x0, ay, w * 0.36, ah, ah / 2, prim, ' opacity=".14"')
         + text(x0 + 14, ay + ah / 2 + ah * 0.17, "+ Add a task", prim, ah * 0.42, 600);
      break;
    }

    case "route": {
      // a bare curve reads as a chart, so this gets the things that make a map
      // legible: a faint street grid underneath, a labelled start and end, and
      // stops big enough to count.
      for (let gx = 1; gx < 5; gx++) s += line(x0 + (w / 5) * gx, y0, x0 + (w / 5) * gx, y0 + h, ink, 1, ' opacity=".07"');
      for (let gy = 1; gy < 4; gy++) s += line(x0, y0 + (h / 4) * gy, x0 + w, y0 + (h / 4) * gy, ink, 1, ' opacity=".07"');
      s += rect(x0 + w * 0.06, y0 + h * 0.08, w * 0.2, h * 0.22, 6, ink, ' opacity=".05"')
         + rect(x0 + w * 0.62, y0 + h * 0.62, w * 0.26, h * 0.28, 6, ink, ' opacity=".05"');

      const stops = [[0.1, 0.78], [0.33, 0.44], [0.58, 0.62], [0.84, 0.26]];
      const pts = stops.map(([fx, fy]) => [x0 + fx * w, y0 + fy * h]);
      let d = `M ${n(pts[0][0])} ${n(pts[0][1])}`;
      for (let i = 1; i < pts.length; i++) {
        const [px, py] = pts[i - 1], [cx, cy] = pts[i];
        d += ` C ${n(px + (cx - px) * 0.55)} ${n(py)} ${n(px + (cx - px) * 0.45)} ${n(cy)} ${n(cx)} ${n(cy)}`;
      }
      s += `<path d="${d}" fill="none" stroke="${prim}" stroke-width="${n(Math.max(5, h * 0.022))}" stroke-linecap="round" stroke-linejoin="round" opacity=".28"/>`
         + `<path d="${d}" fill="none" stroke="${prim}" stroke-width="${n(Math.max(2.5, h * 0.011))}" stroke-linecap="round" stroke-linejoin="round"/>`;

      pts.forEach(([cx, cy], i) => {
        const last = i === pts.length - 1, first = i === 0;
        const rr = first || last ? Math.max(8, h * 0.036) : Math.max(5, h * 0.022);
        if (last) {
          // the destination gets a pin, which is what says "map" at a glance
          s += `<path d="M ${n(cx)} ${n(cy + rr * 1.5)} C ${n(cx - rr * 1.3)} ${n(cy + rr * 0.2)} ${n(cx - rr)} ${n(cy - rr)} ${n(cx)} ${n(cy - rr)} C ${n(cx + rr)} ${n(cy - rr)} ${n(cx + rr * 1.3)} ${n(cy + rr * 0.2)} ${n(cx)} ${n(cy + rr * 1.5)} Z" fill="${prim}"/>`
             + `<circle cx="${n(cx)}" cy="${n(cy - rr * 0.15)}" r="${n(rr * 0.36)}" fill="${surf}"/>`;
        } else {
          s += `<circle cx="${n(cx)}" cy="${n(cy)}" r="${n(rr)}" fill="${surf}" stroke="${first ? prim : ink}" stroke-opacity="${first ? 1 : 0.4}" stroke-width="${n(Math.max(2, h * 0.009))}"/>`;
          if (first) s += `<circle cx="${n(cx)}" cy="${n(cy)}" r="${n(rr * 0.42)}" fill="${prim}"/>`;
        }
      });

      const ph = Math.max(22, h * 0.13), pw = Math.min(w * 0.42, 150);
      s += rect(x0, y0 + h - ph, pw, ph, ph / 2, surf)
         + rect(x0, y0 + h - ph, pw, ph, ph / 2, ink, ' opacity=".07"')
         + text(x0 + 12, y0 + h - ph / 2 + ph * 0.18, "18 min", ink, ph * 0.42, 700)
         + text(x0 + 12 + ph * 1.9, y0 + h - ph / 2 + ph * 0.16, "4 stops", muted, ph * 0.32, 500);
      break;
    }

    case "inbox": {
      const rows = [[0.62, 0], [0.44, 1], [0.72, 0], [0.5, 1]];
      const rowH = Math.min(30, h / 4.5);
      rows.forEach(([fw, mine], i) => {
        const y = y0 + i * rowH, bw = w * fw, bx = mine ? x0 + w - bw : x0;
        s += rect(bx, y, bw, rowH - 7, 9, mine ? prim : ink, mine ? ' opacity=".9"' : ' opacity=".07"')
           + bar(bx + 10, y + (rowH - 7) / 2 - 3, bw - 24, mine ? surf : ink, 5);
      });
      const cy = y0 + 4 * rowH + 4, chh = Math.max(20, h - 4 * rowH - 8);
      s += rect(x0, cy, w, chh, chh / 2, ink, ' opacity=".06"')
         + bar(x0 + 14, cy + chh / 2 - 3, w * 0.4, ink)
         + `<circle cx="${n(x0 + w - chh / 2 - 4)}" cy="${n(cy + chh / 2)}" r="${n(chh * 0.3)}" fill="${prim}"/>`;
      break;
    }

    case "catalog": {
      const cols = 3, gap = 10;
      const cw = (w - gap * (cols - 1)) / cols, chh = Math.min(h * 0.6, cw * 1.1);
      for (let i = 0; i < cols; i++) {
        const cx = x0 + i * (cw + gap);
        s += rect(cx, y0, cw, chh, 10, ink, ' opacity=".06"')
           + rect(cx, y0, cw, chh * 0.58, 10, i === 1 ? prim : acc, i === 1 ? ' opacity=".85"' : ' opacity=".4"')
           + bar(cx + 8, y0 + chh * 0.68, cw - 26, ink)
           + bar(cx + 8, y0 + chh * 0.82, cw * 0.4, ink, 5);
      }
      const by = y0 + chh + 12, bh = Math.max(20, h - chh - 16);
      s += rect(x0, by, w, bh, bh / 2, ink, ' opacity=".06"')
         + bar(x0 + 14, by + bh / 2 - 3, w * 0.34, ink)
         + rect(x0 + w - 78, by + 4, 70, bh - 8, (bh - 8) / 2, prim);
      break;
    }

    default: { // dashboard
      const gap = 10, cols = 3;
      const cw = (w - gap * (cols - 1)) / cols, chh = h * 0.36;
      const vals = ["128", "94%", "12"];
      for (let i = 0; i < cols; i++) {
        const cx = x0 + i * (cw + gap);
        s += rect(cx, y0, cw, chh, 10, i === 0 ? prim : ink, i === 0 ? ' opacity=".12"' : ' opacity=".06"')
           + text(cx + 10, y0 + chh * 0.56, vals[i], i === 0 ? prim : ink, chh * 0.42, 700)
           + bar(cx + 10, y0 + chh * 0.74, cw * 0.5, ink, 5);
      }
      const gy = y0 + chh + 12, gh = h - chh - 16;
      s += rect(x0, gy, w, gh, 10, ink, ' opacity=".05"');
      const bars = 12, bw = (w - 24) / bars;
      for (let i = 0; i < bars; i++) {
        const bh2 = (gh - 20) * (0.28 + Math.abs(Math.sin(i * 0.8 + seed)) * 0.66);
        s += rect(x0 + 12 + i * bw, gy + gh - 10 - bh2, bw - 4, bh2, 3, i === 8 ? prim : acc, i === 8 ? "" : ' opacity=".45"');
      }
      break;
    }
  }
  return s;
}

/** The finished SVG, sized to whatever box the layout gives it. */
function mockupSvg(art, palette, layout) {
  const kind = art && art.kind ? art.kind : "dashboard";
  const seed = art && art.seed ? art.seed : 7;
  const wide = layout === "band" || layout === "editorial";
  const W = wide ? 900 : 640, H = wide ? 340 : 480;
  return `<svg class="field" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid meet" role="img" aria-label="Product interface preview">`
       + mockup(kind, palette, seed, W, H) + "</svg>";
}

module.exports = { ARCHETYPES, archetypeFor, mockupSvg };
