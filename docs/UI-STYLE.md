# OBD2 Reader UI style guide

This guide describes the app's own patterns as they are in the code. Follow it for any new screen, sheet or panel. Where the app doesn't follow it yet, the gap is listed under "Known gaps" at the end. Every UI change gets a design review against this guide before it goes out for approval.

## 1. Colour: always a theme token

There are 13 styles, two of them light (Pit lane, Rally stage), and day/night switching can change the style while the app is open. So **never hard-code a colour**. Read one of the theme attributes in `res/values/attrs.xml` with `themeColor(R.attr.…)` in code or `?attr/…` in XML.

| Token | Use it for |
|---|---|
| `obdBg` | The page behind everything |
| `obdSurface` | Panels on the page (`panelBackground()`), and the background of sheets and dialogs |
| `obdSurfaceHigh` | Anything inside a sheet: tiles, rows, panels (`tripTile()`) |
| `obdOutline` | Hairlines, rings, strokes on the outlined layouts |
| `obdTextPrimary` | Titles, values, body text you must read |
| `obdTextSecondary` | Labels, help lines, notes, captions, units |
| `obdAccent` | Links, the current value in a row, the filled button, the selected tab |
| `obdOk` / `obdWarn` / `obdDanger` | Status only: good, check soon, bad. Use them for fills, dots, bars and large numbers (24sp or more). Don't use `obdWarn` for body text: amber on the light styles is about 2.1 to 2.4:1 contrast, which fails readability. |
| `obdStripe1/2`, `Channels.forKey()` | Livery stripes and the fixed colour of each reading in graphs |

Check the result in **Asphalt** (dark), **Pit lane** (light) and one outlined layout (**Digital cockpit** or **Hairline**) before sending it for review.

## 2. Fonts and text sizes

All text is **Archivo**. A plain `TextView` gets it from the theme. A `Paint` (custom views, the overlay, the drive card) **does not**, so always set a typeface:

| Role | Font | Size | Colour |
|---|---|---|---|
| Sheet or page title | `titleTypeface()` (style `ObdHeading` in XML, then `applyTitleFonts()`) | 22sp in sheets, 20sp on pages | primary |
| Big numbers (readings, tiles, scores) | `displayTypeface()` + `fontFeatureSettings = "tnum"` | 24sp in sheet tiles; dashboard sizes per layout | primary, or a status colour for scores |
| Section heading inside a sheet | `uiTypeface(600)` | **15sp**, padding 16dp above and 4dp below | primary |
| Row title (tappable row, switch row) | `uiTypeface(500)` | 15sp | primary |
| Body paragraph (things to read) | `uiTypeface()` | 14sp, line spacing 1.15 | primary or secondary |
| Note, help line, caption | `uiTypeface()` | 13sp, line spacing 1.15 | secondary |
| Tile label | `uiTypeface()` | 12sp, one line | secondary |
| Canvas text in custom views | `context.uiTypeface(500)` | in **sp** (`TypedValue.COMPLEX_UNIT_SP`), never dp or raw px | secondary or primary |

Rules:
- Size text in **sp**, not dp, so it follows the phone's font size setting.
- Use the theme's display font only for numbers and titles, never for data like a file name or a date. Long data in a title wraps badly.
- Any number that updates or sits in a column uses `tnum`, so the digits don't jump around.
- On one screen, numbers with the same job get the same size. Don't put 22, 24 and 26sp side by side.

## 3. Alignment and centring

