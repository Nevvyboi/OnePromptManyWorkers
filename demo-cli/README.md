# One Prompt. Many Workers. (the live demo)

A tiny orchestrator-worker system. One prompt goes in, a **Planner** turns it
into a crew, each **Worker** does one job, a **Synthesizer** merges everything
back into a single plan. Every agent is the same local Qwen model wearing a
different hat. No cloud, no API key, no bill.

## Before the talk (do this at home, on the venue wifi if you can)

1. Install Ollama: https://ollama.com/download
2. Pull the model once (it caches on disk):
   ```bash
   ollama pull qwen2.5:3b
   ```
3. Warm it up so the first stage-run is fast:
   ```bash
   ollama run qwen2.5:3b "say hi in three words"
   ```
4. Compile ahead of time so `mvn` is silent on stage:
   ```bash
   mvn -q compile
   ```

## On stage

```bash
mvn -q compile exec:java
```

Or feed it your own prompt for a laugh with the room:

```bash
mvn -q compile exec:java -Dexec.args="Plan BBD's takeover of the coffee machine"
```

## What to point at while it runs

- **One prompt** at the top, a single English sentence.
- **The crew** the planner invents, this is *structured output*, lever two.
  Nobody hardcoded these roles; the model chose them.
- **Each worker** answering in character, same model, different system message.
- **One plan** at the bottom, fan-out, then fan-in.

## The three moving parts (map to your slides)

| File | Role | Slide idea |
|------|------|-----------|
| `Planner.java` | one prompt -> a typed crew | structured output |
| `Worker.java`  | one template, many workers | the "many workers" line |
| `Orchestrator.java` | plan -> dispatch -> synthesize | the architecture diagram |

## If the demo gods are angry

- **`Connection refused`** -> Ollama is not running. `ollama serve` in another
  tab, or just open the Ollama app.
- **Slow first response** -> the model is cold. Always do the warm-up in step 3.
- **Weird crew / parsing wobble** -> harmless. `Orchestrator` falls back to a
  house crew of Engineer / Marketer / Legal, so the demo never dies. You can
  even mention it: "small models improvise, so I gave it a safety net."
- **Maven can't resolve `langchain4j`** -> bump `<langchain4j.version>` in
  `pom.xml` to the latest 1.x and re-run. The API used here is stable across 1.x.
- **Too slow live** -> stick with `qwen2.5:3b` (already the default) and keep the
  prompt small. `qwen2.5:1.5b` is even faster if the laptop is modest.

## The shape, in one breath

```
        one prompt
            |
        [ Planner ]  --structured output-->  crew: [Engineer, Marketer, Legal]
            |                                         |        |        |
            |                                     [Worker] [Worker] [Worker]
            |                                         \       |       /
        [ Synthesizer ] <-------------------------- everyone's notes
            |
        one plan
```
