# WhatsAppDesktopBot → Android Local — Migration Plan

> **Branch:** `android-local`  
> **Phase 1 status:** Foundation + Fake Engine + Compose UI (no real WhatsApp)  
> **Desktop/VPS version:** unchanged at repo root (`bot.js`, `server.js`, `public/`)

---

## 1. Current Architecture (Desktop / VPS)

```
index.js → server.js (Express + Socket.io + JWT)
              ↓
           bot.js (WhatsAppBot extends EventEmitter)
              ↓
    whatsapp-web.js + Puppeteer + LocalAuth
              ↓
         store.js → data/*.json
              ↓
         public/ (HTML/CSS/JS dashboard)
```

### Key files

| File | Role |
|------|------|
| `bot.js` (~1060 lines) | All business logic: link, messages, matching, forward, bulk, rate limits |
| `server.js` (~500 lines) | HTTP API, auth, permissions, Socket.io events |
| `store.js` | JSON persistence in `data/` |
| `public/client.js` | Dashboard UI, API calls, real-time updates |
| `public/*.html` | Control, bulk, admin, login pages |

### Data files (`data/`)

| File | Content |
|------|---------|
| `settings.json` | rpm, cooldown, normalize, replyMode, emoji, forward*, bulk* |
| `clients.json` | `[{ name, emoji }]` |
| `groups.json` | Selected group IDs |
| `groupDirectory.json` | id → name map |
| `processed.json` | Message IDs (cap 50,000) |
| `interactedLogs.json` | Last 2,000 interactions |
| `skippedLogs.json` | Last 2,000 skips |
| `forwardQueue.json` | Pending forward items |
| `bulkState.json` | Running bulk job state |
| `nameTracking.json` | pending / interacted names |
| `sessions/` | whatsapp-web.js LocalAuth (must NOT leave device on Android) |

---

## 2. Reusable vs Rewrite

### ✅ Reusable (logic only — port to Kotlin `domain/`)

| Logic | Source (`bot.js`) | Target |
|-------|-------------------|--------|
| Arabic normalization | `normalizeArabic()` | `ArabicNormalizer` |
| Client name matching | `matchClient()` | `ClientMatcher` |
| Processed ID dedup | `isProcessed()` / `markProcessed()` | `ProcessedMessageTracker` + Room |
| Rate limit (RPM window) | `ensureRateLimit()` | `RateLimiter` |
| Cooldown per group | `respectCooldown()` | `CooldownTracker` |
| Message filters | `shouldProcessMessage()` | `MessageFilter` |
| Text extraction rules | `extractText()` | `MessageTextExtractor` (Baileys adapter) |
| Forward queue + batch | `enqueueForward`, `flushForwardBatch` | `ForwardQueueProcessor` |
| Bulk send loop | `runBulkLoop()` | `BulkJobRunner` |
| Reconnect backoff | partial in bot reconnect | `ReconnectBackoff` (5→60s) |
| Pairing phone normalize | `normalizePhoneNumber()` | `PhoneNumberNormalizer` |

### ⚠️ Adapt (different API shape)

| Feature | Desktop | Android |
|---------|---------|---------|
| WhatsApp connection | whatsapp-web.js Client | Baileys (preferred) in embedded Node **or** native Kotlin port |
| Session storage | LocalAuth folder | App-private + Android Keystore encryption |
| Pairing | `pairWithPhoneNumber` | Baileys pairing code API |
| React to message | `message.react()` | Baileys `sendMessage` reaction |
| Forward | `msg.forward()` | Baileys forward / re-send |
| Groups list | `client.getChats()` | Baileys group metadata |

### ❌ Do not port to Android app

| Component | Reason |
|-----------|--------|
| Express / Socket.io server on `0.0.0.0` | Violates local-only requirement |
| Puppeteer / Chromium | Explicitly excluded |
| JWT / users / login | Personal app — use device biometrics instead |
| `server.js` master credentials | Security risk |
| PM2 / VPS deployment | Not applicable |
| `public/` web dashboard | Replaced by Jetpack Compose |

---

## 3. Target Architecture (Android)

