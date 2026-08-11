# The Live Build

The audience scans a QR, submits a one-line product idea from their phone, and a
crew of local agents builds a **real landing page** for it, live, on the
projector. Every token runs on your laptop through Ollama. Nothing leaves the room.

It literally performs the talk's title: one prompt (their idea) becomes seven
workers that build one thing on stage.

## The spider net

Seven agents, not a pipeline. They run in parallel, and when one finishes it helps
another. The stage draws this live as a graph whose edges pulse as work flows.

| Agent | Job |
|---|---|
| **Namer** | invents the product name |
| **Copywriter** | writes the headline, subhead and features |
| **Designer** | picks the colours and type |
| **Illustrator** | draws the hero artwork (SVG, no image files) |
| **Builder** | assembles the page |
| **Reviewer** | sends back one concrete polish |
| **Pricer** | invents three pricing tiers |
| **Skeptic** | asks the hard question |

Two edges run *backwards*: the Reviewer's polish and the Skeptic's critique both
return to the Copywriter, who rewrites live on the projector.

## What the crew actually produces

Not a mockup. A **standalone HTML file** per idea, with everything inlined:

```
GET /page/{id}              open it
GET /page/{id}?download=1   keep it (braathat.html)
GET /api/mine/{id}          the sender's phone polls this
```

A sticky blurred nav, a hero whose artwork bleeds off the edge behind the
headline, three features with icons chosen from what the words actually say,
three pricing tiers with the middle one lifted, an FAQ, an email capture, and a
**build receipt**: a quiet monospace ledger naming which agent produced which
part of the page, with the total build time. No frameworks, no external
requests, nothing to install. The phone that submitted it shows a green
**"Your page is ready"** button the moment it exists.

The receipt is the point. This is not pretending to be an ordinary SaaS page;
it is a product that did not exist ten seconds ago, so its provenance is part
of the design rather than something to hide:

```
namer         the name               BraaiAlert
copywriter    the words              Load-shedding alert: Light your braai just r
designer      the palette            #7c9cff / #22d3ee
illustrator   the artwork            waves
pricer        three tiers            Free · R49 · R149
reviewer      sharpened the button   Try now
skeptic       the hard question      The biggest challenge? How do I ensure my…
```

Six palettes each pick their own display face, so a serif palette does not look
like the sans one with different colours.

## The taste guard

Lever four from the talk, made real. A deterministic guardrail sits between the
agents and the projector, and nothing is served without passing it.

It is deliberately **not** a model. Asking a model "does this page look good"
gets you a yes every time. So the rules a designer would actually enforce are
written down and checked mechanically in `TasteGuard.java`:

| Rule | Why |
|---|---|
| no em-dashes anywhere visible | the most reliable tell that a machine wrote it |
| eyebrow labels rationed to one per three sections | a label above every section is the templated rhythm |
| no three identical feature cards | the layout every model reaches for |
| no tiny tagline under the hero button | the hero carries one message |
| no decorative status dots | a dot with no state is decoration pretending to be information |
| no scroll cues, no version stamps | devtool fixtures, not page content |
| calls to action under 34 characters | longer ones wrap to two lines and read as broken |
| no elevate / seamless / unleash / next-gen | what a model writes when it has nothing to say |
| no saturated blue-violet in the palette | the colour every model drifts to; a room reads it as generated before it reads a word |
| body text at 4.5:1 contrast or better | computed WCAG ratio, because nothing below that survives a projector |
| primary and accent at least 24 degrees apart | two saturated hues on top of each other leave the page with no second voice |

The last three are a different kind of rule. The first eight check for the
**absence of a tell**. Those three require the **presence of quality**, which is
the gap that made early output look competent and generated at the same time.

Rules that can be repaired safely are repaired rather than merely reported: an
em-dash becomes a middle dot, an over-long call to action is cut back to its
first clause. The rest are reported, and the page carries the verdict in its own
build receipt (`taste guard: passed`).

**It earns its place.** Run against the first page that got called finished, it
failed five checks: an em-dash in the title, four eyebrow labels where two were
allowed, three equal feature cards, a tagline under the hero button, and two
decorative dots. Then it caught a 35-character call to action that would have
wrapped at desktop, which no one had noticed by eye. When the colour rules were
added they failed two of the palettes shipped in this repo. That is the argument
for guardrails in one example: the eye gets tired, the rule does not.

## The freshness ledger

