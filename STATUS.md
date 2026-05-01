# Overnight push status

This file is the catch-up sheet when you wake up. The full progress checklist with PR comments is on the draft PR https://github.com/m-bers/Ferngeist/pull/1; this file is the human-readable summary.

## ✅ Landed and CI-green

### Tier T — Testing infrastructure
- **T1+T2** — `Build APK` workflow split into parallel `test` / `lint` / `debug-apk` jobs. Test reports + lint reports upload as artifacts.
- **T3** — New `Release build verification` workflow runs `:app:assembleRelease` and `:app:bundleRelease` (unsigned) on every push and PR.
- Existing `ci.yml` was failing because checkout didn't fetch tags (the `git describe` versionName resolution crashed). Fixed with `fetch-depth: 0`.

### Tier A — Information-architecture restructure
- **A1** — `Workspace` Room entity, `WorkspaceRepository`, migration v11→v12 that buckets every existing session by `(helperKey, cwd)`. Sessions whose serverId no longer maps land in an `orphan:<serverId>` workspace rather than disappearing.
- **A2–A5, A7** — `WorkspaceListScreen` is the new start destination. `WorkspaceDetailScreen` lists threads across all agents on that workspace's helper, with per-row agent badge + connection-state dot. `+` FAB shows an agent picker; picking starts a new thread with the workspace's cwd. Settings icon in the workspace list opens the existing `ServerListScreen` (now Settings-only).
- **A6 (partial)** — Chat top bar subtitle now shows `<agent> · <model>`. Workspace name in the header is a follow-up.
- **A8** — Onboarding rewrite for the new IA is **not yet done**. Existing onboarding still references "add a server".

### Tier B — Rich Android notifications
- **B1+B2** — When an agent calls `session/request_permission`, Ferngeist posts a high-importance notification with one action button per `PermissionOption` plus a Deny fallback. Tapping a button calls back into the right per-server `AcpConnectionManager` to dispatch the response. Covers tool approvals **and** plan-stage multiple-choice prompts (same code path).
- **B3** — When an agent finishes a turn, Ferngeist posts a default-importance notification with a `RemoteInput` reply field. Typing and tapping Reply sends the next prompt on that session without opening the app.
- **B4 (foreground-suppression)** — **Not yet done**. Notifications fire even when the user is in the chat for that session.
- **B5 (preferences)** — **Not yet done**.

### Tier C — ACP protocol parity
- **C1** — `session/cancel` Stop button — already existed in the composer; verified.
- **C7** — All four `PermissionOptionKind` values (allow_once / allow_always / reject_once / reject_always) round-trip through `SessionPermissionOption.kind` and the existing `PermissionRequestSheet` already iterates options. Verified.
- **C2 / C3 / C4 / C5 / C6** — **Not yet done**.

### Tier D — UX (Zed parity)
- **D2** — Message queueing while a turn is generating. New ChatState.queuedMessages; a SendMessage submitted while `isStreaming` queues; on TurnComplete, the next queued message is dispatched automatically. (UI indicator pill above the composer is a follow-up.)
- **D5** — Archive / restore / history. Sessions gain an `isArchived` column (migration v12→v13). Workspace detail screen has a "Show archived (N)" toggle in its kebab; threads can be archived via per-row kebab and restored from the archived view.
- **D1, D3, D4, D6, D7** — **Not yet done**.

### Tier F — Polish
- **F1** — Open thread as Markdown. Kebab in the chat top bar now has an "Open as Markdown" item that builds a Markdown rendering of the thread (user/agent/tool/thought/plan segments) and fires `ACTION_SEND`.
- **F2 – F10** — **Not yet done**.

## Pre-existing fixes shipped earlier in this session (already on this branch)
- Fix: switching threads no longer cancels the agent's in-progress turn (the ACP `session.prompt(...).collect` was running in `viewModelScope`, which got cancelled when the user navigated away).
- Refactor: per-server `AcpConnectionRegistry` so Claude and Gemini run as parallel connections (previously a singleton manager forced a disconnect+reconnect on agent switch).
- Debug APK is renamed `Ferngeist Debug` with `applicationIdSuffix = ".debug"` so it installs side-by-side with the release build.

## Things to verify on-device tomorrow

1. **First-launch migration**: existing data migrates cleanly. The new `workspaces` table should populate from your existing sessions (one workspace per `(helperKey, cwd)` pair). Sanity-check via the WorkspaceList — your existing sessions should appear under one or more workspaces.
2. **Multi-agent parallel**: connect Claude, navigate to its session list (now workspace detail), start a long turn, navigate back to workspace list, connect Gemini. Both should be live; the foreground-service notification should read "Connected to 2 agents".
3. **Permission notifications**: kick off a tool call that requires approval, lock the phone. Notification should appear with the correct option labels. Tap an action — agent proceeds without you unlocking.
4. **End-of-turn reply**: at end of a turn, while app is backgrounded, you should get a "<Agent> finished a turn" notification with a Reply text field. Typing+sending should fire the next prompt.
5. **Archive a thread**: kebab on a thread row → Archive. Disappears from main list. Workspace kebab → "Show archived" → it should be there with Restore.
6. **Open as Markdown**: kebab in chat top bar → Open as Markdown → share intent shows up.

## Known gaps / rough edges (will note in commit messages too)

- The "Settings" entry from the workspace list goes directly to the existing `ServerListScreen` rather than a proper Settings hub.
- Onboarding still uses old IA copy ("add a server" rather than "add an agent / add a workspace").
- No queue UI indicator above the composer — queueing happens silently. The user's typed message disappears immediately, which is the only signal.
- Permission notifications have no foreground-suppression. If you're already in the chat sheet, you'll get *both* the in-app sheet and the notification.
- Authenticating an agent for the first time still has to go via Settings → server list → tap server → enter creds. The workspace agent picker doesn't guide that flow.
- Session "edit and resubmit", multibuffer-style review, `@`-mentions, diff/terminal rendering, etc. — all on the to-do list still.

## Where to grab the APK

Each push produces a debug APK on the GitHub Actions run page for the `Build APK` workflow. The latest commit's run is here:
https://github.com/m-bers/Ferngeist/actions?query=branch%3Aci%2Ftemp-apk-build+workflow%3A%22Build+APK%22

Look for the `ferngeist-debug-<sha>` artifact.
