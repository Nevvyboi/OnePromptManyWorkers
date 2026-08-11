// Mock of the Live Build server. Pure Node, no framework.
// Implements the SAME endpoints and SSE protocol as the Java Spring Boot app,
// with canned agent outputs, so the whole audience -> stage experience can be
// driven and verified without Ollama or Java. Also a stage safety net.
const http = require("http");
const fs = require("fs");
const path = require("path");
const os = require("os");
const QRCode = require("qrcode");
const { renderPage } = require("./page");
const { audit } = require("./taste");

const PORT = process.env.PORT || 4000;
const STATIC = path.join(__dirname, "..", "src", "main", "resources", "static");

function lanIp() {
  for (const ifs of Object.values(os.networkInterfaces()))
    for (const i of ifs) if (i.family === "IPv4" && !i.internal) return i.address;
  return "localhost";
}
const AUDIENCE_URL = `http://${lanIp()}:${PORT}/`;

// Presenter key. The room is on your hotspot, and a dev WILL find /control.
// Without this, anyone can trigger a build on the projector.
const KEY = process.env.KEY || Math.random().toString(36).slice(2, 8);
const keyOk = url => url.searchParams.get("key") === KEY;

// ---------- state ----------
let ideas = [];       // {id, text, name, status}
let nextId = 1;
let running = null;
let submissionsOpen = true;   // the doors. you open and close them from /control
const clients = new Set();

// limits, matching the Java server
const MAX_QUEUE = 250, MAX_PER_IP = 8, COOLDOWN_MS = 6000;
const ipState = new Map();
// A wordlist is a speed bump, not a filter. It cannot catch something crude that
// uses no rude words ("a toilet app that scores your number twos"). The real
// guard is you: nothing reaches the projector until you press Run, and you can
// Hide anything you would rather not read out.
const BLOCK = new Set(["fuck","fucking","shit","shite","cunt","bitch","bastard","asshole","arsehole",
  "dick","prick","piss","damn","bollocks","wank","twat","slut","whore","nigger","faggot","retard","rape","nazi"]);
const CRUDE = ["toilet","poo","poop","turd","fart","genital","penis","vagina","boob","nude","naked","porn","sex","orgasm","nipple","butthole","anus"];
const blocked = t => t.toLowerCase().split(/[^a-z]+/).some(w => BLOCK.has(w));
// cut on a word boundary, never mid-word, so nothing reads as broken on stage
function clip(t, max = 120) {
  if (t.length <= max) return t;
  const cut = t.slice(0, max - 1);
  const sp = cut.lastIndexOf(" ");
  return (sp > max * 0.6 ? cut.slice(0, sp) : cut).replace(/[,;:.\s]+$/, "") + "…";
}
// not blocked, just flagged, so the presenter sees it before it ever runs
const isCrude = t => { const l = t.toLowerCase(); return CRUDE.some(w => l.includes(w)); };

function sse(res, event, data) {
  res.write(`event: ${event}\n`);
  res.write(`data: ${JSON.stringify(data)}\n\n`);
}
function broadcast(event, data) { for (const c of clients) sse(c, event, data); }
// a name is only ever shown if that person ticked the box
const shownName = i => (i.showName && i.name) ? i.name : "";
function queuePayload() {
  return { open: submissionsOpen, ideas: ideas.filter(i => !i.hidden).map(i => ({
    id: i.id, text: i.text, name: shownName(i), status: i.status,
    flagged: !!i.flagged, ms: i.ms || 0 })) };
}

// ---------- the crew (canned outputs; the Java server asks Qwen for these) ----------
const rnd = a => a[Math.floor(Math.random() * a.length)];
const pick = (a, seed) => a[Math.abs(hash(seed)) % a.length];
// FNV-1a plus a finaliser: similar sentences must land on different choices,
// otherwise a wall of twenty pages all says the same thing
const hash = s => {
  let h = 2166136261;
  for (const c of String(s)) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619); }
  h ^= h >>> 13; h = Math.imul(h, 0x5bd1e995); h ^= h >>> 15;
  return h >>> 0;
};