A guard that reads one page at a time cannot see the thing an audience notices
first: that the last four pages were the same page. Six ideas went through the
crew and three of them opened with **"Stop thinking about"**. Every page was
individually fine. Together they were obviously machine-made.

So the harness keeps a short memory per channel (palette, layout, artwork,
headline, subhead, features, call to action) and refuses any value used recently.

Two things this taught, both worth saying on stage:

- **Key on the template, not the string.** The first version remembered finished
  sentences, so "Stop thinking about braai" and "Stop thinking about stokvel"
  looked like two different values. A room does not see strings. It sees one
  sentence with a word swapped, so the ledger remembers the **slot**.
- **Record what the model chose, not only what you chose.** The ledger originally
  wrote down only its own picks. When the Illustrator answered "waves" the value
  was accepted and never recorded, so it answered "waves" five times running and
  nothing stopped it. `remember()` is now called on both paths.

When the Copywriter repeats an opening anyway, the harness names the openings
already used and sends the work back once. If it stays in the groove, the house
draft is used instead.

## What the model actually got wrong

Everything below is real behaviour from qwen2.5:3b on this machine, and each one
is a lever rather than a complaint:

| What happened | The lever |
|---|---|
| a nested `Copy` object came back with an object where a string belonged, every single time | ask for **five flat labelled lines** instead; the same model lands it reliably. The schema is a control lever, same as the prompt |
| asked for `FEATURES: title \| one sentence`, it wrote the label on its own line and listed three beneath | parse **loosely**: any line carrying the separator is a feature, wherever it sits |
| it wrote the format spec back as content: a feature literally titled "Sentence" | refuse placeholder echoes at the parser |
| a headline came back as "Midnight Geyser Guard, Neighbor Peace保証" | refuse anything outside Latin script before it reaches the page |
| the background bundle pointed at `qwen2.5:1.5b`, which was never pulled | the failure was silent and every page quietly used house copy. The catch now prints the reason |

That last one is the most useful slide in the deck. A swallowed exception did not
crash anything. It just made the demo worse, invisibly, for an hour.

## The product mockup

The Illustrator used to draw abstract patterns: rings, waves, a field of dots.
Every page got decoration, and decoration is its own kind of slop. A real landing
page shows the product.

There is no image model here and nothing may leave the laptop, so the product is
drawn as SVG. Nine archetypes cover essentially any idea a room will type in:

| archetype | what it draws | what triggers it |
|---|---|---|
| calendar | a week grid with slots taken and one booked | book, reserve, court, appointment |
| timer | a countdown dial with time remaining | timer, countdown, braai, brew |
| ledger | contribution rows with amounts and a total | money, stokvel, invoice, split |
| chart | a line with a threshold and the breach marked | monitor, sensor, usage, leak |
| checklist | tasks ticked off, and whose turn it is | chore, rota, todo, task |
| route | a street grid, stops, and a destination pin | route, commute, lift club, delivery |
| inbox | a thread of messages and a reply box | chat, group, message, neighbour |
| catalog | product cards and a basket row | shop, menu, subscription, coffee |
| dashboard | stat cards and a bar series | anything else |

The classifier **scores** the idea against every archetype rather than taking the
first keyword that matches. That is not a preference, it is a bug fix: "a braai
timer that syncs with the load-shedding schedule" matched `schedule` first and
drew a calendar for something that is plainly a timer.

**The model proposes, the vocabulary decides.** Asked to choose, Qwen answered
`dashboard` for a stokvel tracker and `calendar` for a coffee subscription. When
the idea's own words are decisive the classifier wins; the model's word is only
taken when nothing in the idea is clear. This one is also deliberately **not** on
the freshness ledger: two booking apps should both get a calendar, because a
mockup that does not match the product is far worse than one that repeats.

## Who is working, and when

The roster said which agent was busy. It could not show the thing the talk is
actually about: that several are busy at the same moment, and that the one which
finishes first picks up more work instead of waiting.

The stage now carries a timeline. Every `startAgent` and `doneAgent` broadcasts a
span stamped against the start of the run, and the strip draws them live:

```
namer       ▇
copywriter  ▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇                    ▇▇▇▇▇▇
designer    ▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇
illustrator                        ▇
pricer                              ▇
builder                              ▇▇
reviewer                               ▇▇▇▇▇▇▇▇▇▇▇▇▇▇
skeptic                                ▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇
critic                                                    ▇▇▇▇▇▇

61.2s of work in 34.3s · 1.8x overlap · peak 3 at once
```

