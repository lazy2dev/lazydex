---
name: lazydex-foundation
description: Inspects current LazyDex Foundation progress in .agents/plan/00-foundation-tasks.md and outputs a copy-pasteable prompt to start the next phase in a new chat. Use when a phase completes, or when user asks for a handoff prompt for the next phase.
---

# LazyDex Foundation Phase Handoff Skill

Use this skill when a phase is completed to generate a self-contained, copy-pasteable kickoff prompt for a brand new chat.

## Workflow

### 1. Inspect State
1. Read `.agents/plan/00-foundation-tasks.md`.
2. Find the last completed phase (`[x]`) and the next pending task (`[ ]`).
3. Note relevant files created or modified in the completed phase.

### 2. Verify Health
1. Verify unit tests pass with `./gradlew testDebugUnitTest`.
2. Verify git status has no unexpected untracked artifacts.

### 3. Generate New Chat Prompt
Format output as a copy-pasteable Markdown code block following this exact template:

````markdown
We are implementing Phase 0 Foundation for LazyDex (Room Database V6 + AniList 2-Way Sync + Komikku Material 3 UI).

Previous Progress:
- [Summary of completed phase, key files updated, and test pass confirmation]

Locked References:
- Architecture: `.agents/plan/00-foundation-plan.md`
- UI/UX Spec: `.agents/plan/00-foundation-spec.md`
- Tasks: `.agents/plan/00-foundation-tasks.md`
- Source UI: `.tmp/komikku/`

Key Directives:
1. Terminology: AniList standard only (Type: Anime, Manga [Novels are format NOVEL]; Format: TV, Movie, Light Novel, Manhwa, etc.).
2. Zero Random UI: All screens and widgets copied directly from `.tmp/komikku` and adapted surgically.
3. Fast Execution & Verification: Follow `00-foundation-tasks.md` sequentially. After every task, verify with `./gradlew testDebugUnitTest` and check off `[x]`.
4. Style: Terse caveman prose, surgical edits, compiler is truth.

Start immediately with [Next Phase Name], [Next Task ID]: [Action description from 00-foundation-tasks.md].
````
