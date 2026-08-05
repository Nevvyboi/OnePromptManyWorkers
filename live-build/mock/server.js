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

// ---------- state ----------
let ideas = [];       // {id, text, name, status}
let nextId = 1;
let running = null;
const clients = new Set();

// limits, matching the Java server
const MAX_QUEUE = 250, MAX_PER_IP = 8, COOLDOWN_MS = 6000;
const ipState = new Map();
const BLOCK = new Set(["fuck", "shit", "cunt", "bitch", "bastard", "asshole", "dick", "nigger", "faggot", "retard", "rape"]);
const blocked = t => t.toLowerCase().split(/[^a-z]+/).some(w => BLOCK.has(w));

function sse(res, event, data) {
  res.write(`event: ${event}\n`);
  res.write(`data: ${JSON.stringify(data)}\n\n`);
}
function broadcast(event, data) { for (const c of clients) sse(c, event, data); }
function queuePayload() { return { ideas: ideas.map(i => ({ id: i.id, text: i.text, name: i.name, status: i.status })) }; }

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

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function runCrew(idea, name) {
  running = idea && idea.id;
  broadcast("run-start", { id: running, idea: idea.text, name });
  await sleep(500);
  broadcast("crew", { workers: [
    { key: "copywriter", label: "Copywriter" },
    { key: "designer", label: "Designer" },
    { key: "builder", label: "Builder" },
    { key: "skeptic", label: "Skeptic" },
  ]});
  await sleep(300);

  broadcast("worker-start", { key: "copywriter", note: "writing the hero…" });
  await sleep(1500);
  const copy = makeCopy(idea.text);
  broadcast("worker-done", { key: "copywriter", payload: copy, summary: `"${copy.headline}"` });

  broadcast("worker-start", { key: "designer", note: "choosing a palette…" });
  await sleep(1300);
  const design = PALETTES[paletteIdx++ % PALETTES.length];
  broadcast("worker-done", { key: "designer", payload: design, summary: `palette + type set` });

  broadcast("worker-start", { key: "builder", note: "assembling the page…" });
  await sleep(900);
  broadcast("worker-done", { key: "builder", summary: "page assembled, 3 sections" });

  broadcast("worker-start", { key: "skeptic", note: "poking holes…" });
  await sleep(1000);
  broadcast("worker-done", { key: "skeptic", payload: { note: skepticNote(idea.text) }, summary: "one honest risk" });

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
  if (p === "/control") return serveStatic(res, "control.html");
  if (p === "/join") return serveStatic(res, "join.html");

  if (p === "/api/info") return json(res, 200, { audienceUrl: AUDIENCE_URL, mock: true });
  if (p === "/api/queue") return json(res, 200, queuePayload());

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
    const ip = req.socket.remoteAddress || "?";
    const text = (d.text || "").trim().slice(0, 120);
    if (text.length < 3) return json(res, 200, { ok:false, error:"Give it a few more words." });
    if (blocked(text)) return json(res, 200, { ok:false, error:"Let's keep it friendly." });
    if (ideas.length >= MAX_QUEUE) return json(res, 200, { ok:false, error:"The queue is full for now. Thanks!" });
    if (ideas.some(i => i.text.toLowerCase() === text.toLowerCase())) return json(res, 200, { ok:false, error:"Someone already sent that one." });
    const now = Date.now();
    const st = ipState.get(ip) || { last: 0, count: 0 };
    if (now - st.last < COOLDOWN_MS) return json(res, 200, { ok:false, error:"One at a time. Give it a few seconds." });
    if (st.count >= MAX_PER_IP) return json(res, 200, { ok:false, error:"That's plenty from you. Let others have a go." });
    st.last = now; st.count++; ipState.set(ip, st);
    const idea = { id: nextId++, text, name: (d.name||"").trim().slice(0,24), status:"new" };
    ideas.push(idea);
    broadcast("queue", queuePayload());
    broadcast("tally", { total: ideas.length });
    return json(res, 200, { ok:true, position: ideas.length, mock:true });
  }

  if (p.startsWith("/api/run/") && req.method === "POST") {
    if (running) return json(res, 200, { ok:false, error:"already running" });
    const id = p.split("/").pop();
    let idea;
    if (id === "demo") idea = { id:"demo", text:"an app that waters your plants when you forget", name:"the house crew" };
    else { const found = ideas.find(i=>String(i.id)===id); if(found) idea = { id:found.id, text:found.text, name:found.name, mark:found }; }
    if (!idea) return json(res, 200, { ok:false, error:"not found" });
    json(res, 200, { ok:true });
    runCrew(idea, idea.name).catch(console.error);
    return;
  }

  serveStatic(res, p.slice(1));
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`\n  Live Build (mock) running`);
  console.log(`  audience : ${AUDIENCE_URL}`);
  console.log(`  stage    : http://localhost:${PORT}/stage`);
  console.log(`  control  : http://localhost:${PORT}/control\n`);
});