Three things it makes visible that a roster cannot:

- **Three bars start together.** Namer, Copywriter and Designer are launched on
  the same line, not one after another.
- **The namer's bar is tiny and early.** It is the smallest job, so it lands
  first and its output unblocks three others.
- **The copywriter has two bars.** The second one is amber: that is the rewrite
  after the Skeptic's critique. An agent that stopped and came back draws twice,
  which is exactly what a feedback edge looks like from the outside.

The number under it is the honest version of "nobody is idle": total agent time
divided by wall clock. Not a claim, a measurement, computed in the browser from
the same spans that draw the bars.

## Which model, and why

| | model | why |
|---|---|---|
| stage | **gemma3:12b** | it is the only one tested with design judgement |
| background | **qwen2.5:3b** | those builds need speed, not judgement |

That first row was measured, not assumed. Asked to choose a hero layout for a
two-person design studio's manifesto site, qwen2.5:3b answered `band` — the same
word it answered for a padel booking board, a coffee subscription and a stokvel
tracker. gemma3:12b answered `editorial`, which is the right call, and gave three
different answers across four ideas.

Gemma also lands the flat copy schema first time and obeys the Critic's two-line
format exactly, where Qwen replies `subhead: <rewrite>` instead.

**The cost is real and worth saying on stage:** a build went from about 12
seconds on qwen2.5:3b to 35 seconds on gemma3:12b. Better copy, better layout
judgement, three times the wait. The timeline strip is what makes the wait worth
watching.

A cold 12B answers its first request in 8.4 seconds and every one after that in
under one, so the model is loaded at startup rather than on the first idea of the
demo. Swap either model without recompiling:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--live.model=qwen2.5:7b --live.backgroundModel=qwen2.5:3b"
```

## The Critic

Every other agent works on one field, so nothing in the crew ever read the
finished page. That is how copy shipped that was individually fine and
collectively flat.

The Critic is the last edge in the web and the only one fed by the assembled
result. It returns exactly one field and one replacement, which is the smallest
useful unit of criticism: ask for everything wrong and you get a list nobody
acts on. Its rewrite then clears the same checks as anything else the crew
writes, and gets refused if it does not.

It was asked for two labelled lines and it replies `subhead: <rewrite>` instead.
That is a usable answer, so the parser takes both shapes. Same lesson as the
flat copy schema: meet the model where it is.

## What the model actually got wrong

Everything below is real behaviour from qwen2.5:3b on this machine, and each one
is a lever rather than a complaint:

| What happened | The lever |
|---|---|
| a nested `Copy` object came back with an object where a string belonged, every single time | ask for **five flat labelled lines** instead; the same model lands it reliably. The schema is a control lever, same as the prompt |
| asked for `FEATURES: title \| one sentence`, it wrote the label on its own line and listed three beneath | parse **loosely**: any line carrying the separator is a feature, wherever it sits |
| it wrote the format spec back as content: features titled "Sentence", "Description" and "Title 1" | a placeholder title costs the title, not the feature. The body is real copy, so the title is built from it |
| a headline came back as "Midnight Geyser Guard, Neighbor Peace保証" | refuse anything outside Latin script before it reaches the page |
| a badge came back as "EasyBookPadel", which the page letter-spaces into a wall | split a run-together badge back into words |
| the background bundle pointed at `qwen2.5:1.5b`, which was never pulled | the failure was silent and every page quietly used house copy. The catch now prints the reason |

That last one is the most useful slide in the deck. A swallowed exception did not
crash anything. It just made the demo worse, invisibly, for an hour.

## The guard that broke the pages it was guarding

Worth its own section, because it is the best argument in the talk for measuring
rather than looking.

The emoji repair rule collapsed whitespace before punctuation. It ran over the
whole document, including `<style>`, which turned the selector `.hero .wrap` into
`.hero.wrap` and the grid value `1.02fr .98fr` into `1.02fr.98fr`. Every
descendant rule in the hero silently stopped applying. The pages still rendered,
still passed all thirteen rules, and still looked plausible, so nobody caught it
by eye. It took measuring a computed style to find.

Repairs are now scoped: a guard that edits markup has to know which markup it is
allowed to edit.

## Background builds and the closing gallery

The queue is not idle. A scheduled job builds one waiting idea at a time, with no
stage events, so pages pile up quietly **while you are talking**. At the end, put
`/gallery` on the projector: a wall of every attendee's finished page, credited by
name. Turn it off with `--live.background=false` if you would rather build only on
demand.

```
        [ name ] ----the name----> [ copy ] <---critique---- [ skeptic ]
            \                        ^  \                        ^
             the name             tone   copy                  the page
              \                    hint    \                      |
               v                     |       v                    |
          [ art ] --artwork-------> [ build ] ------the page------+
               ^                                   |
             palette                            the page
               |                                   v
          [ design ]                          [ review ] --polish the cta--> [ copy ]
