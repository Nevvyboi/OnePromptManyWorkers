// Mock of the Live Build server. Pure Node, no framework.
// Implements the SAME endpoints and SSE protocol as the Java Spring Boot app,
// with canned agent outputs, so the whole audience -> stage experience can be
// driven and verified without Ollama or Java. Also a stage safety net.
const http = require("http");
const fs = require("fs");
const path = require("path");
const os = require("os");
const QRCode = require("qrcode");

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
function queuePayload() { return { ideas: ideas.filter(i => !i.hidden).map(i => ({ id: i.id, text: i.text, name: i.name, status: i.status, flagged: !!i.flagged })) }; }

// ---------- canned crew ----------
const rnd = a => a[Math.floor(Math.random() * a.length)];
const PALETTES = [
  { bg:"#0f1220", surface:"#191d2e", ink:"#f4f5fb", muted:"#a6adc4", primary:"#7c9cff", accent:"#22d3ee", font:"-apple-system,Segoe UI,Roboto,sans-serif" },
  { bg:"#fffaf3", surface:"#ffffff", ink:"#1c1a17", muted:"#7a736a", primary:"#e8622c", accent:"#f2b705", font:"Georgia,'Times New Roman',serif" },
  { bg:"#0b1a14", surface:"#12241b", ink:"#eafff5", muted:"#8fb3a3", primary:"#34d399", accent:"#a3e635", font:"-apple-system,Segoe UI,Roboto,sans-serif" },
  { bg:"#faf7ff", surface:"#ffffff", ink:"#1e1633", muted:"#6b6486", primary:"#7c3aed", accent:"#ec4899", font:"-apple-system,Segoe UI,Roboto,sans-serif" },
  { bg:"#071018", surface:"#0f1c26", ink:"#eaf6ff", muted:"#93b2c6", primary:"#38bdf8", accent:"#f59e0b", font:"-apple-system,Segoe UI,Roboto,sans-serif" },
];
let paletteIdx = 0;

function makeCopy(idea) {
  const clean = idea.trim().replace(/[.\s]+$/, "");
  const stripped = clean.replace(/^an?\s+/i, "");
  const Title = clean.charAt(0).toUpperCase() + clean.slice(1);
  return {
    badge: rnd(["introducing", "new", "meet", "now live"]),
    headline: rnd([`${Title}.`, `Finally, ${stripped}.`, `Meet ${stripped}.`, `${Title}, done right.`]),
    subhead: `A delightfully simple way to ${stripped}. No setup, no nonsense, working in seconds.`,
    cta: rnd(["Get early access", "Start free", "Join the waitlist", "Try it now"]),
    features: [
      { title: "Effortless", body: `It just works. It handles the hard part of ${stripped} so you don't have to.` },
      { title: "Yours in seconds", body: "Open it and you're already going. Zero learning curve, no manual." },
      { title: "Private by default", body: "Runs close to home. Your data stays where it belongs." },
    ],
  };
}
const skepticNote = idea => {
  const s = idea.trim().replace(/[.\s]+$/, "").replace(/^an?\s+/i, "");
  return rnd([
    `Lovely idea. The hard part isn't building ${s}, it's getting the first ten people to care.`,
    `Great, but who actually pays for ${s}? Nail that before the logo.`,
    `Three people already pitched me ${s} this year. What's your unfair advantage?`,
    `Ship the ugly version this week. You'll learn more from that than another month of polish.`,
  ]);
};

const cap = s => s.charAt(0).toUpperCase() + s.slice(1);
function reviseHeadline(idea) {
  const s = idea.trim().replace(/[.\s]+$/, "").replace(/^an?\s+/i, "");
  return rnd([
    `${cap(s)}. Zero effort, zero guilt.`,
    `Set it up once. Never think about it again.`,
    `${cap(s)}, minus the guesswork.`,
    `The lazy way to ${s}. On purpose.`,
  ]);
}

const sleep = ms => new Promise(r => setTimeout(r, ms));

