// One Prompt. Many Workers. -- BBD talk deck generator
// Dark, premium, motif-driven. No accent stripes, no dashes, no slop.
const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const Fi = require("react-icons/fi");

// ---------- palette ----------
const INK = "0B0D12", INK2 = "12151E", PANEL = "161B27", PANEL2 = "0E1119", LINE = "2A3040";
const PAPER = "ECE7DE", MUTED = "9297A8", FAINT = "5A6072";
const AMBER = "F5A524", AMBER2 = "F7B84E";
const TEAL = "2DD4BF", VIOLET = "8B5CF6", ROSE = "FB7185", SKY = "38BDF8";

// ---------- fonts (safe-list, render true to width) ----------
const SERIF = "Cambria";      // display, gravitas
const SANS = "Calibri";       // body
const MONO = "Consolas";      // code + labels

// ---------- icon rasteriser ----------
const iconCache = new Map();
async function icon(name, hex) {
  const key = name + hex;
  if (iconCache.has(key)) return iconCache.get(key);
  const Comp = Fi[name];
  let svg = ReactDOMServer.renderToStaticMarkup(React.createElement(Comp, { size: 256 }));
  svg = svg.replace(/currentColor/g, "#" + hex);
  const png = await sharp(Buffer.from(svg)).png().toBuffer();
  const data = "image/png;base64," + png.toString("base64");
  iconCache.set(key, data);
  return data;
}

const pres = new pptxgen();
pres.defineLayout({ name: "W", width: 13.333, height: 7.5 });
pres.layout = "W";
const W = 13.333, H = 7.5, MX = 0.92;

// ---------- helpers ----------
function slide() {
  const s = pres.addSlide();
  s.background = { color: INK };
  return s;
}
function eyebrow(s, text, y = 0.62, color = AMBER) {
  s.addText(text.toUpperCase(), {
    x: MX, y, w: 10, h: 0.32, fontFace: SANS, bold: true, fontSize: 11,
    color, charSpacing: 4, align: "left",
  });
}
function shadow() { return { type: "outer", color: "000000", blur: 14, offset: 4, angle: 90, opacity: 0.55 }; }
function card(s, x, y, w, h, opts = {}) {
  s.addShape("roundRect", {
    x, y, w, h, rectRadius: 0.11,
    fill: { color: opts.fill || PANEL },
    line: { color: opts.line || LINE, width: 1 },
    shadow: shadow(),
  });
}
function dot(s, x, y, d, color, glow) {
  const o = { x: x - d / 2, y: y - d / 2, w: d, h: d, fill: { color }, line: { color, width: 0 } };
  if (glow) o.shadow = { type: "outer", color, blur: 12, offset: 0, angle: 0, opacity: 0.6 };
  s.addShape("ellipse", o);
}
// straight radiating line from origin (ox,oy) to (ex,ey)
function ray(s, ox, oy, ex, ey, color) {
  const x = Math.min(ox, ex), y = Math.min(oy, ey), w = Math.abs(ex - ox), h = Math.abs(ey - oy);
  s.addShape("line", { x, y, w, h, flipV: ey < oy, line: { color, width: 1.5 } });
}

