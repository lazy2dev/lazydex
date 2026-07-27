# LazyDex Design System

> **Reference**: See `.agents/reference/mihon_ui_reference.md` for visual UI patterns adapted from Mihon/Komikku.

## Philosophy

- **Content-first**: UI is a frame for media items — cards show covers, badges, progress. No chrome.
- **Consistency over novelty**: Use Material3 defaults unless a deliberate design decision says otherwise.
- **Theme-aware**: Every color comes from `MaterialTheme.colorScheme`. No hardcoded hex values.

## Color Palette

- Dark theme: Deep background (`#121212`), elevated surfaces, accent from dynamic color or fallback teal
- Light theme: Clean white background, `surfaceVariant` for cards, same accent
- Amoled: Pure black (`#000000`) background
- Status colors defined in `Color.kt`: Completed green, Dropped red, On Hold amber, etc.

## Component Patterns

- **MediaCard**: Cover image (2:3 ratio) + title + status badge + progress bar + star rating
- **Badges** (CategoryBadge, StatusBadge): Small pills, outline or filled, no shadows
- **StarRating**: 1.0–5.0 half-star tappable input, colored by theme
- **Bottom sheets**: Material3 standard sheet, use `surfaceVariant` for dividers

## Typography

Material3 default type scale. No custom fonts. Body text at `bodyMedium`, titles at `titleMedium`/`titleLarge`.