// Palette families, deliberately none of them the AI blue-violet default.
// Each is a named family so the harness can refuse to use the same one twice
// in a row: a wall of twenty pages must not read as one template recoloured.
const PALETTES = [
  { family:"forest",     bg:"#0c1410", surface:"#14201a", ink:"#eef5ef", muted:"#93a89a", primary:"#5fa777", accent:"#d8a53d", font:"-apple-system,Segoe UI,Roboto,sans-serif" },
  { family:"black-tan",  bg:"#111010", surface:"#1c1a18", ink:"#f4f1ec", muted:"#a49b8f", primary:"#c8925a", accent:"#5d7f8c", font:"-apple-system,Segoe UI,Roboto,sans-serif" },
  { family:"cobalt",     bg:"#fbfaf7", surface:"#ffffff", ink:"#12171f", muted:"#5f6773", primary:"#1a4fd6", accent:"#0f766e", font:"-apple-system,Segoe UI,Roboto,sans-serif" },
  { family:"terracotta", bg:"#f7f5f3", surface:"#ffffff", ink:"#1f1d1c", muted:"#6b6663", primary:"#b8532f", accent:"#3f5a68", font:"Georgia,'Times New Roman',serif" },
  { family:"olive",      bg:"#16170f", surface:"#20221a", ink:"#f2f2e6", muted:"#9ba18a", primary:"#8a9a3f", accent:"#c8562f", font:"-apple-system,Segoe UI,Roboto,sans-serif" },
  { family:"cold-lux",   bg:"#0e0f11", surface:"#191b1e", ink:"#f2f3f5", muted:"#9aa0a8", primary:"#c9ced6", accent:"#6fb0c4", font:"-apple-system,Segoe UI,Roboto,sans-serif" },
  { family:"mono-pop",   bg:"#faf9f8", surface:"#ffffff", ink:"#141414", muted:"#666361", primary:"#e0472c", accent:"#141414", font:"-apple-system,Segoe UI,Roboto,sans-serif" },
  { family:"ink-emerald",bg:"#0b0f0e", surface:"#141a19", ink:"#edf4f2", muted:"#8fa39e", primary:"#2fae7e", accent:"#e6c458", font:"Georgia,'Times New Roman',serif" },
];

// The harness, not the model, guarantees variety. It refuses the family used
// last, so consecutive pages never share a look.
// --- Freshness ledger -------------------------------------------------
// The taste guard reads one page in isolation, so it cannot see the thing an
// audience notices first: that the last four pages were the same page. This
// keeps a short memory per channel and refuses any value used recently. It is
// the harness, not the model, that enforces variety.
const RECENT = new Map();
const MEMORY = { palette: 6, layout: 2, art: 3, name: 5, headline: 5, subhead: 4, cta: 4, features: 3, badge: 3 };
const memKey = v => typeof v === "string" ? v : JSON.stringify(v).slice(0, 60);

function fresh(list, seed, channel) {
  const seen = RECENT.get(channel) || [];
  const pool = list.filter(x => !seen.includes(memKey(x)));
  const from = pool.length ? pool : list;
  const chosen = from[Math.abs(hash(seed)) % from.length];
  seen.push(memKey(chosen));
  const depth = Math.min(MEMORY[channel] || 3, list.length - 1);
  while (seen.length > depth) seen.shift();
  RECENT.set(channel, seen);
  return chosen;
}

// Picks a template slot rather than a rendered string. Keying on the finished
// sentence was a real bug: three pages opened "Stop thinking about" and the
// ledger saw three different strings. The room sees the template, not the string.
function slot(options, seed, channel) {
  const idx = options.map((_, i) => i);
  return options[fresh(idx, seed, channel)];
}

const pickPalette = idea => fresh(PALETTES, idea + "pal", "palette");
const LAYOUTS = ["split", "editorial", "band"];
const pickLayout = idea => fresh(LAYOUTS, idea + "lay", "layout");

const { ARCHETYPES, archetypeFor } = require("./mockup.js");

const words = idea => idea.trim().replace(/[.\s]+$/, "").replace(/^an?\s+/i, "");
const Cap = s => s.charAt(0).toUpperCase() + s.slice(1);