async function build() {

  // =========================================================== 1 TITLE
  {
    const s = slide();
    eyebrow(s, "BBD Tech Talk  ·  Java");
    s.addText([
      { text: "One Prompt.", options: { breakLine: true } },
      { text: "Many Workers", options: {} },
      { text: ".", options: { color: AMBER } },
    ], { x: MX, y: 1.5, w: 8, h: 2.6, fontFace: SERIF, fontSize: 66, color: PAPER, lineSpacing: 62, align: "left" });
    s.addText("Building multi-agent AI systems in Java, where a single sentence commands a whole crew.",
      { x: MX, y: 4.2, w: 6.4, h: 1, fontFace: SANS, fontSize: 17, color: MUTED, lineSpacing: 26 });
    s.addText([
      { text: "Nevin Tom", options: { color: PAPER, bold: true } },
      { text: "  @  BBD", options: { color: AMBER } },
      { text: "\n local model  ·  no cloud  ·  live code", options: { color: MUTED, breakLine: false } },
    ], { x: MX, y: 5.5, w: 7, h: 0.9, fontFace: MONO, fontSize: 12.5, lineSpacing: 24 });
    // fan-out motif
    const ox = 9.05, oy = 3.75, ex = 11.7;
    const targets = [[1.35, TEAL], [2.55, VIOLET], [3.75, AMBER], [4.95, ROSE], [6.15, SKY]];
    targets.forEach(([ey, c]) => ray(s, ox, oy, ex, ey, c));
    targets.forEach(([ey, c]) => dot(s, ex, ey, 0.22, c));
    dot(s, ox, oy, 0.42, AMBER, true);
    s.addText("PROMPT", { x: ox - 0.85, y: oy + 0.28, w: 1.7, h: 0.3, align: "center", fontFace: MONO, fontSize: 9, color: AMBER, charSpacing: 2 });
    s.addText("WORKERS", { x: ex - 0.6, y: 6.35, w: 1.7, h: 0.3, align: "center", fontFace: MONO, fontSize: 9, color: MUTED, charSpacing: 2 });
    s.addNotes("Wait for the room to settle. That fan is the whole talk: one thing becoming a team. Tonight we build it for real, in Java, on this laptop, with a model that never phones home. Say your name, thank BBD.");
  }

  // =========================================================== 2 HOOK
  {
    const s = slide();
    eyebrow(s, "The promise");
    s.addText([
      { text: "I typed ", options: { color: PAPER } },
      { text: "one sentence.", options: { color: AMBER, italic: true } },
      { text: "\nMy laptop hired ", options: { color: PAPER } },
      { text: "five workers.", options: { color: AMBER, italic: true } },
    ], { x: MX, y: 1.9, w: 7.1, h: 2.4, fontFace: SERIF, fontSize: 40, lineSpacing: 48 });
    s.addText("No cloud. No API bill. It never touched the internet. By the end of tonight you will have built it yourself.",
      { x: MX, y: 4.5, w: 6.2, h: 1.2, fontFace: SANS, fontSize: 17, color: MUTED, lineSpacing: 26 });
    // 1 -> five chips
    s.addText("1", { x: 8.4, y: 2.3, w: 1.6, h: 2.6, fontFace: SERIF, italic: true, fontSize: 150, color: AMBER, align: "center" });
    s.addShape("line", { x: 10.05, y: 3.62, w: 0.62, h: 0, line: { color: FAINT, width: 2, endArrowType: "triangle" } });
    const crew = [["engineer", TEAL], ["marketer", VIOLET], ["designer", AMBER], ["legal", ROSE], ["devrel", SKY]];
    crew.forEach(([label, c], i) => {
      const cy = 2.45 + i * 0.6;
      dot(s, 10.95, cy + 0.13, 0.2, c);
      s.addText(label.toUpperCase(), { x: 11.2, y: cy - 0.06, w: 2.0, h: 0.38, fontFace: MONO, fontSize: 11, color: MUTED, charSpacing: 2, valign: "middle" });
    });
    s.addNotes("The hook, delivered slowly. Last week I typed one sentence, fourteen words, and my laptop assembled a team of five, split the work, handed me back a plan. No cloud, no bill, no internet. Two reactions: that is magic, then, that is horrifying, because it was so little code.");
  }

  // =========================================================== 3 ROADMAP
  {
    const s = slide();
    eyebrow(s, "Where we're going");
    s.addText([
      { text: "Five rungs from ", options: { color: PAPER } },
      { text: "a sentence", options: { color: AMBER, italic: true } },
      { text: " to ", options: { color: PAPER } },
      { text: "a swarm.", options: { color: AMBER, italic: true } },
    ], { x: MX, y: 1.2, w: 11, h: 1, fontFace: SERIF, fontSize: 34 });
    const rows = [
      ["01", "prompt", "The atom.", "What a prompt actually is.", false],
      ["02", "agent", "The loop", "that turns talk into action.", false],
      ["03", "stack", "The infrastructure,", "and why Java.", false],
      ["04", "control", "Six levers", "beyond the prompt.", true],
      ["05", "swarm", "One prompt, many workers,", "live.", false],
    ];
    rows.forEach((r, i) => {
      const y = 2.5 + i * 0.82;
      s.addText(r[0] + "  ·  " + r[1], { x: MX, y, w: 2.6, h: 0.5, fontFace: MONO, fontSize: 12, color: r[4] ? AMBER : MUTED, charSpacing: 2, valign: "middle" });
      s.addText([
        { text: r[2] + " ", options: { color: PAPER, bold: true } },
        { text: r[3], options: { color: MUTED } },
      ], { x: 3.7, y, w: 8.5, h: 0.5, fontFace: SANS, fontSize: 17, valign: "middle" });
    });
    s.addNotes("Quick map. We climb a short ladder: a prompt, an agent, the infrastructure, the control panel almost nobody talks about, and then we point one prompt at a whole crew and run it live. Promise the demo early, it buys patience.");
  }

  // =========================================================== ACT dividers
  async function act(numeral, roman, title, subtitle, cool, ghostName) {
    const s = slide();
    // faint ghost motif, bottom right
    s.addImage({ data: await icon(ghostName, LINE), x: 8.7, y: 3.1, w: 4.4, h: 4.4, transparency: 45 });
    s.addText(roman, { x: MX, y: 1.3, w: 4, h: 1.6, fontFace: SERIF, italic: true, fontSize: 96, color: LINE });
    eyebrow(s, "Act " + numeral, 3.15, cool ? TEAL : AMBER);
    s.addText(title, { x: MX, y: 3.5, w: 9, h: 1.5, fontFace: SERIF, fontSize: 52, color: PAPER, lineSpacing: 50 });
    s.addText(subtitle, { x: MX, y: title.includes("\n") ? 5.5 : 5.0, w: 7.5, h: 0.9, fontFace: SANS, fontSize: 17, color: MUTED, lineSpacing: 25 });
    return s;
  }

  // 4 ACT 1
  (await act("one", "i.", "The Prompt", "The smallest unit of the whole thing, and the most misunderstood.", false, "FiMessageSquare"))
    .addNotes("Reset the room. Slow down. Rung one, the prompt, and almost everyone misunderstands what it is.");

  // =========================================================== 5 WHAT A PROMPT IS
  {
    const s = slide();
    eyebrow(s, "The atom");
    s.addText([
      { text: "A model is just a ", options: { color: PAPER } },
      { text: "function.", options: { color: AMBER, italic: true } },
    ], { x: MX, y: 1.5, w: 6.4, h: 1.6, fontFace: SERIF, fontSize: 40, lineSpacing: 44 });
    s.addText("Text goes in. Text comes out. No memory of a moment ago. No hands. No way to check its own work.",
      { x: MX, y: 3.2, w: 5.7, h: 1.5, fontFace: SANS, fontSize: 18, color: MUTED, lineSpacing: 28 });
    s.addText("A prompt is you calling that function.",
      { x: MX, y: 5.2, w: 6, h: 0.5, fontFace: MONO, fontSize: 13, color: FAINT, charSpacing: 1 });
    // code card
    card(s, 7.4, 1.7, 5.0, 3.8, { fill: PANEL2 });
    s.addText([
      { text: "String", options: { color: VIOLET } },
      { text: " reply = ", options: { color: FAINT } },
      { text: "\n   model.", options: { color: PAPER } },
      { text: "chat", options: { color: AMBER } },
      { text: "(", options: { color: FAINT } },
      { text: "\"summarise this\"", options: { color: TEAL } },
      { text: ");", options: { color: FAINT } },
      { text: "\n\n// text in  ->  text out", options: { color: FAINT } },
      { text: "\n// no memory. no hands.", options: { color: FAINT } },
      { text: "\n// ask twice -> two answers.", options: { color: FAINT } },
    ], { x: 7.8, y: 2.1, w: 4.3, h: 3, fontFace: MONO, fontSize: 15, lineSpacing: 30, valign: "top" });
    s.addNotes("Strip away the marketing and a model is a function. Text in, text out. No memory, no hands, no way to check itself. Ask twice, get two answers. It is a very good guesser, but a guesser. A prompt is you calling that function.");
  }

  // =========================================================== 6 THREE WALLS
  {
    const s = slide();
    eyebrow(s, "The ceiling");
    s.addText([
      { text: "A raw prompt hits ", options: { color: PAPER } },
      { text: "three walls.", options: { color: AMBER, italic: true } },
    ], { x: MX, y: 1.2, w: 11, h: 1, fontFace: SERIF, fontSize: 34 });
    const walls = [
      ["FiDatabase", "No memory", "Every call starts from zero. It forgot the last sentence the instant it finished it."],
      ["FiZap", "No hands", "It can describe sending the email in beautiful detail. It cannot send it."],
      ["FiShield", "No proof", "It sounds equally sure when it is right and when it is making things up."],
    ];
    const cw = 3.7, gap = 0.35, x0 = MX;
    for (let i = 0; i < walls.length; i++) {
      const x = x0 + i * (cw + gap), y = 2.6, h = 3.2;
      card(s, x, y, cw, h);
      s.addImage({ data: await icon(walls[i][0], ROSE), x: x + 0.4, y: y + 0.45, w: 0.5, h: 0.5 });
      s.addText("WALL 0" + (i + 1), { x: x + cw - 1.5, y: y + 0.5, w: 1.2, h: 0.3, align: "right", fontFace: MONO, fontSize: 9.5, color: ROSE, charSpacing: 2 });
      s.addText(walls[i][1], { x: x + 0.4, y: y + 1.2, w: cw - 0.8, h: 0.6, fontFace: SERIF, fontSize: 23, color: PAPER });
      s.addText(walls[i][2], { x: x + 0.4, y: y + 1.85, w: cw - 0.8, h: 1.1, fontFace: SANS, fontSize: 14, color: MUTED, lineSpacing: 20 });
    }
    s.addNotes("Three walls, fast. No memory, every call starts from zero. No hands, it can describe the email but not send it. No proof, it sounds equally sure when right and when inventing. A brilliant intern who never remembers your name and occasionally invents a co-worker.");
  }

  // 7 ACT 2
  (await act("two", "ii.", "The Agent", "Same model. Now it can remember, act, and try again.", true, "FiRefreshCw"))
    .addNotes("We give the function three things it is missing: a memory, some hands, and a loop. And it stops being a chatbot. It becomes an agent.");

  // =========================================================== 8 EQUATION
  {
    const s = slide();
    eyebrow(s, "The equation", 0.62, AMBER);
    const tiles = [["FiCpu", "model", "the brain", SKY], ["FiTool", "tools", "the hands", VIOLET], ["FiRefreshCw", "a loop", "the magic", TEAL]];
    const tw = 2.5, tgap = 1.15, total = tiles.length * tw + (tiles.length - 1) * tgap;
    let x = (W - total) / 2; const ty = 2.2;
    for (let i = 0; i < tiles.length; i++) {
      const [ic, name, sub, col] = tiles[i];
      s.addShape("roundRect", { x, y: ty, w: tw, h: tw, rectRadius: 0.16, fill: { color: PANEL }, line: { color: i === 2 ? TEAL : LINE, width: 1 }, shadow: shadow() });
      s.addImage({ data: await icon(ic, col), x: x + tw / 2 - 0.35, y: ty + 0.5, w: 0.7, h: 0.7 });
      s.addText(name, { x, y: ty + 1.35, w: tw, h: 0.5, align: "center", fontFace: SERIF, fontSize: 22, color: col });
      s.addText(sub.toUpperCase(), { x, y: ty + 1.85, w: tw, h: 0.35, align: "center", fontFace: MONO, fontSize: 10, color: MUTED, charSpacing: 2 });
      if (i < tiles.length - 1) s.addText("+", { x: x + tw, y: ty + tw / 2 - 0.4, w: tgap, h: 0.8, align: "center", fontFace: SERIF, fontSize: 34, color: FAINT });
      x += tw + tgap;
    }
    s.addText("That is an agent. Everything else is decoration. The loop is where the magic actually lives.",
      { x: 2.5, y: 5.4, w: 8.33, h: 0.8, align: "center", fontFace: SANS, fontSize: 18, color: MUTED, lineSpacing: 26 });
    s.addNotes("The whole definition, worth remembering: an agent is a model, plus tools it is allowed to call, wrapped in a loop. Everything else anyone sells you is a flavour of this. The model is the brain, tools are the hands, the loop is where the magic lives.");
  }

  // =========================================================== 9 THE LOOP
  {
    const s = slide();
    eyebrow(s, "The whole trick", 0.62, TEAL);
    s.addText([
      { text: "Reason. Act.\nObserve. ", options: { color: PAPER } },
      { text: "Again.", options: { color: AMBER, italic: true } },
    ], { x: MX, y: 1.7, w: 5.6, h: 1.8, fontFace: SERIF, fontSize: 40, lineSpacing: 46 });
    s.addText("The model does not answer once. It works in a loop until the job is actually done, checking its own output along the way.",
      { x: MX, y: 3.7, w: 5.3, h: 1.5, fontFace: SANS, fontSize: 17, color: MUTED, lineSpacing: 26 });
    s.addText("Take away the loop and you are back to a chatbot.",
      { x: MX, y: 5.4, w: 5.6, h: 0.5, fontFace: MONO, fontSize: 12, color: FAINT });
    // cycle: three nodes in a triangle with arrows
    const cx = 9.7, cy = 3.9, r = 1.7;
    const P = [[cx, cy - r, "REASON", TEAL], [cx + r * 0.87, cy + r * 0.5, "ACT", VIOLET], [cx - r * 0.87, cy + r * 0.5, "OBSERVE", AMBER]];
    // arrows P0->P1->P2->P0
    for (let i = 0; i < 3; i++) {
      const a = P[i], b = P[(i + 1) % 3];
      const x = Math.min(a[0], b[0]), y = Math.min(a[1], b[1]), w = Math.abs(b[0] - a[0]), h = Math.abs(b[1] - a[1]);
      s.addShape("line", { x, y, w, h, flipH: b[0] < a[0], flipV: b[1] < a[1], line: { color: a[3], width: 2, endArrowType: "triangle" } });
    }
    P.forEach(p => { dot(s, p[0], p[1], 0.26, p[3]); });
    s.addText("REASON", { x: cx - 1, y: cy - r - 0.55, w: 2, h: 0.3, align: "center", fontFace: MONO, fontSize: 11, color: PAPER, charSpacing: 1 });
    s.addText("ACT", { x: cx + r * 0.87 + 0.15, y: cy + r * 0.5 - 0.15, w: 1.4, h: 0.3, fontFace: MONO, fontSize: 11, color: PAPER, charSpacing: 1 });
    s.addText("OBSERVE", { x: cx - r * 0.87 - 1.55, y: cy + r * 0.5 - 0.15, w: 1.4, h: 0.3, align: "right", fontFace: MONO, fontSize: 11, color: PAPER, charSpacing: 1 });
    s.addText("done?", { x: cx - 0.7, y: cy - 0.15, w: 1.4, h: 0.3, align: "center", fontFace: MONO, fontSize: 10, color: FAINT });
    s.addNotes("The single most important idea. It reasons, it acts by calling a tool, it observes what came back, and it asks: am I done? If not, around again. That circle is the difference between a model that talks and a system that finishes.");
  }

  // 10 ACT 3
  (await act("three", "iii.", "The Infrastructure", "What you actually need underneath, and why the JVM is a great place to put it.", false, "FiLayers"))
    .addNotes("A loop on a slide is easy. A loop you can run at BBD, on real infrastructure, that a client will trust, needs a stack. Let's build one.");

  // =========================================================== 11 STACK
  {
    const s = slide();
    eyebrow(s, "The anatomy");
    s.addText([
      { text: "Five layers under every ", options: { color: PAPER } },
      { text: "agent.", options: { color: AMBER, italic: true } },
    ], { x: MX, y: 1.15, w: 11, h: 1, fontFace: SERIF, fontSize: 34 });
    const layers = [
      ["FiServer", "runtime", "The model host.", "Ollama, running Qwen on your machine.", false],
      ["FiShare2", "orchestration", "The loop and wiring.", "LangChain4j or Spring AI.", false],
      ["FiTool", "tools", "The hands.", "Java methods the model may call.", false],
      ["FiDatabase", "memory", "The notebook.", "What it carries between turns.", false],
      ["FiBox", "structure", "Typed output.", "Answers as objects, not prose.", true],
    ];
    for (let i = 0; i < layers.length; i++) {
      const y = 2.5 + i * 0.86;
      const col = layers[i][5] ? AMBER : MUTED;
      s.addImage({ data: await icon(layers[i][0], col), x: MX, y: y + 0.02, w: 0.34, h: 0.34 });
      s.addText(layers[i][1], { x: MX + 0.55, y, w: 2.5, h: 0.4, fontFace: MONO, fontSize: 12, color: col, charSpacing: 2, valign: "middle" });
      s.addText([
        { text: layers[i][2] + " ", options: { color: PAPER, bold: true } },
        { text: layers[i][3], options: { color: MUTED } },
      ], { x: 4.3, y, w: 8, h: 0.4, fontFace: SANS, fontSize: 16.5, valign: "middle" });
    }
    s.addNotes("Five layers. A runtime to hold the model, Ollama with Qwen. An orchestration layer that runs the loop, LangChain4j or Spring AI. Tools, the hands. Memory, the notebook. And structured output, so the answer comes back as data, not a paragraph. Hold onto that last one.");
  }

  // =========================================================== 12 WHY JAVA
  {
    const s = slide();
    eyebrow(s, "The obvious question");
    s.addText([{ text: "Why ", options: { color: PAPER } }, { text: "Java?", options: { color: AMBER, italic: true } }],
      { x: MX, y: 1.5, w: 6, h: 1.2, fontFace: SERIF, fontSize: 44 });
    s.addText([
      { text: "Because the systems your clients trust with real money already run on the JVM. The agent should live " },
      { text: "next to the business", options: { color: PAPER, bold: true } },
      { text: ", not in a Python service bolted on the side." },
    ], { x: MX, y: 2.9, w: 5.7, h: 2, fontFace: SANS, fontSize: 17, color: MUTED, lineSpacing: 27 });
    s.addText("Behind Spring Security. Inside your transactions. Beside your data.",
      { x: MX, y: 5.2, w: 5.7, h: 0.8, fontFace: MONO, fontSize: 12, color: FAINT, lineSpacing: 20 });
    const rows = [
      ["FiLink", "LangChain4j", "agents, tools and AI Services, the idiomatic way", AMBER],
      ["FiFeather", "Spring AI", "the same, wired into Spring Boot", AMBER],
      ["FiCheck", "the point", "type-safe, testable, production-grade agents", TEAL],
    ];
    for (let i = 0; i < rows.length; i++) {
      const y = 2.2 + i * 1.15, x = 7.3;
      card(s, x, y, 5.1, 0.95);
      s.addImage({ data: await icon(rows[i][0], rows[i][3]), x: x + 0.35, y: y + 0.3, w: 0.36, h: 0.36 });
      s.addText(rows[i][1], { x: x + 0.95, y: y + 0.14, w: 3.9, h: 0.35, fontFace: MONO, fontSize: 12, color: rows[i][3], charSpacing: 1 });
      s.addText(rows[i][2], { x: x + 0.95, y: y + 0.46, w: 3.95, h: 0.4, fontFace: SANS, fontSize: 13.5, color: MUTED });
    }
    s.addNotes("Every AI tutorial online is Python, and Python is lovely. But your systems of record, the ones under audit, run on the JVM. You do not have to rewrite any of it. LangChain4j and Spring AI mean the AI lives next to the business, behind Spring Security, inside your transactions, next to your data.");
  }

  // =========================================================== 13 LOCAL + FREE
  {
    const s = slide();
    eyebrow(s, "The model, tonight");
    s.addText([
      { text: "Qwen.", options: { color: AMBER, italic: true } },
      { text: " Local. Free.\nNever leaves the room.", options: { color: PAPER } },
    ], { x: MX, y: 1.6, w: 6, h: 2, fontFace: SERIF, fontSize: 38, lineSpacing: 44 });
    s.addText("Ollama serves the model from this laptop. No cloud call, no per-token bill, no data leaving the building. That last part is what your regulated clients care about most.",
      { x: MX, y: 3.9, w: 5.6, h: 1.8, fontFace: SANS, fontSize: 17, color: MUTED, lineSpacing: 27 });
    // illustration: building boundary + laptop + shield + dots + no cloud
    const bx = 7.7, by = 1.7, bw = 4.2, bh = 3.9;
    s.addShape("roundRect", { x: bx, y: by, w: bw, h: bh, rectRadius: 0.12, fill: { color: "00000000", transparency: 100 }, line: { color: LINE, width: 1.2, dashType: "dash" } });
    s.addText("YOUR BUILDING", { x: bx + 0.3, y: by + 0.22, w: 3, h: 0.3, fontFace: MONO, fontSize: 10, color: TEAL, charSpacing: 2 });
    // laptop
    const lx = bx + 0.75, ly = by + 0.95, lw = bw - 1.5, lh = 1.7;
    s.addShape("roundRect", { x: lx, y: ly, w: lw, h: lh, rectRadius: 0.08, fill: { color: PANEL2 }, line: { color: MUTED, width: 1.2 } });
    s.addShape("trapezoid", { x: lx - 0.3, y: ly + lh, w: lw + 0.6, h: 0.28, fill: { color: INK2 }, line: { color: MUTED, width: 1.2 }, rotate: 180 });
    // shield (chevron) + check
    s.addImage({ data: await icon("FiShield", AMBER), x: lx + lw / 2 - 0.32, y: ly + 0.45, w: 0.64, h: 0.64 });
    s.addText("✓", { x: lx + lw / 2 - 0.32, y: ly + 0.55, w: 0.64, h: 0.5, align: "center", fontFace: SANS, fontSize: 16, color: AMBER, bold: true });
    dot(s, lx + 0.35, ly + 0.4, 0.16, TEAL);
    dot(s, lx + lw - 0.35, ly + 0.4, 0.16, VIOLET);
    dot(s, lx + 0.35, ly + lh - 0.4, 0.16, SKY);
    dot(s, lx + lw - 0.35, ly + lh - 0.4, 0.16, ROSE);
    s.addText("every token stays inside", { x: bx, y: by + bh - 0.5, w: bw, h: 0.3, align: "center", fontFace: MONO, fontSize: 10, color: MUTED, charSpacing: 1 });
    // no cloud outside
    s.addImage({ data: await icon("FiCloud", FAINT), x: 12.15, y: 1.75, w: 0.85, h: 0.85 });
    s.addShape("line", { x: 12.1, y: 1.7, w: 0.95, h: 0.95, line: { color: ROSE, width: 2 } });
    s.addText("no cloud", { x: 11.85, y: 2.7, w: 1.45, h: 0.3, align: "center", fontFace: MONO, fontSize: 10, color: FAINT });
    s.addNotes("Qwen, through Ollama, entirely on this laptop. The client's data never leaves the building. No token bill that scales with success. Works on a plane. For a bank or an insurer, it never leaves the building is not a nice to have, it is the whole conversation.");
  }

  // 14 ACT 4
  (await act("four", "iv.", "Control Beyond\nthe Prompt", "The prompt is one lever. There are five more, and they are where reliability comes from.", false, "FiSliders"))
    .addNotes("The heart of the talk. Most people think the prompt is the steering wheel. It is one control out of six. Let me show you the whole cockpit.");

  // =========================================================== 15 SIX LEVERS
  {
    const s = slide();
    eyebrow(s, "The cockpit");
    s.addText([
      { text: "Six ways to steer. Only ", options: { color: PAPER } },
      { text: "one", options: { color: AMBER, italic: true } },
      { text: " is the prompt.", options: { color: PAPER } },
    ], { x: MX, y: 1.1, w: 11, h: 0.9, fontFace: SERIF, fontSize: 32 });
    const levers = [
      ["FiMessageSquare", "lever 01", "The prompt", "What you want. The instruction everyone knows.", AMBER],
      ["FiTool", "lever 02", "Tools", "What it is allowed to touch. The capability boundary.", TEAL],
      ["FiCode", "lever 03", "Schema", "The shape of the answer. A typed object, not prose.", VIOLET],
      ["FiShield", "lever 04", "Guardrails", "Validation that rejects bad output before it escapes.", ROSE],
      ["FiDatabase", "lever 05", "Memory", "What it remembers, and pointedly what it forgets.", SKY],
      ["FiShare2", "lever 06", "Orchestration", "Who talks to whom. Tonight, a crew.", AMBER],
    ];
    const cw = 3.72, ch = 2.0, gx = 0.28, gy = 0.28, x0 = MX, y0 = 2.25;
    for (let i = 0; i < levers.length; i++) {
      const col = i % 3, row = Math.floor(i / 3);
      const x = x0 + col * (cw + gx), y = y0 + row * (ch + gy);
      card(s, x, y, cw, ch);
      s.addImage({ data: await icon(levers[i][0], levers[i][4]), x: x + cw - 0.72, y: y + 0.32, w: 0.4, h: 0.4 });
      s.addText(levers[i][1].toUpperCase(), { x: x + 0.35, y: y + 0.28, w: 2.4, h: 0.3, fontFace: MONO, fontSize: 9.5, color: MUTED, charSpacing: 2 });
      s.addText(levers[i][2], { x: x + 0.35, y: y + 0.6, w: cw - 0.7, h: 0.5, fontFace: SERIF, fontSize: 21, color: i === 0 ? AMBER2 : PAPER });
      s.addText(levers[i][3], { x: x + 0.35, y: y + 1.15, w: cw - 0.7, h: 0.75, fontFace: SANS, fontSize: 12.5, color: MUTED, lineSpacing: 17 });
    }
    s.addNotes("The prompt says what. Tools decide what it can touch. Schema decides the shape. Guardrails reject bad output. Memory decides what it knows and forgets. Orchestration decides who talks to whom. You barely touch the prompt, you change the constraints around it.");
  }

  // =========================================================== 16 CONSTRAIN
  {
    const s = slide();
    eyebrow(s, "The shift");
    s.addText([
      { text: "You don't ", options: { color: PAPER } },
      { text: "program", options: { color: AMBER, italic: true } },
      { text: " an agent.\nYou ", options: { color: PAPER } },
      { text: "constrain", options: { color: AMBER, italic: true } },
      { text: " it.", options: { color: PAPER } },
    ], { x: 1.2, y: 1.4, w: 11, h: 1.8, fontFace: SERIF, fontSize: 46, align: "center", lineSpacing: 52 });
    // constraint diagram
    const cx = W / 2, boxW = 1.5, boxH = 1.0, bx = cx - boxW / 2, by = 4.35;
    s.addShape("roundRect", { x: bx, y: by, w: boxW, h: boxH, rectRadius: 0.12, fill: { color: "00000000", transparency: 100 }, line: { color: AMBER, width: 1.5, dashType: "dash" } });
    dot(s, cx, by + boxH / 2, 0.28, AMBER, true);
    const labels = [
      ["PROMPT", "says what", TEAL, cx, by - 0.55, "ctr"],
      ["TOOLS", "say how far", VIOLET, bx + boxW + 0.35, by + boxH / 2 - 0.16, "l"],
      ["SCHEMA", "says what shape", SKY, cx, by + boxH + 0.28, "ctr"],
      ["ORCHESTRATION", "says who", ROSE, bx - 0.35, by + boxH / 2 - 0.16, "r"],
    ];
    labels.forEach(([t, d, c, lx, ly, al]) => {
      const align = al === "ctr" ? "center" : al === "l" ? "left" : "right";
      const w = 3.2, x = al === "ctr" ? lx - w / 2 : al === "l" ? lx : lx - w;
      s.addText([{ text: t + "  ", options: { color: c, bold: true } }, { text: d, options: { color: FAINT } }],
        { x, y: ly, w, h: 0.32, align, fontFace: MONO, fontSize: 11, charSpacing: 1 });
    });
    s.addText("Good agent design is good constraint design.",
      { x: 2.5, y: 6.5, w: 8.33, h: 0.5, align: "center", fontFace: SANS, fontSize: 17, color: MUTED });
    s.addNotes("The line to remember, then stop talking for two seconds. You don't program an agent, you constrain it. The prompt says what, the tools say how far, the schema says what shape, the orchestration says who. Good agent engineering is good constraint design.");
  }

  // 17 ACT 5
  (await act("five", "v.", "One Prompt.\nMany Workers.", "The orchestrator-worker pattern. Then we run it, live.", true, "FiShare2"))
    .addNotes("Lift the energy, this is what they came for. Lever six, orchestration, is the fun one. Instead of one agent doing everything, we point a single prompt at a whole crew. One becomes many.");

  // =========================================================== 18 ARCHITECTURE
  {
    const s = slide();
    eyebrow(s, "The pattern", 0.62, TEAL);
    s.addText([{ text: "Plan. Dispatch. ", options: { color: PAPER } }, { text: "Synthesize.", options: { color: AMBER, italic: true } }],
      { x: MX, y: 1.15, w: 11, h: 0.9, fontFace: SERIF, fontSize: 34 });
    const midY = 3.7;
    function box(x, y, w, h, title, sub, col) {
      s.addShape("roundRect", { x, y, w, h, rectRadius: 0.1, fill: { color: PANEL }, line: { color: col || LINE, width: 1.2 }, shadow: shadow() });
      s.addText(title, { x, y: y + h / 2 - 0.32, w, h: 0.4, align: "center", fontFace: MONO, fontSize: 13, color: col === LINE ? PAPER : col || PAPER });
      s.addText(sub.toUpperCase(), { x, y: y + h / 2 + 0.05, w, h: 0.3, align: "center", fontFace: MONO, fontSize: 8.5, color: FAINT, charSpacing: 1.5 });
    }
    function arrow(x, y, w) { s.addShape("line", { x, y, w, h: 0, line: { color: FAINT, width: 1.6, endArrowType: "triangle" } }); }
    box(MX, midY - 0.45, 1.7, 0.9, "one prompt", "english", AMBER);
    arrow(2.72, midY, 0.5);
    box(3.32, midY - 0.45, 1.9, 0.9, "Planner", "structured", AMBER);
    arrow(5.32, midY, 0.5);
    // three workers stacked
    const wx = 5.92, ww = 1.85, wh = 0.72;
    box(wx, midY - 1.35, ww, wh, "Worker", "engineer", TEAL);
    box(wx, midY - 0.36, ww, wh, "Worker", "marketer", VIOLET);
    box(wx, midY + 0.63, ww, wh, "Worker", "legal", ROSE);
    arrow(wx + ww + 0.1, midY, 0.5);
    box(wx + ww + 0.7, midY - 0.45, 1.9, 0.9, "Synthesizer", "fan-in", AMBER);
    arrow(wx + ww + 2.7, midY, 0.5);
    box(wx + ww + 3.3, midY - 0.45, 1.6, 0.9, "one plan", "result", AMBER);
    s.addText([
      { text: "The planner ", options: { color: MUTED } },
      { text: "invents", options: { color: AMBER } },
      { text: " the crew. Nobody hardcoded those roles.", options: { color: MUTED } },
    ], { x: 2.5, y: 6.2, w: 8.33, h: 0.5, align: "center", fontFace: SANS, fontSize: 16 });
    s.addNotes("Walk it left to right. One prompt hits a planner. The planner does not do the work, it decides who should and hands back a typed crew. Each worker is the same model wearing a different hat. A synthesizer folds every answer into one plan. Fan out, fan in. And nobody hardcoded those roles, the planner invented them.");
  }

  // =========================================================== 19 WHAT STEERS
  {
    const s = slide();
    eyebrow(s, "What's really steering");
    s.addText([{ text: "The prompt barely ", options: { color: PAPER } }, { text: "changed.", options: { color: AMBER, italic: true } }],
      { x: MX, y: 1.6, w: 6, h: 1.2, fontFace: SERIF, fontSize: 38 });
    s.addText("The power came from the levers around it, not from a cleverer sentence.",
      { x: MX, y: 3.1, w: 5.4, h: 1.2, fontFace: SANS, fontSize: 18, color: MUTED, lineSpacing: 27 });
    const rows = [
      ["lever 01", "Prompt", "one plain English line", false],
      ["lever 03", "Schema", "a sentence becomes a typed crew", true],
      ["lever 06", "Orchestration", "they run as a team", true],
    ];
    for (let i = 0; i < rows.length; i++) {
      const y = 2.1 + i * 1.15, x = 7.2;
      card(s, x, y, 5.2, 0.95, { line: rows[i][3] ? AMBER : LINE });
      s.addText(rows[i][0].toUpperCase(), { x: x + 0.35, y: y + 0.3, w: 1.9, h: 0.35, fontFace: MONO, fontSize: 10.5, color: rows[i][3] ? AMBER : MUTED, charSpacing: 2, valign: "middle" });
      s.addText([{ text: rows[i][1] + ":  ", options: { color: PAPER, bold: true } }, { text: rows[i][2], options: { color: MUTED } }],
        { x: x + 2.2, y: y + 0.3, w: 2.85, h: 0.35, fontFace: SANS, fontSize: 14.5, valign: "middle" });
    }
    s.addNotes("Look what steers the crew. The prompt, lever one, barely changes. The real control is structured output turning a sentence into a typed crew, and orchestration deciding they run as a team. Two levers you could never reach from the prompt alone.");
  }

  // ---------- code slide helper ----------
  function codeSlide(idxLabel, titleRuns, lines, note) {
    const s = slide();
    eyebrow(s, idxLabel);
    s.addText(titleRuns, { x: MX, y: 1.05, w: 11, h: 0.7, fontFace: SERIF, fontSize: 28 });
    card(s, MX, 2.0, W - 2 * MX, 3.7, { fill: PANEL2 });
    s.addText(lines, { x: MX + 0.5, y: 2.35, w: W - 2 * MX - 1, h: 3.0, fontFace: MONO, fontSize: 15.5, lineSpacing: 30, valign: "top" });
    s.addNotes(note);
    return s;
  }

  // 20 CODE 1
  codeSlide("Live code  ·  1 of 2",
    [{ text: "One interface. ", options: { color: PAPER } }, { text: "Every worker.", options: { color: AMBER, italic: true } }],
    [
      { text: "// No implementation. The annotations are the program.", options: { color: FAINT, breakLine: true } },
      { text: "interface ", options: { color: VIOLET } }, { text: "Worker", options: { color: SKY } }, { text: " {", options: { color: PAPER, breakLine: true } },
      { text: "", options: { breakLine: true } },
      { text: "  @SystemMessage", options: { color: AMBER } }, { text: "(", options: { color: PAPER } }, { text: "\"You are a {{role}}.", options: { color: TEAL } }, { text: "", options: { breakLine: true } },
      { text: "                 Answer only from that view.\"", options: { color: TEAL } }, { text: ")", options: { color: PAPER, breakLine: true } },
      { text: "  @UserMessage", options: { color: AMBER } }, { text: "(", options: { color: PAPER } }, { text: "\"{{task}}\"", options: { color: TEAL } }, { text: ")", options: { color: PAPER, breakLine: true } },
      { text: "  String ", options: { color: SKY } }, { text: "work(", options: { color: PAPER } }, { text: "@V", options: { color: AMBER } }, { text: "(\"role\") String role,", options: { color: PAPER, breakLine: true } },
      { text: "              ", options: { color: PAPER } }, { text: "@V", options: { color: AMBER } }, { text: "(\"task\") String task);", options: { color: PAPER, breakLine: true } },
      { text: "}", options: { color: PAPER } },
    ],
    "A worker in LangChain4j is an interface. No implementation. The annotations are the program. The system message makes it a specialist, role and task get slotted in at call time. One interface backs every worker, that is the many workers line in code.");

  // 21 CODE 2
  codeSlide("Live code  ·  2 of 2",
    [{ text: "Plan, dispatch, ", options: { color: PAPER } }, { text: "synthesize.", options: { color: AMBER, italic: true } }],
    [
      { text: "Plan", options: { color: SKY } }, { text: " plan = planner.assemble(prompt);", options: { color: PAPER } }, { text: "   // 1 prompt -> a crew", options: { color: FAINT, breakLine: true } },
      { text: "", options: { breakLine: true } },
      { text: "var", options: { color: VIOLET } }, { text: " notes = ", options: { color: PAPER } }, { text: "new", options: { color: VIOLET } }, { text: " StringBuilder();", options: { color: PAPER, breakLine: true } },
      { text: "for", options: { color: VIOLET } }, { text: " (", options: { color: PAPER } }, { text: "var", options: { color: VIOLET } }, { text: " a : plan.crew()) {", options: { color: PAPER } }, { text: "        // fan out", options: { color: FAINT, breakLine: true } },
      { text: "    String answer = worker.work(a.role(), a.task());", options: { color: PAPER, breakLine: true } },
      { text: "    notes.append(a.role()).append(answer);", options: { color: PAPER, breakLine: true } },
      { text: "}", options: { color: PAPER, breakLine: true } },
      { text: "", options: { breakLine: true } },
      { text: "String", options: { color: SKY } }, { text: " result = synthesizer.synthesize(notes);", options: { color: PAPER } }, { text: "  // fan in", options: { color: FAINT } },
    ],
    "The orchestrator is the whole talk in fifteen lines. The planner turns one prompt into a crew, structured output, a typed Plan. We loop over the crew, each worker does its job, we collect the notes, the synthesizer folds them into one answer. Fan out, fan in. Now, let's run it.");

  // =========================================================== 22 DEMO
  {
    const s = slide();
    dot(s, MX + 0.12, 0.78, 0.2, AMBER, true);
    s.addText("LIVE NOW", { x: MX + 0.4, y: 0.62, w: 4, h: 0.32, fontFace: MONO, fontSize: 12, color: AMBER, charSpacing: 4, valign: "middle" });
    s.addText("Let's run it.", { x: 0, y: 2.3, w: W, h: 1.4, align: "center", fontFace: SERIF, fontSize: 72, color: PAPER });
    card(s, W / 2 - 3.3, 4.0, 6.6, 0.85, { fill: PANEL2 });
    s.addText([{ text: "$ ", options: { color: FAINT } }, { text: "mvn -q compile exec:java", options: { color: PAPER } }],
      { x: W / 2 - 3.3, y: 4.0, w: 6.6, h: 0.85, align: "center", fontFace: MONO, fontSize: 18, valign: "middle" });
    s.addText("One sentence in. Watch a crew assemble itself and hand back one plan, on this laptop, offline.",
      { x: 2.5, y: 5.3, w: 8.33, h: 0.8, align: "center", fontFace: SANS, fontSize: 17, color: MUTED, lineSpacing: 25 });
    s.addNotes("STOP presenting, go to the terminal. Warm-up already done. Run it and narrate: one prompt at the top, the planner is inventing a crew, there is the engineer, the marketer, legal always says no. Let the room read the workers, then the plan lands. If it dies, the house crew catches it, and you have a recording as backup.");
  }

  // =========================================================== 23 WHAT HAPPENED
  {
    const s = slide();
    eyebrow(s, "What just happened", 0.62, TEAL);
    s.addText([{ text: "One sentence became a ", options: { color: PAPER } }, { text: "team.", options: { color: AMBER, italic: true } }],
      { x: MX, y: 1.15, w: 11, h: 0.9, fontFace: SERIF, fontSize: 34 });
    const items = [
      ["FiShare2", TEAL, "It self-assembled", "You never named the crew. The planner chose who to hire."],
      ["FiHome", VIOLET, "It stayed home", "Every token ran on this laptop. Nothing left the room."],
      ["FiCode", AMBER, "It was small", "Roughly a hundred lines of Java. That's the scary part."],
    ];
    const cw = 3.7, gap = 0.35;
    for (let i = 0; i < items.length; i++) {
      const x = MX + i * (cw + gap), y = 2.6, h = 3.0;
      card(s, x, y, cw, h);
      s.addImage({ data: await icon(items[i][0], items[i][1]), x: x + 0.4, y: y + 0.45, w: 0.5, h: 0.5 });
      s.addText(items[i][2], { x: x + 0.4, y: y + 1.2, w: cw - 0.8, h: 0.6, fontFace: SERIF, fontSize: 22, color: PAPER });
      s.addText(items[i][3], { x: x + 0.4, y: y + 1.85, w: cw - 0.8, h: 1.0, fontFace: SANS, fontSize: 14, color: MUTED, lineSpacing: 20 });
    }
    s.addNotes("Reflect while the awe is warm. One English sentence became a team of specialists that split the work and reported back. It self-assembled, it stayed home, and it was about a hundred lines. The scary part is not that it worked, it is how little it took.");
  }

  // =========================================================== 24 CLOSE
  {
    const s = slide();
    // faint fan motif behind
    const ox = 11.4, oy = 3.4, ex = 12.7;
    [[2.2, TEAL], [2.8, VIOLET], [3.4, AMBER], [4.0, ROSE], [4.6, SKY]].forEach(([ey, c]) => ray(s, ox, oy, ex, ey, c));
    eyebrow(s, "The takeaway", 0.9);
    s.addText([
      { text: "Your codebase becomes\na ", options: { color: PAPER } },
      { text: "team you direct", options: { color: AMBER, italic: true } },
      { text: " in English.", options: { color: PAPER } },
    ], { x: MX, y: 1.9, w: 10.5, h: 1.9, fontFace: SERIF, fontSize: 44, lineSpacing: 50 });
    s.addText("One prompt. Many workers. In Java, on your own machine. The skill ahead is not writing every line, it is knowing what to ask, and how to constrain the answer.",
      { x: MX, y: 4.2, w: 8.2, h: 1.3, fontFace: SANS, fontSize: 18, color: MUTED, lineSpacing: 28 });
    s.addText([
      { text: "Nevin Tom", options: { color: PAPER, bold: true } },
      { text: "  @  BBD    ·    thank you", options: { color: AMBER } },
      { text: "\nquestions: what do you want to break first?", options: { color: FAINT, breakLine: false } },
    ], { x: MX, y: 5.9, w: 9, h: 0.9, fontFace: MONO, fontSize: 13, lineSpacing: 24 });
    s.addNotes("Land the plane. We started with a function that guesses. We gave it a loop, a stack, six levers, and a crew. A single sentence learned to command a team, in Java, offline, tonight. Soon the real skill is knowing what to ask and how to constrain the answer. I'm Nevin, the code is yours, what do you want to break first?");
  }

  await pres.writeFile({ fileName: "../One-Prompt-Many-Workers.pptx" });
  console.log("wrote One-Prompt-Many-Workers.pptx with", 24, "slides");
}

build().catch(e => { console.error(e); process.exit(1); });
