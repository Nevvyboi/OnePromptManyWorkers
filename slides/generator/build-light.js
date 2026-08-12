// One Prompt. Many Workers. -- LIGHT edition.
// A different design language: white paper, bold sans, indigo accent,
// icons living inside filled colored circles. No stripes, no dashes, no slop.
const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const QRCode = require("qrcode");
const Fi = require("react-icons/fi");

// ====== EDIT THESE with the real links, then re-run ======
const LINKEDIN = "https://www.linkedin.com/in/nevin-tom";
const GITHUB = "https://github.com/Nevvyboi/OnePromptManyWorkers";
// The audience join URL. On stage, prefer the app's live /join screen (auto IP);
// this baked QR is only a backup: go.sh prints the real public URL on the night.
// The URL the room scans. A quick tunnel gets a new address every restart, so
// this must never be typed by hand: deck.sh reads it off the running app and
// passes it in. Building without it prints a warning and marks the slide.
const JOIN_URL = process.env.JOIN_URL || "";
const HAS_JOIN = JOIN_URL.trim().length > 0;
const JOIN_SHOWN = HAS_JOIN ? JOIN_URL.replace(/^https?:\/\//, "").replace(/\/$/, "") : "start the app, then run deck.sh";
if (!HAS_JOIN) console.warn("\n  !!  no JOIN_URL: the QR slides will say so. Start the app and run ./deck.sh\n");
// ===========================================================================

// ---------- light palette ----------
const BG = "FFFFFF", CARD = "F5F7FA", CARD2 = "EEF1F6", HAIR = "E1E5EC";
const INK = "16181D", MUTED = "666C7A", FAINT = "9AA1B0";
const INDIGO = "4F46E5", INDIGO2 = "6366F1";
const TEAL = "0F9E8E", VIOLET = "7C3AED", ROSE = "E11D48", SKY = "0284C7", AMBER = "D97706";

// ---------- fonts (safe list, true-to-width) ----------
const DISP = "Arial";       // bold sans display
const BODY = "Calibri";     // body
const MONO = "Consolas";    // code + labels

// ---------- icon rasteriser ----------
const iconCache = new Map();
async function iconData(name, hex) {
  const key = name + hex;
  if (iconCache.has(key)) return iconCache.get(key);
  let svg = ReactDOMServer.renderToStaticMarkup(React.createElement(Fi[name], { size: 256 }));
  svg = svg.replace(/currentColor/g, "#" + hex);
  const png = await sharp(Buffer.from(svg)).png().toBuffer();
  const data = "image/png;base64," + png.toString("base64");
  iconCache.set(key, data);
  return data;
}
async function qrData(text) {
  const buf = await QRCode.toBuffer(text, { type: "png", margin: 1, width: 512, errorCorrectionLevel: "M", color: { dark: "#" + INK, light: "#FFFFFF" } });
  return "image/png;base64," + buf.toString("base64");
}

const pres = new pptxgen();
pres.defineLayout({ name: "W", width: 13.333, height: 7.5 });
pres.layout = "W";
const W = 13.333, H = 7.5, MX = 0.92;

let count = 0;
function slide() { count++; const s = pres.addSlide(); s.background = { color: BG }; return s; }
function eyebrow(s, text, y = 0.62, color = INDIGO) {
  s.addText(text.toUpperCase(), { x: MX, y, w: 10, h: 0.32, fontFace: DISP, bold: true, fontSize: 11, color, charSpacing: 3 });
}
function softShadow() { return { type: "outer", color: "9AA6BC", blur: 11, offset: 3, angle: 90, opacity: 0.28 }; }
function card(s, x, y, w, h, opts = {}) {
  s.addShape("roundRect", { x, y, w, h, rectRadius: 0.1, fill: { color: opts.fill || CARD }, line: { color: opts.line || HAIR, width: 1 }, shadow: softShadow() });
}
function dot(s, x, y, d, color) {
  s.addShape("ellipse", { x: x - d / 2, y: y - d / 2, w: d, h: d, fill: { color }, line: { type: "none" } });
}
// icon inside a filled colored circle -- the recurring motif
async function chip(s, cx, cy, d, name, color) {
  s.addShape("ellipse", { x: cx - d / 2, y: cy - d / 2, w: d, h: d, fill: { color }, line: { type: "none" }, shadow: softShadow() });
  const id = d * 0.52;
  s.addImage({ data: await iconData(name, "FFFFFF"), x: cx - id / 2, y: cy - id / 2, w: id, h: id });
}
function ray(s, ox, oy, ex, ey, color) {
  const x = Math.min(ox, ex), y = Math.min(oy, ey), w = Math.abs(ex - ox), h = Math.abs(ey - oy);
  s.addShape("line", { x, y, w, h, flipV: ey < oy, line: { color, width: 1.75 } });
}
// Three short lines the room can actually read. A slide with one sentence on it
// leaves the audience listening only; these give them something to hold onto.
function points(s, x, y, w, items, color, size) {
  const gap = 0.44;
  items.forEach((t, i) => {
    dot(s, x + 0.08, y + i * gap + 0.13, 0.14, color || INDIGO);
    s.addText(t, { x: x + 0.32, y: y + i * gap - 0.04, w: w - 0.32, h: 0.42,
      fontFace: BODY, fontSize: size || 13.5, color: MUTED, lineSpacing: 17, valign: "middle" });
  });
}
function stat(s, x, y, big, label, color) {
  s.addText(big, { x, y, w: 2.0, h: 0.75, fontFace: DISP, bold: true, fontSize: 38, color: color || INK });
  s.addText(label.toUpperCase(), { x, y: y + 0.72, w: 2.1, h: 0.6, fontFace: MONO, fontSize: 9.5, color: MUTED, charSpacing: 1.5, lineSpacing: 12 });
}

async function build() {

  // =============================================== 1 TITLE
  {
    const s = slide();
    eyebrow(s, "BBD Tech Talk  /  Java");
    s.addText([
      { text: "One Prompt.", options: { breakLine: true, color: INK } },
      { text: "Many Workers", options: { color: INK } },
      { text: ".", options: { color: INDIGO } },
    ], { x: MX, y: 1.55, w: 8, h: 2.5, fontFace: DISP, bold: true, fontSize: 60, lineSpacing: 62, charSpacing: -0.5 });
    s.addText("Building multi-agent AI systems in Java, where a single sentence commands a whole crew.",
      { x: MX, y: 4.25, w: 6.3, h: 1, fontFace: BODY, fontSize: 17, color: MUTED, lineSpacing: 26 });
    s.addText([
      { text: "Nevin Tom", options: { color: INK, bold: true } },
      { text: "  /  BBD", options: { color: INDIGO, bold: true } },
      { text: "\nlocal model   ·   no cloud   ·   live code", options: { color: MUTED } },
    ], { x: MX, y: 5.55, w: 7, h: 0.9, fontFace: MONO, fontSize: 12.5, lineSpacing: 24 });
    const ox = 9.15, oy = 3.75, ex = 11.75;
    const targets = [[1.4, TEAL], [2.55, VIOLET], [3.75, INDIGO], [4.95, ROSE], [6.1, SKY]];
    targets.forEach(([ey, c]) => ray(s, ox, oy, ex, ey, c));
    targets.forEach(([ey, c]) => dot(s, ex, ey, 0.24, c));
    dot(s, ox, oy, 0.46, INDIGO);
    s.addImage({ data: await iconData("FiMessageSquare", "FFFFFF"), x: ox - 0.13, y: oy - 0.12, w: 0.26, h: 0.26 });
    s.addText("PROMPT", { x: ox - 0.9, y: oy + 0.32, w: 1.8, h: 0.3, align: "center", fontFace: MONO, fontSize: 9, color: INDIGO, charSpacing: 2 });
    s.addText("WORKERS", { x: ex - 0.6, y: 6.3, w: 1.7, h: 0.3, align: "center", fontFace: MONO, fontSize: 9, color: MUTED, charSpacing: 2 });
    s.addNotes("Wait for the room. That fan is the whole talk: one thing becoming a team. Tonight we build it for real, in Java, on this laptop, with a model that never phones home. Name, thank BBD.");
  }

  // =============================================== 2 HOOK
  {
    const s = slide();
    eyebrow(s, "The promise");
    s.addText([
      { text: "I typed ", options: { color: INK } },
      { text: "one sentence.", options: { color: INDIGO } },
      { text: "\nMy laptop hired ", options: { color: INK } },
      { text: "five workers.", options: { color: INDIGO } },
    ], { x: MX, y: 1.9, w: 7.2, h: 2.3, fontFace: DISP, bold: true, fontSize: 38, lineSpacing: 48 });
    s.addText("No cloud. No API bill. It never touched the internet. By the end of tonight you will have built it yourself.",
      { x: MX, y: 4.35, w: 6.2, h: 1.1, fontFace: BODY, fontSize: 17, color: MUTED, lineSpacing: 26 });
    stat(s, MX, 5.75, "5", "workers hired", INDIGO);
    stat(s, MX + 2.15, 5.75, "0", "cloud calls", INK);
    stat(s, MX + 4.1, 5.75, "~100", "lines of Java", INK);
    s.addText("1", { x: 8.3, y: 2.15, w: 1.7, h: 2.7, fontFace: DISP, bold: true, fontSize: 150, color: INDIGO, align: "center" });
    s.addShape("line", { x: 10.05, y: 3.6, w: 0.6, h: 0, line: { color: FAINT, width: 2, endArrowType: "triangle" } });
    const crew = [["engineer", TEAL, "FiCode"], ["marketer", VIOLET, "FiTrendingUp"], ["designer", AMBER, "FiPenTool"], ["legal", ROSE, "FiShield"], ["devrel", SKY, "FiMic"]];
    for (let i = 0; i < crew.length; i++) {
      const cy = 2.4 + i * 0.62;
      await chip(s, 11.0, cy + 0.12, 0.36, crew[i][2], crew[i][1]);
      s.addText(crew[i][0].toUpperCase(), { x: 11.32, y: cy - 0.05, w: 2.0, h: 0.38, fontFace: MONO, fontSize: 11, color: MUTED, charSpacing: 2, valign: "middle" });
    }
    s.addNotes("The hook, slow. Last week I typed one sentence, fourteen words, and my laptop assembled a team of five, split the work, handed me back a plan. No cloud, no bill, no internet. That is magic, then, that is horrifying, because it was so little code.");
  }

  // =============================================== 3 SCAN TO JOIN
  {
    const s = slide();
    eyebrow(s, "Join in  ·  this one's interactive");
    s.addText([{ text: "Scan it. Type ", options: { color: INK } }, { text: "one line.", options: { color: INDIGO } }, { text: "\nThe crew starts on it now.", options: { color: INK } }],
      { x: MX, y: 1.5, w: 7, h: 1.9, fontFace: DISP, bold: true, fontSize: 38, lineSpacing: 46 });
    const steps = [["1", "Scan the code with your phone"], ["2", "Type one line, any product idea"], ["3", "Watch the crew build it live"]];
    steps.forEach((st, i) => {
      const y = 3.85 + i * 0.74;
      s.addShape("ellipse", { x: MX, y, w: 0.44, h: 0.44, fill: { color: INDIGO }, line: { type: "none" } });
      s.addText(st[0], { x: MX, y, w: 0.44, h: 0.44, align: "center", valign: "middle", fontFace: DISP, bold: true, fontSize: 15, color: "FFFFFF" });
      s.addText(st[1], { x: MX + 0.62, y: y - 0.02, w: 6, h: 0.48, fontFace: BODY, fontSize: 16.5, color: MUTED, valign: "middle" });
    });
    s.addText("From the moment you send it, agents on this laptop start building your page. You will see them all at the end.",
      { x: MX, y: 6.35, w: 6.6, h: 0.6, fontFace: MONO, fontSize: 11, color: FAINT, lineSpacing: 16 });
    const qx = 8.7, qy = 1.55, qw = 3.5, qh = 4.45;
    card(s, qx, qy, qw, qh);
    if (HAS_JOIN) {
      s.addImage({ data: await qrData(JOIN_URL), x: qx + (qw - 2.4) / 2, y: qy + 0.35, w: 2.4, h: 2.4 });
    } else {
      s.addShape("roundRect", { x: qx + (qw - 2.4) / 2, y: qy + 0.35, w: 2.4, h: 2.4, rectRadius: 0.1,
        fill: { color: "FDECEC" }, line: { color: ROSE, width: 1.5, dashType: "dash" } });
      s.addText("no URL yet\nrun ./deck.sh", { x: qx + (qw - 2.4) / 2, y: qy + 1.25, w: 2.4, h: 0.7,
        align: "center", fontFace: MONO, fontSize: 12, color: ROSE, lineSpacing: 16 });
    }
    await chip(s, qx + qw / 2, qy + 3.15, 0.5, "FiSmartphone", INDIGO);
    s.addText("scan to join", { x: qx, y: qy + 3.47, w: qw, h: 0.32, align: "center", fontFace: DISP, bold: true, fontSize: 16, color: INK });
    // the address in words, for the phone whose camera will not play along
    s.addText(JOIN_SHOWN, { x: qx + 0.12, y: qy + 3.82, w: qw - 0.24, h: 0.5, align: "center",
      fontFace: MONO, fontSize: JOIN_SHOWN.length > 34 ? 8.5 : 10, color: HAS_JOIN ? INDIGO : ROSE, lineSpacing: 12 });
    s.addNotes("Put this up early and leave it up. This is the setup for the whole talk. 'Scan that, from wherever you are, no wifi to join, and send my agents a product idea. Here is the thing: the moment you send it, a crew of agents on this laptop starts building a real landing page for it. They will keep working the entire time I am talking. At the end I will show you every single one.' Then carry on. The queue fills while you teach, and the payoff is waiting.");
  }

  // =============================================== 4 ROADMAP
  {
    const s = slide();
    eyebrow(s, "Where we're going");
    s.addText([
      { text: "Five rungs from ", options: { color: INK } },
      { text: "a sentence", options: { color: INDIGO } },
      { text: " to ", options: { color: INK } },
      { text: "a swarm.", options: { color: INDIGO } },
    ], { x: MX, y: 1.2, w: 11, h: 1, fontFace: DISP, bold: true, fontSize: 32 });
    const rows = [
      ["FiMessageSquare", INDIGO, "01  prompt", "The atom.", "What a prompt actually is."],
      ["FiRefreshCw", TEAL, "02  agent", "The loop", "that turns talk into action."],
      ["FiLayers", SKY, "03  stack", "The infrastructure,", "and why Java."],
      ["FiSliders", VIOLET, "04  control", "Six levers", "beyond the prompt."],
      ["FiShare2", AMBER, "05  swarm", "One prompt, many workers,", "live."],
    ];
    for (let i = 0; i < rows.length; i++) {
      const y = 2.55 + i * 0.82;
      await chip(s, MX + 0.28, y + 0.2, 0.5, rows[i][0], rows[i][1]);
      s.addText(rows[i][2].toUpperCase(), { x: MX + 0.75, y, w: 2.5, h: 0.4, fontFace: MONO, fontSize: 11.5, color: MUTED, charSpacing: 1.5, valign: "middle" });
      s.addText([
        { text: rows[i][3] + " ", options: { color: INK, bold: true } },
        { text: rows[i][4], options: { color: MUTED } },
      ], { x: 4.3, y, w: 8.3, h: 0.4, fontFace: BODY, fontSize: 17, valign: "middle" });
    }
    s.addNotes("Quick map. A prompt, an agent, the infrastructure, the control panel almost nobody talks about, and then we point one prompt at a whole crew and run it live. Promise the demo early.");
  }

  // =============================================== ACT dividers
  async function act(roman, actLabel, title, subtitle, accent, ghostName) {
    const s = slide();
    s.addImage({ data: await iconData(ghostName, CARD2), x: 8.9, y: 3.0, w: 4.4, h: 4.4 });
    s.addText(roman, { x: MX, y: 1.25, w: 4, h: 1.6, fontFace: DISP, bold: true, fontSize: 92, color: HAIR });
    eyebrow(s, actLabel, 3.05, accent);
    s.addText(title, { x: MX, y: 3.45, w: 9, h: 1.6, fontFace: DISP, bold: true, fontSize: 50, color: INK, lineSpacing: 50 });
    s.addText(subtitle, { x: MX, y: title.includes("\n") ? 5.6 : 5.05, w: 7.6, h: 0.9, fontFace: BODY, fontSize: 17, color: MUTED, lineSpacing: 25 });
    // small accent chip by the numeral
    return s;
  }

  (await act("i", "Act one", "The Prompt", "The smallest unit of the whole thing, and the most misunderstood.", INDIGO, "FiMessageSquare"))
    .addNotes("Reset. Slow down. Rung one, the prompt, and almost everyone misunderstands what it is.");

  // =============================================== 5 WHAT A PROMPT IS
  {
    const s = slide();
    eyebrow(s, "The atom");
    s.addText([{ text: "A model is just a ", options: { color: INK } }, { text: "function.", options: { color: INDIGO } }],
      { x: MX, y: 1.5, w: 6.4, h: 1.5, fontFace: DISP, bold: true, fontSize: 38, lineSpacing: 44 });
    s.addText("Text goes in. Text comes out. No memory of a moment ago. No hands. No way to check its own work.",
      { x: MX, y: 3.1, w: 5.7, h: 1.5, fontFace: BODY, fontSize: 18, color: MUTED, lineSpacing: 28 });
    s.addText("A prompt is you calling that function.",
      { x: MX, y: 5.1, w: 6, h: 0.5, fontFace: MONO, fontSize: 13, color: INDIGO });
    points(s, MX, 5.62, 5.9, [
      "Ask the same question twice and you get two different answers.",
      "It cannot look anything up unless you hand it a tool.",
      "Everything tonight is scaffolding around this one call.",
    ]);
    card(s, 7.4, 1.7, 5.0, 3.8, { fill: INK });
    s.addText([
      { text: "String", options: { color: "C4B5FD" } }, { text: " reply = ", options: { color: "9AA1B0" } },
      { text: "\n   model.", options: { color: "E6E9F0" } }, { text: "chat", options: { color: "FBBF24" } }, { text: "(", options: { color: "9AA1B0" } },
      { text: "\"summarise this\"", options: { color: "6EE7D8" } }, { text: ");", options: { color: "9AA1B0" } },
      { text: "\n\n// text in  ->  text out", options: { color: "6B7280" } },
      { text: "\n// no memory. no hands.", options: { color: "6B7280" } },
      { text: "\n// ask twice -> two answers.", options: { color: "6B7280" } },
    ], { x: 7.8, y: 2.1, w: 4.3, h: 3, fontFace: MONO, fontSize: 15, lineSpacing: 30, valign: "top" });
    s.addNotes("Strip the marketing, a model is a function. Text in, text out. No memory, no hands, no way to check itself. Ask twice, get two answers. A very good guesser, but a guesser. A prompt is you calling that function.");
  }

  // =============================================== 6 THREE WALLS
  {
    const s = slide();
    eyebrow(s, "The ceiling");
    s.addText([{ text: "A raw prompt hits ", options: { color: INK } }, { text: "three walls.", options: { color: INDIGO } }],
      { x: MX, y: 1.2, w: 11, h: 1, fontFace: DISP, bold: true, fontSize: 32 });
    const walls = [
      ["FiDatabase", "No memory", "Every call starts from zero. It forgot the last sentence the instant it finished it."],
      ["FiZap", "No hands", "It can describe sending the email in beautiful detail. It cannot send it."],
      ["FiHelpCircle", "No proof", "It sounds equally sure when it is right and when it is making things up."],
    ];
    const cw = 3.7, gap = 0.35;
    for (let i = 0; i < walls.length; i++) {
      const x = MX + i * (cw + gap), y = 2.6, h = 3.2;
      card(s, x, y, cw, h);
      await chip(s, x + 0.68, y + 0.72, 0.66, walls[i][0], ROSE);
      s.addText("WALL 0" + (i + 1), { x: x + cw - 1.5, y: y + 0.55, w: 1.15, h: 0.3, align: "right", fontFace: MONO, fontSize: 9.5, color: FAINT, charSpacing: 2 });
      s.addText(walls[i][1], { x: x + 0.4, y: y + 1.35, w: cw - 0.8, h: 0.6, fontFace: DISP, bold: true, fontSize: 21, color: INK });
      s.addText(walls[i][2], { x: x + 0.4, y: y + 2.0, w: cw - 0.8, h: 1.1, fontFace: BODY, fontSize: 14, color: MUTED, lineSpacing: 20 });
    }
    s.addNotes("Three walls, fast. No memory, every call from zero. No hands, describes the email but cannot send it. No proof, equally sure when right and when inventing. A brilliant intern who forgets your name and invents a co-worker.");
  }

  (await act("ii", "Act two", "The Agent", "Same model. Now it can remember, act, and try again.", TEAL, "FiRefreshCw"))
    .addNotes("We give the function a memory, some hands, and a loop. It stops being a chatbot. It becomes an agent.");

  // =============================================== 8 EQUATION
  {
    const s = slide();
    eyebrow(s, "The equation");
    const tiles = [["FiCpu", "model", "the brain", SKY], ["FiTool", "tools", "the hands", VIOLET], ["FiRefreshCw", "a loop", "the magic", TEAL]];
    const tw = 2.6, tgap = 1.15, total = tiles.length * tw + (tiles.length - 1) * tgap;
    let x = (W - total) / 2; const ty = 2.15;
    for (let i = 0; i < tiles.length; i++) {
      const [ic, name, sub, col] = tiles[i];
      s.addShape("roundRect", { x, y: ty, w: tw, h: tw, rectRadius: 0.14, fill: { color: i === 2 ? "ECFDF9" : CARD }, line: { color: i === 2 ? TEAL : HAIR, width: i === 2 ? 1.5 : 1 }, shadow: softShadow() });
      await chip(s, x + tw / 2, ty + 0.78, 0.78, ic, col);
      s.addText(name, { x, y: ty + 1.4, w: tw, h: 0.5, align: "center", fontFace: DISP, bold: true, fontSize: 21, color: INK });
      s.addText(sub.toUpperCase(), { x, y: ty + 1.92, w: tw, h: 0.35, align: "center", fontFace: MONO, fontSize: 10, color: MUTED, charSpacing: 2 });
      if (i < tiles.length - 1) s.addText("+", { x: x + tw, y: ty + tw / 2 - 0.4, w: tgap, h: 0.8, align: "center", fontFace: DISP, bold: true, fontSize: 30, color: FAINT });
      x += tw + tgap;
    }
    s.addText("That is an agent. Everything else is decoration.",
      { x: 2.5, y: 5.15, w: 8.33, h: 0.5, align: "center", fontFace: BODY, fontSize: 18, color: MUTED });
    points(s, 3.35, 5.75, 7.0, [
      "Model: guesses the next step. Tools: let it change something real.",
      "The loop: run again until the job is actually finished, not just answered.",
      "Drop any one of the three and you are back to a chatbot.",
    ], TEAL);
    s.addNotes("The whole definition: an agent is a model, plus tools it can call, wrapped in a loop. Everything else is a flavour of this. Model is the brain, tools the hands, the loop is where the magic lives.");
  }

  // =============================================== 9 THE LOOP
  {
    const s = slide();
    eyebrow(s, "The whole trick", 0.62, TEAL);
    s.addText([{ text: "Reason. Act.\nObserve. ", options: { color: INK } }, { text: "Again.", options: { color: INDIGO } }],
      { x: MX, y: 1.7, w: 5.6, h: 1.8, fontFace: DISP, bold: true, fontSize: 38, lineSpacing: 46 });
    s.addText("The model does not answer once. It works in a loop until the job is actually done, checking its own output along the way.",
      { x: MX, y: 3.7, w: 5.3, h: 1.5, fontFace: BODY, fontSize: 17, color: MUTED, lineSpacing: 26 });
    s.addText("Take away the loop and you are back to a chatbot.",
      { x: MX, y: 5.35, w: 5.6, h: 0.4, fontFace: MONO, fontSize: 12, color: FAINT });
    points(s, MX, 5.8, 5.6, [
      "REASON: decide the next move from what it knows right now.",
      "ACT: call a tool. This is the only part that touches the real world.",
      "OBSERVE: read what came back, and ask whether the job is done.",
    ], TEAL);
    const cx = 9.7, cy = 3.95, r = 1.65;
    const P = [[cx, cy - r, TEAL], [cx + r * 0.87, cy + r * 0.5, VIOLET], [cx - r * 0.87, cy + r * 0.5, AMBER]];
    for (let i = 0; i < 3; i++) {
      const a = P[i], b = P[(i + 1) % 3];
      const x = Math.min(a[0], b[0]), y = Math.min(a[1], b[1]), w = Math.abs(b[0] - a[0]), h = Math.abs(b[1] - a[1]);
      s.addShape("line", { x, y, w, h, flipH: b[0] < a[0], flipV: b[1] < a[1], line: { color: "C7CBD6", width: 2, endArrowType: "triangle" } });
    }
    await chip(s, P[0][0], P[0][1], 0.5, "FiCpu", P[0][2]);
    await chip(s, P[1][0], P[1][1], 0.5, "FiZap", P[1][2]);
    await chip(s, P[2][0], P[2][1], 0.5, "FiEye", P[2][2]);
    s.addText("REASON", { x: cx - 1, y: cy - r - 0.6, w: 2, h: 0.3, align: "center", fontFace: MONO, fontSize: 11, color: INK, charSpacing: 1 });
    s.addText("ACT", { x: cx + r * 0.87 + 0.35, y: cy + r * 0.5 - 0.14, w: 1.4, h: 0.3, fontFace: MONO, fontSize: 11, color: INK, charSpacing: 1 });
    s.addText("OBSERVE", { x: cx - r * 0.87 - 1.75, y: cy + r * 0.5 - 0.14, w: 1.4, h: 0.3, align: "right", fontFace: MONO, fontSize: 11, color: INK, charSpacing: 1 });
    s.addText("done?", { x: cx - 0.7, y: cy - 0.15, w: 1.4, h: 0.3, align: "center", fontFace: MONO, fontSize: 10, color: FAINT });
    s.addNotes("The most important idea. It reasons, acts by calling a tool, observes what came back, asks am I done. If not, around again. That circle is the difference between a model that talks and a system that finishes.");
  }

  (await act("iii", "Act three", "The Infrastructure", "What you actually need underneath, and why the JVM is a great place to put it.", SKY, "FiLayers"))
    .addNotes("A loop on a slide is easy. A loop you can run at BBD, that a client trusts, needs a stack. Let's build one.");

  // =============================================== 11 STACK
  {
    const s = slide();
    eyebrow(s, "The anatomy");
    s.addText([{ text: "Five layers under every ", options: { color: INK } }, { text: "agent.", options: { color: INDIGO } }],
      { x: MX, y: 1.15, w: 11, h: 1, fontFace: DISP, bold: true, fontSize: 32 });
    const layers = [
      ["FiServer", SKY, "runtime", "A model behind one interface.", "Local on a laptop, or a hosted API. Swappable."],
      ["FiShare2", VIOLET, "orchestration", "LangChain4j AiServices.", "Java interfaces with annotations. No implementations."],
      ["FiTool", TEAL, "tools", "Plain Java methods.", "Whatever you would let an intern call."],
      ["FiDatabase", AMBER, "memory", "A queue and a result per idea.", "Nothing global. Each run is its own object."],
      ["FiBox", INDIGO, "structure", "Java records: Copy, Palette, Tier.", "The model fills a type, it does not write prose."],
    ];
    for (let i = 0; i < layers.length; i++) {
      const y = 2.5 + i * 0.84;
      await chip(s, MX + 0.25, y + 0.18, 0.5, layers[i][0], layers[i][1]);
      s.addText(layers[i][2], { x: MX + 0.72, y, w: 2.6, h: 0.4, fontFace: MONO, fontSize: 12, color: MUTED, charSpacing: 1.5, valign: "middle" });
      s.addText([
        { text: layers[i][3] + " ", options: { color: INK, bold: true } },
        { text: layers[i][4], options: { color: MUTED } },
      ], { x: 4.4, y, w: 8, h: 0.4, fontFace: BODY, fontSize: 16.5, valign: "middle" });
    }
    s.addNotes("Five layers. Runtime, whatever model you point it at. Orchestration, LangChain4j or Spring AI. Tools, the hands. Memory, the notebook. Structured output, so the answer comes back as data, not a paragraph. Hold that last one.");
  }

  // =============================================== 12 WHY JAVA
  {
    const s = slide();
    eyebrow(s, "The obvious question");
    s.addText([{ text: "Why ", options: { color: INK } }, { text: "Java?", options: { color: INDIGO } }],
      { x: MX, y: 1.5, w: 6, h: 1.2, fontFace: DISP, bold: true, fontSize: 44 });
    s.addText([
      { text: "Because the systems your clients trust with real money already run on the JVM. The agent should live " },
      { text: "next to the business", options: { color: INK, bold: true } },
      { text: ", not in a Python service bolted on the side." },
    ], { x: MX, y: 2.9, w: 5.7, h: 2, fontFace: BODY, fontSize: 17, color: MUTED, lineSpacing: 27 });
    s.addText("Behind Spring Security. Inside your transactions. Beside your data.",
      { x: MX, y: 5.05, w: 5.7, h: 0.5, fontFace: MONO, fontSize: 12, color: INDIGO, lineSpacing: 20 });
    points(s, MX, 5.62, 5.9, [
      "No rewrite: the agent is a bean like any other service.",
      "Your existing auth, logging and transactions still apply to it.",
      "Typed records mean the compiler checks the model's answer.",
    ]);
    const rows = [
      ["FiLink", INDIGO, "LangChain4j", "agents, tools and AI Services, the idiomatic way"],
      ["FiWind", TEAL, "Spring AI", "the same, wired into Spring Boot"],
      ["FiCheckCircle", AMBER, "the point", "type-safe, testable, production-grade agents"],
    ];
    for (let i = 0; i < rows.length; i++) {
      const y = 2.2 + i * 1.15, x = 7.3;
      card(s, x, y, 5.1, 0.95);
      await chip(s, x + 0.6, y + 0.475, 0.56, rows[i][0], rows[i][1]);
      s.addText(rows[i][2], { x: x + 1.05, y: y + 0.15, w: 3.85, h: 0.35, fontFace: MONO, fontSize: 12, color: INK, charSpacing: 1 });
      s.addText(rows[i][3], { x: x + 1.05, y: y + 0.47, w: 3.9, h: 0.4, fontFace: BODY, fontSize: 13, color: MUTED });
    }
    s.addNotes("Every AI tutorial online is Python, and Python is lovely. But your systems of record run on the JVM. You do not rewrite any of it. LangChain4j and Spring AI put the AI next to the business, behind Spring Security, inside your transactions, next to your data.");
  }

  // =============================================== 13 LOCAL + FREE
  {
    const s = slide();
    eyebrow(s, "Where it runs");
    s.addText([{ text: "Local.", options: { color: INDIGO } }, { text: " Free.\nNever leaves the room.", options: { color: INK } }],
      { x: MX, y: 1.6, w: 6, h: 2, fontFace: DISP, bold: true, fontSize: 36, lineSpacing: 44 });
    s.addText("The model runs on this laptop. No cloud call, no per-token bill, no data leaving the building. That last part is what your regulated clients care about most.",
      { x: MX, y: 3.85, w: 5.6, h: 1.5, fontFace: BODY, fontSize: 16, color: MUTED, lineSpacing: 25 });
    points(s, MX, 5.28, 5.9, [
      "Nothing to sign, no data processing agreement, no vendor review.",
      "It still works on a plane, or when the venue wifi dies.",
    ], TEAL);
    stat(s, MX, 6.1, "3B", "params, on a laptop", SKY);
    stat(s, MX + 2.0, 6.1, "$0", "per token, ever", TEAL);
    stat(s, MX + 3.85, 6.1, "0", "bytes leave the room", INDIGO);
    const bx = 7.7, by = 1.7, bw = 4.2, bh = 3.9;
    s.addShape("roundRect", { x: bx, y: by, w: bw, h: bh, rectRadius: 0.12, fill: { color: CARD }, line: { color: HAIR, width: 1.2, dashType: "dash" }, shadow: softShadow() });
    s.addText("YOUR BUILDING", { x: bx + 0.3, y: by + 0.22, w: 3, h: 0.3, fontFace: MONO, fontSize: 10, color: TEAL, charSpacing: 2 });
    const lx = bx + 0.75, ly = by + 0.95, lw = bw - 1.5, lh = 1.7;
    s.addShape("roundRect", { x: lx, y: ly, w: lw, h: lh, rectRadius: 0.08, fill: { color: BG }, line: { color: MUTED, width: 1.2 } });
    s.addShape("trapezoid", { x: lx - 0.3, y: ly + lh, w: lw + 0.6, h: 0.28, fill: { color: CARD2 }, line: { color: MUTED, width: 1.2 }, rotate: 180 });
    await chip(s, lx + lw / 2, ly + 0.78, 0.72, "FiShield", INDIGO);
    dot(s, lx + 0.35, ly + 0.4, 0.16, TEAL);
    dot(s, lx + lw - 0.35, ly + 0.4, 0.16, VIOLET);
    dot(s, lx + 0.35, ly + lh - 0.4, 0.16, SKY);
    dot(s, lx + lw - 0.35, ly + lh - 0.4, 0.16, ROSE);
    s.addText("every token stays inside", { x: bx, y: by + bh - 0.5, w: bw, h: 0.3, align: "center", fontFace: MONO, fontSize: 10, color: MUTED, charSpacing: 1 });
    await chip(s, 12.55, 2.15, 0.8, "FiCloud", FAINT);
    s.addShape("line", { x: 12.15, y: 1.75, w: 0.8, h: 0.8, line: { color: ROSE, width: 2.5 } });
    s.addText("no cloud", { x: 11.85, y: 2.72, w: 1.4, h: 0.3, align: "center", fontFace: MONO, fontSize: 10, color: FAINT });
    s.addNotes("The model runs entirely on this laptop. The client's data never leaves the building. No token bill that scales with success. Works on a plane. For a bank, it never leaves the building is the whole conversation.");
  }

  (await act("iv", "Act four", "Control Beyond\nthe Prompt", "The prompt is one lever. There are five more, and they are where reliability comes from.", VIOLET, "FiSliders"))
    .addNotes("The heart. Most people think the prompt is the steering wheel. It is one control out of six. Here is the whole cockpit.");

  // =============================================== 15 SIX LEVERS
  {
    const s = slide();
    eyebrow(s, "The cockpit");
    s.addText([{ text: "Six ways to steer. Only ", options: { color: INK } }, { text: "one", options: { color: INDIGO } }, { text: " is the prompt.", options: { color: INK } }],
      { x: MX, y: 1.1, w: 11, h: 0.9, fontFace: DISP, bold: true, fontSize: 30 });
    const levers = [
      ["FiMessageSquare", INDIGO, "lever 01", "The prompt", "What you want. The instruction everyone knows."],
      ["FiTool", TEAL, "lever 02", "Tools", "What it is allowed to touch. The capability boundary."],
      ["FiCode", VIOLET, "lever 03", "Schema", "The shape of the answer. A typed object, not prose."],
      ["FiShield", ROSE, "lever 04", "Guardrails", "Validation that rejects bad output before it escapes."],
      ["FiDatabase", SKY, "lever 05", "Memory", "What it remembers, and pointedly what it forgets."],
      ["FiShare2", AMBER, "lever 06", "Orchestration", "Who talks to whom. Tonight, a crew."],
    ];
    const cw = 3.72, ch = 2.0, gx = 0.28, gy = 0.28, x0 = MX, y0 = 2.25;
    for (let i = 0; i < levers.length; i++) {
      const col = i % 3, row = Math.floor(i / 3);
      const x = x0 + col * (cw + gx), y = y0 + row * (ch + gy);
      card(s, x, y, cw, ch);
      await chip(s, x + 0.62, y + 0.6, 0.56, levers[i][0], levers[i][1]);
      s.addText(levers[i][2].toUpperCase(), { x: x + 1.05, y: y + 0.3, w: 2.4, h: 0.3, fontFace: MONO, fontSize: 9.5, color: FAINT, charSpacing: 2 });
      s.addText(levers[i][3], { x: x + 1.05, y: y + 0.58, w: cw - 1.3, h: 0.4, fontFace: DISP, bold: true, fontSize: 18, color: INK });
      s.addText(levers[i][4], { x: x + 0.35, y: y + 1.2, w: cw - 0.7, h: 0.75, fontFace: BODY, fontSize: 12.5, color: MUTED, lineSpacing: 17 });
    }
    s.addNotes("Prompt says what. Tools decide what it can touch. Schema decides shape. Guardrails reject bad output. Memory decides what it knows and forgets. Orchestration decides who talks to whom. You barely touch the prompt, you change the constraints.");
  }

  // =============================================== 16 CONSTRAIN
  {
    const s = slide();
    eyebrow(s, "The shift");
    s.addText([
      { text: "You don't ", options: { color: INK } }, { text: "program", options: { color: INDIGO } },
      { text: " an agent.\nYou ", options: { color: INK } }, { text: "constrain", options: { color: INDIGO } }, { text: " it.", options: { color: INK } },
    ], { x: 1.2, y: 1.35, w: 11, h: 1.8, fontFace: DISP, bold: true, fontSize: 44, align: "center", lineSpacing: 52 });
    const cx = W / 2, boxW = 1.6, boxH = 1.0, bx = cx - boxW / 2, by = 4.3;
    s.addShape("roundRect", { x: bx, y: by, w: boxW, h: boxH, rectRadius: 0.12, fill: { color: CARD }, line: { color: INDIGO, width: 1.5, dashType: "dash" } });
    dot(s, cx, by + boxH / 2, 0.3, INDIGO);
    const labels = [
      ["PROMPT", "says what", TEAL, cx, by - 0.55, "ctr"],
      ["TOOLS", "say how far", VIOLET, bx + boxW + 0.35, by + boxH / 2 - 0.16, "l"],
      ["SCHEMA", "says what shape", SKY, cx, by + boxH + 0.3, "ctr"],
      ["ORCHESTRATION", "says who", ROSE, bx - 0.35, by + boxH / 2 - 0.16, "r"],
    ];
    labels.forEach(([t, d, c, lx, ly, al]) => {
      const align = al === "ctr" ? "center" : al === "l" ? "left" : "right";
      const w = 3.2, x = al === "ctr" ? lx - w / 2 : al === "l" ? lx : lx - w;
      s.addText([{ text: t + "  ", options: { color: c, bold: true } }, { text: d, options: { color: FAINT } }],
        { x, y: ly, w, h: 0.32, align, fontFace: MONO, fontSize: 11, charSpacing: 1 });
    });
    s.addText("Good agent design is good constraint design.",
      { x: 2.5, y: 6.35, w: 8.33, h: 0.45, align: "center", fontFace: BODY, fontSize: 17, color: MUTED });
    s.addText("When output is wrong, the fix is almost never a longer prompt. It is a tighter tool, a stricter schema, or a different agent doing the job.",
      { x: 2.9, y: 6.8, w: 7.5, h: 0.55, align: "center", fontFace: BODY, fontSize: 13, color: FAINT, lineSpacing: 17 });
    s.addNotes("The line to remember, then stop for two seconds. You don't program an agent, you constrain it. Prompt says what, tools say how far, schema says what shape, orchestration says who. Good agent engineering is good constraint design.");
  }

  (await act("v", "Act five", "One Prompt.\nMany Workers.", "Agents that help each other, and a critique that loops back. Then we run it, live.", AMBER, "FiShare2"))
    .addNotes("Lift the energy. Lever six, orchestration, is the fun one. Instead of one agent doing everything, we point a single prompt at a whole crew. One becomes many.");

  // =============================================== 18 THE SPIDER NET
  {
    const s = slide();
    eyebrow(s, "The pattern", 0.62, AMBER);
    s.addText([{ text: "Not a pipeline. ", options: { color: INK } }, { text: "A web.", options: { color: INDIGO } }],
      { x: MX, y: 1.1, w: 11, h: 0.9, fontFace: DISP, bold: true, fontSize: 32 });
    s.addText("Agents run in parallel. The one that finishes first helps another. And the critique loops back.",
      { x: MX, y: 1.95, w: 7.4, h: 0.5, fontFace: BODY, fontSize: 15.5, color: MUTED });

    // node centres
    const N = {
      copy:    { x: 6.30, y: 3.20, c: TEAL,   label: "copy",    sub: "writes" },
      design:  { x: 3.95, y: 4.45, c: VIOLET, label: "design",  sub: "styles" },
      build:   { x: 8.65, y: 4.45, c: SKY,    label: "build",   sub: "assembles" },
      skeptic: { x: 6.30, y: 5.75, c: ROSE,   label: "skeptic", sub: "critiques" },
    };
    const R = 0.52;

    function link(a, b, opts = {}) {
      const x = Math.min(a.x, b.x), y = Math.min(a.y, b.y);
      s.addShape("line", {
        x, y, w: Math.abs(b.x - a.x), h: Math.abs(b.y - a.y),
        flipH: b.x < a.x, flipV: b.y < a.y,
        line: { color: opts.color || HAIR, width: opts.width || 1.6,
                dashType: opts.dash ? "dash" : "solid", endArrowType: "triangle" },
      });
    }
    function tag(text, x, y, color, w = 1.9) {
      s.addText(text, { x: x - w / 2, y, w, h: 0.3, align: "center",
        fontFace: MONO, fontSize: 9.5, color: color || MUTED, charSpacing: 1 });
    }

    // forward edges
    link(N.design, N.copy,   { color: VIOLET });
    link(N.copy,   N.build,  { color: TEAL });
    link(N.design, N.build,  { color: VIOLET });
    link(N.build,  N.skeptic,{ color: SKY });
    tag("tone hint", 4.72, 3.52, VIOLET);
    tag("copy",      7.72, 3.52, TEAL, 1.2);
    tag("palette",   6.30, 4.62, VIOLET, 1.4);
    tag("the page",  7.85, 5.20, SKY, 1.5);

    // the feedback loop, routed around the outside so it reads instantly
    const LX = 11.15;
    s.addShape("line", { x: N.skeptic.x, y: N.skeptic.y, w: LX - N.skeptic.x, h: 0, line: { color: ROSE, width: 1.7, dashType: "dash" } });
    s.addShape("line", { x: LX, y: N.copy.y, w: 0, h: N.skeptic.y - N.copy.y, flipV: true, line: { color: ROSE, width: 1.7, dashType: "dash" } });
    s.addShape("line", { x: N.copy.x, y: N.copy.y, w: LX - N.copy.x, h: 0, flipH: true, line: { color: ROSE, width: 1.7, dashType: "dash", endArrowType: "triangle" } });
    s.addText("critique", { x: LX - 0.05, y: 4.28, w: 1.6, h: 0.3, fontFace: MONO, fontSize: 9.5, color: ROSE, charSpacing: 1 });
    s.addText("the copy is rewritten, live", { x: 7.5, y: 2.82, w: 3.5, h: 0.3, align: "right", fontFace: MONO, fontSize: 9, color: ROSE, charSpacing: .5 });

    // nodes on top, so the lines tuck under them
    for (const k of ["copy", "design", "build", "skeptic"]) {
      const n = N[k];
      s.addShape("ellipse", { x: n.x - R, y: n.y - R, w: R * 2, h: R * 2,
        fill: { color: n.c }, line: { type: "none" }, shadow: softShadow() });
      s.addText(n.label, { x: n.x - R, y: n.y - 0.22, w: R * 2, h: 0.3, align: "center",
        fontFace: MONO, fontSize: 11.5, color: "FFFFFF", bold: true });
      s.addText(n.sub, { x: n.x - R, y: n.y + 0.02, w: R * 2, h: 0.28, align: "center",
        fontFace: MONO, fontSize: 8, color: "FFFFFF" });
    }

    s.addText([
      { text: "Every agent is the same local model. ", options: { color: MUTED } },
      { text: "The web is the architecture.", options: { color: INDIGO, bold: true } },
    ], { x: 2.5, y: 6.65, w: 8.33, h: 0.5, align: "center", fontFace: BODY, fontSize: 16 });

    s.addNotes("This is the shape of tonight's crew, and it is not a line. Copy and design start together. Design finishes first, and instead of sitting idle it helps: it hands the copywriter a tone hint. Both feed the builder. The builder hands the page to the skeptic. And then the important edge, the dashed one: the skeptic's critique loops all the way back to the copywriter, who rewrites the headline. You will watch that happen on the screen in a minute. Output from one agent becoming input to another, including backwards. That is the web.");
  }

  // =============================================== 19 WHAT STEERS
  {
    const s = slide();
    eyebrow(s, "What's really steering");
    s.addText([{ text: "The prompt barely ", options: { color: INK } }, { text: "changed.", options: { color: INDIGO } }],
      { x: MX, y: 1.6, w: 6, h: 1.2, fontFace: DISP, bold: true, fontSize: 36 });
    s.addText("The power came from the levers around it, not from a cleverer sentence.",
      { x: MX, y: 3.0, w: 5.4, h: 0.9, fontFace: BODY, fontSize: 18, color: MUTED, lineSpacing: 27 });
    points(s, MX, 4.15, 5.6, [
      "Same instruction, wildly different output, because the shape changed.",
      "Prompt tweaking is the slowest way to fix an agent.",
      "Schema and orchestration are the two levers people forget.",
    ]);
    const rows = [
      ["FiMessageSquare", FAINT, "lever 01", "Prompt", "one plain English line", false],
      ["FiCode", INDIGO, "lever 03", "Schema", "a sentence becomes a typed crew", true],
      ["FiShare2", INDIGO, "lever 06", "Orchestration", "they run as a team", true],
    ];
    for (let i = 0; i < rows.length; i++) {
      const y = 2.1 + i * 1.15, x = 7.2;
      card(s, x, y, 5.2, 0.95, { fill: rows[i][5] ? "EEF0FE" : CARD, line: rows[i][5] ? INDIGO : HAIR });
      await chip(s, x + 0.6, y + 0.475, 0.56, rows[i][0], rows[i][1]);
      s.addText(rows[i][2].toUpperCase(), { x: x + 1.05, y: y + 0.3, w: 1.9, h: 0.35, fontFace: MONO, fontSize: 10, color: MUTED, charSpacing: 1.5, valign: "middle" });
      s.addText([{ text: rows[i][3] + ":  ", options: { color: INK, bold: true } }, { text: rows[i][4], options: { color: MUTED } }],
        { x: x + 2.55, y: y + 0.3, w: 2.5, h: 0.35, fontFace: BODY, fontSize: 14, valign: "middle" });
    }
    s.addNotes("Look what steers the crew. The prompt barely changes. The real control is structured output turning a sentence into a typed crew, and orchestration deciding they run as a team. Two levers you could never reach from the prompt alone.");
  }

  // =============================================== HARNESS 1: WHAT IT IS
  {
    const s = slide();
    eyebrow(s, "The part nobody demos", 0.62, VIOLET);
    s.addText([{ text: "The model is the brain.\nThe ", options: { color: INK } }, { text: "harness", options: { color: INDIGO } }, { text: " is the body.", options: { color: INK } }],
      { x: MX, y: 1.45, w: 7.2, h: 1.9, fontFace: DISP, bold: true, fontSize: 36, lineSpacing: 46 });
    s.addText("A brain in a jar cannot build a website. Everything between the model's guess and the finished page is the harness, and that is where the quality actually comes from.",
      { x: MX, y: 3.5, w: 5.9, h: 1.3, fontFace: BODY, fontSize: 17, color: MUTED, lineSpacing: 26 });
    points(s, MX, 5.0, 5.9, [
      "Swap the model and the harness is untouched.",
      "Improve the harness and every model gets better.",
      "The same prompt, through a better harness, is a better page.",
    ], VIOLET);
    const items = [
      ["FiCpu", SKY, "the model", "guesses words"],
      ["FiShield", ROSE, "guards", "refuse bad output"],
      ["FiBox", VIOLET, "structure", "decide the page shape"],
      ["FiImage", TEAL, "assets", "real pictures, real palettes"],
      ["FiCheckCircle", AMBER, "the page", "what the room sees"],
    ];
    for (let i = 0; i < items.length; i++) {
      const y = 1.75 + i * 1.02, x = 7.5;
      card(s, x, y, 4.9, 0.86, { fill: i === 0 ? CARD2 : CARD });
      await chip(s, x + 0.55, y + 0.43, 0.54, items[i][0], items[i][1]);
      s.addText(items[i][2], { x: x + 1.0, y: y + 0.1, w: 2.2, h: 0.35, fontFace: MONO, fontSize: 11.5, color: INK, charSpacing: 1 });
      s.addText(items[i][3], { x: x + 1.0, y: y + 0.42, w: 3.6, h: 0.35, fontFace: BODY, fontSize: 12.5, color: MUTED });
      if (i < items.length - 1) s.addShape("line", { x: x + 0.55, y: y + 0.86, w: 0, h: 0.16, line: { color: FAINT, width: 1.4, endArrowType: "triangle" } });
    }
    s.addNotes("Everyone demos the model. Nobody demos the harness, and the harness is the product. A brain in a jar cannot build a website. Between the model's guess and the finished page sits validation, structure, assets, taste rules. Swap the model, the harness is untouched. Improve the harness and every model you ever plug in gets better. That is the leverage.");
  }

  // =============================================== HARNESS 2: WHAT WE BUILT
  {
    const s = slide();
    eyebrow(s, "What we actually built", 0.62, VIOLET);
    s.addText([{ text: "Sixteen agents and a ", options: { color: INK } }, { text: "set of rules.", options: { color: INDIGO } }],
      { x: MX, y: 1.1, w: 11, h: 0.9, fontFace: DISP, bold: true, fontSize: 32 });
    s.addText("Every one of these started as output we were not happy with.",
      { x: MX, y: 1.95, w: 8, h: 0.4, fontFace: BODY, fontSize: 15.5, color: MUTED });
    const built = [
      ["FiUsers", VIOLET, "16 agents", "Insight, researcher, namer, copywriter, designer, illustrator, pricer, strategist, architect, critic, skeptic, proofreader and more."],
      ["FiShield", ROSE, "18 taste rules", "Deterministic checks that reject em dashes, three equal cards, AI purple, low-contrast buttons, widowed headlines."],
      ["FiLayout", TEAL, "Page architect", "The AI picks the sections: a food truck gets a menu and a story, a tool gets three steps. No fixed template."],
      ["FiRefreshCw", AMBER, "Best of three", "The crew drafts several names and headlines, then a judge picks the sharpest one."],
    ];
    for (let i = 0; i < built.length; i++) {
      const y = 2.55 + i * 1.12;
      card(s, MX, y, 11.5, 0.98);
      await chip(s, MX + 0.6, y + 0.49, 0.58, built[i][0], built[i][1]);
      s.addText(built[i][2], { x: MX + 1.15, y: y + 0.12, w: 2.6, h: 0.38, fontFace: DISP, bold: true, fontSize: 16, color: INK, valign: "middle" });
      s.addText(built[i][3], { x: MX + 3.75, y: y + 0.1, w: 7.5, h: 0.8, fontFace: BODY, fontSize: 12.5, color: MUTED, lineSpacing: 16, valign: "middle" });
    }
    s.addNotes("This is the harness. Sixteen agents, each with one job. Eighteen deterministic taste rules that reject output the model was happy with. An architect that decides what sections the page even needs, so a food truck gets a menu and a story instead of the same template. And best of three on the name and the headline. Every one of these exists because we looked at the output and were not happy.");
  }

  // =============================================== HARNESS 3: BENEFITS
  {
    const s = slide();
    eyebrow(s, "Why this is worth your time", 0.62, TEAL);
    s.addText([{ text: "The harness is the ", options: { color: INK } }, { text: "asset.", options: { color: INDIGO } }],
      { x: MX, y: 1.15, w: 11, h: 0.9, fontFace: DISP, bold: true, fontSize: 32 });
    const bens = [
      ["FiRefreshCw", INDIGO, "Model agnostic", "Point it at a local model, or a frontier API. One config line. The crew never changes."],
      ["FiTrendingUp", TEAL, "Quality has a floor", "Guards run every time. The bad version cannot reach the client, even on a bad day."],
      ["FiTool", VIOLET, "Cheap to improve", "Every fix is a rule or an agent, not a retrain. Ship it this afternoon."],
      ["FiEye", AMBER, "You can explain it", "When it goes wrong you can point at the agent that did it. Not a black box."],
      ["FiLock", ROSE, "It survives the model", "Models change every few months. The harness you build outlives all of them."],
      ["FiCheckCircle", SKY, "It compounds", "Each rule makes every future build better, forever. That is the whole bet."],
    ];
    const cw = 3.72, ch = 1.95, gx = 0.28, gy = 0.28;
    for (let i = 0; i < bens.length; i++) {
      const col = i % 3, row = Math.floor(i / 3);
      const x = MX + col * (cw + gx), y = 2.2 + row * (ch + gy);
      card(s, x, y, cw, ch);
      await chip(s, x + 0.6, y + 0.58, 0.56, bens[i][0], bens[i][1]);
      s.addText(bens[i][2], { x: x + 1.05, y: y + 0.35, w: cw - 1.25, h: 0.45, fontFace: DISP, bold: true, fontSize: 15.5, color: INK, valign: "middle" });
      s.addText(bens[i][3], { x: x + 0.35, y: y + 1.1, w: cw - 0.7, h: 0.75, fontFace: BODY, fontSize: 12, color: MUTED, lineSpacing: 16 });
    }
    s.addText("Everyone is chasing the next model. The harness is the part you own.",
      { x: 2.5, y: 6.62, w: 8.33, h: 0.45, align: "center", fontFace: BODY, fontSize: 16, color: INDIGO });
    s.addNotes("Why you should care as engineers. It is model agnostic, one config line moves it from a laptop model to a frontier API. Quality has a floor because the guards run every time. It is cheap to improve, every fix is a rule, not a retrain. You can explain it, when it goes wrong you point at the agent that did it. And it survives the model, models change every few months, the harness outlives them. Everyone is chasing the next model. The harness is the part you own.");
  }

  // =============================================== LIVE BUILD, WIRED UP
  {
    const s = slide();
    eyebrow(s, "Tonight's demo, wired up", 0.62, AMBER);
    s.addText([{ text: "How the ", options: { color: INK } }, { text: "Live Build", options: { color: INDIGO } }, { text: " works.", options: { color: INK } }],
      { x: MX, y: 1.15, w: 11, h: 0.9, fontFace: DISP, bold: true, fontSize: 32 });
    const stages = [
      ["FiSmartphone", INDIGO, "Phone", "POST /submit"],
      ["FiServer", SKY, "Spring Boot", "on this laptop"],
      ["FiShare2", VIOLET, "The crew", "LangChain4j + Qwen"],
      ["FiZap", AMBER, "Live events", "server-sent stream"],
      ["FiMonitor", TEAL, "The stage", "page builds live"],
    ];
    const n = stages.length, cw = 1.95, gap = (W - 2 * MX - n * cw) / (n - 1), y = 2.95, ch = 2.35;
    for (let i = 0; i < n; i++) {
      const x = MX + i * (cw + gap);
      card(s, x, y, cw, ch);
      await chip(s, x + cw / 2, y + 0.72, 0.7, stages[i][0], stages[i][1]);
      s.addText(stages[i][2], { x, y: y + 1.22, w: cw, h: 0.4, align: "center", fontFace: DISP, bold: true, fontSize: 15, color: INK });
      s.addText(stages[i][3], { x: x + 0.1, y: y + 1.6, w: cw - 0.2, h: 0.6, align: "center", fontFace: MONO, fontSize: 9, color: MUTED, lineSpacing: 12 });
      if (i < n - 1) s.addShape("line", { x: x + cw + gap * 0.15, y: y + ch / 2, w: gap * 0.7, h: 0, line: { color: FAINT, width: 1.6, endArrowType: "triangle" } });
    }
    s.addText("Their phone reaches this laptop through a tunnel. The agents build the page here. Every step streams to the projector.",
      { x: 2.0, y: 5.75, w: W - 4, h: 0.8, align: "center", fontFace: BODY, fontSize: 15, color: MUTED, lineSpacing: 22 });
    s.addNotes("This is the demo's plumbing, all boring reliable Java. Their phone posts one line to a Spring Boot server on my hotspot. LangChain4j runs the crew on the local Qwen model. Each event, an agent starting, a headline, a palette, streams to the stage over server-sent events. No internet anywhere in that path.");
  }

  // =============================================== STRUCTURED OUTPUT, UP CLOSE
  {
    const s = slide();
    eyebrow(s, "Lever 3, up close");
    s.addText([{ text: "The crew returns ", options: { color: INK } }, { text: "data", options: { color: INDIGO } }, { text: ", not prose.", options: { color: INK } }],
      { x: MX, y: 1.15, w: 11, h: 0.9, fontFace: DISP, bold: true, fontSize: 30 });
    card(s, MX, 2.25, 5.5, 3.5, { fill: INK, line: INK });
    s.addText([
      { text: "// the Copywriter's return type\n", options: { color: "6B7280" } },
      { text: "record ", options: { color: "C4B5FD" } }, { text: "Copy", options: { color: "7DD3FC" } }, { text: "(\n", options: { color: "E6E9F0" } },
      { text: "   String ", options: { color: "7DD3FC" } }, { text: "headline,\n", options: { color: "E6E9F0" } },
      { text: "   String ", options: { color: "7DD3FC" } }, { text: "subhead,\n", options: { color: "E6E9F0" } },
      { text: "   String ", options: { color: "7DD3FC" } }, { text: "cta,\n", options: { color: "E6E9F0" } },
      { text: "   List<Feature> ", options: { color: "7DD3FC" } }, { text: "features\n", options: { color: "E6E9F0" } },
      { text: ") {}", options: { color: "E6E9F0" } },
    ], { x: MX + 0.45, y: 2.55, w: 4.7, h: 2.9, fontFace: MONO, fontSize: 14, lineSpacing: 25, valign: "top" });
    card(s, 7.0, 2.25, 5.4, 3.5, { fill: CARD });
    s.addText([
      { text: "{\n", options: { color: MUTED } },
      { text: '  "headline"', options: { color: INDIGO } }, { text: ': "Plants, but they text you.",\n', options: { color: INK } },
      { text: '  "subhead"', options: { color: INDIGO } }, { text: ': "Never kill a fern again.",\n', options: { color: INK } },
      { text: '  "cta"', options: { color: INDIGO } }, { text: ': "Get early access",\n', options: { color: INK } },
      { text: '  "features"', options: { color: INDIGO } }, { text: ": [ {…}, {…}, {…} ]\n", options: { color: INK } },
      { text: "}", options: { color: MUTED } },
    ], { x: 7.4, y: 2.55, w: 4.6, h: 2.9, fontFace: MONO, fontSize: 13.5, lineSpacing: 25, valign: "top" });
    s.addText("No scraping a paragraph. The page is built field by field from a typed object, so it never surprises you.",
      { x: MX, y: 6.1, w: W - 2 * MX, h: 0.6, fontFace: BODY, fontSize: 15, color: MUTED });
    s.addNotes("Structured output made concrete. The Copywriter does not hand back a wall of text I have to scrape. It returns a typed Copy object. Same for the Designer's palette. The stage builds the page field by field. When you control the shape of the answer, you control the app.");
  }

  // code slide helper
  function codeSlide(idxLabel, titleRuns, lines, note) {
    const s = slide();
    eyebrow(s, idxLabel);
    s.addText(titleRuns, { x: MX, y: 1.05, w: 11, h: 0.7, fontFace: DISP, bold: true, fontSize: 27 });
    card(s, MX, 2.0, W - 2 * MX, 3.7, { fill: INK, line: INK });
    s.addText(lines, { x: MX + 0.5, y: 2.35, w: W - 2 * MX - 1, h: 3.0, fontFace: MONO, fontSize: 15.5, lineSpacing: 30, valign: "top" });
    s.addNotes(note);
    return s;
  }
  const cKw = "C4B5FD", cTy = "7DD3FC", cSt = "6EE7D8", cAn = "FBBF24", cCm = "6B7280", cTx = "E6E9F0";

  codeSlide("Live code  /  1 of 2",
    [{ text: "One interface. ", options: { color: INK } }, { text: "Every worker.", options: { color: INDIGO } }],
    [
      { text: "// No implementation. The annotations are the program.", options: { color: cCm, breakLine: true } },
      { text: "interface ", options: { color: cKw } }, { text: "Worker", options: { color: cTy } }, { text: " {", options: { color: cTx, breakLine: true } },
      { text: "", options: { breakLine: true } },
      { text: "  @SystemMessage", options: { color: cAn } }, { text: "(", options: { color: cTx } }, { text: "\"You are a {{role}}.", options: { color: cSt } }, { text: "", options: { breakLine: true } },
      { text: "                 Answer only from that view.\"", options: { color: cSt } }, { text: ")", options: { color: cTx, breakLine: true } },
      { text: "  @UserMessage", options: { color: cAn } }, { text: "(", options: { color: cTx } }, { text: "\"{{task}}\"", options: { color: cSt } }, { text: ")", options: { color: cTx, breakLine: true } },
      { text: "  String ", options: { color: cTy } }, { text: "work(", options: { color: cTx } }, { text: "@V", options: { color: cAn } }, { text: "(\"role\") String role,", options: { color: cTx, breakLine: true } },
      { text: "              ", options: { color: cTx } }, { text: "@V", options: { color: cAn } }, { text: "(\"task\") String task);", options: { color: cTx, breakLine: true } },
      { text: "}", options: { color: cTx } },
    ],
    "A worker in LangChain4j is an interface. No implementation. The annotations are the program. The system message makes it a specialist, role and task get slotted in. One interface backs every worker, that is the many workers line in code.");

  codeSlide("Live code  /  2 of 2",
    [{ text: "Plan, dispatch, ", options: { color: INK } }, { text: "synthesize.", options: { color: INDIGO } }],
    [
      { text: "Plan", options: { color: cTy } }, { text: " plan = planner.assemble(prompt);", options: { color: cTx } }, { text: "   // 1 prompt -> a crew", options: { color: cCm, breakLine: true } },
      { text: "", options: { breakLine: true } },
      { text: "var", options: { color: cKw } }, { text: " notes = ", options: { color: cTx } }, { text: "new", options: { color: cKw } }, { text: " StringBuilder();", options: { color: cTx, breakLine: true } },
      { text: "for", options: { color: cKw } }, { text: " (", options: { color: cTx } }, { text: "var", options: { color: cKw } }, { text: " a : plan.crew()) {", options: { color: cTx } }, { text: "        // fan out", options: { color: cCm, breakLine: true } },
      { text: "    String answer = worker.work(a.role(), a.task());", options: { color: cTx, breakLine: true } },
      { text: "    notes.append(a.role()).append(answer);", options: { color: cTx, breakLine: true } },
      { text: "}", options: { color: cTx, breakLine: true } },
      { text: "", options: { breakLine: true } },
      { text: "String", options: { color: cTy } }, { text: " result = synthesizer.synthesize(notes);", options: { color: cTx } }, { text: "  // fan in", options: { color: cCm } },
    ],
    "The orchestrator is the whole talk in fifteen lines. The planner turns one prompt into a crew, a typed Plan. We loop, each worker does its job, we collect notes, the synthesizer folds them into one answer. Fan out, fan in. Now, let's run it.");

  // =============================================== THE LIVE BUILD (demo moment)
  {
    const s = slide();
    dot(s, MX + 0.12, 0.78, 0.2, INDIGO);
    s.addText("THE LIVE BUILD", { x: MX + 0.4, y: 0.62, w: 5, h: 0.32, fontFace: MONO, fontSize: 12, color: INDIGO, charSpacing: 4, valign: "middle" });
    s.addText([{ text: "Let's build ", options: { color: INK } }, { text: "one of yours.", options: { color: INDIGO } }],
      { x: 0, y: 2.0, w: W, h: 1.3, align: "center", fontFace: DISP, bold: true, fontSize: 58 });
    const flow = [["your idea", INDIGO], ["the crew", VIOLET], ["a live landing page", TEAL]];
    const fw = 3.0, fg = 0.9, tot = flow.length * fw + (flow.length - 1) * fg; let fx = (W - tot) / 2; const fy = 3.7;
    for (let i = 0; i < flow.length; i++) {
      s.addShape("roundRect", { x: fx, y: fy, w: fw, h: 0.9, rectRadius: 0.12, fill: { color: CARD }, line: { color: flow[i][1], width: 1.5 }, shadow: softShadow() });
      s.addText(flow[i][0], { x: fx, y: fy, w: fw, h: 0.9, align: "center", valign: "middle", fontFace: DISP, bold: true, fontSize: 16, color: INK });
      if (i < flow.length - 1) s.addShape("line", { x: fx + fw + fg * 0.2, y: fy + 0.45, w: fg * 0.6, h: 0, line: { color: FAINT, width: 1.8, endArrowType: "triangle" } });
      fx += fw + fg;
    }
    s.addText("Didn't send one yet? Scan the code. I'll pick a good one and we watch the crew build it, live.",
      { x: 2.0, y: 5.35, w: W - 4, h: 0.7, align: "center", fontFace: BODY, fontSize: 16, color: MUTED });
    s.addImage({ data: await qrData(JOIN_URL), x: W / 2 - 0.6, y: 6.1, w: 1.05, h: 1.05 });
    s.addNotes("The moment. Switch the projector to the stage view and keep the control panel on your laptop. 'Right, let's see what you gave me.' Pick a fun submission, hit Run. Narrate as the crew lights up: copywriter writing the hero, designer choosing colours, the page recolouring live, then the skeptic's one-liner, read it out loud. If Qwen wobbles the house crew covers it, and mock mode is your parachute.");
  }

  // =============================================== 23 WHAT HAPPENED
  {
    const s = slide();
    eyebrow(s, "What just happened", 0.62, AMBER);
    s.addText([{ text: "One sentence became a ", options: { color: INK } }, { text: "team.", options: { color: INDIGO } }],
      { x: MX, y: 1.15, w: 11, h: 0.9, fontFace: DISP, bold: true, fontSize: 32 });
    const items = [
      ["FiShare2", TEAL, "It self-assembled", "You never named the crew. The planner chose who to hire."],
      ["FiHome", VIOLET, "It stayed home", "Every token ran on this laptop. Nothing left the room."],
      ["FiCode", INDIGO, "It was small", "Roughly a hundred lines of Java. That's the scary part."],
    ];
    const cw = 3.7, gap = 0.35;
    for (let i = 0; i < items.length; i++) {
      const x = MX + i * (cw + gap), y = 2.6, h = 3.0;
      card(s, x, y, cw, h);
      await chip(s, x + 0.68, y + 0.72, 0.66, items[i][0], items[i][1]);
      s.addText(items[i][2], { x: x + 0.4, y: y + 1.35, w: cw - 0.8, h: 0.6, fontFace: DISP, bold: true, fontSize: 21, color: INK });
      s.addText(items[i][3], { x: x + 0.4, y: y + 2.0, w: cw - 0.8, h: 1.0, fontFace: BODY, fontSize: 14, color: MUTED, lineSpacing: 20 });
    }
    s.addNotes("Reflect while the awe is warm. One sentence became a team that split the work and reported back. It self-assembled, it stayed home, it was a hundred lines. The scary part is not that it worked, it is how little it took.");
  }

  // =============================================== WHAT IT ACTUALLY TOOK
  {
    const s = slide();
    eyebrow(s, "No hand waving", 0.62, TEAL);
    s.addText([{ text: "The whole thing, ", options: { color: INK } }, { text: "measured.", options: { color: INDIGO } }],
      { x: MX, y: 1.1, w: 11.4, h: 0.9, fontFace: DISP, bold: true, fontSize: 32 });
    s.addText("Everything on this slide is counted from the repository you can clone tonight, not rounded up for a talk.",
      { x: MX, y: 1.95, w: 8.6, h: 0.5, fontFace: BODY, fontSize: 15, color: MUTED });
    const nums = [
      ["1 212", "lines of Java", "across 8 files", INDIGO],
      ["7", "agents", "one model, seven system prompts", VIOLET],
      ["9", "HTTP endpoints", "submit, run, hide, queue, gallery, page…", SKY],
      ["10", "event types", "streamed to the projector over SSE", TEAL],
      ["8", "dependencies", "Spring Boot, LangChain4j, Ollama, ZXing", AMBER],
      ["0", "cloud calls", "nothing left the laptop, all evening", ROSE],
    ];
    const cw = 3.72, ch = 1.62, gx = 0.28, gy = 0.26, x0 = MX, y0 = 2.7;
    for (let i = 0; i < nums.length; i++) {
      const col = i % 3, row = Math.floor(i / 3);
      const x = x0 + col * (cw + gx), y = y0 + row * (ch + gy);
      card(s, x, y, cw, ch);
      s.addText(nums[i][0], { x: x + 0.35, y: y + 0.22, w: cw - 0.7, h: 0.62, fontFace: DISP, bold: true, fontSize: 30, color: nums[i][3] });
      s.addText(nums[i][1], { x: x + 0.35, y: y + 0.82, w: cw - 0.7, h: 0.34, fontFace: DISP, bold: true, fontSize: 14, color: INK });
      s.addText(nums[i][2], { x: x + 0.35, y: y + 1.14, w: cw - 0.7, h: 0.38, fontFace: BODY, fontSize: 11.5, color: MUTED, lineSpacing: 14 });
    }
    s.addText([
      { text: "A landing page comes out as ", options: { color: MUTED } },
      { text: "one file", options: { color: INDIGO, bold: true } },
      { text: ": about 24 KB of markup, plus the photograph embedded in it. No framework, no build step, no network.", options: { color: MUTED } },
    ], { x: MX, y: 6.5, w: W - 2 * MX, h: 0.6, align: "center", fontFace: BODY, fontSize: 15 });
    s.addNotes("Use this to kill the 'yes but in the real world' objection before it is asked. Twelve hundred lines. Eight files. Eight agents that are one model with eight different system prompts. Nine endpoints. Eight dependencies. Zero cloud calls. You could read the whole thing on the train home, and the repo is in the QR at the end. Nothing here is a framework you have to buy into.");
  }

  // =============================================== THE HARNESS
  {
    const s = slide();
    eyebrow(s, "What you actually watched", 0.62, VIOLET);
    s.addText([{ text: "The model was the easy part. ", options: { color: INK } }, { text: "This is the work.", options: { color: INDIGO } }],
      { x: MX, y: 1.1, w: 11.4, h: 0.9, fontFace: DISP, bold: true, fontSize: 30 });
    s.addText("Swap Qwen for a bigger model and almost nothing here changes. The harness is the part you build, own and get paged about.",
      { x: MX, y: 1.95, w: 8.6, h: 0.6, fontFace: BODY, fontSize: 15.5, color: MUTED });
    const items = [
      ["FiGitBranch", INDIGO, "Parallelism", "Three agents start together. The one that finishes first assists, it does not idle."],
      ["FiRefreshCw", VIOLET, "Feedback loops", "The Reviewer and the Skeptic send work backwards, and the copy is rewritten."],
      ["FiShield", ROSE, "Fallbacks", "Every agent has a house answer. Kill Ollama mid-talk and the page still builds."],
      ["FiUserCheck", TEAL, "Human in the loop", "Nothing reaches the projector until I press Run. I can Hide anything first."],
      ["FiSliders", AMBER, "Limits", "Rate limits, a queue cap, dedup and a key on the panel. A room of devs will test all four."],
      ["FiClock", SKY, "Background work", "The queue kept building while I talked. Scheduling is orchestration too."],
    ];
    const cw = 3.72, ch = 1.95, gx = 0.28, gy = 0.26, x0 = MX, y0 = 2.75;
    for (let i = 0; i < items.length; i++) {
      const col = i % 3, row = Math.floor(i / 3);
      const x = x0 + col * (cw + gx), y = y0 + row * (ch + gy);
      card(s, x, y, cw, ch);
      await chip(s, x + 0.6, y + 0.58, 0.54, items[i][0], items[i][1]);
      s.addText(items[i][2], { x: x + 1.02, y: y + 0.32, w: cw - 1.25, h: 0.5, fontFace: DISP, bold: true, fontSize: 15.5, color: INK, valign: "middle" });
      s.addText(items[i][3], { x: x + 0.35, y: y + 1.0, w: cw - 0.7, h: 0.8, fontFace: BODY, fontSize: 12, color: MUTED, lineSpacing: 16 });
    }
    s.addText([
      { text: "None of that is prompting. ", options: { color: MUTED } },
      { text: "All of it is engineering you already know how to do.", options: { color: INDIGO, bold: true } },
    ], { x: 2, y: 6.85, w: W - 4, h: 0.5, align: "center", fontFace: BODY, fontSize: 15.5 });
    s.addNotes("This is the slide that makes the talk worth an hour of a senior engineer's evening. Say it plainly: 'Everything you just watched, the parallel starts, the handoffs, the loops back, the fact that it did not die when I unplugged the model, the fact that nothing rude reached the screen, the fact that the queue kept building while I talked. None of that came from the model. That is a harness, and it is ordinary engineering. Threads, queues, schedulers, validation, a human approval step. The model is a component. The harness is the product.'");
  }

  // =============================================== WHY IT STOPPED LOOKING GENERATED
  {
    const s = slide();
    eyebrow(s, "Lever four, the honest version", 0.62, ROSE);
    s.addText([{ text: "Every page passed. ", options: { color: INK } }, { text: "Together they looked machine-made.", options: { color: ROSE } }],
      { x: MX, y: 1.1, w: 11.4, h: 0.9, fontFace: DISP, bold: true, fontSize: 30 });
    s.addText("A guard that reads one page at a time cannot see the thing a room notices first. Six ideas, six clean pages, and three of them opened with the same four words.",
      { x: MX, y: 1.95, w: 9.2, h: 0.6, fontFace: BODY, fontSize: 15.5, color: MUTED });
    const items = [
      ["FiEyeOff", ROSE, "Absence is not quality", "Eight rules checked that tells were missing. Nothing required anything to be good, so the output was clean and forgettable."],
      ["FiDroplet", VIOLET, "Colour, computed", "Nine of thirty-six palette values sat in the blue-violet band. Now the hue, the WCAG contrast and the accent separation are arithmetic."],
      ["FiLayers", INDIGO, "One template is the tell", "Three hero compositions, eight palette families, five artworks. The layout is a decision per idea, not a constant."],
      ["FiList", TEAL, "Remember the slot", "The ledger first remembered finished sentences, so two pages that shared a template looked like two values. A room sees the template."],
      ["FiCpu", AMBER, "Record the model's pick too", "It only wrote down its own choices. The Illustrator answered 'waves' five times running and nothing stopped it."],
      ["FiCode", SKY, "The schema is a lever", "A nested object failed every call on a 3b model. Five flat labelled lines land nearly every time. Same model, same prompt."],
    ];
    const cw = 3.72, ch = 1.95, gx = 0.28, gy = 0.26, x0 = MX, y0 = 2.75;
    for (let i = 0; i < items.length; i++) {
      const col = i % 3, row = Math.floor(i / 3);
      const x = x0 + col * (cw + gx), y = y0 + row * (ch + gy);
      card(s, x, y, cw, ch);
      await chip(s, x + 0.6, y + 0.58, 0.54, items[i][0], items[i][1]);
      s.addText(items[i][2], { x: x + 1.02, y: y + 0.32, w: cw - 1.25, h: 0.5, fontFace: DISP, bold: true, fontSize: 15, color: INK, valign: "middle" });
      s.addText(items[i][3], { x: x + 0.35, y: y + 1.0, w: cw - 0.7, h: 0.85, fontFace: BODY, fontSize: 11.5, color: MUTED, lineSpacing: 15 });
    }
    s.addText([
      { text: "The guard reads one page. ", options: { color: MUTED } },
      { text: "The harness has to read the room.", options: { color: ROSE, bold: true } },
    ], { x: 2, y: 6.85, w: W - 4, h: 0.5, align: "center", fontFace: BODY, fontSize: 15.5 });
    s.addNotes("This is the slide where you admit something, and it lands better than any claim. Say: 'I built a guard with eight rules and every page passed it. Then I put six of them side by side and they were obviously made by a machine. Three opened with the same four words. My guard checked for the absence of tells. Nothing in it required the presence of quality. So I added colour rules that are arithmetic, three real layouts, and a memory that refuses a value the last few pages used. The colour rules failed two of my own palettes on the first run. And the memory had two bugs worth your time: it remembered finished sentences instead of templates, so the same sentence with a word swapped looked like variety. And it only recorded its own choices, so when the model picked waves five times in a row, nothing stopped it. Both of those are harness bugs, not model bugs.'");
  }

  // =============================================== SHOW THE PRODUCT
  {
    const s = slide();
    eyebrow(s, "What the page actually shows", 0.62, TEAL);
    s.addText([{ text: "Abstract art is decoration. ", options: { color: INK } }, { text: "Decoration is slop.", options: { color: TEAL } }],
      { x: MX, y: 1.1, w: 11.4, h: 0.9, fontFace: DISP, bold: true, fontSize: 30 });
    s.addText("The Illustrator used to pick rings, waves or a field of dots. Every page got a pattern. A real landing page shows the product, so now it draws one: nine interfaces, in SVG, offline.",
      { x: MX, y: 1.95, w: 9.4, h: 0.6, fontFace: BODY, fontSize: 15.5, color: MUTED });
    const items = [
      ["FiCalendar", INDIGO, "calendar", "slots taken, one booked"],
      ["FiClock", AMBER, "timer", "a dial and the time left"],
      ["FiDollarSign", TEAL, "ledger", "contributions and a total"],
      ["FiActivity", ROSE, "chart", "a threshold, and the breach"],
      ["FiCheckSquare", VIOLET, "checklist", "ticked off, and whose turn"],
      ["FiMapPin", SKY, "route", "stops, and a destination pin"],
      ["FiMessageSquare", INDIGO, "inbox", "a thread and a reply box"],
      ["FiShoppingBag", AMBER, "catalog", "product cards and a basket"],
      ["FiGrid", TEAL, "dashboard", "when nothing else fits"],
    ];
    const cw = 3.72, ch = 1.2, gx = 0.28, gy = 0.22, x0 = MX, y0 = 2.72;
    for (let i = 0; i < items.length; i++) {
      const col = i % 3, row = Math.floor(i / 3);
      const x = x0 + col * (cw + gx), y = y0 + row * (ch + gy);
      card(s, x, y, cw, ch);
      await chip(s, x + 0.55, y + 0.44, 0.46, items[i][0], items[i][1]);
      s.addText(items[i][2], { x: x + 0.95, y: y + 0.2, w: cw - 1.15, h: 0.4, fontFace: MONO, bold: true, fontSize: 13, color: INK, valign: "middle" });
      s.addText(items[i][3], { x: x + 0.95, y: y + 0.58, w: cw - 1.15, h: 0.42, fontFace: BODY, fontSize: 11.5, color: MUTED });
    }
    s.addText([
      { text: "Asked to choose, Qwen said dashboard for a stokvel tracker. ", options: { color: MUTED } },
      { text: "When the idea's own words are clear, the words win.", options: { color: TEAL, bold: true } },
    ], { x: 1.4, y: 6.9, w: W - 2.8, h: 0.5, align: "center", fontFace: BODY, fontSize: 14.5 });
    s.addNotes("The point of this slide is that 'let the model decide' is not automatically the right call. I asked Qwen which interface to draw and it said dashboard for a stokvel tracker and calendar for a coffee subscription. A twenty-line keyword scorer beats it, so the scorer decides when the idea's own words are decisive and the model only breaks ties. That is a harness decision, and it is the opposite of the reflex. Also worth saying: this one is deliberately NOT on the variety ledger. Two booking apps should both get a calendar. A mockup that does not match the product is much worse than one that repeats.");
  }

  // =============================================== THE CRITIC
  {
    const s = slide();
    eyebrow(s, "The last edge in the web", 0.62, ROSE);
    s.addText([{ text: "Seven agents wrote a field. ", options: { color: INK } }, { text: "Nobody read the page.", options: { color: ROSE } }],
      { x: MX, y: 1.1, w: 11.4, h: 0.9, fontFace: DISP, bold: true, fontSize: 30 });
    s.addText("Every worker owns one slice, so nothing in the crew ever saw the assembled result. That is how copy ships that is individually fine and collectively flat.",
      { x: MX, y: 1.95, w: 9.2, h: 0.6, fontFace: BODY, fontSize: 15.5, color: MUTED });
    const items = [
      ["FiEye", ROSE, "It sees the whole page", "Headline, subhead, button and all three features, together, after everything else has run."],
      ["FiEdit3", INDIGO, "It changes one line", "One field, one replacement. Ask for everything wrong and you get a list nobody acts on."],
      ["FiShield", TEAL, "Its rewrite is not trusted", "The replacement clears the same checks as any other copy, and gets refused if it does not."],
    ];
    const cw = 3.72, ch = 2.1, gx = 0.28, x0 = MX, y0 = 2.85;
    for (let i = 0; i < items.length; i++) {
      const x = x0 + i * (cw + gx);
      card(s, x, y0, cw, ch);
      await chip(s, x + 0.6, y0 + 0.6, 0.54, items[i][0], items[i][1]);
      s.addText(items[i][2], { x: x + 1.02, y: y0 + 0.34, w: cw - 1.25, h: 0.5, fontFace: DISP, bold: true, fontSize: 15, color: INK, valign: "middle" });
      s.addText(items[i][3], { x: x + 0.35, y: y0 + 1.05, w: cw - 0.7, h: 0.95, fontFace: BODY, fontSize: 12, color: MUTED, lineSpacing: 16 });
    }
    s.addText([
      { text: "It was asked for two labelled lines. It replies ", options: { color: MUTED } },
      { text: "subhead: <rewrite>", options: { color: ROSE, bold: true, fontFace: MONO } },
      { text: " instead. That is a usable answer, so the parser takes both.", options: { color: MUTED } },
    ], { x: 1.2, y: 5.6, w: W - 2.4, h: 0.5, align: "center", fontFace: BODY, fontSize: 14.5 });
    s.addNotes("Two things to land here. First, the structural point: a crew of specialists has a blind spot exactly where the specialisms meet, and the only fix is an agent whose input is the finished artefact. Second, the practical one: this agent does not get trusted just because it is the reviewer. Its rewrite goes through the same guard as everything else, and it does get refused. If you take one design idea home, take that one: the reviewer is not privileged.");
  }

  // =============================================== THE REVEAL
  {
    const s = slide();
    eyebrow(s, "The reveal", 0.62, AMBER);
    s.addText([{ text: "You have been ", options: { color: INK } }, { text: "employing them", options: { color: INDIGO } }, { text: "\nthis whole time.", options: { color: INK } }],
      { x: MX, y: 1.6, w: 8.4, h: 2, fontFace: DISP, bold: true, fontSize: 40, lineSpacing: 50 });
    s.addText("Every idea you sent went straight into the queue. While I was talking about prompts and levers and loops, eight agents on this laptop were quietly building a landing page for each one.",
      { x: MX, y: 3.85, w: 6.5, h: 1.6, fontFace: BODY, fontSize: 17, color: MUTED, lineSpacing: 27 });
    stat(s, MX, 5.6, "7", "agents per idea", INDIGO);
    stat(s, MX + 2.2, 5.6, "0", "cloud calls", INK);
    stat(s, MX + 4.1, 5.6, "1", "laptop, still warm", INK);
    const steps = [["FiSmartphone", INDIGO, "you sent it"], ["FiCpu", VIOLET, "they built it"], ["FiGrid", TEAL, "here it all is"]];
    for (let i = 0; i < steps.length; i++) {
      const y = 1.9 + i * 1.5, x = 8.4;
      card(s, x, y, 4.0, 1.15);
      await chip(s, x + 0.62, y + 0.575, 0.6, steps[i][0], steps[i][1]);
      s.addText(steps[i][2], { x: x + 1.15, y, w: 2.7, h: 1.15, valign: "middle", fontFace: DISP, bold: true, fontSize: 17, color: INK });
      if (i < steps.length - 1) s.addShape("line", { x: x + 2.0, y: y + 1.15, w: 0, h: 0.35, line: { color: FAINT, width: 1.6, endArrowType: "triangle" } });
    }
    s.addNotes("This is the moment the whole talk has been building to. Say it slowly. 'Remember the code at the start? Every idea you sent went into a queue. And while I was up here talking about prompts and levers and loops, the crew never stopped. Eight agents. One laptop. No internet. Let me show you what they made.' Then switch the projector to the gallery.");
  }

  // =============================================== THE WALL
  {
    const s = slide();
    dot(s, MX + 0.12, 0.78, 0.2, INDIGO);
    s.addText("THE WALL", { x: MX + 0.4, y: 0.62, w: 5, h: 0.32, fontFace: MONO, fontSize: 12, color: INDIGO, charSpacing: 4, valign: "middle" });
    s.addText([{ text: "Everyone's, ", options: { color: INK } }, { text: "all at once.", options: { color: INDIGO } }],
      { x: 0, y: 1.6, w: W, h: 1.1, align: "center", fontFace: DISP, bold: true, fontSize: 46 });
    s.addText("Find yours. Your name is on it.",
      { x: 0, y: 2.85, w: W, h: 0.5, align: "center", fontFace: BODY, fontSize: 19, color: MUTED });
    const cols = [["a name", INDIGO], ["artwork", VIOLET], ["copy", TEAL], ["a palette", AMBER], ["a critique", ROSE]];
    const cw = 2.1, gap = 0.28, tot = cols.length * cw + (cols.length - 1) * gap;
    let cx = (W - tot) / 2;
    for (let i = 0; i < cols.length; i++) {
      s.addShape("roundRect", { x: cx, y: 3.75, w: cw, h: 1.5, rectRadius: 0.12, fill: { color: CARD }, line: { color: cols[i][1], width: 1.4 }, shadow: softShadow() });
      s.addText(cols[i][0], { x: cx, y: 3.75, w: cw, h: 1.5, align: "center", valign: "middle", fontFace: DISP, bold: true, fontSize: 15, color: INK });
      cx += cw + gap;
    }
    s.addText("every page invented from scratch, by a different run of the same eight agents",
      { x: 2, y: 5.6, w: W - 4, h: 0.5, align: "center", fontFace: MONO, fontSize: 12, color: FAINT });
    s.addText("open  /gallery  on the projector",
      { x: 2, y: 6.4, w: W - 4, h: 0.5, align: "center", fontFace: MONO, fontSize: 13, color: INDIGO });
    s.addNotes("Put the gallery on the projector and just let the room look for a few seconds. Do not talk over it. Then: 'Every one of these has its own name, its own artwork, its own colours, its own copy, and its own honest criticism. Same eight agents, run once per idea, on one laptop, while I was busy talking.' Point out one or two of the funnier ones by name. Let people find theirs.");
  }

  // =============================================== 24 CLOSE + QR
  {
    const s = slide();
    eyebrow(s, "The takeaway", 0.7);
    s.addText([
      { text: "Your codebase\nbecomes a ", options: { color: INK } },
      { text: "team\nyou direct", options: { color: INDIGO } },
      { text: " in English.", options: { color: INK } },
    ], { x: MX, y: 1.45, w: 6.5, h: 2.6, fontFace: DISP, bold: true, fontSize: 36, lineSpacing: 44 });
    s.addText("One prompt. Many workers. In Java, on your own machine. The skill ahead is knowing what to ask, and how to constrain the answer.",
      { x: MX, y: 4.55, w: 6.3, h: 1.4, fontFace: BODY, fontSize: 16, color: MUTED, lineSpacing: 25 });
    s.addText([
      { text: "Nevin Tom", options: { color: INK, bold: true } },
      { text: "   /   BBD", options: { color: INDIGO, bold: true } },
      { text: "\nwhat do you want to break first?", options: { color: FAINT } },
    ], { x: MX, y: 6.2, w: 6, h: 0.9, fontFace: MONO, fontSize: 12.5, lineSpacing: 22 });
    // two QR cards, fitted inside the right margin
    const qrs = [
      ["FiLinkedin", INDIGO, "LinkedIn", "Nevin Tom", await qrData(LINKEDIN)],
      ["FiGithub", INK, "GitHub", "the demo repo", await qrData(GITHUB)],
    ];
    const qw = 2.05, qh = 3.45, gx = 0.35, x0 = 8.05, y0 = 1.85;
    for (let i = 0; i < qrs.length; i++) {
      const x = x0 + i * (qw + gx);
      card(s, x, y0, qw, qh);
      s.addImage({ data: qrs[i][4], x: x + (qw - 1.5) / 2, y: y0 + 0.3, w: 1.5, h: 1.5 });
      await chip(s, x + qw / 2, y0 + 2.28, 0.46, qrs[i][0], qrs[i][1]);
      s.addText(qrs[i][2], { x, y: y0 + 2.6, w: qw, h: 0.35, align: "center", fontFace: DISP, bold: true, fontSize: 15, color: INK });
      s.addText(qrs[i][3], { x, y: y0 + 2.94, w: qw, h: 0.3, align: "center", fontFace: BODY, fontSize: 11.5, color: MUTED });
    }
    s.addNotes("Land the plane. A function that guesses, given a loop, a stack, six levers, and a crew, learns to command a team, in Java, offline. The real skill is knowing what to ask and how to constrain the answer. Scan to connect on LinkedIn and grab the code on GitHub. What do you want to break first?");
  }

  // Absolute, from this file: a relative path silently wrote the deck into
  // whatever directory the generator happened to be run from, which is how a
  // dead QR code survived a rebuild.
  const OUT = require("path").join(__dirname, "..", "One-Prompt-Many-Workers-Light.pptx");
  await pres.writeFile({ fileName: OUT });
  console.log(`wrote light deck, ${count} slides. LinkedIn:`, LINKEDIN, "GitHub:", GITHUB);
}

build().catch(e => { console.error(e); process.exit(1); });
