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
- [ ] Order Book schema (`GM-order-book`: `allow-mint`, `initial`, `d`) —
  `GmOrderBookXml` scaffolded with a TODO (2026-09-10) but not yet filled in;
  see "Order Book mechanism" below

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
- [x] `buyShares`/`closeEvent` now branch on `Event.getMethod()` (2026-09-10)
  — `buyShares` rejects Order Book events outright; `closeEvent` delegates to
  a separate `closeOrderBookEvent` for them. See "Order Book mechanism" below.
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

**Order Book mechanism (Appendix ב)** — 2026-09-10 session, engine + UI mostly
done, two self-contained pieces deliberately left as TODOs for Daniel to write:

- [x] Design validated against `docs/xml_tests/clob_simulation.html` (a
  simulation Daniel added mid-session, uploaded to Mama per spec line 1141) —
  confirmed price bounds (`0.01` to `d-0.01`), match-at-resting-price
  behavior, multi-level walk-through-the-book fills, the peer-to-peer mint
  mechanic (resting order fills at its own price, incoming order pays
  `d - restingPrice`), on-purchase commission charged to *both* sides of a
  mint, on-close commission math, and "cancel everything still resting" at
  close. Daniel's call: same-option match is tried before cross-option mint
  when both are possible.
- [x] `OrderSide` enum, `Order` (resting bid/ask, price-time priority via a
  monotonic sequence number, not wall-clock), `OrderBook` (per-option sorted
  bid/ask lists + last-trade price + bid/ask/mid/spread accessors) —
  `engine/.../model/`
- [x] `Option` now carries an `OrderBook` and a per-user holdings map
  (`getHolding`/`addHolding`/`removeHolding`/`getAllHoldings`) — Order Book
  needs this because, unlike LMSR, it allows selling, so a user's current
  position can't be derived by just summing buy-only `TradeRecord` history
  anymore.
- [x] `Event` carries `allowMint`/`initialInvestment`/`baseValue` (the XML's
  `allow-mint`/`initial`/`d`) via `setOrderBookConfig(...)`.
- [x] `TradeRecord` gained an `OrderSide side` field (new constructor
  overload; the old one defaults to `BUY`, so every existing LMSR call site
  is untouched) so Order Book fills can show as BUY or SELL in trade history.
- [x] `EngineManager.activateEvent` — Order Book branch: checks the MM can
  afford `initialInvestment`, debits them, credits the event's pool, and
  mints `initialInvestment / baseValue` pairs straight into the MM's
  holdings on both options (logged as `TradeRecord`s too, so the MM shows up
  as an "active" participant immediately).
- [x] `EngineManager.placeOrder(eventId, optionIndex, side, quantity, price, username)`
  — new method (not a `buyShares` overload, since the parameter shape is
  genuinely different): validates price bounds/holdings, then loops
  match-same-option-first / mint-if-allowed-and-BUY / rest-the-remainder,
  same order the simulation demonstrates. `buyShares` now rejects Order Book
  events outright ("use Place Order instead") instead of running LMSR math
  on them.
- [x] `EngineManager.closeEvent` branches to a new `closeOrderBookEvent` for
  Order Book: since every share was minted at a cost of exactly `d` per pair
  and matches never touch the event's pooled cash (buyer pays seller
  directly), `winningShares * d` always exactly equals the pool — no LMSR-style
  leftover to worry about. Commission-on-close carves out of that pool before
  splitting it across winning holders (read from `Option.holdings`, not
  `TradeRecord` history); all resting orders on both options are cancelled.
- [x] UI: `EventDetailController`'s Order Book option pane now shows live
  last/bid/ask/mid/spread, the resting bids/asks themselves, the acting
  user's own holdings, and a BUY/SELL + quantity + price form wired to
  `placeOrder`. A new participant-holdings panel (event-detail.fxml) lists
  every holder's shares per option, valued at mid-price (deliberately *not*
  at `d` — a share is only worth `d` if it wins; spec's own text on OB
  pricing ambiguity backs this call). `UserDetailController` shows the
  selected user's current per-option holdings for Order Book events too.
  `mvn compile` clean on both modules; app launches with no
  `FXMLLoadException` (checked via a background `javafx:run`, not
  click-tested — no GUI automation tool in this session).
