# LazyDex 0.0.2-beta — Lower-Priority Extras

> **Tagline**: Polish, automation, safety nets, and quality-of-life improvements that the app doesn't need to function, but makes it more robust and delightful.
> **Prerequisite**: Built on top of 0.0.1-beta codebase — all features here assume 0.0.1 foundations exist.

---

## 1. Automation & Background Tasks

### Auto-Backup (WorkManager)
- [ ] Scheduled backup — configurable: Never / Daily / Weekly
- [ ] SAF folder picker (`ACTION_OPEN_DOCUMENT_TREE`) — user selects destination directory
- [ ] Persisted URI via `takePersistableUriPermission`
- [ ] WorkManager `PeriodicWorkRequest` (min interval: Daily = 24h, Weekly = 168h)
- [ ] Timestamped filenames: `lazydex_backup_YYYY-MM-DD_HHmmss.lazydex` (ZIP archive containing backup.json and optional covers/)
- [ ] Safe copy flow: writes to temp file in app-internal storage first, packages ZIP, and only copies to the SAF directory once it is fully validated (prevents partial-write corruption)
- [ ] Retention policy: keep last 30 backup files, auto-delete older
- [ ] UI feedback: last backup timestamp in Settings
- [ ] Error handling: failed backup → notification (not just toast, since user may not be in app)

---

## 2. External API & Search Integration

### AniList / MyAnimeList Search (Metadata Fetch, No Login)
- [ ] AniList GraphQL query — search anime/manga by title
- [ ] MyAnimeList search fallback (REST API, no OAuth required for search)
- [ ] Source URL auto-fill from search result
- [ ] Title, cover image, alt titles populated from selected result
- [ ] Category pre-set from search context (anime vs manga)
- [ ] Search UI in UnifiedAddEditScreen — search bar + result list, triggered from URL field or dedicated button
- [ ] Rate limiting: respect API rate limits, add delay between requests
- [ ] No OAuth/account required for search — public endpoints only

### AniList / MyAnimeList OAuth Sync (Post-v1.0, Scoped)
- [ ] OAuth 2.0 PKCE flow (no client secret, secure)
- [ ] Token storage in EncryptedSharedPreferences / Android Keystore
- [ ] Two-way progress sync: local ↔ remote
- [ ] Status mapping: LazyDex statuses ↔ AniList/MAL statuses
- [ ] Conflict resolution: newest timestamp wins (same as merge logic)
- [ ] Sync trigger: manual button in Settings + auto-sync on app launch (configurable)
- [ ] Rate limiting: respect API rate limits for sync operations
- [ ] Disconnect / revoke token option
- [ ] UI: Sync status indicator, last synced timestamp

---

## 3. Testing Infrastructure

### Unit Tests (JUnit 5 Jupiter + MockK + Turbine)
- [ ] Test framework setup: `test/` directory, Jupiter API + engine, MockK, Turbine, Kotest assertions
- [ ] `ViewModel` tests:
  - [ ] `HomeViewModel` — filter/sort state, empty state, item observation
  - [ ] `UnifiedAddEditViewModel` — add flow, edit flow, scrape flow, validation, discard detection
  - [ ] `SettingsViewModel` — theme toggle, export/import state
- [ ] `MediaRepository` tests:
  - [ ] `add()` — UUID generation, `normalize()` called, lastUpdated set
  - [ ] `update()` — `normalize()` clamping, lastUpdated set
  - [ ] `incrementProgress()` / `decrementProgress()` — atomic, no race
  - [ ] `replaceAll()` — atomic, normalize on each item, exception mapping
  - [ ] `MetadataScraper` tests:
  - [ ] Valid URL → parsed metadata
  - [ ] Invalid URL → error
  - [ ] Timeout → timeout Result via `withTimeout(30_000L)`
  - [ ] Non-HTML response → Content-Type check error
  - [ ] Large response → abort via `SizeLimitedSource`
  - [ ] Auto-charset sniffing → correct parsing without mojibake (null charset)
  - [ ] SSRF SafeDns → local/loopback IPs blocked
- [ ] `BackupProcessor` and `BackupManager` tests:
  - [ ] Round-trip: serialize → deserialize → serialize → compare
  - [ ] ZIP export packaging (backup.json + optional covers/)
  - [ ] ZIP import extraction and parsing
  - [ ] Missing schemaVersion → default 1
  - [ ] Schema > 1 → rejected
  - [ ] Empty items → empty list
  - [ ] Missing id → UUID generated
  - [ ] Missing title → item rejected
  - [ ] Bad status → defaulted to category-adaptive status
  - [ ] Negative progress → clamped to 0
  - [ ] Notes, coverImageUrl, dateAdded preserve/serialize correctly
  - [ ] Merge: timestamps, local wins tie, pure-additions preserve timestamps
  - [ ] Merge duplicate by URL: force local ID mapping to prevent SQLite constraint failures
  - [ ] Merge cover restoration: tie cover image copy/overwrite to conflict resolution winner
  - [ ] All normalized after merge