// The single most important helper here. An idea arrives as a whole sentence
// ("a braai app that tells you when load-shedding starts so you know when...").
// Splicing that into every slot produces garbage, so pull out the short noun
// phrase at the front and write around that plus the product name instead.
const STOP = new Set(["that","which","who","when","where","so","and","but","for","to","with",
                      "if","because","while","after","before","from","by","of","in","on","at",
  "i","you","we","they","he","she","it","my","our","your","their","me","us",
  "can","could","would","should","will","shall","may","might","do","does","did"]);
function subject(idea) {
  const w = words(idea).split(/\s+/);
  const out = [];
  for (const t of w) {
    if (STOP.has(t.toLowerCase().replace(/[^a-z]/g, ""))) break;
    out.push(t);
    if (out.length >= 4) break;
  }
  return (out.length ? out.join(" ") : words(idea).split(/\s+/).slice(0, 3).join(" "))
          .replace(/[^\w\s-]/g, "").trim();
}
// what the thing is about, in one or two words, for FAQ and feature lines
function topic(idea) {
  const s = subject(idea).split(/\s+/);
  const junk = new Set(["app","tool","tracker","finder","scanner","map","planner","timer","thing","system","kit"]);
  const core = s.filter(x => !junk.has(x.toLowerCase()));
  return (core.length ? core : s).slice(0, 2).join(" ");
}

// --- Namer: invents a product name ---
function makeName(idea) {
  const w = words(idea).split(/\s+/).filter(x => x.length > 3);
  const a = (w[0] || "Nova").replace(/[^a-zA-Z]/g, "");
  const b = (w[1] || w[0] || "Kit").replace(/[^a-zA-Z]/g, "");
  const forms = [
    Cap(a) + Cap(b), Cap(a) + " Works", Cap(a) + " Lane", Cap(b) + " Yard",
    Cap(a) + Cap(b.slice(0, 1)) + b.slice(1, 3), Cap(a) + " Co", Cap(b) + " Club", "The " + Cap(a),
  ];
  return { name: fresh(forms, idea, "name"), tagline: "for everyone who keeps forgetting" };
}

// --- Copywriter ---
function makeCopy(idea, productName) {
  const s = subject(idea), t = topic(idea), P = productName || Cap(s);
  return {
    badge: slot(["introducing", "new", "meet", "now live"], idea, "badge"),
    headline: slot([
      `${Cap(s)}, finally done properly.`,
      `${P}. The ${s} that remembers for you.`,
      `Never think about your ${t} again.`,
      `${Cap(s)} that actually works.`,
      `${P} handles your ${t}. You do not.`,
      `The ${s} you will actually keep using.`,
    ], idea + "h", "headline"),
    subhead: slot([
      `${P} watches your ${t} so you can get on with your day. Set it up once, in under a minute.`,
      `A ${s} with one job, done quietly in the background. No dashboards, no nagging, no setup wizard.`,
      `Everything you need for your ${t}, and nothing you don't. It works even when you forget it exists.`,
      `Built for the days you forget. ${P} keeps an eye on your ${t} and only speaks up when it matters.`,
      `One screen, one job. ${P} deals with your ${t} and stays out of the way the rest of the time.`,
      `The ${s} you set up in a minute and then stop thinking about. That is the whole pitch.`,
      `No accounts to chase, no group chat. ${P} handles your ${t} and tells you only what changed.`,
    ], idea + "sh", "subhead"),
    cta: slot(["Get early access", "Start free", "Join the waitlist", "Try it now",
                "See how it works", "Set it up now"], idea + "c", "cta"),
    features: slot([
      [ { title: "Effortless", body: `${P} handles the ${t} in the background. You get on with your day.` },
        { title: "Ready in seconds", body: "Open it and you are already going. No manual, no setup wizard." },
        { title: "Private by default", body: "Runs close to home. Your data stays where it belongs." } ],
      [ { title: "Always watching", body: `It keeps an eye on ${t} even when you have forgotten it exists.` },
        { title: "One tap", body: "The whole thing is a single screen. That is the entire product." },
        { title: "Works offline", body: "No signal, no problem. It catches up when you are back." } ],
      [ { title: "Quietly clever", body: `It learns your habits around ${t} and stops asking the obvious questions.` },
        { title: "Share it", body: "Bring in the family, the team, the whole street. Everyone stays in sync." },
        { title: "Free to start", body: "Use it properly before you decide whether it is worth anything." } ],
      [ { title: "No nagging", body: "It tells you once, at the right moment, and then leaves you alone." },
        { title: "Honest numbers", body: `See exactly what ${t} is costing you, in plain language.` },
        { title: "Yours to keep", body: "Export everything, any time. No hostage taking." } ],
    ], idea + "f", "features"),
  };
}

