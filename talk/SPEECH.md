# One Prompt. Many Workers.
### Speaker script for practice, Nevin Tom @ BBD

**Runtime:** ~30 minutes. Roughly 22 minutes of talking, 6 minutes of live demo, a
minute or two of breathing room. Aim for ~125 words a minute. Slower than feels
natural. The pauses marked `[beat]` are not optional; they are where the room
catches up and where the jokes land.

**How to use this:** the deck already carries your notes (press `N` on any slide).
This is the full flowing version to rehearse out loud. Slide cues in `[SLIDE n]`.
Do not read it word for word on the night. Learn the shape, keep the four or five
lines marked ★, and let the rest be yours.

---

## Cold open · [SLIDE 1 · title]

*Walk on. Let the fan-out animation finish drawing before you say anything. One dot
becomes five, on screen, in silence. Then look up.*

Good evening. That little animation you just watched? That is the entire talk.

One thing, becoming a team. A single sentence, becoming a crew of workers that
split the job between them and hand you back an answer.

Tonight we are going to build that. For real. In Java. On this laptop. With a model
that never once phones home to a cloud. And I promise you it is less code than you
think.

I'm Nevin, and this is *One Prompt. Many Workers.*

---

## The promise · [SLIDE 2 · hook]

*Slow right down. This is the hook. Sell it.*

★ Last week I typed one sentence into this laptop. Fourteen words. And my laptop
quietly assembled a team of five, gave each of them a different job, let them work,
and handed me back a finished plan. [beat] No cloud. No API bill. It never even
touched the internet.

Now, when I first saw that happen, I had two reactions. The first was, *that is
magic.* The second, about a second later, was, *wait, that is horrifying, because
it was so little code.*

By the end of tonight, you will have both reactions too. And you will know exactly
how to build it.

---

## Roadmap · [SLIDE 3]

*Quick. Don't read all five. Point at the ladder.*

Here is the climb. We start with the humble prompt. We turn it into an agent. We
put real infrastructure underneath it. Then I show you the control panel almost
nobody talks about. And then, rung five, we point one prompt at a whole crew and we
run it, live, right here.

If the theory ever gets heavy, just hold on for the demo. It is worth the wait.

---

## ACT ONE · [SLIDE 4 · The Prompt]

*Reset. Lower your voice. Beginning of a story.*

Rung one. The prompt. The smallest piece of this whole thing, and honestly the most
misunderstood.

---

## What a prompt is · [SLIDE 5]

Here is the uncomfortable truth. Strip away all the marketing, and a large language
model is a function. Text goes in. Text comes out. That is it.

*Gesture at the code card.*

It has no memory of the sentence it just finished. It has no hands, it cannot
actually do anything in the world. And it cannot check its own work. Ask it the
same question twice and it will happily give you two different answers.

★ It is a function that guesses. A very, very good guesser. But a guesser.

And a prompt? A prompt is just you calling that function.

---

## The ceiling · [SLIDE 6]

So a raw prompt, on its own, hits three walls almost immediately.

Wall one, no memory. Every call starts from zero. It forgot your name the instant
it finished saying it.

Wall two, no hands. It can describe sending the email in beautiful, poetic detail.
It cannot send the email.

Wall three, no proof. And this is the dangerous one. It sounds *exactly* as
confident when it is right as when it is completely making things up.

*[beat]*

Put those together and what you have got is a brilliant intern who never remembers
your name, cannot reach the keyboard, and occasionally invents a co-worker. [beat]
Useful. But not something you would put in production.

So let's fix all three.

---

## ACT TWO · [SLIDE 7 · The Agent]

We give that function the three things it is missing. A memory. Some hands. And a
loop. And the moment we do, it stops being a chatbot. It becomes an agent.

---

## The equation · [SLIDE 8]

Here is the whole definition, and I want you to remember it, because it will save
you from a lot of expensive conference talks.

★ An agent is a model, plus tools it is allowed to call, wrapped in a loop. That is
it. Everything else anyone sells you is some flavour of this equation.

The model is the brain. The tools are the hands. And the loop, the loop is where
the magic actually lives.

---

## The loop · [SLIDE 9]

*Trace the circle with your finger as you talk.*

Watch the circle. It reasons about what to do next. It acts, it calls a tool, runs
a query, fetches a file. It observes what came back. And then it asks itself one
question: am I done? If no, it goes around again.

That little loop is the entire difference between a model that *talks* about doing
the job, and a system that actually *finishes* it.

Take the loop away, and you are right back to a chatbot. Keep it, and you have got
something that works until the work is done.

---

## ACT THREE · [SLIDE 10 · Infrastructure]

