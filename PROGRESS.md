# Guess Market — Progress

Source of truth for project status. Update this whenever a feature/fix lands, not
just at session end — and keep it lean: this file should describe *current state
and what's left*, not a session-by-session change log (git log already covers
history). Last updated: 2026-09-10.

## Overview

Guess Market is a prediction-market simulator built as a semester-long Java course
project (3 graded parts, one growing Maven repo, submitted via Mama). Users trade on
binary-outcome events. **Exercise 1** (console app, done/submitted) built the engine
core: JAXB-loaded XML event data, LMSR-based pricing (`LmsrCalculator`), and a
single implicit market-maker. **Exercise 2** (in progress, due 2026-09-12) turns
this into a JavaFX GUI app with multiple named users/accounts and a second trading
mechanism, Order Book (bid/ask matching), alongside LMSR. **Exercise 3** (not
started, schema described in spec) will move the engine behind an HTTP
client-server split. Architecture is a passive `engine` module (all state/logic,
returns data/messages, never prints) driven by an active UI module — `console-ui`
for Ex1, `javafx-ui` for Ex2 onward.

## Requirements checklist

### Exercise 1 — console app (submitted, graded)
- [x] JAXB XML loading with rollback-safe validation, LMSR pricing engine,
  command 1-6 console UI, packaged (2 jars + `lib/` + `run.bat`), readme delivered.
- Frozen: `console-ui` module is deliberately excluded from the Maven reactor
  (see root `pom.xml` comment) since Ex2 changed `EngineManager` method
  signatures its Ex1 call sites don't account for. Source untouched on disk.

### Exercise 2 — JavaFX GUI + Users + Order Book — functionally complete
- [x] **File loading/validation**: `FileChooser` + off-thread `LoadFileTask`,
  rollback-safe (a bad file never corrupts previously-loaded state). Validates
  unique event IDs, commission 0-90, unique usernames, initial-cash ≥ 0,
  exactly-one-MM-per-event, MM event-refs point at real events, Order Book's
  `allow-mint`/`initial`/`d` (via `GmOrderBookXml`).
- [x] **Users/accounts**: `User` model, Users tab list, user-detail view
  (name/balance/blocked flag, active-event list — participation counted from
  first trade, not MM assignment — per-event trade history, current Order Book
  holdings, closed-event summary).
- [x] **Events tab/detail GUI**: event list with Type/Status/Commission filters,
  full detail panel for both trading methods (LMSR price/buy controls; Order
  Book book stats + resting bids/asks + holdings + BUY/SELL/price form),
  activate/close controls gated on acting-user/MM status, participant-holdings
  panel, event-wide trade history, resizable layout wrapped in a `ScrollPane`
  (spec requires the UI to stay usable when the window is resized — no
  disabling resizability), status color-coded (red=not started, green=active,
  gray=closed).
- [x] **Engine trading logic**: money flows through each `Event`'s own pooled
  `accountBalance` (not a global MM balance) plus each MM's personal `User.balance`
  for commission income. `activateEvent`/`buyShares`/`closeEvent` branch on
  `Event.getMethod()` — LMSR and Order Book each get their own path through
  activation, trading, and close/payout.
- [x] **Order Book mechanism (Appendix ב)**: `Order`/`OrderBook`/`OrderSide`
  models (price-time priority book per option), `Option` per-user holdings
  tracking, `EngineManager.placeOrder` (match-same-option-first, then
  cross-option peer-mint if allowed, remainder rests), MM's initial purchase
  at activation, `closeOrderBookEvent` payout (winning holders paid from
  `Option.holdings`, since Order Book allows selling unlike LMSR).
  **Verified** against `docs/xml_tests/clob_simulation.html`'s exact scenario
  and numbers, in both commission modes — see Test status.
- [x] Code cleaned of scaffold `TODO` comments (2026-09-10) — none remain
  anywhere in `src/main`.

### Exercise 3 — client-server (not started)
- Schema differences and requirements are described in `docs/assignment-spec.md`
  (lines ~769-920, schema at line 1211+) but no work has begun; explicitly meant to
  reuse Ex2 components per the spec.

## Test status

- **JUnit 5** wired up in the `engine` module: `junit-jupiter` 5.11.3
  (test-scoped — verified empirically that it does *not* leak into
  `console-ui`'s packaged `lib/` folder), `maven-surefire-plugin` 3.2.5 pinned
  explicitly. Run via `mvn test` (no system-wide Maven on this machine — use
  IntelliJ's Maven tool window/run-gutter, or the bundled Maven at
  `C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd`).
- `EngineManagerOrderBookTest` (`engine/src/test/java/.../api/`) — 3 tests, all
  passing: replays the exact `clob_simulation.html` order sequence through the
  real `EngineManager` (resting orders, a match, a partial fill, a multi-level
  sell, a peer-mint with partial rest, an out-of-bounds rejection, close) for
  both on-purchase and on-close commission, asserting final balances to 4
  decimals against the simulation's own numbers plus money conservation; a
  third test confirms a rejected order never touches the book.
- `engine/src/test/resources/samples/` holds older, still-unused validation
  fixtures (duplicate ID, out-of-range commission, etc.) — no test consumes
  them yet; a natural next test class if more coverage is wanted later.
- `docs/xml_tests/` — Daniel's manual GUI-testing fixtures (untracked in git):
  `small.xml`, `multiple.xml`, `error-2.xml`, `error-3.xml`,
  `clob_simulation.html`, `clob_sim_purchase.xml`, `clob_sim_close.xml`.
- Ex1 was manually verified against the 4 official Mama sample files, not via
  automated tests. `console-ui` itself isn't tested by anything (frozen, excluded
  from the reactor).

## Next up

Order Book and the full LMSR + Users path are both functionally complete,
click-tested, and (for Order Book) numerically verified. What's left before
Ex2 can be submitted:

1. **Write the submission readme** (`console-ui`-style, per `CLAUDE.md`) —
   covers `javafx-ui` + `engine` (not `console-ui`, which was Ex1's own
   separate submission).
2. **Package for submission**: runnable jar(s) + `lib/` + `run.bat` for
   `javafx-ui`, mirroring `console-ui`'s Maven setup
   (`maven-jar-plugin` for the manifest/classpath, `maven-dependency-plugin`'s
   `copy-dependencies` for `lib/`). Remember the lesson already learned on
   `console-ui`: that `copy-dependencies` execution **must** set
   `<excludeScope>test</excludeScope>` from the start, or JUnit and its
   transitive jars ship in the submission for no reason.
3. Once both are done: a final end-to-end sanity pass (fresh `mvn test` green,
   fresh package build, run the packaged jar — not just `mvn javafx:run` —
   to make sure the `lib/`-relative classpath actually works standalone).

## Known issues

- Windows CRLF/LF drift has repeatedly left files "modified but never committed"
  — worth a `git status` check before assuming a session's work is fully pushed;
  IntelliJ's Commit window separates brand-new files into an easy-to-miss
  "Unversioned Files" group.
- `console-ui`'s Ex1-era `closeEvent(eventId, winningOptionIndex)` call site is
  stale (missing the `username` param, LMSR-only vs. the engine's branching) —
  harmless since the module is excluded from the reactor and won't be resubmitted.
- Order Book has no self-trade prevention (a user's own resting order can match
  or mint against their own new order) and no order-cancellation command —
  neither is required by the spec, but worth knowing they're absent if a grader
  probes for them.
