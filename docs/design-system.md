# Design system

Rules for every screen in `composeApp`. Code lives in `composeApp/.../designsystem/`. Built in M2-6a (#86); later view tickets use it and do not restyle on their own.

Design reference (private Claude Design canvas, tokens, components and the four Settings states in light and dark): https://claude.ai/artifact/GdeA5eXF5ZHQBPNniw4ZSb

## Tokens

- **Color:** Material 3 roles from an indigo seed (`#3F51B5`), `Color.kt`. On Android 12+ the wallpaper-based dynamic scheme replaces it (`dynamicColorScheme`); never hard-code a color in a screen, read `MaterialTheme.colorScheme`. The only fixed colors are Google's button colors (below).
- **Type:** `MaterialTheme.typography` (`Type.kt`), system font. Titles and labels are Medium weight. Use the style names, never raw `sp` values.
- **Shape:** `MaterialTheme.shapes`: small 8, medium 12, large 16, extra large 28 dp. Cards use large, dialogs and pill buttons use extra large, text fields medium.
- **Spacing:** `MaterialTheme.spacing`: xs 4, sm 8, md 12, lg 16, xl 24, xxl 32 dp. Screen edge padding is `lg`. `MinTouchTarget` is 48 dp.
- Theme switching (light, dark, system, palette choice) is not built yet; see [future features](future-features.md).

## Structure

- Every screen uses `AppScaffold(title, onNavigateBack?, actions)`. It gives the top bar (title marked as a heading, back button with description "Back") and passes the inner padding to the content. Apply that padding to scrolling content as `contentPadding`.
- Screens are split into `XRoot` (view model) and `XScreen` (state in, actions out), as in the MVI skill.

## Components (`designsystem/components/`)

| Component | Use for |
|---|---|
| `AppButton(style, loading)` | All buttons: Filled (main action), Tonal, Outlined, Text. `loading` keeps the size and ignores taps. |
| `AppCard` | Group related content (Settings sections). |
| `AppTextField` | Text input with visible label; `errorText` is announced. |
| `AppListRow` | A row with title, supporting text, leading and trailing slots; clickable if `onClick` is set. |
| `AppDialog` | Confirm or cancel a decision. |
| `EmptyState` | Nothing to show yet, with an optional action. |
| `ErrorState` | A failure with an optional retry; announced politely. |
| `GoogleSignInButton` (`settings/`) | Google sign-in only. |

Icons are hand-drawn `ImageVector`s in `designsystem/icons/AppIcons.kt`. `material-icons-extended` is not used: its last release is 1.7.3 (Dec 2024) and it is not tied to Compose Multiplatform 1.12.

## Accessibility rules

- Every tappable control is at least 48 dp tall and wide.
- Every control has text or a `contentDescription`; decorative icons use `contentDescription = null`.
- Errors and loading changes are announced (`liveRegion`, "in progress" description).
- Text contrast at least 4.5:1, non-text (strokes, outlines) at least 3:1. Checked values below; recheck if a color changes.
- Layouts must survive font scale 1.5 without clipping.

### Contrast (computed from the color tokens, WCAG relative luminance)

| Pair | Light | Dark |
|---|---|---|
| onSurface on surface | 16.30 | 14.35 |
| onSurfaceVariant on surface | 8.88 | 10.89 |
| onSurfaceVariant on card (surfaceContainerHigh) | 7.62 | 8.46 |
| onSurface on card | 13.99 | 11.15 |
| onPrimary on primary | 6.87 | 8.14 |
| primary on surface (section titles) | 6.54 | 10.42 |
| primary on card | 5.61 | 8.10 |
| onPrimaryContainer on primaryContainer | 13.20 | 8.09 |
| onSecondaryContainer on secondaryContainer | 13.33 | 7.23 |
| onErrorContainer on errorContainer | 13.26 | 7.24 |
| error on surface | 6.14 | 10.93 |
| Google button text on fill | 16.48 | 14.47 |
| Google button stroke on fill (3:1) | 4.53 | 5.83 |
| outline on surface (3:1) | 4.26 | 5.83 |

## Previews

Every component and screen state has an `@Preview` in light and dark, using `ThemedPreview(darkTheme)` (`designsystem/PreviewSupport.kt`). New components and screens in later tickets do the same.

## Google sign-in button

Source: https://developers.google.com/identity/branding-guidelines (checked 2026-10-23).

- Text is exactly "Sign in with Google" (not "Google" alone).
- Light: fill `#FFFFFF`, 1 dp stroke `#747775`, text `#1F1F1F`. Dark: fill `#131314`, stroke `#8E918F`, text `#E3E3E3`. Fixed colors, picked by theme, not from the color scheme.
- Padding: 12 dp left of the "G", 10 dp between "G" and text, 12 dp right of the text. Label style is `labelLarge` (Medium, 14/20); Google specifies Google Sans, which is not bundled, so the system font is used. Height is at least 48 dp.
- The "G" is Google's own logo file (`g-logo.png`, 200x204, from the guidelines page), shipped unchanged as `composeResources/drawable/google_g.png` and scaled with its aspect ratio kept. Never recolor, stretch or redraw it.
- Loading: spinner replaces the content, button stays the same size and disabled.

Checked 2026-10-23 against the guidelines page and the Android Credential Manager page (both read through page summaries, not raw pages), and against the official Android + Web SVG buttons in Google's asset bundle:

- Match: light and dark colors, 1 dp stroke, padding 12/10/12, label text, standard-color "G" unchanged. The official SVG button is 40 px high with a 20x20 "G" at 12 px and a pill shape; ours is a pill with a 20 dp "G" and a 48 dp minimum height (the touch target rule).
- Android page: a button drawn in Compose is accepted ("this can be done with a Jetpack Compose button"). No Play review rule for the button was found.
- Open: Google specifies Google Sans Medium 14/20; the system font is used (size and weight match). The label is English only; localize it when the app is localized (Google allows and encourages it). Google gives no minimum width, corner radius or height, so the 220 dp minimum width is our own choice.
- Not verified: licence terms for the logo file. The guidelines page states none, and says use not covered by the page needs Google's written consent. Recheck before a Play Store release.