// --- Illustrator: picks an abstract hero artwork the browser draws as SVG ---
function makeArt(idea, palette) {
  return { kind: archetypeFor(idea, null), seed: Math.abs(hash(idea)) % 997,
           colors: [palette.primary, palette.accent] };
}

// --- Reviewer: one concrete polish, applied to the page ---
function makeReview(idea) {
  const s = words(idea);
  return {
    verdict: "solid, one thing to sharpen",
    field: "cta",
    value: slot([
      "Try it in 30 seconds", "Get it working today", "See it in action",
      "Set it up now", "Take it for a spin",
      "Start free, no card", "Let it remember", "Give it one job",
    ], idea + "r", "cta"),
    note: "the call to action was generic",
  };
}

// --- Pricer: three tiers, so the page has something to sell ---
function makePricing(idea, productName) {
  const s = topic(idea), P = productName || "it";
  const cheap = pick(["Free", "R0", "Free forever"], idea + "p0");
  const mid = pick(["R49", "R79", "R99", "R120"], idea + "p1");
  const top = pick(["R199", "R249", "R299", "R350"], idea + "p2");
  return [
    { name: "Starter", price: cheap, per: "forever", blurb: `Enough to see whether ${s} is really your problem.`,
      lines: ["One device", "The basics, properly", "No card needed"] },
    { name: "Everyday", price: mid, per: "per month", blurb: `The one most people pick. ${P} on all your things.`,
      lines: ["Everything in Starter", "Unlimited use", "Share with family", "Email support"], featured: true },
    { name: "For the street", price: top, per: "per month", blurb: "When the whole block wants in.",
      lines: ["Everything in Everyday", "Up to 25 people", "Priority support", "Export anything"] },
  ];
}

function makeFaq(idea) {
  const s = topic(idea);
  return [
    { q: `Does it really work for ${s}?`,
      a: "Yes, and it keeps working when you forget about it, which is the entire point." },
    { q: "Where does my data go?",
      a: "Nowhere. It stays on your device. There is no cloud account and nothing to leak." },
    { q: "What does it cost to start?",
      a: "Nothing. Use the free tier for as long as you like, then upgrade only if it earns it." },
  ];
}

// --- Skeptic ---
const skepticNote = idea => {
  const s = subject(idea);
  return pick([
    `Lovely idea. The hard part isn't building ${s}, it's getting the first ten people to care.`,
    `Great, but who actually pays for ${s}? Nail that before the logo.`,
    `Three people already pitched me ${s} this year. What's your unfair advantage?`,
    `Ship the ugly version this week. You'll learn more from that than another month of polish.`,
  ], idea + "s");
};

// --- Copywriter's rewrite after the skeptic ---
function reviseHeadline(idea, productName) {
  const s = subject(idea), t = topic(idea), first = t.split(/\s+/)[0] || "it";
  const P = productName || Cap(s);
  return slot([
    `${Cap(s)}. Zero effort, zero guilt.`,
    `${P} remembers your ${t}, so you don't have to.`,
    `${Cap(s)}, minus the guesswork.`,
    `The lazy way to handle your ${t}. On purpose.`,
    `Set it once. ${P} takes it from there.`,
    `${P}. Because you will forget again.`,
    `Stop thinking about your ${first}. ${P} has it.`,
    `${Cap(s)}, without the admin.`,
  ], idea + "rv", "headline");
}