```
┌─────────────────────────────────────────────────────────┐
│  app/          Compose UI · ViewModels · Navigation     │
│                Foreground Service (Phase 4)             │
│                NetworkMonitor (Phase 4)                 │
└────────────┬───────────────────────────────┬──────────┘
             │                               │
┌────────────▼──────────┐         ┌──────────▼──────────┐
│  domain/              │         │  data/               │
│  Models · Use cases   │◄────────│  Room · Repositories │
│  ArabicNormalizer     │         │  Keystore (Phase 3)  │
│  RateLimiter · Backoff│         └─────────────────────┘
└────────────┬──────────┘
             │
┌────────────▼──────────────────────────────────────────┐
│  engine/                                               │
│  WhatsAppEngine interface                              │
│  FakeWhatsAppEngine (Phase 1) ✅                       │
│  NodeBaileysEngine (Phase 3 — validate build first)   │
└────────────────────────────────────────────────────────┘
             │
      127.0.0.1 only (if Node bridge used)
```

### Module responsibilities

| Module | Purpose |
|--------|---------|
| `:domain` | Pure Kotlin — no Android deps. Business rules + engine contract |
| `:data` | Room, DataStore, encrypted session storage, repositories |
| `:engine` | WhatsApp engine implementations (Fake now, real later) |
| `:app` | Android entry, Compose, Hilt, Service, receivers |

---

## 4. WhatsApp Engine Strategy

### Primary plan: Baileys in embedded Node.js

Libraries evaluated:

| Option | Pros | Cons |
|--------|------|------|
| **nodejs-mobile** + Baileys | Reuse JS ecosystem, pairing code supported | Large APK (~40–80MB), ARM ABI builds, maintenance |
| **Pure Kotlin Baileys port** | No Node in APK | Does not exist as mature library |
| **External Termux** | Quick test | Not “normal app” — rejected |

**Decision for Phase 3:** Spike `nodejs-mobile` with minimal Baileys connect + pairing code.  
**If spike fails:** Document failure in this file and evaluate:
- `@itsukichan/baileys` / `@whiskeysockets/baileys` in Termux-free embedded runtime
- Or fork Baileys protocol into Kotlin (long timeline)

**Phase 1 rule:** No claim Baileys works on Android until `./gradlew assembleDebug` + device test pass.

### Bridge protocol (Kotlin ↔ Engine)

Commands / events as typed Kotlin models (JSON only at engine boundary):

- Commands: `Connect`, `RequestPairingCode`, `StartBot`, `StopBot`, `RefreshGroups`, `SendMessage`, …
- Events: `PairingCode`, `ConnectionStateChanged`, `MessageReceived`, `GroupsUpdated`, …

Internal HTTP/WebSocket on `127.0.0.1` allowed **only** inside app process if Node runs in separate thread/process.

---

## 5. Connection States

```
NOT_LINKED → REQUESTING_CODE → PAIRING → CONNECTING → CONNECTED
                ↓                              ↓
           AUTH_FAILED                    DISCONNECTED
                ↓                              ↓
            LOGGED_OUT              WAITING_FOR_NETWORK
                                           ↓
                                  PAUSED_NETWORK_CHANGE
                                           ↓
                                         ERROR
```

Implemented in Phase 1 as `ConnectionState` enum + FakeEngine transitions.

---

## 6. Room Schema (Phase 1 entities created)

| Table | Replaces | Retention cap |
|-------|----------|---------------|
| `clients` | `clients.json` | — |
| `selected_groups` | `groups.json` | — |
| `group_directory` | `groupDirectory.json` | — |
| `processed_messages` | `processed.json` | 50,000 |
| `interaction_logs` | `interactedLogs.json` | 2,000 |
| `skipped_logs` | `skippedLogs.json` | 2,000 |
| `bot_settings` | `settings.json` | single row |
| `forward_queue` | `forwardQueue.json` | — |
| `bulk_jobs` + `bulk_messages` | `bulkState.json` | — |
| `connection_events` | new | 1,000 |

Phase 1: schema + DAOs + sample seed data. Full cleanup jobs in Phase 2.

---

## 7. UI Screens (Phase 1 — demo data / Fake Engine)