- **Centred cells** (scores, g-force: equal columns in one tile): the cell is `LinearLayout(VERTICAL)` with weight 1 and `gravity = CENTER_HORIZONTAL`, **and every TextView in it also gets `gravity = CENTER_HORIZONTAL`**. A TextView that fills its cell's width keeps its text at the left unless its own gravity is set. This caused the 2.5.3 trip summary bug.
- **Left-aligned tiles** (Distance, Driving time and so on): the label sits **above** the value, with 14dp side padding and 12dp top and bottom.
- A row with a value: name on the left (weight 1), value on the right in `obdAccent`, then the chevron. The value is the **current setting** ("Automatic", "Racing green"), never an action word like "Open".
- In a row with a switch, the switch is vertically centred on the row.
- Text drawn on a canvas must never overlap a ring, line or edge. Reserve room for it and centre it in that room. Leave at least 8dp to the panel edge.
- Bulleted and numbered lists wrap with a hanging indent, so the second line starts under the text, not under the bullet.

## 4. Spacing

Spacing comes in steps of **4dp**: 4, 8, 12, 14 (tile padding), 16, 20, 24.

- Sheets: 20dp side padding, 8dp top, 24dp bottom.
- 8dp gap between tiles and between rows.
- Section heading: 16dp above, 4dp below.
- Buttons along the bottom of a sheet: 18dp above.

## 5. Shapes

- Panels and tiles use `obdCardRadius` (`panelBackground()`, `tripTile()`, `themeShape()`).
- Buttons use `obdButtonRadius`. `sheet_car.xml` does this with `app:cornerRadius="?attr/obdButtonRadius"`. A plain `MaterialButton` is a fully round pill by default, which doesn't match the app.
- The main screen's big actions are `SkewButton` (they lean with the theme's `obdSkew`).

## 6. Sheets: use the shared helpers

Build every settings or info sheet from the helpers in `MainActivity` so they match. Don't write a new local `heading()` or `line()` with different sizes.

- `choiceSheet(title)`: the title and body padding.
- `sheetNote()` for notes, `sheetHeading()` for section headings, `sheetRow()` for a tappable row with its value, `sheetOptions()` for a single choice, `sheetButtons()` for the button row.
- **No more than 2 buttons in a row.** The row is right-aligned and sized to its content, and a third button doesn't fit on 360dp phones. For 3 or more actions, stack full-width buttons the way the trip summary does: filled (main action), then outlined, then borderless (destructive, with `obdDanger` text).
- Deleting always asks first, with a `MaterialAlertDialog` like "Delete this drive?" and Cancel / Delete buttons.
- Lists of things (drives, recordings) go in a sheet, not an alert dialog.
- Switches in sheets: 15sp primary text, 48dp minimum height. Use one shared helper for all of them.

## 7. Words, dates and numbers

- Use sentence case, plain words and British spelling, the way the rest of the app does.
- Dates on screen: `EEE d MMM, h:mm a` ("Sat 14 Nov, 9:52 AM"). Add the year only when it isn't this year. Never show ISO dates (2026-11-13) on screen.
- Join items on one line with " · ".
- Put units after a space: "62 %", "1.21 g", "138 °F". Use `Units` for anything that can be metric or imperial.

## 8. Before you ask for a design review

1. Tour screenshots in **Asphalt and Pit lane** of every screen or sheet you changed, plus landscape if it's on the Dashboard.
2. The HTML preview updated to match the **app**, not the other way round.
3. Check at the largest font size (Settings → Display → Font size), or at least on a 360dp-wide screen.
4. Send the reviewer the PR, its head commit and the screenshot paths.

## Known gaps (to fix)

The design review keeps the current list, with priorities. As of 2026-11-14:
- Sheets, dialogs and the bottom bar take Material's default purple-grey or pink tint, not the theme's `obdSurface` (`colorSurfaceContainer*` isn't set in the themes).
- `sheetRow` panels use `obdSurface` inside sheets, so they nearly vanish. They should use `obdSurfaceHigh`.
- Material buttons and button toggles are round pills in most places.
- `TrendView` (drive history) draws its labels with no typeface.
- Section headings in sheets are 14sp in some and 15sp in others. Body text is 13sp in some and 14sp in others.
- `obdWarn` is used for text in several places (performance estimate, car support "maybe", medium-severity codes).
