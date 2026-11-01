# Psyche: revise developmental psychology whilst you play Minecraft

A Fabric mod for **Minecraft 1.21.1** that turns moments in your survival world into short, typed revision
questions for **C98HD Developmental Psychology**. Kill a mob, mine some ore, wander into a new biome or go to bed, and a
question opens: you type the answer in your own words, and you can't carry on until you've got it.

It's the sister mod of [Parle!](https://github.com/muircat/parle) and works the same way, with the same clean UI.

## What's in it
**481 hand-written questions in 25 topics**, taken only from the course materials: the lecture and tutorial slides
and the two course books, Berk's *Development Through the Lifespan* (chapters 1–9) and Smith et al.'s chapter 9,
*Helping others and moral development*.

| Topic | Source |
| --- | --- |
| Why study development · Research methods · Ethics with children | Lecture 1, Week 3 tutorial, Berk ch. 1 |
| Theories of development (lifespan perspective, Freud, Erikson, behaviourism, ethology) | Berk ch. 1 |
| Nature, nurture & influences · Bronfenbrenner's model · Twin & adoption studies | Lecture 2, Berk ch. 1–2 |
| Prenatal development & birth | Berk ch. 3 |
| Growth & the brain · Motor development | Lecture 3, Berk ch. 4, 7, 9 |
| Perceptual development | Lecture 4, Berk ch. 3–4 |
| Piaget · Vygotsky · Information processing & comparing theories | Lecture 5, Week 7 tutorial, Berk ch. 1, 5, 7, 9 |
| Executive functions · Theory of mind | Lecture 6, Berk ch. 5, 7, 9 |
| Language development | Berk ch. 5, 7, 9 |
| Emotions & temperament · Self, play, aggression & gender | Berk ch. 6, 8 |
| Attachment · Parenting & family | Lectures 2 and 7, Berk ch. 2, 6, 8 |
| Middle childhood thinking (concrete operations, memory strategies, metacognition) | Berk ch. 9 |
| Prosocial behaviour · Moral development (Piaget, Kohlberg, Gilligan, Turiel, PATHS) | Smith et al. ch. 9, Berk ch. 8 |
| Coursework & exam skills | Lecture 1, Week 4 and 5 tutorials |

Every question shows its source after you answer it (for example *Lecture 5; Berk ch. 7* or *Smith et al. ch. 9*).
Plus a 153-term glossary that the marker uses to explain mix-ups.

## How it teaches
- **Recall, not recognition.** You always **type** the answer. No multiple choice.
- **Guided → independent.** Every question climbs its own ladder: *Guided* (with the clue given for free),
  *Complete* (fill the gap in a sentence), *Recall* (answer from the question alone), then *Apply* (diagnose a fresh
  scenario: "When her mum comes back, Ava keeps playing and turns away. Which attachment type?"). A clean answer moves
  it up a rung, a miss moves it down. *Easier* steps down for this try.
- **Hints in small steps.** A clue, then the first letters (or how many key ideas you need and one of them), then the
  word bank, where the terms you need are mixed in with other terms from the topic. Nothing that gives the answer
  away is shown until you ask for it. Each hint costs a quarter of the XP. After a wrong try the clue comes free.
- **Marks meaning, not exact wording.** Answers are marked offline on their *key ideas*: "the gap between what a
  child can do alone and with help" and "what a kid can manage on their own vs with an adult helping" both get the ZPD.
  Word endings (assimilate / assimilating), British and American spelling (behaviour, disorganized) and small typos
  are fine (typos get a warning). A typo is never accepted when the word you typed is itself a different course term,
  so *macrosystem* is not a slip for *microsystem*.
- **Explains misconceptions.** Common confusions are recognised and explained: assimilation vs accommodation,
  authoritarian vs authoritative, the three ToM tasks' "reality" answers, Bronfenbrenner's levels, validity vs
  reliability, IV vs DV, experience-expectant vs -dependent, over- vs underextension, Kohlberg's levels, moral rules vs
  social conventions, and more. Use another course term by mistake and it
  tells you what that term means. Hedging ("assimilation or accommodation") doesn't count. The misconceptions you
  keep making come back more often.