// the whole crew's output for one idea, with no stage events (used for the
// quiet background builds that happen while the talk is going on)
function buildResult(idea) {
  const named = makeName(idea);
  const palette = pickPalette(idea);
  const copy = makeCopy(idea, named.name);
  const review = makeReview(idea);
  copy.cta = review.value;
  copy.headline = reviseHeadline(idea, named.name);
  return { product: named, palette, copy, art: makeArt(idea, palette), review, layout: pickLayout(idea),
           pricing: makePricing(idea, named.name), faq: makeFaq(idea), skeptic: skepticNote(idea) };
}

const sleep = ms => new Promise(r => setTimeout(r, ms));

const CREW = [
  { key: "namer",       label: "name"    },
  { key: "copywriter",  label: "copy"    },
  { key: "designer",    label: "design"  },
  { key: "illustrator", label: "art"     },
  { key: "pricer",      label: "price"   },
  { key: "builder",     label: "build"   },
  { key: "reviewer",    label: "review"  },
  { key: "skeptic",     label: "skeptic" },
];
const EDGES = [
  { from: "namer", to: "copywriter" },
  { from: "namer", to: "illustrator" },
  { from: "designer", to: "copywriter" },
  { from: "designer", to: "illustrator" },
  { from: "namer", to: "pricer" },
  { from: "copywriter", to: "builder" },
  { from: "pricer", to: "builder" },
  { from: "designer", to: "builder" },
  { from: "illustrator", to: "builder" },
  { from: "builder", to: "reviewer" },
  { from: "builder", to: "skeptic" },
  { from: "reviewer", to: "copywriter", feedback: true },
  { from: "skeptic", to: "copywriter", feedback: true },
];

