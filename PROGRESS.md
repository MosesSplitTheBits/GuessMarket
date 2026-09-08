# Guess Market — Progress

Source of truth for project status. Update this at the end of a session instead of
re-deriving it from scratch next time. Last updated: 2026-09-08.

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
- [x] JAXB XML loading with rollback-safe validation (`EngineManager.loadDataFromXml`)
- [x] LMSR pricing engine (`LmsrCalculator`)
- [x] Command 1-6 console UI, tested against official Mama sample files
- [x] Packaged (2 jars + `lib/` + `run.bat`), readme delivered
- Frozen: `console-ui` module is deliberately excluded from the Maven reactor now
  (see `pom.xml` comment) since Ex2 changes `EngineManager` method signatures that
  `console-ui`'s Ex1 call sites don't account for. Source untouched on disk.

### Exercise 2 — JavaFX GUI + Users + Order Book (in progress, due 2026-09-12)

**File loading / validation**
- [x] `FileChooser`-based load, off-thread via `LoadFileTask` (JFX `Task`), with
  simulated progress delay per spec
- [x] User schema parsing (`GmUserXml`, `EventRefXml`, `XmlParser.mapToUser`)
- [x] Validates: unique event IDs, commission 0-90, unique usernames, initial-cash
  ≥ 0, MM event-refs point at real events
- [ ] **Missing validation**: spec requires *every* event to have exactly one MM
  assigned from the file's users (line 560 in `docs/assignment-spec.md`) —
  `loadDataFromXml` currently never checks this
- [ ] Order Book schema (`GM-order-book`: `allow-mint`, `initial`, `d`) — no
  `GmOrderBookXml` class exists yet; only `GmLmsrXml`/`GmMethodXml` are parsed

**Users / accounts**
- [x] `User` model (balance, blocked flag, managed-event-ids) exists and loads
- [ ] Users tab GUI is a stub: `UsersController` just holds an empty
  `ListView<String>`, not wired to `engine.getAllUsers()` at all (its own
  docstring/TODO is stale — it says "engine has no concept of users yet", which is
  no longer true)
- [ ] No user-detail view (balance, active events, per-event trade history /
  holdings) — required by spec lines 584-616

**Events tab / detail GUI**
- [x] Event list + selection wired (`EventsController`, `EventDetailController`)
- [ ] Detail panel only shows option name + share count
  (`EventDetailController.showEvent`) — spec requires the full Ex1-command-3
  equivalent (current price, event account/commission state, trade history, closed
  status + winner) for LMSR, and order-book/bid-ask/participant view for OB
- [ ] No filter row (by type / status / commission method) — `EventsController`
  has a TODO saying this was blocked on `Event` having a real status; that's no
  longer true (`EventStatus` enum exists), so the TODO is stale and this is
  buildable now for status + commission filters (type filter still needs Order
  Book to exist)
- [ ] No UI controls at all yet for activate/buy/close actions — `currentUserComboBox`
  exists in `MainController` (tracks the "acting" user) but nothing calls
  `EngineManager.activateEvent`/`buyShares`/`closeEvent` from the GUI yet

**Engine trading logic — LMSR + accounts**
- [x] `activateEvent(eventId, username)` — MM auth check + status flip implemented
  and reviewed correct
- [ ] `activateEvent` never debits the LMSR subsidy from the MM's own balance, and
  never checks the MM can afford it (spec line 698: MM can't open an event they
  can't afford) — currently just flips status, no money moves
- [ ] `buyShares(eventId, optionIndex, amount)` takes **no `username`** — can't
  identify the buyer, so it never touches `User.balance`, never checks
  `user.isBlocked()`, and never checks the event is `ACTIVE` before allowing a
  trade
- [ ] `buyShares`/`closeEvent` always run LMSR math unconditionally — neither
  branches on `Event.getMethod()`, so an Order Book event would be traded as if it
  were LMSR
- [ ] `closeEvent(eventId, winningOptionIndex)` has no MM-authorization check
  (unlike `activateEvent`) and **ignores the boolean** `Event.close(...)` returns —
  if the event wasn't `ACTIVE`, it still computes and returns a payout message
  instead of erroring
- [ ] `closeEvent` never pays real users — it returns a formatted payout string but
  never calls `user.adjustBalance(...)` for anyone, because nothing tracks which
  user holds which shares
- [ ] `EngineManager.marketMakerBalance` is a single global field left over from
  Ex1's one-MM model; with multiple MMs each owning multiple events, commissions
  and subsidies need to flow through the specific MM's own `User.balance` (and
  events likely need their own account/pool separate from that). **Architecture
  decision Daniel needs to make, not yet decided.**