- [ ] **Left for Daniel** (scaffolded with TODO comments pointing at the
  exact patterns to mirror — his choice, to keep the XML-parsing and
  holdings-map pieces hands-on): `GmOrderBookXml` (mirror `GmLmsrXml`, but
  `allow-mint`/`initial`/`d` are all `@XmlAttribute`, like `CommissionXml`'s
  `type`), wiring it into `GmMethodXml` (one field + getter, same shape as
  the GM-LMSR one), and `XmlParser.mapToEvent`'s Order Book branch (call
  `event.setOrderBookConfig(...)`, validate `d > 0` — a zero divides by zero
  in `activateEvent`/`placeOrder`). Plus `Option.getHolding`/`addHolding`/
  `removeHolding` themselves (currently `throw new
  UnsupportedOperationException("TODO...")`) — everything in the engine and
  UI already calls these with the exact signatures needed, so once they're
  filled in the whole feature should light up end-to-end.
- [x] **Verified against `clob_simulation.html`'s exact numbers (2026-09-10)**,
  once Daniel's TODOs above were filled in (see below — one real bug found
  and fixed along the way in `XmlParser`). Two new fixtures,
  `docs/xml_tests/clob_sim_purchase.xml`/`clob_sim_close.xml`, reproduce the
  simulation's event (Zoe/Alice/Bob/Carol, d=1, initial=100, allow-mint=true)
  once each with on-purchase vs. on-close commission. A scratch driver
  replayed the simulation's exact order sequence (Bob/Carol's YES bids,
  Zoe's asks on both options, Alice's matched buy, Zoe's multi-level sell
  into the bids, Carol's NO bid, Alice's peer-mint buy, Bob's
  out-of-bounds rejected order, Bob's unmatched NO ask, then close) through
  the real `EngineManager`, then closed the event with YES winning.
  Final balances matched the simulation's own `computeState` output to 4
  decimal places in **both** commission modes:
  on-purchase — Zoe 486.3055, Alice 224.8520, Bob 198.5375, Carol 190.3050,
  total commission 0.7555; on-close — Zoe 486.4500, Alice 224.6000,
  Bob 198.5500, Carol 190.4000, total commission 1.3500. The out-of-bounds
  order was correctly rejected before ever touching the book. Both totals
  sum to exactly 1100 (the four starting balances), confirming no money
  was created or destroyed.
- **Bug found and fixed during Daniel's TODO work**: his first pass at the
  `XmlParser.mapToEvent` Order Book branch computed `allowMint`/`initial`/`d`
  unconditionally after the `if (lmsr != null)/else` block instead of only
  when `method == ORDER_BOOK`, which NPE'd on `getGmOrderBook()` for every
  LMSR event (confirmed by trying to load `small.xml`, which mixes both
  methods). Fixed in two steps, talked through rather than shown outright:
  (1) declare the three variables with default values before the `if/else`
  so every path definitely assigns them (mirroring how `method`/`bParameter`
  already do this a few lines up) — this fixed the compiler's
  definite-assignment error; (2) still needed an explicit
  `if (method == ORDER_BOOK)` guard around the `setOrderBookConfig(...)`
  call itself, since without it every LMSR event silently got contaminated
  with sentinel `-1`/`false` config values instead of its natural `0`/`false`
  defaults. Daniel fixed both himself after each was explained; verified via
  a scratch load of `small.xml` before and after.

### Exercise 3 — client-server (not started)
- Schema differences and requirements are described in `docs/assignment-spec.md`
  (lines ~769-920, schema at line 1211+) but no work has begun; explicitly meant to
  reuse Ex2 components per the spec.

## Test status

