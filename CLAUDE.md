# Guess Market — Project Memory

Java course project (3 graded parts, one Maven repo, growing across the semester). Submitted via Mama.
GitHub: https://github.com/MosesSplitTheBits/GuessMarket

## How Daniel wants to work — read this first

**Daniel is learning Java through this project. He explicitly does not want the fastest path to a finished
solution — he wants to understand what he's building.** This is the single most important thing to carry
forward into every session.

In practice, that means:
- For a genuinely new concept (new API, new pattern, a subtle bug): explain the idea *before* showing code.
  Scaffold with skeletons/TODOs and let him fill them in, then review what he writes — point at the bug or
  gap rather than silently fixing it, unless he's stuck and explicitly asks to be shown.
- For repetitive/boilerplate work once a pattern is already established (e.g. writing the 4th nearly-identical
  handler after he understood the first one), move faster and just write it — he's said explicitly he wants
  boilerplate sped up, it's the new/subtle stuff he wants to slow down for.
- Explain *why*, not just *what* — especially for anything tied to a spec requirement, so he can defend his
  design choices to a grader.
- Don't assume a big design/architecture decision — ask, or explain trade-offs and let him choose.
- When something in his code is wrong, say so plainly and explain the failure scenario, rather than quietly
  patching around it.

## Current status

Exercise 1 (console app) is **done and submitted**: JAXB-based XML loading with rollback-safe validation,
all 6 console-ui commands working, packaged into 2 jars + `lib/` (JAXB runtime deps) + `run.bat`, tested
against all 4 official Mama sample files, readme delivered. Now starting **Exercise 2** (JavaFX GUI).

## Architecture (established in Exercise 1, carries forward)

- Maven multi-module: `engine` (passive: all state/logic, never prints, methods return data/messages) +
  `console-ui` (active: only module allowed to print/read input, holds `main()`). Exercise 2 will add a
  JavaFX UI module following the same passive-engine/active-UI split — see `docs/assignment-spec.md`,
  "תרגיל 2", for what changes vs. Exercise 1.
- Java 25 (`maven.compiler.release=25`). JDK lives at a path outside the project (not on system PATH by
  default on Daniel's machine — already fixed via System PATH once, but worth knowing if a fresh clone
  ever complains `java`/`javac` not found).
- XML parsing uses JAXB (`jakarta.xml.bind-api` + `jaxb-runtime`), not DOM — one class per schema level,
  living in `engine/.../xml/`. Migrated deliberately per the lecturer's guidance; don't reintroduce DOM.
- LMSR math lives in `LmsrCalculator` (pure functions, no state). Exercise 2 adds a second trading
  mechanism, Order Book — see Appendix ב in `docs/assignment-spec.md` before implementing it; it's a
  genuinely different mechanism (bid/ask book, matching, mint), not a variant of LMSR.
- Rollback-safe loading pattern: validate a new file fully into temp storage before it replaces live state,
  so a bad file never corrupts previously-loaded good data. Keep this pattern for Exercise 2's file loading
  too (now via a `FileChooser` dialog per spec, not a typed path).

## Reference docs in this repo

- `docs/assignment-spec.md` — full assignment text (Hebrew), extracted from the official docx, including
  the XML schema element tables for Exercise 1/2/3. Read the Exercise 2 section + Appendix א (LMSR recap),
  ב (Order Book), and ג (XML schema, "סכמה תרגיל 2") before starting new Exercise 2 work.
- **Missing from that extraction:** the docx's Appendix ד (visual schema diagrams) and the separate
  graphical UI sketch file the course provided for the JavaFX layout ("קובץ סקיצה גרפי") are images, not
  text — they didn't come through. If Daniel has those files, they should be added to `docs/` too so
  they're available to reference.
- The Exercise 1 submission readme (design choices, class summaries) — ask Daniel where he saved it, or
  regenerate a similar one for Exercise 2 at submission time; same requirements apply (Word/PDF only, not
  plain text, must link to GitHub, must list submitter name/ID/email).

## Known rough edges to watch for

- Windows line-ending (CRLF/LF) drift has silently left files "modified but never committed" more than
  once — worth a `git status` sanity check before assuming everything is pushed.
  IntelliJ's Commit window shows brand-new untracked files in a separate "Unversioned Files" group that's
  easy to miss — double check nothing new is left out of a commit.