| # | Screen | Phase 1 |
|---|--------|---------|
| 1 | Status | ✅ Fake connection + bot controls |
| 2 | Link | ✅ Phone input + fake pairing code |
| 3 | Groups | ✅ Checkbox list from fake engine |
| 4 | Clients | ✅ CRUD UI (Room in Phase 2 wire-up) |
| 5 | Forwarding | ✅ Toggles + queue count (fake) |
| 6 | Bulk send | ✅ Progress from fake engine |
| 7 | Logs | ✅ Tabs with sample logs |
| 8 | Settings | ✅ Local preferences UI |

---

## 8. Implementation Phases

### Phase 1 ✅ (commit f01cf85)

- [x] `MIGRATION_PLAN.md`
- [x] Multi-module Gradle project under `android/`
- [x] Hilt + Compose + Room + Navigation
- [x] Domain logic + unit tests
- [x] `FakeWhatsAppEngine`
- [x] 8 Compose screens
- [ ] `./gradlew test` + `./gradlew assembleDebug` (blocked: no JDK on dev machine)

### Phase 2 ✅ (this commit)

- [x] Port `processMessage` pipeline → `BotOrchestrator`
- [x] `MessageFilter`, `MessageTextExtractor`, `ForwardQueueProcessor`, `CooldownTracker`
- [x] `BotProcessingStore` + `RoomBotProcessingStore` + retention trims
- [x] `DesktopJsonImporter` + bundled sample assets + import UI
- [x] Wire Fake Engine to real business logic + simulate message
- [x] Unit tests: orchestrator, forward batch, message filter, JSON import
- [ ] Device APK build verification (requires JDK 17)

### Phase 3 — Real WhatsApp engine

- Baileys / nodejs-mobile spike
- Pairing code primary, QR fallback
- Encrypted session (Keystore)
- Group fetch + message receive

### Phase 4 — Background + network

- `WhatsAppBotService` foreground service
- `NetworkMonitor` + VPN detection
- Reconnect backoff integration
- `BOOT_COMPLETED` receiver (optional auto-start)

### Phase 5 — Forward + bulk + logs + backup

- Full forward queue persistence
- Bulk job restore after kill
- Log export
- Client list import/export

### Phase 6 — Release APK

- Signed release build
- Device testing matrix
- Battery optimization guide screen

---

## 9. Risks & Constraints

| Risk | Impact | Mitigation |
|------|--------|------------|
| Baileys on Android unproven | Blocks real WhatsApp | Phase 1 Fake Engine; Phase 3 spike with go/no-go |
| WhatsApp account ban | Account loss | Rate limits, sequential send, user warning |
| OEM battery kill | Bot stops | Foreground service + user education screen |
| Large APK (Node + Baileys) | Install friction | Accept for personal use; strip unused ABIs |
| Protocol breaks on WA update | Engine stops | Pin Baileys version; monitor upstream |
| No Play Store | Manual install | `BUILD_AND_INSTALL.md` sideload guide |

---

## 10. Security Changes from Desktop

| Desktop issue | Android approach |
|---------------|------------------|
| Hardcoded master password in `server.js` | Removed — biometric lock optional |
| JWT secret default `CHANGE_ME_SECRET` | No JWT |
| Session in plain `data/sessions/` | Encrypted app storage + Keystore |
| Logs may contain message snippets | Truncate + opt-in full logging |
| `allowBackup` default | `allowBackup="false"` for sensitive data |

---

## 11. Phase 1 File Map

```
android/
├── MIGRATION_PLAN.md          ← this file
├── README.md
├── BUILD_AND_INSTALL.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── domain/                    ← pure logic + tests
├── data/                      ← Room + repositories
├── engine/                    ← FakeWhatsAppEngine
└── app/                       ← Compose UI + Hilt
```

---

## 12. Go / No-Go for Baileys-on-Android (Phase 3 gate)

Proceed only if ALL pass:

1. Node native libs load on `arm64-v8a` device/emulator
2. Baileys generates pairing code
3. Session persists across app restart
4. One group message received in foreground
5. APK size acceptable to user (<200MB)

Otherwise: document alternative in Section 4 and re-plan before continuing.
