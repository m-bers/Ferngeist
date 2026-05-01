# Overnight push status

This file is the catch-up sheet. Full details are in commit messages on `ci/temp-apk-build` and the draft PR https://github.com/m-bers/Ferngeist/pull/1.

## ✅ Landed and CI-green

### Tier T — Testing infrastructure
- **T1+T2** — `Build APK` workflow split into parallel `test` / `lint` / `debug-apk` jobs. Test reports + lint reports upload as artifacts.
- **T3** — New `Release build verification` workflow runs `:app:assembleRelease` and `:app:bundleRelease` (unsigned) on every push and PR.
- **(fix)** Existing `ci.yml` was failing because checkout didn't fetch tags (the `git describe` versionName resolution crashed). Fixed with `fetch-depth: 0`.

### Tier A — Information-architecture restructure
- **A1** — `Workspace` Room entity, `WorkspaceRepository`, migration v11→v12 that buckets every existing session by `(helperKey, cwd)`. Sessions whose serverId no longer maps land in an `orphan:<serverId>` workspace rather than disappearing.
- **A2–A5, A7** — `WorkspaceListScreen` is the new start destination. `WorkspaceDetailScreen` lists threads across all agents on that workspace's helper, with per-row agent badge + connection-state dot. `+` FAB shows an agent picker; picking starts a new thread with the workspace's cwd. Settings icon opens the existing `ServerListScreen` (now Settings-only). Workspace cards in the list also get a green/gray dot indicating whether *any* agent on the workspace's helper is connected.
- **A6** — Chat top bar subtitle now shows `<agent> · <model>`.
- **A8** — Onboarding rewrite for the new IA is **not yet done**. Existing onboarding still references "add a server".

### Tier B — Rich Android notifications
- **B1+B2** — When an agent calls `session/request_permission`, Ferngeist posts a high-importance notification with one action button per `PermissionOption` plus a Deny fallback. Tapping a button calls back into the right per-server `AcpConnectionManager` to dispatch the response. Covers tool approvals **and** plan-stage multiple-choice prompts (same code path).
- **B3** — When an agent finishes a turn, Ferngeist posts a default-importance notification with a `RemoteInput` reply field. Typing and tapping Reply sends the next prompt on that session without opening the app.
- **B4** — Session-aware foreground suppression via `ProcessLifecycleOwner` + a `CurrentChatTracker` singleton. A notification is suppressed only when the user is on *that exact* chat session; events for *other* sessions still fire even when the app is in the foreground.
- **B5 (preferences)** — **Not yet done**.

### Tier C — ACP protocol parity
- **C1** — `session/cancel` Stop button — already existed in the composer; verified.
- **C2 (partial — heuristic)** — Tool-call output that looks like a unified diff is now line-colored (red/green/blue/muted) in the tool details sheet. Detection is heuristic from the output text; the structured `ToolCallContent::diff` block isn't yet plumbed through the SDK adapter.
- **C7** — All four `PermissionOptionKind` values (allow_once / allow_always / reject_once / reject_always) round-trip through `SessionPermissionOption.kind`. Verified in existing rendering.
- **C3 / C4 / C5 / C6** — **Not yet done**.