- **JUnit 5 is now wired up (2026-09-10)**, `engine` module only:
  `junit-jupiter` 5.11.3 added to `engine/pom.xml` as a **test-scoped**
  dependency, `maven-surefire-plugin` 3.2.5 pinned explicitly (not relying
  on Maven's default lifecycle binding, for reliable JUnit 5 auto-detection).
  Run via `mvn test` (or `-pl engine test` from the root).
- **Packaging check, done before adding JUnit rather than after**: verified
  empirically (temporarily adding the dependency and running the exact
  `maven-dependency-plugin:copy-dependencies` goal `console-ui/pom.xml`
  uses) that without an explicit scope filter, this goal copies **all**
  scopes including `test` — junit-jupiter and 7 transitive jars landed in
  the output folder. Fixed by adding `<excludeScope>test</excludeScope>` to
  `console-ui/pom.xml`'s `copy-dependencies` execution; re-verified
  afterward that its `lib/` output now contains only the real runtime jars
  (engine + JAXB). `javafx-ui`'s own submission packaging doesn't exist yet
  (per that pom's comment, "a separate concern we'll solve later") — when
  it's built, it needs the same `excludeScope test` from the start.
- **`EngineManagerOrderBookTest`** (`engine/src/test/java/.../api/`) — the
  first real test class in the project. Replays the exact scenario from
  `docs/xml_tests/clob_simulation.html` (fixtures copied into
  `engine/src/test/resources/` as `clob_sim_purchase.xml`/`clob_sim_close.xml`)
  through the real `EngineManager`: resting bids/asks, a match, a partial
  fill, a sell order walking two bid levels, a peer-to-peer mint with a
  partial rest, an out-of-bounds rejection, and final resolution. Three
  tests, all passing: on-purchase commission, on-close commission (both
  asserting final balances to 4 decimals against the simulation's own
  numbers, commission totals, and that money is conserved — the four
  balances always sum to exactly 1100), and a focused test that a
  rejected order never touches the book.
- `engine/src/test/resources/` also holds older, still-unused fixtures
  (`test_events.xml`, `samples/valid_single_event.xml`,
  `samples/valid_multi_event.xml`, `samples/invalid_duplicate_id.xml`,
  `samples/invalid_commission_out_of_range.xml`) — still nothing consumes
  these; could become `XmlParser`/load-validation tests using the same
  pattern `EngineManagerOrderBookTest` now establishes.
- `docs/xml_tests/` (`small.xml`, `multiple.xml`, `error-2.xml`, `error-3.xml`,
  `clob_simulation.html`, plus the two `clob_sim_*.xml` files — also kept
  here, alongside their `engine/src/test/resources/` copies, for manual
  GUI-driven testing) — Daniel's manual Ex2 test files (untracked, `??
  docs/xml_tests/` in git status); not run by any automated suite.
- Ex1 was manually verified against the 4 official Mama sample files (per
  `CLAUDE.md`), not via automated tests.

## Next up

Order Book is functionally complete and verified end-to-end (2026-09-10) —
engine, UI, Daniel's TODOs, and now a real JUnit regression test. What's
left before Ex2 is fully done:

1. A manual click-test pass of the actual JavaFX UI (activate → place orders
   — match, partial fill, mint, rejection → close), on both an on-purchase
   and an on-close commission event — the engine math is proven correct by
   `EngineManagerOrderBookTest`, but the OB option pane / participant-holdings
   panel / place-order form itself haven't been click-tested by a human yet.
   Daniel ran the app and saw the GUI (2026-09-10) and is now making some UI
   modifications — revisit this once those land.
2. Once Ex2 is fully done: a `console-ui`-style submission readme (per
   `CLAUDE.md`), and remember `javafx-ui`'s eventual packaging setup needs
   `excludeScope test` from the start (see Test status above).

## Known issues / TODOs in code

- Windows CRLF/LF drift has repeatedly left files "modified but never committed"
  (noted in `CLAUDE.md`) — worth a `git status` check before assuming a session's
  work is fully pushed; IntelliJ's Commit window separates brand-new files into an
  easy-to-miss "Unversioned Files" group.
- `console-ui`'s Ex1-era `closeEvent(eventId, winningOptionIndex)` call site
  is stale (missing the `username` param, and now also LMSR-only vs. the
  engine's branching) but stays harmless since the module is excluded from
  the reactor.
- Order Book has no self-trade prevention (a user's own resting order can
  match or mint against their own new order) and no order-cancellation
  command — neither is required by the spec, but worth knowing they're
  absent if a grader probes for them.