// The spider net: agents work in parallel, and when one finishes it helps
// another. The Skeptic's critique loops BACK to the Copywriter, who revises
// the headline live. That feedback edge is what makes it a net, not a line.
async function runCrew(idea, name) {
  running = idea && idea.id;
  broadcast("run-start", { id: running, idea: idea.text, name });
  await sleep(450);
  broadcast("graph", {
    nodes: [
      { key: "copywriter", label: "copy" },
      { key: "designer", label: "design" },
      { key: "builder", label: "build" },
      { key: "skeptic", label: "skeptic" },
    ],
    edges: [
      { from: "designer", to: "copywriter" },
      { from: "copywriter", to: "builder" },
      { from: "designer", to: "builder" },
      { from: "builder", to: "skeptic" },
      { from: "skeptic", to: "copywriter", feedback: true },
    ],
  });
  await sleep(400);

  // round 1: copywriter and designer start together
  broadcast("agent-state", { key: "copywriter", state: "working", note: "drafting the hero…" });
  broadcast("agent-state", { key: "designer", state: "working", note: "choosing a palette…" });
  await sleep(1300);

  // designer lands first, applies the palette, then helps the copywriter
  const design = PALETTES[paletteIdx++ % PALETTES.length];
  broadcast("worker-done", { key: "designer", payload: design });
  broadcast("agent-state", { key: "designer", state: "assisting", note: "done early, so it helps: sends the copywriter a tone hint" });
  broadcast("flow", { from: "designer", to: "copywriter", what: "tone hint" });
  await sleep(1000);
  broadcast("agent-state", { key: "designer", state: "done" });

  // copywriter lands, feeds the builder (and so does the designer's palette)
  const copy = makeCopy(idea.text);
  broadcast("worker-done", { key: "copywriter", payload: copy });
  broadcast("agent-state", { key: "copywriter", state: "done", note: `hero: "${copy.headline}"` });
  broadcast("flow", { from: "copywriter", to: "builder", what: "copy" });
  broadcast("flow", { from: "designer", to: "builder", what: "palette" });
  broadcast("agent-state", { key: "builder", state: "working", note: "assembling the page…" });
  await sleep(900);
  broadcast("worker-done", { key: "builder" });
  broadcast("agent-state", { key: "builder", state: "done", note: "page assembled, 3 sections" });

  // builder hands the page to the skeptic
  broadcast("flow", { from: "builder", to: "skeptic", what: "the page" });
  broadcast("agent-state", { key: "skeptic", state: "working", note: "poking holes…" });
  await sleep(1100);
  const note = skepticNote(idea.text);
  broadcast("worker-done", { key: "skeptic", payload: { note } });
  broadcast("agent-state", { key: "skeptic", state: "done" });

  // the net closes: critique flows back, the copywriter revises live
  broadcast("flow", { from: "skeptic", to: "copywriter", what: "critique" });
  broadcast("agent-state", { key: "copywriter", state: "working", note: "takes the critique, revising the hero…" });
  await sleep(1300);
  broadcast("revise", { field: "headline", value: reviseHeadline(idea.text), by: "copywriter" });
  broadcast("flow", { from: "copywriter", to: "builder", what: "revised hero" });
  broadcast("agent-state", { key: "copywriter", state: "done", note: "headline revised. the web is settled" });

  if (idea.mark) idea.mark.status = "done";
  running = null;
  broadcast("run-done", { id: idea.id });
  broadcast("queue", queuePayload());
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

  if (p === "/api/info") return json(res, 200, { audienceUrl: AUDIENCE_URL, mock: true });
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
    req.on("close", () => clients.delete(res));
    return;
  }

  if (p === "/api/submit" && req.method === "POST") {
    const d = JSON.parse((await body(req)) || "{}");
    // mirror the Java server: trust a forwarded address if present
    const fwd = req.headers["x-forwarded-for"];
    const ip = (fwd ? String(fwd).split(",")[0].trim() : req.socket.remoteAddress) || "?";
    const text = clip((d.text || "").trim());
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
    const idea = { id: nextId++, text, name: (d.name||"").trim().slice(0,24), status:"new", flagged: isCrude(text) };
    ideas.push(idea);
    broadcast("queue", queuePayload());
    broadcast("tally", { total: ideas.length });
    // hand back exactly what we stored, so the phone can never show something
    // different from what the room will see
    return json(res, 200, { ok:true, position: ideas.length, stored: text, mock:true });
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
