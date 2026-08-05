# The Live Build

The audience scans a QR, submits a one-line product idea from their phone, and a
crew of local agents builds a **real landing page** for it, live, on the
projector. Every token runs on your laptop through Ollama. Nothing leaves the room.

It literally performs the talk's title: one prompt (their idea) becomes many
workers (Copywriter, Designer, Builder, Skeptic) that build one thing on stage.

## The spider net

The crew is not a pipeline, it is a web. Agents run in parallel, and when one
finishes it helps another. The stage draws this live as a graph whose edges pulse
as work flows across them.

```
              [ copy ]
             ^    \    ^........
   tone hint/      \copy        . critique
           /        v           .  (feedback)
     [ design ]---->[ build ]   .
           palette      \       .
                         v      .
                      [ skeptic ]
```

1. **Copywriter and Designer start together** (round one is parallel).
2. The Designer's job is smaller so it lands first, and instead of idling it
   **assists**: it sends the Copywriter a tone hint.
3. Both outputs flow into the **Builder**, which assembles the page.
4. The Builder hands the page to the **Skeptic**.
5. The Skeptic's critique **loops back** to the Copywriter, who revises the
   headline live. You watch the hero text change on the projector, with a
   "revised after the skeptic's critique" tag.

That last edge is the point: output from one agent becoming input to another,
including going backwards. A net, not a line.

## The three views

| URL | Who | What |
|---|---|---|
| `http://<laptop-ip>:8080/` | audience phones (the QR) | submit an idea |
| `http://localhost:8080/stage` | the projector | the crew + the page building live |
| `http://localhost:8080/control?key=…` | you | the QR, the queue, the Run button (key printed in your terminal) |

## Before the talk

**1. The network (your hotspot).** Turn on your phone's personal hotspot. Join
your *laptop* to that hotspot. The audience joins the same hotspot. Everyone is
now on one little network with your laptop. The model still runs offline on the
laptop; the data plan only creates the network.

> The `/control` and `/join` pages show the exact QR and URL, auto-detected from whatever
> network you are on. No editing needed.

**2. The model (for the real thing).**
```bash
ollama pull qwen2.5:3b
ollama run qwen2.5:3b "hi"   # warm it up
```

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

## If the model misbehaves

Each agent has a house fallback, so a single bad response never breaks the build.
If Ollama is down entirely, flip to mock mode (above) and the show goes on. You can
even say it: "small local models improvise, so I gave the crew a safety net."

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
| Profanity | small word filter | "Let's keep it friendly." |

Tune the numbers at the top of `CrewService.java`. Even with all of this, one idea
building at a time and your own curation are the real safety.

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