```

The two dashed edges are the point: the Reviewer and the Skeptic both send work
*backwards* to the Copywriter, which rewrites while the room watches. Output from
one agent becoming input to another, including in reverse. A net, not a line.

## The three views

| URL | Who | What |
|---|---|---|
| `http://<laptop-ip>:8080/` | audience phones (the QR) | submit an idea |
| `http://localhost:8080/stage` | the projector | the crew + the page building live |
| `http://localhost:8080/control?key=…` | you | the QR, the queue, the Run button (key printed in your terminal) |
| `http://localhost:8080/join` | the projector, early on | a full-screen QR to get people submitting |
| `http://localhost:8080/gallery` | the projector, at the end | every page the crew built while you talked |

## Before the talk

**1. The network (your hotspot).** Turn on your phone's personal hotspot. Join
your *laptop* to that hotspot. The audience joins the same hotspot. Everyone is
now on one little network with your laptop. The model still runs offline on the
laptop; the data plan only creates the network.

> The `/control` and `/join` pages show the exact QR and URL, auto-detected from whatever
> network you are on. No editing needed.

**1a. The toolchain**, if you do not have it (macOS):
```bash
brew install openjdk@21 maven ollama
brew services start ollama
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```

**2. The models (for the real thing).** Two, on purpose: the stage crew gets the
better model because the room is watching, and the background queue gets a
smaller one so it can never slow your live run down.

```bash
ollama pull qwen2.5:7b     # the stage crew
ollama pull qwen2.5:3b     # the background queue
ollama run qwen2.5:7b "hi" # warm the big one
```

On a 32GB Apple Silicon machine `qwen2.5:7b` is the sweet spot for the stage:
noticeably better names and copy than 3b, and still fast enough to watch.

Both are configurable:

```
live.model=qwen2.5:7b            # what the projector sees
live.backgroundModel=qwen2.5:3b  # what fills the gallery while you talk
live.background=false            # or turn background building off entirely
```

Background builds also pause completely while a stage run is in flight, so the
live one always wins.

**3. Rehearse in mock mode first** (no Ollama, instant, canned outputs):
```bash
cd live-build
mvn spring-boot:run -Dspring-boot.run.arguments=--live.mock=true
```
Open the printed `/control?key=…` URL, click **Run a built-in demo idea**, watch `/stage`. This is also
your on-stage safety net.

## On stage (the real thing)

```bash
cd live-build
mvn spring-boot:run
```

1. Put `/stage` on the projector, keep `/control?key=…` on your laptop (never show it).
2. Show the QR: project `/join`, or drop `/api/qr` on a slide.
3. People submit. Ideas appear in `/control`.
4. Pick a good one, hit **Run**. The crew assembles the page live.
5. Read the Skeptic's line out loud. It always gets a laugh.
6. At the very end, switch the projector to `/gallery` and let the room find theirs.

## If the model misbehaves

Each agent has a house fallback, so a single bad response never breaks the build.
If Ollama is down entirely, flip to mock mode (above) and the show goes on. You can
even say it: "small local models improvise, so I gave the crew a safety net."

## Opening and closing the doors

`/control` has one big button: **Close submissions**. Open at the start of the
talk, closed when you are ready to reveal the wall, so the queue is final and
nobody is still typing during your ending.

Closed is enforced on the server, not just hidden in the UI: `POST /api/submit`
returns `{"closed": true}` and every phone in the room flips to a "Submissions
are closed. Watch the big screen." panel within a few seconds. Only the key
holder can open or close them.

## Names are opt in

The audience form has a tick box, **"Show my name on the big screen"**. If it is
not ticked, that person's name is never sent to the stage, the gallery, their
page footer, or your control panel; they simply appear as *anonymous*. The
consent travels with the submission, so nothing has to be scrubbed later.

> It ships ticked by default, because the credit is half the fun and the box is
> right there under the name field. If you would rather it be off by default,
> flip `checked` on `#showName` in `static/index.html`.

