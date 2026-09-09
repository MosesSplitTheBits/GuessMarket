# Guess Market — Progress

Source of truth for project status. Update this at the end of a session instead of
re-deriving it from scratch next time. Last updated: 2026-09-09.

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
- [x] **Exactly-one-MM-per-event validation** (2026-09-09, spec line 560):
  `loadDataFromXml` now tracks an `eventOwners` map while assigning MMs —
  rejects a second user claiming the same event ("Event N has more than one MM
  assigned: X and Y") instead of silently overwriting `Event.ownerUsername`
  via `setOwnerUsername`, and after the loop rejects any event nobody claimed
  ("Event N has no MM assigned to it."). Verified against
  `docs/xml_tests/{multiple,small,error-2}.xml` (all still load — no
  regression) plus two new scratch fixtures, one with an unassigned event and
  one with two users claiming the same event (both correctly rejected).
- [ ] Order Book schema (`GM-order-book`: `allow-mint`, `initial`, `d`) — no
  `GmOrderBookXml` class exists yet; only `GmLmsrXml`/`GmMethodXml` are parsed

**Users / accounts**
- [x] `User` model (balance, blocked flag, managed-event-ids) exists and loads
- [x] Users tab list is wired: `UsersController.refreshUsers()` populates
  `ListView<User>` from `engine.getAllUsers()`, called from `MainController`
  alongside `eventsTabController.refreshEvents()` after a successful load
- [x] **User-detail view built** (2026-09-09, spec lines 584-616): new
  `user-detail.fxml` + `UserDetailController`, wired into `users-tab.fxml`
  (replacing the placeholder `VBox`) the same `fx:include` way
  `event-detail.fxml` is wired into `events-tab.fxml`. Shows name, balance
  (+ BLOCKED flag), and a `ListView<Event>` of "active events" — events where
  the user has at least one `TradeRecord`, *not* just events they manage as
  MM (spec: participation counts from the first trade). Selecting an active
  event shows that user's own trade rows (option, quantity, price paid,
  commission paid — newest first) filtered from `Event.getTradeHistory()`,
  plus, if the event is `CLOSED`, a summary of final shares per option and
  the winning option name. LMSR-only for now (Order Book deferred, per
  usual). Two decisions made explicitly with Daniel: (1) added a
  `commissionAmount` field to `TradeRecord` (previously only stored the
  combined `totalPaid`) so price-paid and commission-paid can be shown as
  separate columns per spec — `buyShares`'s call site updated accordingly;
  (2) trade rows shown are the selected user's own trades only, not the
  full event-wide history (which already exists separately on the Events
  tab). Verified: `mvn compile` clean, app launches with no
  `FXMLLoadException` (Claude checked the process log — no click-through,
  no GUI automation tool available in this session), **and Daniel
  click-tested it manually — confirmed working.**

**Events tab / detail GUI**
- [x] Event list + selection wired (`EventsController`, `EventDetailController`)
- [x] **Detail panel fleshed out to full LMSR command-3-equivalent**
  (2026-09-09): `EventDetailController.showEvent` now shows, per option,
  name/shares/current price (via `LmsrCalculator.calculateOptionPrice`,
  LMSR-only — Order Book events show a placeholder note instead, per usual
  deferral); a status label (`ACTIVE`/`NOT_STARTED`, or `CLOSED — Winner: X`);
  an account-state line (`Event.accountBalance`, commission type/rate,
  `totalCommissionsCollected`); and the event's full trade history (all
  users, newest first, option/quantity/price paid/commission paid) in a new
  `ListView<TradeRecord>` — `event-detail.fxml`'s `participationsPane` was
  replaced with `accountStateLabel` + `tradeHistoryListView`. (Contrast with
  the Users-tab detail view: that one filters trade rows to a single user;
  this one is intentionally event-wide.) Order-book/bid-ask/participant view
  still not started — waits on the Order Book mechanism itself. Verified:
  `mvn compile` clean, app launches with no `FXMLLoadException`. Not
  click-tested by Claude (no GUI automation tool) — left running for Daniel
  to confirm.