**Order Book mechanism (Appendix ב)**
- [ ] Not started at all: no `Order`/`OrderBook` model classes, no bid/ask
  matching, no mint logic, no per-user per-event share-holdings tracking (needed
  for both OB trading and the OB participant/holdings display)
- Per spec's own recommended build order (`docs/assignment-spec.md` line 738),
  this is meant to be tackled *last*, after the JFX shell + LMSR + Users path is
  fully working — worth deciding explicitly whether "finish the engine" for a
  given session means that LMSR+Users path, or the full thing including OB

### Exercise 3 — client-server (not started)
- Schema differences and requirements are described in `docs/assignment-spec.md`
  (lines ~769-920, schema at line 1211+) but no work has begun; explicitly meant to
  reuse Ex2 components per the spec.

## Test status

- **No automated test framework is wired up.** No `junit` dependency in any
  `pom.xml`, no test classes under any `src/test/java`.
- `engine/src/test/resources/` holds XML fixtures (`test_events.xml`,
  `samples/valid_single_event.xml`, `samples/valid_multi_event.xml`,
  `samples/invalid_duplicate_id.xml`, `samples/invalid_commission_out_of_range.xml`)
  but nothing currently consumes them — they look staged for JUnit tests that were
  never written, or for manual loading.
- `docs/xml_tests/` (`small.xml`, `multiple.xml`, `error-2.xml`, `error-3.xml`) —
  likely Daniel's manual Ex2 test files (untracked, `?? docs/xml_tests/` in git
  status); also not run by any automated suite.
- Ex1 was manually verified against the 4 official Mama sample files (per
  `CLAUDE.md`), not via automated tests.
- **Gap**: no regression safety net for the trading-logic rewrite ahead (accounts,
  balances, Order Book) — worth considering whether to add JUnit now, given how
  much of `EngineManager` is about to change.

## Next up

1. **Fix the current compile break** — `EngineManager.buyShares` (line ~222) still
   calls the old 4-arg `TradeRecord` constructor; `TradeRecord` now requires a
   5th `username` arg. The engine module will not build until this is fixed, which
   blocks everything else below.
2. Decide the `marketMakerBalance` → per-MM-`User.balance` architecture question
   above — this shapes the fix for `activateEvent`, `buyShares`, and `closeEvent`
   alike, so settle it before touching any of the three.
3. Rework `buyShares` to take a `username`, deduct/validate via
   `user.adjustBalance`/`user.isBlocked()`, and require `Event.getStatus() ==
   ACTIVE`.
4. Rework `activateEvent` to debit the LMSR subsidy from the MM's balance and
   reject the call if they can't afford it.
5. Rework `closeEvent` to take a `username`, check MM authorization, honor
   `Event.close(...)`'s boolean, and actually pay winners via `user.adjustBalance`.
6. Add the missing "exactly one MM per event" validation to `loadDataFromXml`.
7. Wire `UsersController`/`users-tab.fxml` to `engine.getAllUsers()` and build the
   user-detail view (spec lines 584-616).
8. Flesh out `EventDetailController.showEvent` to the full LMSR command-3-equivalent
   view (price, account state, trade history, closed/winner).
9. Add activate/buy/close controls to the GUI using `MainController.getCurrentUser()`.
10. Once the above is solid end-to-end for LMSR, start Order Book: `GmOrderBookXml`
    parsing, `Order`/`OrderBook` model, matching + mint logic, OB detail/holdings
    UI — per spec, deliberately last.

## Known issues / TODOs in code

- `EngineManager.buyShares` — compile-breaking `TradeRecord` constructor mismatch
  (see Next up #1).
- `javafx-ui/.../UsersController.java:18` — `// TODO: swap String for a real User
  model once it exists` (it now exists; controller predates `User`/`GmUserXml` and
  is stale).
- `javafx-ui/.../EventsController.java:22-29` — TODO block for the filter row;
  partially stale (status filtering is now buildable, `EventStatus` exists).
- Windows CRLF/LF drift has repeatedly left files "modified but never committed"
  (noted in `CLAUDE.md`) — worth a `git status` check before assuming a session's
  work is fully pushed; IntelliJ's Commit window separates brand-new files into an
  easy-to-miss "Unversioned Files" group.
- `CLAUDE.md` says Appendix ד diagrams and the graphical UI sketch are "missing" from
  `docs/` — that's now stale: `docs/AppScetch.png` and `docs/SchemaV2.png` exist.
  Worth updating that note in `CLAUDE.md`.