- **Feedback on explanations.** For longer answers it ticks off the ideas you've already got and says how many more
  you need.
- **Follow-ups.** A correct answer can lead straight into a question that goes one step further (Piaget's stages →
  their ages; the Maxi task → the age children pass it → why wording matters).
- **Adapts to you.** Questions are Foundation, Intermediate or Advanced; harder ones unlock per topic as its mastery
  grows. Spaced repetition (Leitner boxes) brings back what you miss.
- **Villagers ask for advice.** Right-click a villager with a job and, before trading, they ask you something:
  usually a new parent ("My little one never crawled. Should I be worried?"), sometimes a librarian planning a study
  or a cleric asking about research ethics. A good answer opens the trade window for 5 minutes. Practice for
  explaining the science to non-specialists.
- **Not optional.** Gameplay questions open straight away (as soon as no other menu is open), Esc does nothing and
  there's no skip. Get it right, or press *Show answer* and type the model answer's key ideas out yourself.
- Solving a question gives vanilla XP (more for harder rungs and levels, less with hints or a wrong try, none if you
  needed the answer).

## Controls
| Key | Action |
| --- | --- |
| **K** | Extra practice: a review question (this one you may close) |
| **P** | Notebook: topic mastery, mistakes to revisit, how often questions come (every 30 s to 5 min) |
| Enter | Check / continue |

Both can be rebound in Options → Controls → *Psyche (Developmental Psychology)*. The defaults differ from Parle's,
so the two mods can be installed together.

## Build and install
Requires Java 21.
```
./gradlew build          # Windows: gradlew.bat build
```
The mod is `build/libs/psyche-1.0.0.jar`. Put it in `.minecraft/mods/` together with
[Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1 and the Fabric Loader. `./gradlew runClient` starts a
dev game. Every push builds the jar on GitHub (Actions → latest run → *psyche-mod* artifact). The build also runs a
test that marks every model answer and cloze sentence with the real grader, so a broken question can't ship.

The mod goes on the client; for multiplayer the server needs it too (it detects the gameplay moments and pays the XP).
Singleplayer just works.

## Adding your own questions
Drop `.json` files into `config/psyche/packs/` using the same format as
`src/main/resources/assets/psyche/content.json`:
```json
{ "challenges": [ {
  "id": "my_first", "topic": "piaget", "level": 1,
  "ask": "What does Piaget call fitting a new experience into an existing schema?",
  "need": [["assimilation", "assimilate"]],
  "answer": "Assimilation.",
  "cloze": "Fitting new information into a schema you already have is [assimilation].",
  "apply": "A toddler who knows 'dog' calls a cat 'doggy'. Which process is this?",
  "hint": "The schema stays the same.",
  "traps": [{ "any": ["accommodation"], "why": "Accommodation changes the schema.", "skill": "assimaccom", "veto": true }],
  "extra": ["accommodation"], "next": ["pia_accom"] } ] }
```
- `need` lists the key ideas; each is a list of accepted phrasings, or `{"idea": "label", "any": [...]}` to give it a
  label for feedback. `min` says how many ideas are enough (default all) and `"order": true` makes them count only in order.
- `cloze` is a sentence with the answer in `[brackets]` for *Complete* mode; `apply` is a scenario for *Apply* mode.
- `traps` are known wrong answers with an explanation; `veto` makes them fail even next to the right answer.
- `on` lists gameplay triggers (otherwise the topic's are used): `kill:<mob>`, `mine:<block>`, `biome:<biome>`,
  `dim:<dimension>`, `sleep`, `death`, `lowhp`, `dawn`, with `*` as a wildcard. Villager questions use `trade:<job>`
  or `trade:any` plus an `"npc"` line.
- New `topics` and glossary `terms` can be added the same way.

## Code layout
- `core/` — plain Java, no Minecraft code: `Content` (library, glossary and picker), `Grader` (offline marking),
  `Profile` (progress)
- `Psyche` — server: gameplay triggers and XP payout
- `client/` — `PsycheClient` (queue, keys, villagers), `ChallengeScreen`, `NotebookScreen`, `UI`
- `src/test/` — grader tests and a check of every question in the bank

Progress is saved in `config/psyche/profile.json`.
