# The Live Build

The audience scans a QR, submits a one-line product idea from their phone, and a
crew of local agents builds a **real landing page** for it, live, on the
projector. Every token runs on your laptop through Ollama. Nothing leaves the room.

It literally performs the talk's title: one prompt (their idea) becomes many
workers (Copywriter, Designer, Builder, Skeptic) that build one thing on stage.

## The three views

| URL | Who | What |
|---|---|---|
| `http://<laptop-ip>:8080/` | audience phones (the QR) | submit an idea |
| `http://localhost:8080/stage` | the projector | the crew + the page building live |
| `http://localhost:8080/control` | you | the QR, the queue, the Run button |

## Before the talk

**1. The network (your hotspot).** Turn on your phone's personal hotspot. Join
your *laptop* to that hotspot. The audience joins the same hotspot. Everyone is
now on one little network with your laptop. The model still runs offline on the
laptop; the data plan only creates the network.

> The `/control` page shows the exact QR and URL, auto-detected from whatever
> network you are on. No editing needed.

**2. The model (for the real thing).**
```bash
ollama pull qwen2.5:3b
ollama run qwen2.5:3b "hi"   # warm it up
```

**3. Rehearse in mock mode first** (no Ollama, instant, canned outputs):
```bash
cd bbd-live
mvn spring-boot:run -Dspring-boot.run.arguments=--live.mock=true
```
Open `/control`, click **Run a built-in demo idea**, watch `/stage`. This is also
your on-stage safety net.

## On stage (the real thing)

```bash
cd bbd-live
mvn spring-boot:run
```

1. Put `/stage` on the projector, keep `/control` on your laptop (or phone).
2. Show the QR (it's on `/control`, and you can drop `/api/qr` on a slide).
3. People submit. Ideas appear in `/control`.
4. Pick a good one, hit **Run**. The crew assembles the page live.
5. Read the Skeptic's line out loud. It always gets a laugh.

## If the model misbehaves

Each agent has a house fallback, so a single bad response never breaks the build.
If Ollama is down entirely, flip to mock mode (above) and the show goes on. You can
even say it: "small local models improvise, so I gave the crew a safety net."

## No Java handy? Preview the whole thing in Node

Same frontend, same protocol, canned crew, zero Java or Ollama:
```bash
cd bbd-live/mock
PORT=5099 node server.js
```
Then open `http://localhost:5099/stage` and `/control`. Great for showing someone
the experience on any laptop.

## How it maps to the talk

- **One prompt** = the audience's one line.
- **Structured output** (lever 3) = the Copywriter returns a typed `Copy` object; the Designer returns a palette. The page is built from data, not a pasted blob.
- **Orchestration** (lever 6) = the crew runs and streams to the stage over SSE.
- **Local + free** = Qwen through Ollama, on your laptop, on your hotspot.

## Layout

```
bbd-live/
  pom.xml
  src/main/java/com/bbd/live/
    LiveApplication.java     Spring Boot entry + /stage /control routes
    ApiController.java        submit / queue / run / events(SSE) / qr / info
    CrewService.java          plan -> dispatch -> synthesize, mock + live, fallbacks
    Agents.java               Copywriter / Designer / Skeptic (LangChain4j)
    Model.java                Idea, Copy, Feature, Palette
    Net.java, Qr.java         hotspot IP + the join QR
  src/main/resources/
    static/index.html         audience form
    static/stage.html         the projector view
    static/control.html       presenter control
    application.properties
  mock/server.js              Node mirror for a no-Java preview
```
