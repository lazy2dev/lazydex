# Tsundoku Extension Port & Browsing Engine Plan

> **Note**: Stored for execution after Phase 0 Foundation changes.
> Reference repo: `.tmp/tsundoku`

## 1. Goal & Scope
Port the extension subsystem and browsing experience from Tsundoku into LazyDex.
Sole purpose: search, browse, and add web novels (and manga) to LazyDex for local tracking.
No chapter reader or viewer.

## 2. Dependencies & Assets
- Add `io.github.dokar3:quickjs-kt:1.0.0-alpha13` to `app/build.gradle.kts`.
- Copy `.tmp/tsundoku/app/src/main/assets/js/vendor/cheerio.bundle.js` -> `app/src/main/assets/js/vendor/cheerio.bundle.js`.
- Delete legacy 1,730-line `app/lazydex/extension/runtime/JsRuntimeEngine.kt`.

## 3. Core Engine Architecture
Port from Tsundoku `eu.kanade.tachiyomi.jsplugin`:
- `PluginRuntime.kt`: QuickJS execution scope, code sanitization, module loader.
- `JSLibraryProvider.kt`: Bridge providing OkHttp `fetch()`, native Cheerio, crypto (AES-GCM/SHA/MD5), storage, DOMParser.
- `JsSource.kt`: Implements `ContentSource` (`search`, `popular`, `latest`, `getDetails`).
- `JsPluginManager.kt`: Downloads, installs, caches, and initializes `.js` plugins.

## 4. UI: Browse Hub, Sources, and Catalogue
- **Browse Tab**:
  - Top search bar across sources.
  - Category filter carousel (`All`, `Novels`, `Manga`, `Anime`, `Games`).
  - Sources list grouped by category with "Popular" and "Latest" shortcut chips.
- **Source Catalogue Screen**:
  - Responsive 3-column media grid with Coil cover loading and titles.
  - Search query bar in TopAppBar.
  - "Popular" vs "Latest" mode tabs.
  - Dynamic Filter Bottom Sheet powered by source `FilterList`.
- **ItemDetailScreen (Preview Mode)**:
  - Header: backdrop + cover.
  - Title, author, status badge, genre flow-chips, expandable description.
  - "+ Add to Dex" one-tap action (sets default status `PLAN_TO`, streams cover to `files/covers/{id}`, creates `media_items` + `media_links` row).
  - Button animates to "In Dex".