- [x] **Events filter row built** (2026-09-09, spec line 630): a `ComboBox`
  per filter (Type / Status / Commission, each defaulting to "All") placed
  above `eventsListView` in `events-tab.fxml`, combined with AND logic across
  the three in `EventsController.applyFilters()`. First pass used
  `ToggleButton` rows/`ToggleGroup`s (single-select per row); Daniel didn't
  like the look ("crowded") and asked for the ComboBox swap instead — same
  filtering logic underneath, just a different control. Turned out the type
  filter didn't need to wait on Order Book after all — `XmlParser.mapToEvent`
  already sets `Event.getMethod()` to `LMSR`/`ORDER_BOOK` correctly today,
  even though full OB trading isn't implemented. Filters are always
  re-applied against a cached `allEvents` field (the full unfiltered list
  from the last load), not against the already-filtered `ListView` contents,
  so loosening a filter brings events back correctly. Folded in a related
  spec gap noticed along the way (line 636-644): the list row now shows
  status + type (Daniel asked to drop commission from the row — it's still
  in the detail panel). Verified: `mvn compile` clean, fresh launch shows no
  `FXMLLoadException`. **Daniel click-tested it — confirmed working.**
- [x] **Activate/buy/close controls wired up** (2026-09-09): designed with
  Daniel first — an "Activate Event" button next to the status label; each
  option pane gets a `Spinner<Integer>` + "Buy" button (clicking under an
  option implies its `optionIndex`, no separate picker) and a "Close — this
  wins" button (same per-option pattern, implies the winning option). All
  three buttons `disable`d (not hidden) based on event status + whether the
  acting user is that event's MM / isn't blocked, so the state itself stays
  visible. Results shown via a modal `Alert` (the exact string
  `EngineManager` already returns — no separate success/error styling, kept
  deliberately simple). `EventDetailController` needed two things it didn't
  have: `engine` (passed down like the other controllers) and "who's acting"
  — solved with a `Supplier<User>` MainController hands down as
  `this::getCurrentUser`, evaluated fresh on each click rather than kept in
  sync. After any successful action, a `Runnable onActionCompleted` callback
  (also from MainController) refreshes both the events list and the Users
  tab, since a trade changes balances/state on both sides —
  `UsersController.refreshUsers()` now also re-shows whichever user is
  currently selected, since same-object-same-index means the ListView's own
  selection listener won't refire on its own. Also fixed a related staleness
  gap while wiring: switching "Acting as" now immediately re-renders the
  selected event's detail panel (`EventsController.refreshSelectedEventDetail()`)
  so button gating updates without needing to reselect the event. Verified:
  `mvn compile` clean, fresh launch shows no `FXMLLoadException`, **and
  Daniel click-tested the full activate → buy → close flow end-to-end —
  confirmed correct.**

**Engine trading logic — LMSR + accounts**
- [x] **Architecture decided** (2026-09-09): money no longer flows through a
  single global `marketMakerBalance`. Each `Event` now has its own pooled
  `accountBalance` (subsidy + trade proceeds; drawn down to pay winners at
  close), and each MM's commission income lands directly in their own
  `User.balance`. `marketMakerBalance` field is removed.
- [x] `activateEvent(eventId, username)` — MM auth check, status flip, **and
  now the LMSR subsidy debit**: computes `C(0)` for the event, rejects if
  `user.getBalance() < subsidy` (spec line 698), else debits the MM and
  credits `event.accountBalance`. `loadDataFromXml` no longer pays subsidies
  upfront for every event at once — that's gone, replaced by this per-event,
  per-MM debit at activation time.
- [x] `buyShares` (2026-09-09) — Daniel filled in all the TODOs: user lookup,
  `isBlocked()`/`ACTIVE` rejection, `tradeCost` credited into
  `event.adjustAccountBalance(...)`, on-purchase commission routed to the MM's
  own balance, `totalPaid` deducted via `user.adjustBalance(-totalPaid)`.
  Reviewed: one real bug found (commission was credited to
  `activeUsers.get(username)` — the *buyer* — instead of the MM via
  `currentEvent.getOwnerUsername()`, so the commission netted to zero and
  never reached the MM); Daniel fixed it himself, re-reviewed correct.