// The stage run: seven agents, parallel starts, visible handoffs, two loops back.
async function runCrew(idea, name) {
  running = idea && idea.id;
  const t0 = Date.now(); const marks = {};
  const startAgent = k => marks[k] = Date.now();
  const doneAgent = k => { const ms = Date.now() - (marks[k] || t0); broadcast("timing", { key: k, ms }); return ms; };
  broadcast("run-start", { id: running, idea: idea.text, name });
  await sleep(420);
  broadcast("graph", { nodes: CREW, edges: EDGES });
  await sleep(380);

  // round one: three agents start together
  startAgent("namer"); broadcast("agent-state", { key: "namer", state: "working", note: "inventing a name…" });
  startAgent("designer"); broadcast("agent-state", { key: "designer", state: "working", note: "choosing a palette…" });
  startAgent("copywriter"); broadcast("agent-state", { key: "copywriter", state: "working", note: "drafting the hero…" });
  await sleep(1100);

  // the namer is quickest; it helps two others
  const named = makeName(idea.text);
  broadcast("worker-done", { key: "namer", payload: named });
  doneAgent("namer"); broadcast("agent-state", { key: "namer", state: "assisting", note: "done first, so it helps" });
  broadcast("flow", { from: "namer", to: "copywriter", what: "the name" });
  broadcast("flow", { from: "namer", to: "illustrator", what: "the name" });
  broadcast("flow", { from: "namer", to: "pricer", what: "the name" });
  await sleep(900);
  broadcast("agent-state", { key: "namer", state: "done" });

  // the designer lands next and helps too
  const palette = pickPalette(idea.text);
  broadcast("worker-done", { key: "designer", payload: palette });
  doneAgent("designer"); broadcast("agent-state", { key: "designer", state: "assisting", note: "hands the illustrator its colours" });
  broadcast("flow", { from: "designer", to: "copywriter", what: "tone hint" });
  broadcast("flow", { from: "designer", to: "illustrator", what: "palette" });
  await sleep(850);
  broadcast("agent-state", { key: "designer", state: "done" });

  // the illustrator can only start once it has a name and colours
  startAgent("illustrator"); broadcast("agent-state", { key: "illustrator", state: "working", note: "drawing the hero artwork…" });
  await sleep(1200);
  broadcast("worker-done", { key: "illustrator", payload: makeArt(idea.text, palette) });
  doneAgent("illustrator"); broadcast("agent-state", { key: "illustrator", state: "done", note: "artwork ready" });
  broadcast("flow", { from: "illustrator", to: "builder", what: "artwork" });

  // the pricer needs the name before it can sell anything
  startAgent("pricer"); broadcast("agent-state", { key: "pricer", state: "working", note: "working out three tiers…" });
  await sleep(950);
  broadcast("worker-done", { key: "pricer", payload: { pricing: makePricing(idea.text, named.name) } });
  doneAgent("pricer"); broadcast("agent-state", { key: "pricer", state: "done", note: "three tiers, one featured" });
  broadcast("flow", { from: "pricer", to: "builder", what: "pricing" });

  // the copy lands
  const copy = makeCopy(idea.text, named.name);
  broadcast("worker-done", { key: "copywriter", payload: copy });
  doneAgent("copywriter"); broadcast("agent-state", { key: "copywriter", state: "done", note: `hero: "${copy.headline}"` });
  broadcast("flow", { from: "copywriter", to: "builder", what: "copy" });
  broadcast("flow", { from: "designer", to: "builder", what: "palette" });

  startAgent("builder"); broadcast("agent-state", { key: "builder", state: "working", note: "assembling the page…" });
  await sleep(900);
  broadcast("worker-done", { key: "builder" });
  doneAgent("builder"); broadcast("agent-state", { key: "builder", state: "done", note: "page assembled" });

  // two checkers read the finished page at the same time
  broadcast("flow", { from: "builder", to: "reviewer", what: "the page" });
  broadcast("flow", { from: "builder", to: "skeptic", what: "the page" });
  startAgent("reviewer"); broadcast("agent-state", { key: "reviewer", state: "working", note: "checking it over…" });
  startAgent("skeptic"); broadcast("agent-state", { key: "skeptic", state: "working", note: "poking holes…" });
  await sleep(1150);

  // the reviewer sends back one concrete polish
  const review = makeReview(idea.text);
  broadcast("worker-done", { key: "reviewer", payload: review });
  doneAgent("reviewer"); broadcast("agent-state", { key: "reviewer", state: "done", note: review.note });
  broadcast("flow", { from: "reviewer", to: "copywriter", what: "polish the cta" });
  broadcast("agent-state", { key: "copywriter", state: "working", note: "taking the reviewer's note…" });
  await sleep(950);
  broadcast("revise", { field: "cta", value: review.value, by: "reviewer" });

  // and the skeptic sends back the harder question
  const note = skepticNote(idea.text);
  broadcast("worker-done", { key: "skeptic", payload: { note } });
  doneAgent("skeptic"); broadcast("agent-state", { key: "skeptic", state: "done" });
  broadcast("flow", { from: "skeptic", to: "copywriter", what: "critique" });
  await sleep(1000);
  broadcast("revise", { field: "headline", value: reviseHeadline(idea.text, named.name), by: "skeptic" });
  broadcast("flow", { from: "copywriter", to: "builder", what: "revised hero" });
  broadcast("agent-state", { key: "copywriter", state: "done", note: "rewritten. the web is settled" });

  const total = Date.now() - t0;
  broadcast("timing", { key: "total", ms: total });
  if (idea.mark) { idea.mark.status = "done"; idea.mark.result = buildResult(idea.text); idea.mark.ms = total; }
  running = null;
  broadcast("run-done", { id: idea.id });
  broadcast("queue", queuePayload());
  broadcast("gallery", galleryPayload());
}

// ---------- quiet background builds, while the talk is happening ----------
let backgroundOn = true;
async function backgroundTick() {
  if (!running && backgroundOn) {
    const next = ideas.find(i => !i.hidden && i.status === "new" && !i.result);
    if (next) {
      const bt = Date.now();
      next.status = "built";
      next.result = buildResult(next.text);
      next.ms = Date.now() - bt;
      broadcast("queue", queuePayload());
      broadcast("gallery", galleryPayload());
    }
  }
  setTimeout(backgroundTick, 2500);
}
setTimeout(backgroundTick, 3000);