## Measured on real hardware

Not estimates. M-series Mac, 32GB, `qwen2.5:7b` on stage and `qwen2.5:3b` behind:

| | time |
|---|---|
| A full stage build, 8 agents | **11.6 to 14.0 s** |
| A background build on the small model | **~5.2 s** |
| Slowest agent (Copywriter, longest prompt) | ~5.7 s |
| Server start | under 1 s |

So a room of 30 ideas fills the gallery in roughly three minutes of background
work, comfortably inside a talk, and a live run is a watchable ten to fifteen
seconds.

## Timing

Every agent is timed. The stage roster shows how long each one took, and the
header shows the total (`built in 9.8s`). The gallery puts the build time on
each card. This is how you find out, on your own hardware, whether the Skeptic
or the Copywriter is the one making the room wait.

## Presenter key

The room is on your hotspot, and at a dev meetup somebody *will* try `/control`.
So the presenter panel and the Run endpoint are behind a key. On startup the
server prints three URLs and only one is private:

```
audience : http://172.20.10.3:8080/            <- the QR points here
stage    : http://localhost:8080/stage         <- the projector
control  : http://localhost:8080/control?key=a9f3k2   <- YOU, keep this private
```

Anyone without the key gets a polite "presenter only" page, and a build cannot be
triggered from a phone. Pin your own key with `--live.key=whatever` if you prefer.

## Limits and moderation

Submissions land in a queue that only you see on `/control`. A landing page is built
only when you click **Run** on one you pick, one at a time. Nobody's idea reaches the
projector unless you choose it, so you are the final filter.

On top of that, the server guards itself so a live crowd can't flood or embarrass it:

| Guard | Value | Message when hit |
|---|---|---|
| Queue cap | 250 ideas total | "The queue is full for now." |
| Per phone | 8 ideas max | "That's plenty from you." |
| Cooldown | 6 seconds between submits | "One at a time." |
| Duplicates | rejected (case-insensitive) | "Someone already sent that one." |
| Length | 3 to 120 characters | "Give it a few more words." |
| Profanity | small word list | "Let's keep it friendly." |
| Crude wording | **flagged, not blocked** | shown with a red "check this one" |

Tune the numbers at the top of `CrewService.java`.

**Be honest with yourself about the word list.** It is a speed bump. It catches
someone typing a swear word, and nothing else. It cannot catch an idea that is
crude in *meaning* but polite in vocabulary, and a room full of developers will
find that gap within minutes. A live crowd test on this exact app got "a smart
toilet app that scores your number twos with fart sound effects" straight past it.

So the design does not pretend otherwise:

- anything matching a crude word list is **flagged** in your panel with a red
  "check this one", rather than silently accepted,
- every idea has a **Hide** button, which drops it from the list and stops it
  being run at all,
- and nothing is ever on the projector until *you* press Run.

You are the filter. The software's job is to make sure you always see what you
are about to show the room.

## No Java handy? Preview the whole thing in Node

Same frontend, same protocol, canned crew, zero Java or Ollama:
```bash
cd live-build/mock
PORT=5099 node server.js
```
Then open `http://localhost:5099/stage` and the printed `/control?key=…`. Great for showing someone
the experience on any laptop.

## How it maps to the talk

- **One prompt** = the audience's one line.
- **Structured output** (lever 3) = the Copywriter returns a typed `Copy` object; the Designer returns a palette. The page is built from data, not a pasted blob.
- **Orchestration** (lever 6) = the crew runs and streams to the stage over SSE.
- **Local + free** = Qwen through Ollama, on your laptop, on your hotspot.

## Layout

```
live-build/
  pom.xml
  src/main/java/com/bbd/live/
    LiveApplication.java     Spring Boot entry, routes, the keyed /control gate
    ApiController.java        submit / queue / run / events(SSE) / qr / info
    CrewService.java          plan -> dispatch -> synthesize, mock + live, fallbacks
    Agents.java               Copywriter / Designer / Skeptic (LangChain4j)
    Model.java                Idea, Copy, Feature, Palette
    Net.java, Qr.java         hotspot IP + the join QR
  src/main/resources/
    static/index.html         audience form
    static/stage.html         the projector view
    static/control.html       presenter control (keyed)
    static/join.html          full-screen join QR for the projector
    static/presenter-only.html  shown when the key is missing
    application.properties
  mock/server.js              Node mirror for a no-Java preview
```