- [ ] `buyShares`/`closeEvent` always run LMSR math unconditionally — neither
  branches on `Event.getMethod()`, so an Order Book event would be traded as if it
  were LMSR
- [x] `closeEvent` (2026-09-09) — takes a `username` param, checks MM
  authorization (same pattern as `activateEvent`), honors the boolean
  `Event.close(...)` returns, routes on-close commission to the MM's balance,
  and pays winners: derives each winner's holdings by summing `tradeHistory`
  entries matching the winning option's name (grouped by username), credits
  `payoutPerShare * theirShares` to each, and drains `event.accountBalance` by
  `totalPool` once after the loop. Reviewed: two bugs found during
  implementation, both now fixed — (1) `event.adjustAccountBalance(-totalPool)`
  was originally called once *per winning trade record* inside the loop
  instead of once after it, over-draining the account whenever more than one
  trade won; Daniel fixed it. (2) matching trade records to the winning option
  used `==` instead of `.equals()` on the option-name `String`s — happened to
  work today only because both strings trace back to the same `Option.name`
  field reference, but is not reliable in general; fixed (by Claude, at
  Daniel's request) to `.equals()`.

**Order Book mechanism (Appendix ב)**
- [ ] Not started at all: no `Order`/`OrderBook` model classes, no bid/ask
  matching, no mint logic, no per-user per-event share-holdings tracking (needed
  for both OB trading and the OB participant/holdings display)
- Per spec's own recommended build order (`docs/assignment-spec.md` line 738),
  this is meant to be tackled *last*, after the JFX shell + LMSR + Users path is
  fully working.

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

The full LMSR + Users path (file loading/validation, Users tab, Events tab +
filters, activate/buy/close) is now solid end-to-end, click-tested by
Daniel. Per spec's own recommended build order (line 738), **Order Book is
next** — deliberately last, now that everything it doesn't touch is done:

1. `GmOrderBookXml` parsing (`allow-mint`, `initial`, `d` — schema described
   in Appendix ב) so `XmlParser.mapToEvent` can read OB-specific config
   instead of just recognizing the method exists.
2. `Order`/`OrderBook` model classes: bid/ask book, matching logic, mint
   logic, per-user per-event share-holdings tracking (needed for OB trading
   *and* the OB participant/holdings display — nothing tracks holdings for
   OB the way LMSR now does via `TradeRecord`).
3. Make `buyShares`/`closeEvent` branch on `Event.getMethod()` instead of
   always running LMSR math unconditionally (existing known issue below) —
   OB events need their own trading path through these, not a shared one.
4. OB detail/holdings UI in `EventDetailController`'s option panes (currently
   just a placeholder note) and the OB half of spec lines 610-616 in the
   Users-tab detail view.

## Known issues / TODOs in code

- Windows CRLF/LF drift has repeatedly left files "modified but never committed"
  (noted in `CLAUDE.md`) — worth a `git status` check before assuming a session's
  work is fully pushed; IntelliJ's Commit window separates brand-new files into an
  easy-to-miss "Unversioned Files" group.
- **As of 2026-09-09 end of session, the entire Users/Events/trading-controls
  work above is still uncommitted** — `git status` shows 10 modified files
  (`PROGRESS.md`, `EngineManager`/`Event`/`TradeRecord` in `engine`,
  `EventDetailController`/`EventsController`/`MainController`/`UsersController`
  + `event-detail.fxml`/`events-tab.fxml`/`users-tab.fxml` in `javafx-ui`)
  **plus 2 brand-new untracked files**: `UserDetailController.java` and
  `user-detail.fxml`. Untracked files are exactly the sharp edge `CLAUDE.md`
  already warns about — IntelliJ's Commit window puts them in a separate
  "Unversioned Files" group that's easy to miss, so double-check both are
  included before committing. Everything above is verified compiling and
  click-tested working, so there's no reason to leave it uncommitted going
  into next session — worth committing (and considering a `console-ui`-style
  submission readme once Ex2 is otherwise done) before starting Order Book.
  `console-ui`'s Ex1-era `closeEvent(eventId, winningOptionIndex)` call site
  is now doubly stale (missing the `username` param too) but stays harmless
  since the module is excluded from the reactor.