function galleryPayload() {
  const done = ideas.filter(i => !i.hidden && i.result);
  return { total: done.length, items: done.map(i => ({
    id: i.id, name: shownName(i), idea: i.text, result: i.result, ms: i.ms || 0 })) };
}

// ---------- http ----------
const TYPES = { ".html":"text/html", ".js":"text/javascript", ".css":"text/css", ".png":"image/png", ".svg":"image/svg+xml" };
function serveStatic(res, file) {
  const p = path.join(STATIC, file);
  if (!p.startsWith(STATIC) || !fs.existsSync(p)) { res.writeHead(404); res.end("not found"); return; }
  res.writeHead(200, { "Content-Type": TYPES[path.extname(p)] || "application/octet-stream" });
  fs.createReadStream(p).pipe(res);
}
function body(req) { return new Promise(r => { let b=""; req.on("data",d=>b+=d); req.on("end",()=>r(b)); }); }
function json(res, code, obj) { res.writeHead(code, { "Content-Type":"application/json" }); res.end(JSON.stringify(obj)); }

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://x");
  const p = url.pathname;

  if (p === "/") return serveStatic(res, "index.html");
  if (p === "/stage") return serveStatic(res, "stage.html");
  if (p === "/control") {
    if (!keyOk(url)) { res.writeHead(403, { "Content-Type":"text/html" });
      return res.end("<body style='background:#0B0D12;color:#8A8FA0;font-family:system-ui;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center'><div><h2 style='color:#ECE7DE'>Presenter only</h2><p>Open the control URL printed in your terminal.</p></div></body>"); }
    return serveStatic(res, "control.html");
  }
  if (p === "/join") return serveStatic(res, "join.html");
  if (p === "/gallery") return serveStatic(res, "gallery.html");

  if (p === "/api/info") return json(res, 200, { audienceUrl: AUDIENCE_URL, mock: true, open: submissionsOpen });
  if (p === "/api/gallery") return json(res, 200, galleryPayload());

  // open or close the doors on submissions
  if (p === "/api/gate" && req.method === "POST") {
    if (!keyOk(url)) return json(res, 403, { ok:false, error:"presenter only" });
    submissionsOpen = url.searchParams.get("open") !== "false";
    broadcast("gate", { open: submissionsOpen });
    broadcast("queue", queuePayload());
    return json(res, 200, { ok:true, open: submissionsOpen });
  }

  // "is mine done yet?" for the phone that sent it
  if (p.startsWith("/api/mine/")) {
    const it = ideas.find(i => String(i.id) === p.split("/")[3]);
    return json(res, 200, { ready: !!(it && it.result && !it.hidden) });
  }

  // the real thing: a standalone landing page anyone can open, keep or email
  if (p.startsWith("/page/")) {
    const id = p.split("/")[2];
    const it = ideas.find(i => String(i.id) === id && i.result && !i.hidden);
    if (!it) { res.writeHead(404, {"Content-Type":"text/html"}); return res.end("<body style='font-family:system-ui;padding:3rem'>No page for that id yet.</body>"); }
    // nothing is served without passing the taste guard first
    const checked = audit(renderPage({ name: shownName(it), idea: it.text, result: it.result, ms: it.ms }));
    const html = checked.html;
    if (!checked.passed) console.log("  taste guard flagged:", checked.violations.map(v => v.id).join(", "));
    const slug = (it.result.product.name || "page").toLowerCase().replace(/[^a-z0-9]+/g, "-");
    const headers = { "Content-Type": "text/html; charset=utf-8" };
    if (url.searchParams.get("download") === "1") headers["Content-Disposition"] = `attachment; filename="${slug}.html"`;
    res.writeHead(200, headers);
    return res.end(html);
  }
  // the raw submission list is presenter-only: it is unvetted text with names on it
  if (p === "/api/queue") {
    if (!keyOk(url)) return json(res, 403, { ideas: [], error: "presenter only" });
    return json(res, 200, queuePayload());
  }

  if (p.startsWith("/api/hide/") && req.method === "POST") {
    if (!keyOk(url)) return json(res, 403, { ok:false, error:"presenter only" });
    const id = p.split("/").pop();
    const it = ideas.find(i => String(i.id) === id);
    if (it) it.hidden = true;
    broadcast("queue", queuePayload());
    return json(res, 200, { ok:true });
  }

  if (p === "/api/qr") {
    const png = await QRCode.toBuffer(AUDIENCE_URL, { margin: 1, width: 480, color: { dark:"#0B0D12", light:"#FFFFFF" } });
    res.writeHead(200, { "Content-Type":"image/png" }); return res.end(png);
  }

  if (p === "/api/events") {
    res.writeHead(200, { "Content-Type":"text/event-stream", "Cache-Control":"no-cache", "Connection":"keep-alive" });
    res.write("\n"); clients.add(res);
    sse(res, "info", { mock:true, tally: ideas.length });
    sse(res, "queue", queuePayload());
    sse(res, "gallery", galleryPayload());
    sse(res, "gate", { open: submissionsOpen });
    req.on("close", () => clients.delete(res));
    return;
  }

  if (p === "/api/submit" && req.method === "POST") {
    const d = JSON.parse((await body(req)) || "{}");
    // mirror the Java server: trust a forwarded address if present
    const fwd = req.headers["x-forwarded-for"];
    const ip = (fwd ? String(fwd).split(",")[0].trim() : req.socket.remoteAddress) || "?";
    const text = clip((d.text || "").trim());
    if (!submissionsOpen) return json(res, 200, { ok:false, closed:true, error:"Submissions are closed. Watch the big screen." });
    if (text.length < 3) return json(res, 200, { ok:false, error:"Give it a few more words." });
    if (blocked(text)) return json(res, 200, { ok:false, error:"Let's keep it friendly." });
    if (ideas.length >= MAX_QUEUE) return json(res, 200, { ok:false, error:"The queue is full for now. Thanks!" });
    if (ideas.some(i => i.text.toLowerCase() === text.toLowerCase())) return json(res, 200, { ok:false, error:"Someone already sent that one." });
    const now = Date.now();
    const st = ipState.get(ip) || { last: 0, count: 0 };
    if (now - st.last < COOLDOWN_MS) {
      const wait = Math.ceil((COOLDOWN_MS - (now - st.last)) / 1000);
      return json(res, 200, { ok:false, retryAfter: wait, error:`One at a time. Try again in ${wait} second${wait===1?"":"s"}.` });
    }
    if (st.count >= MAX_PER_IP) return json(res, 200, { ok:false, error:"That's plenty from you. Let others have a go." });
    st.last = now; st.count++; ipState.set(ip, st);
    const idea = { id: nextId++, text, name: (d.name||"").trim().slice(0,24),
                   showName: d.showName === true, status:"new", flagged: isCrude(text) };
    ideas.push(idea);
    broadcast("queue", queuePayload());
    broadcast("tally", { total: ideas.length });
    // hand back exactly what we stored, so the phone can never show something
    // different from what the room will see
    return json(res, 200, { ok:true, id: idea.id, position: ideas.length, stored: text, mock:true });
  }

  if (p.startsWith("/api/run/") && req.method === "POST") {
    if (!keyOk(url)) return json(res, 403, { ok:false, error:"presenter only" });
    if (running) return json(res, 200, { ok:false, error:"already running" });
    const id = p.split("/").pop();
    let idea;
    if (id === "demo") idea = { id:"demo", text:"an app that waters your plants when you forget", name:"the house crew" };
    else { const found = ideas.find(i=>String(i.id)===id && !i.hidden); if(found) idea = { id:found.id, text:found.text, name:found.name, mark:found }; }
    if (!idea) return json(res, 200, { ok:false, error:"not found" });
    json(res, 200, { ok:true });
    runCrew(idea, idea.name).catch(console.error);
    return;
  }

  serveStatic(res, p.slice(1));
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`\n  Live Build (mock) running`);
  console.log(`  audience : ${AUDIENCE_URL}          <- the QR points here`);
  console.log(`  stage    : http://localhost:${PORT}/stage           <- the projector`);
  console.log(`  control  : http://localhost:${PORT}/control?key=${KEY}  <- YOU (keep this one private)\n`);
});
