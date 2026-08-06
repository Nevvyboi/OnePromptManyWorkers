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

Rules that can be repaired safely are repaired rather than merely reported: an
em-dash becomes a middle dot, an over-long call to action is cut back to its
first clause. The rest are reported, and the page carries the verdict in its own
build receipt (`taste guard: passed`).

**It earns its place.** Run against the first page that got called finished, it
failed five checks: an em-dash in the title, four eyebrow labels where two were
allowed, three equal feature cards, a tagline under the hero button, and two
decorative dots. Then it caught a 35-character call to action that would have
wrapped at desktop, which no one had noticed by eye. That is the argument for
guardrails in one example: the eye gets tired, the rule does not.

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
