// Turns one crew Result into a real, standalone landing page.
// No frameworks, no external requests: one file you can open, keep or email.
// The Java server produces byte-for-byte the same shape.

const esc = s => String(s == null ? "" : s).replace(/[&<>"]/g, m => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[m]));

function artSvg(a) {
  if (!a) return "";
  const [c1, c2] = a.colors || ["#F5A524", "#38BDF8"];
  const sd = a.seed || 7, r = n => ((sd * (n + 3) * 9301 + 49297) % 233280) / 233280;
  let inner;
  if (a.kind === "rings") inner = [0,1,2,3,4].map(i => `<circle cx="${70+i*100}" cy="70" r="${22+r(i)*34}" fill="none" stroke="${i%2?c2:c1}" stroke-width="6" opacity=".9"/>`).join("");
  else if (a.kind === "waves") inner = [0,1,2].map(i => `<path d="M0 ${42+i*26} Q 130 ${10+r(i)*70} 260 ${42+i*26} T 540 ${42+i*26}" fill="none" stroke="${i%2?c2:c1}" stroke-width="6" opacity=".85"/>`).join("");
  else if (a.kind === "grid") inner = Array.from({length:28},(_,i)=>`<rect x="${(i%14)*38+8}" y="${Math.floor(i/14)*66+8}" width="30" height="52" rx="7" fill="${r(i)>.5?c1:c2}" opacity="${.3+r(i)*.65}"/>`).join("");
  else if (a.kind === "burst") inner = Array.from({length:18},(_,i)=>`<line x1="270" y1="70" x2="${270+Math.cos(i/18*6.28)*(80+r(i)*170)}" y2="${70+Math.sin(i/18*6.28)*(28+r(i)*48)}" stroke="${i%2?c1:c2}" stroke-width="5" opacity=".8"/>`).join("") + `<circle cx="270" cy="70" r="18" fill="${c1}"/>`;
  else inner = [0,1,2,3,4].map(i=>`<ellipse cx="${65+i*110}" cy="${70+(r(i)-.5)*54}" rx="${40+r(i)*38}" ry="${27+r(i)*24}" fill="${i%2?c1:c2}" opacity=".55"/>`).join("");
  return `<svg viewBox="0 0 540 140" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="abstract artwork">${inner}</svg>`;
}

function renderPage(item) {
  const R = item.result || {}, c = R.copy || {}, p = R.palette || {};
  const name = (R.product && R.product.name) || "This";
  const who = (item.name || "").trim();
  const tiers = R.pricing || [], faq = R.faq || [];

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${esc(name)} — ${esc(c.headline || "")}</title>
<meta name="description" content="${esc(c.subhead || "")}">
<style>
  :root{
    --bg:${p.bg || "#0e1016"}; --surface:${p.surface || "#171a22"};
    --ink:${p.ink || "#ECE7DE"}; --muted:${p.muted || "#9aa0ae"};
    --primary:${p.primary || "#F5A524"}; --accent:${p.accent || "#38BDF8"};
    --font:${p.font || "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif"};
  }
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
  .hero .art svg{width:100%;max-height:140px;display:block}
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
  .tier.featured .pick{background:var(--primary);color:#12100a;border-color:var(--primary)}

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
                color:var(--ink);font-size:1rem;font-family:inherit;min-width:min(320px,100%)}
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
    <div class="logo">${esc(name)}<span>.</span></div>
    <a href="#features">Features</a>
    <a href="#pricing">Pricing</a>
    <a href="#faq">FAQ</a>
    <a class="btn" href="#get">${esc(c.cta || "Get started")}</a>
  </nav>

  <header class="hero">
    <div class="art">${artSvg(R.art)}</div>
    <span class="badge">${esc(c.badge || "introducing")}</span>
    <h1>${esc(c.headline || "")}</h1>
    <p class="sub">${esc(c.subhead || "")}</p>
    <a class="cta" href="#get">${esc(c.cta || "Get started")}</a>
    <p class="note">No card. Runs on your own machine.</p>
  </header>

  <section id="features">
    <h2>What it does</h2>
    <div class="feats">
      ${(c.features || []).map(f => `<div class="feat"><div class="ic"></div><h3>${esc(f.title)}</h3><p>${esc(f.body)}</p></div>`).join("\n      ")}
    </div>
  </section>

  <section id="pricing">
    <h2>Pricing</h2>
    <div class="tiers">
      ${tiers.map(t => `<div class="tier${t.featured ? " featured" : ""}">
        <div class="tag">${t.featured ? "most people pick this" : ""}</div>
        <h3>${esc(t.name)}</h3>
        <div class="price">${esc(t.price)}</div>
        <div class="per">${esc(t.per)}</div>
        <p class="blurb">${esc(t.blurb)}</p>
        <ul>${(t.lines || []).map(l => `<li>${esc(l)}</li>`).join("")}</ul>
        <a class="pick" href="#get">${esc(c.cta || "Get started")}</a>
      </div>`).join("\n      ")}
    </div>
  </section>

  <section id="faq">
    <h2>Questions</h2>
    <div class="faq">
      ${faq.map(f => `<details><summary>${esc(f.q)}</summary><p>${esc(f.a)}</p></details>`).join("\n      ")}
    </div>
  </section>

  <section id="get">
    <div class="signup">
      <h2>${esc(c.cta || "Get started")}</h2>
      <p>Leave an address and we will tell you the moment it is ready.</p>
      <form onsubmit="event.preventDefault();document.getElementById('ok').textContent='Thanks. Nothing was actually sent, this page is a demo.';">
        <input type="email" required placeholder="you@example.com" aria-label="Your email">
        <button type="submit">${esc(c.cta || "Get started")}</button>
      </form>
      <div class="ok" id="ok"></div>
    </div>
  </section>

  <footer>
    <span>&copy; ${esc(name)}. A page that did not exist a few minutes ago.</span>
    <span class="made">idea by <b>${esc(who || "someone in the room")}</b> · built by seven agents on one laptop${R.skeptic ? "" : ""}</span>
  </footer>

</div>
</body>
</html>`;
}

module.exports = { renderPage };