Now, a loop on a slide is easy. A loop you can run at BBD, on real infrastructure,
that a client will actually trust, that needs a proper stack underneath it. So
let's build one.

---

## The stack · [SLIDE 11]

Five layers under every agent. Quickly.

A runtime to host the model, tonight that is Ollama, running on this machine. An
orchestration layer that runs the loop and does the wiring, that is LangChain4j or
Spring AI. A tool registry, the hands. Memory, the notebook it carries between
turns. And this last one, hold onto it, structured output. Getting the answer back
as *data*, as a typed object, instead of a paragraph you have to parse by hand.

That highlighted layer is going to come back and steal the show later. Remember it.

---

## Why Java · [SLIDE 12]

*This is a Java room. Earn the laugh, then earn the point.*

Now, the obvious question. Every single AI tutorial on the internet is written in
Python. So, why Java?

*[beat]* And look, Python is lovely. I am not here to start a war.

But be honest with yourselves. The systems your clients actually trust with real
money? The ones that move the transactions, the ones under audit? Those run on the
JVM. They run right here, in this ecosystem.

★ And the good news is you do not have to rewrite any of it. LangChain4j and Spring
AI mean the agent can finally live *next to* the business, behind your Spring
Security, inside your transactions, sitting right next to your data. Not bolted on
the side in some Python service nobody wants to own at two in the morning.

Type safe. Testable. Production grade. The AI, finally, where your business already
lives.

---

## Local and free · [SLIDE 13]

And the model tonight never leaves this room.

It is Qwen, a genuinely capable open model, running through Ollama, entirely on this
laptop. Which means three things. The client's data never leaves the building.
There is no token bill that quietly scales with your success. And it works on a
plane.

For a bank, for an insurer, for anyone under a regulator, that first one, *it never
leaves the building*, is not a nice to have. That is the whole conversation.

---

## ACT FOUR · [SLIDE 14 · Control]

Okay. This next part is the bit I actually care about most. Because here is where
most people stop. They think the prompt is the steering wheel. [beat] The prompt is
one control out of six. Let me show you the whole cockpit.

---

## Six levers · [SLIDE 15]

Six ways to steer an agent. Only one of them is the prompt.

The prompt says *what* you want. Fine, everyone knows that one. But then. Tools
decide what it is even *allowed* to touch. Schema decides the *shape* of the answer.
Guardrails *reject* bad output before it ever escapes. Memory decides what it knows,
and, just as importantly, what it forgets. And orchestration decides *who talks to
whom.* One agent, or a whole crew.

★ Here is the thing that took me embarrassingly long to learn. You can barely touch
the prompt, and completely change the behaviour, just by changing the five
constraints around it.

---

## The shift · [SLIDE 16]

Which brings me to the one line I want you to walk out of here with.

*[beat, let the slide sit]*

★ You do not program an agent. You *constrain* it.

The prompt says what. The tools say how far. The schema says what shape. The
orchestration says who. Great agent engineering is not clever wording. It is great
constraint design.

*[beat, two full seconds, let it land]*

---

## ACT FIVE · [SLIDE 17 · One Prompt, Many Workers]

*Lift the energy. This is what they came for.*

And lever six, orchestration, is the fun one. Because instead of one agent trying to
do everything, we point a single prompt at a whole crew. This is where one becomes
many. And then, I promise, we run it.

---

## The pattern · [SLIDE 18 · architecture]

*Walk the diagram left to right, in time with your hand.*

Here is the shape. One prompt comes in, in plain English. It hits a planner. Now,
the planner does not do the work. Its only job is to decide *who should,* and to
hand that back as a typed crew. A list of roles and jobs.

Then each worker runs. And here is my favourite part, every worker is the *same
model.* The only thing that makes the engineer different from the lawyer is the
instruction we hand it.

Finally a synthesizer takes everyone's notes and folds them into one answer. Fan
out. Fan in.

★ And notice, nobody hardcoded those roles. The planner *invented* the crew. We
never wrote the word "marketer" anywhere. It decided that.

---

## What's steering · [SLIDE 19]

Before we run it, look at what is actually doing the steering here. The prompt, lever
one, barely changed. It is one plain sentence.

The real power came from two of the other levers. Structured output, turning that
sentence into a typed crew. And orchestration, deciding they run as a team. Two
levers you could *never* reach by just writing a cleverer prompt.

---

## Live code, part 1 · [SLIDE 20]

Two quick slices of code, then we run the real thing. It is less than it looks.

A worker, in LangChain4j, is an *interface.* That is it. There is no implementation.
The annotations *are* the program. This system message turns it into a specialist,
and the role and the task get slotted in when we call it.