- [ ] `UrlNormalizer` tests:
  - [ ] Lowercase scheme+host
  - [ ] Strip fragment
  - [ ] Remove trailing slash
  - [ ] Invalid URL → returns trimmed original

### Instrumented UI Tests (JUnit 4 + ComposeTestRule)
- [ ] Test framework setup: `androidTest/` directory, `de.mannodermaus.android-junit5` plugin bridge
- [ ] `HomeScreen` tests:
  - [ ] Empty state displayed when no items
  - [ ] Items displayed in list
  - [ ] Filter bottom sheet opens and filters correctly
  - [ ] Sort bottom sheet opens and sorts correctly
  - [ ] FAB navigates to add mode
  - [ ] Tap card navigates to edit mode
- [ ] `UnifiedAddEditScreen` tests:
  - [ ] Add mode: empty fields, URL field visible
  - [ ] Edit mode: pre-filled fields, delete button visible
  - [ ] Validation: blank title → save blocked
  - [ ] Validation: progress > total → error text + save disabled
  - [ ] Save → item appears in HomeScreen list
  - [ ] Delete → confirmation → item removed from list
- [ ] `SettingsScreen` tests:
  - [ ] Theme toggle persists and takes effect
  - [ ] Export button opens SAF picker
  - [ ] Import button opens SAF picker
- [ ] Navigation tests:
  - [ ] Home → AddEdit (add mode) → back
  - [ ] Home → AddEdit (edit mode) → back
  - [ ] Home → Settings → back
- [ ] Accessibility tests:
  - [ ] Content descriptions on icons and buttons
  - [ ] Focus order in forms

---

## 4. Safety & Error Handling Enhancements

### DB Corruption Recovery
- [ ] Detect `SQLiteDatabaseCorruptException` on DB access
- [ ] Error screen with message: "Database appears corrupted"
- [ ] [Reset Database] button — deletes DB file, re-creates via Room `fallbackToDestructiveMigration()` (or delete database file manually)
- [ ] Export current data before reset option (if any readable data remains)
- [ ] Log corruption details for developer debugging

### Similar Title Detection
- [ ] On Save in UnifiedAddEditScreen: check if main title or any alt title fuzzy-matches existing items
- [ ] Simple similarity: lowercase contains match or Levenshtein distance < 3
- [ ] Warning: "Similar title already exists: 'Existing Title'" — inline below Save button
- [ ] Non-blocking — user can still save (warning only)
- [ ] Case-insensitive comparison, trim both sides

### Enhanced Validation
- [ ] URL format validation on sourceUrl field (not just HTTPS prefix)
- [ ] Max title length validation (200 chars)
- [ ] Max notes length validation (5000 chars)
- [ ] Max alt titles count validation (20 items)

---

## 5. Polish & UX Refinements

### Rating Color Interpolation
- [ ] Star color maps to rating value:
  - 5.0 → `#22c55e` (green)
  - 4.0 → `#84cc16` (yellow-green)
  - 3.0 → `#eab308` (yellow)
  - 2.0 → `#f97316` (orange)
  - 1.0 → `#ef4444` (red)
- [ ] Smooth interpolation between breakpoints, not stepped
- [ ] Stars tinted with interpolated color

### Animations & Transitions
- [ ] Shared element transition: MediaCard cover image → UnifiedAddEditScreen cover
- [ ] Fade transition between HomeScreen and SettingsScreen
- [ ] LazyColumn item animations: `animateItemPlacement()`
- [ ] Bottom sheet: spring animation on open/close
- [ ] FAB: rotate + icon morph on scroll (optional)
- [ ] Progress change animation (subtle number crossfade)
- [ ] Delete item: fade-out + slide-out animation before removal

### Accessibility (a11y)
- [ ] Content descriptions on all icons (FAB, filter, sort, settings, delete, swap, remove, add, open URL, auto-fill, stars, category/status badges)
- [ ] Meaningful content descriptions for images (cover image → item title + "cover")
- [ ] `isImportantForAccessibility = true` on primary interactive elements
- [ ] Focus order follows visual order in forms (UnifiedAddEditScreen)
- [ ] Star Rating: announce current rating on focus, change on swipe up/down
- [ ] TalkBack-friendly error announcements (announce when progress > total error appears)
- [ ] Minimum touch target size (48dp) on all interactive elements
- [ ] High contrast mode support (respect system settings for forced colors)