### Tier D — UX (Zed parity)
- **D2** — Message queueing while a turn is generating. New `ChatState.queuedMessages`; a `SendMessage` submitted while `isStreaming` queues; on `TurnComplete`, the next queued message is dispatched automatically. A "<N> queued — tap to clear" pill appears above the composer.
- **D3 (partial)** — Tap any past user message to copy it back into the composer for a quick re-send / tweak. (True Zed-style "rewind to checkpoint" requires a feature ACP doesn't standardize, parked as D7.)
- **D4 (search half)** — Search bar at the top of `WorkspaceListScreen` (filters workspaces by name/cwd) and `WorkspaceDetailScreen` (filters threads by title/cwd/agent name). Auto-titling is the other half — needs an LLM call, parked.
- **D5** — Archive / restore / history. Sessions gain an `isArchived` column (migration v12→v13). Workspace detail screen has a "Show archived (N)" toggle in its kebab; threads can be archived via per-row kebab and restored from the archived view.
- **(bonus)** — Composer-draft persistence per (serverId, sessionId): typed text now survives navigation between threads and activity destruction, via a new `ChatDraftStore` SharedPreferences-backed singleton.
- **D1, D4 (titling), D6, D7** — **Not yet done**.

### Tier F — Polish
- **F1** — Open thread as Markdown. Kebab in the chat top bar now has an "Open as Markdown" item that builds a Markdown rendering of the thread (user/agent/tool/thought/plan segments) and fires `ACTION_SEND`.
- **F2 (partial)** — Kebab also has "Copy as Markdown" — same rendering, copied to the system clipboard with a "Thread copied as Markdown" snackbar. Per-message long-press copy is still pending.
- **F3 – F10** — **Not yet done**.

### Pre-existing fixes shipped earlier in this session (also on this branch)
- Fix: switching threads no longer cancels the agent's in-progress turn (the ACP `session.prompt(...).collect` was running in `viewModelScope`, which got cancelled when the user navigated away).
- Refactor: per-server `AcpConnectionRegistry` so Claude and Gemini run as parallel connections (previously a singleton manager forced a disconnect+reconnect on agent switch).
- Debug APK is renamed `Ferngeist Debug` with `applicationIdSuffix = ".debug"` so it installs side-by-side with the release build.

### Tests added
- `ThreadMarkdownTest` — covers F1's markdown rendering.
- `DiffTextTest` — covers C2's diff detection heuristic.
- (existing) `ChatViewModelTest`, `AcpBridgeTest`, `SessionRuntimeTest` — kept compiling through every refactor.

## Things to verify on-device

1. **First-launch migration**: existing data migrates cleanly. The new `workspaces` table should populate from your existing sessions (one workspace per `(helperKey, cwd)` pair). Sanity-check via the WorkspaceList — your existing sessions should appear under one or more workspaces.
2. **Multi-agent parallel**: connect Claude, navigate to its session list (now workspace detail), start a long turn, navigate back to workspace list, connect Gemini. Both should be live; the foreground-service notification should read "Connected to 2 agents".
3. **Permission notifications**: kick off a tool call that requires approval, lock the phone. Notification should appear with the correct option labels. Tap an action — agent proceeds without you unlocking.
4. **End-of-turn reply**: at end of a turn, while app is backgrounded, you should get a "<Agent> finished a turn" notification with a Reply text field. Typing+sending should fire the next prompt.
5. **Foreground suppression**: with the chat open, trigger a permission/turn-complete event — no notification should appear (in-app sheet/UI handles it). Background the app and try again — notification should appear.
6. **Archive a thread**: kebab on a thread row → Archive. Disappears from main list. Workspace kebab → "Show archived" → it should be there with Restore.
7. **Search**: in workspace list and workspace detail, the search field at the top filters by name/cwd/agent.
8. **Open as Markdown**: kebab in chat top bar → Open as Markdown → share intent shows up.
9. **Diff coloring**: when a tool call produces unified-diff output (e.g. `git diff`-style), the bottom sheet shows green/red colored lines.

## Known gaps / rough edges

- The "Settings" entry from the workspace list goes directly to the existing `ServerListScreen` rather than a proper Settings hub.
- Onboarding still uses old IA copy ("add a server" rather than "add an agent / add a workspace").
- No queue UI indicator above the composer — queueing happens silently. The user's typed message disappears immediately, which is the only signal.
- Authenticating an agent for the first time still has to go via Settings → server list → tap server → enter creds. The workspace agent picker doesn't guide that flow.
- `@`-mentions, edit-and-resubmit, restore-checkpoint, full edit/diff review with per-hunk Keep/Reject, embedded-terminal rendering, follow-along, ListSessions auto-merge — all still on the to-do list.
- Auto-titling threads requires an LLM round-trip and isn't implemented; existing manual rename works.

## Where to grab the APK

Each push produces a debug APK on the GitHub Actions run page for the `Build APK` workflow:
https://github.com/m-bers/Ferngeist/actions?query=branch%3Aci%2Ftemp-apk-build+workflow%3A%22Build+APK%22

Look for the `ferngeist-debug-<sha>` artifact on the latest green run.