One interface. And it backs every worker in the crew. That, right there, is the
"many workers" line of this talk, written out in code.

---

## Live code, part 2 · [SLIDE 21]

And the orchestrator is basically the whole talk in about fifteen lines.

The planner turns one prompt into a crew, structured output, a typed `Plan`. We loop
over that crew. Each worker does its one job. We collect the notes. The synthesizer
folds them into a single answer.

Fan out. Fan in. That is the entire pattern.

Right. Enough slides. Let's actually run it.

---

## LIVE DEMO · [SLIDE 22]

*Switch to the terminal. Warm-up already done before the talk. Take a breath. This is
the moment. Narrate everything, the room cannot read a terminal as fast as you.*

Command, `mvn compile exec:java`. One prompt at the top, watch, *"Plan the launch of
our new developer API."*

*Run it. As the planner responds:*

There. Watch this. The planner is inventing a crew right now, on this machine... and
there it is. It picked an engineer. A marketer. And, of course, legal, who I promise
is about to tell us why we can't do any of it.

*As each worker prints, read one line from each out loud. Let the room enjoy the
legal one.*

And now the synthesizer pulls all of that into one plan. [beat] There it is. One
sentence in. A whole team's worth of thinking out. On this laptop. Offline.

*If it is slow:* while that thinks, notice it is genuinely running the model locally,
no network, so this is the actual speed on real hardware, not a cached trick.

*If it does something weird or the crew looks odd:* and this is perfect, actually,
because small local models improvise. So I gave it a safety net, a house crew it
falls back to. Which is itself a lesson: in production, you always design for the
model having a bad day.

*Backup plan if it fully dies: have a screen recording ready. "Let me show you the
one I ran earlier, same code, same laptop." Never apologise more than once.*

---

## What just happened · [SLIDE 23]

*Come back to the deck while the awe is still warm.*

So. What did you just watch.

One English sentence became a team of specialists, who split the work and reported
back. It self-assembled, you never named that crew, the planner chose it. It stayed
home, every token ran on this laptop, nothing left the room. And it was small.
Roughly a hundred lines of Java.

★ And that last one is the part that should keep you up tonight. Not that it worked.
That it took so *little.*

---

## Close · [SLIDE 24]

*Land the plane. Warm, not rushed.*

We started tonight with a function that just guesses. We gave it a loop. We gave it
a stack. We gave it six levers instead of one. And we gave it a crew. And a single
sentence learned to command a team. In Java. Offline. Tonight.

★ Here is where I think this goes. Soon, the real skill will not be writing every
line yourself. It will be knowing exactly what to ask, and exactly how to constrain
the answer. Your codebase stops being something you type into, and starts being a
team you *direct,* in English.

*[beat]*

I'm Nevin. The code is yours, take it, break it, ship it. [beat] So, what do you
want to break first?

*Open hands. Smile. Wait for the first question. Do not fill the silence.*

---

## Q&A · seed these, they will come up

- **"Isn't a local model too weak for this?"** For orchestration, no. The planner
  and synthesizer are doing small, structured jobs. You can always route the *hard*
  worker to a bigger model. Same code, one line changed. Mix and match by task.
- **"How is this different from just one big prompt?"** Separation of concerns,
  same reason you don't write your whole app in one method. Each worker has a narrow
  job, so it is more reliable, cheaper to test, and easy to swap. And you can run
  them in parallel.
- **"What about hallucination / trust?"** That is exactly what levers three and four
  are for. Structured output plus guardrails. The synthesizer can be told to cite
  which worker said what. You constrain the risk, you don't pray it away.
- **"Does it cost anything?"** Tonight, nothing. It is a local open model. Your only
  bill is electricity and a laptop fan working hard.
- **"Can I put this in production at a client?"** That is the whole reason it is in
  Java. Wrap the workers as Spring beans, put them behind your existing security and
  transactions, add observability. It is ordinary Java from there.

---

## Timing map (check against a stopwatch in rehearsal)

| Section | Slides | Target |
|---|---|---|
| Open + promise | 1-2 | 2:30 |
| Prompt | 3-6 | 4:30 |
| Agent | 7-9 | 4:00 |
| Infrastructure | 10-13 | 4:30 |
| Control (the heart) | 14-16 | 4:00 |
| Swarm + code | 17-21 | 4:30 |
| **Live demo** | 22 | 5:00 |
| Reflect + close | 23-24 | 2:00 |
| **Total** | | **~31:00** |

If you are running long, the two safe cuts are: trim the stack walk on slide 11, and
tighten the Q&A seeds. Never cut the demo, and never rush slide 16.