### Multi-Language / i18n
- [ ] Extract all user-facing strings to `strings.xml`
- [ ] Use `UiText` sealed interface (ResourceString / PlainString) for all UI messages
- [ ] Support locale: `values/strings.xml` (English, default)
- [ ] Support locale: `values-ja/strings.xml` (Japanese)
- [ ] Support locale: `values-es/strings.xml` (Spanish)
- [ ] Support locale: `values-zh/strings.xml` (Simplified Chinese)
- [ ] Date/time formatting via `java.time.format.DateTimeFormatter` (locale-aware)
- [ ] Number formatting (progress, rating) via `java.text.NumberFormat` (locale-aware)

---

## 6. Build & Release Tooling

### ProGuard / R8 Optimization
- [ ] Custom `proguard-rules.pro` for:
  - kotlinx.serialization (keep `@Serializable` classes)
  - Room (keep entities, DAOs)
  - Coil (keep image loader components)
  - Koin (keep module definitions)
  - OkHttp (keep DNS, interceptors)
  - Jsoup (keep parser classes)
- [ ] Enable R8 full mode (`isMinifyEnabled = true`, `isShrinkResources = true`)
- [ ] Test release build: verify no crashes from stripping
- [ ] Mapping file upload for crash reporting (if crash reporting added)

### CI/CD Configuration
- [ ] GitHub Actions workflow:
  - Build debug APK on push (all branches)
  - Build release APK on tag (v*)
  - Run unit tests on push
  - Lint check on push
  - Optional: upload APK as artifact
- [ ] Pre-merge checks:
  - All unit tests pass
  - All lint checks pass
  - Build succeeds

### Crash Reporting (Optional)
- [ ] Firebase Crashlytics or Sentry integration
- [ ] Non-blocking: app functions without crash reporting dependency
- [ ] Rate limiting: max 1 crash report per 30 seconds
- [ ] User consent dialog on first crash (opt-in)
- [ ] Attach non-sensitive context: Android version, device model, app version

---

## 7. Infrastructure Improvements

### DataStore Migration (if needed)
- [ ] If SharedPreferences was used for 0.0.1 theme prefs: migrate to DataStore
- [ ] If DataStore already used: ensure proper error handling (`DataStoreException`), corruption recovery

### Backup Enhancements
- [ ] Encrypted backup option (AES-GCM, key derived from user-provided passphrase)
- [ ] Backup file integrity check (SHA-256 hash in envelope alongside items)
- [ ] Verify backup on export: re-import and compare item count before confirming success
- [ ] Partial import: show diff of what will be added/updated/unchanged before confirming

### Performance Optimization
- [ ] LazyColumn: `key` parameter on items for stable identity
- [ ] Image caching: Coil disk cache size limit, memory cache tuning
- [ ] Database: index analysis via Room `@Query(EXPLAIN QUERY PLAN ...)`, add composite indexes if needed
- [ ] Scraper: connection pool tuning, keep-alive strategy
- [ ] Backup: streaming JSON parsing for large exports (avoid loading entire list into memory twice)
- [ ] Startup: benchmark `LazyDexApp.onCreate()` — defer non-critical Koin modules to lazy init

---

## Deferred from 0.0.2 (Future Releases)

| Feature | Target | Rationale |
|---------|--------|-----------|
| Extension/plugin system | v2.0+ | Complex, requires plugin API design |
| Widget (home screen) | v2.0+ | App Widgets API changes, not critical |
| Multi-device / tablet layout | v2.0+ | Adaptive layout needed |
| Web version (Compose Multiplatform) | Future | Requires Compose for Web maturity |
| Cloud sync (own server) | Future | Server infrastructure, privacy-first design |
| Push notifications (reminders) | Future | Requires FCM, opt-in only |
| Reading stats / charts | Future | Analytics over time, requires data accumulation |
| CSV import/export | Future | Niche, JSON is primary format |
| Batch operations | Future | Select multiple items, bulk edit/delete |

---

## Summary

**0.0.2-beta is not a visual redesign or a new-user experience** — it's the layer of robustness, automation, and confidence that turns a working app into a reliable daily driver. Every item here is either "nice to have but not urgent" (auto-backup, AniList search) or "important but can wait" (tests, CI, a11y).

None of these features are required to open the app, add a novel, track progress, rate it, and close the app. That's 0.0.1's job.
