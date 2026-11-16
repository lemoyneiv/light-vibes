# Changelog

## 0.6.8

### Authors
- Co-authored by **Kyle ([dibble9012](https://github.com/dibble9012))** and **[lemoyneiv](https://github.com/lemoyneiv)**.

### Material Design 3 interface & presets
- Full **Material Design 3** split-pane UI tailored for wide 1280×480 Gen5W screens in both Day and Night themes, replacing the tall mode button grid with a compact single-row header, categorized **Mode** sheet, segmented tabs, and M3 Expressive sliders.
- **Synesthesia** is now the default preset, alongside **Phase Pulse**, **Phase Drift**, **Duet** (with beat-synced pair walk), **Ember** (with snare/clap spark re-ignition), **Night Drive** (EV power/regen or speed colour + music beat brightness), **Lively**, **Bold Flash**, **Epic**, **Glow**, **Speed**, **Power** and **Efficiency**.

### Hyundai Ioniq 5 & stock `0x01xx` mood palette
- Added all 10 official Hyundai `0x01xx` mood-theme stock colours—including Healing Forest emerald green, Mindful Teal, Ocean Blue, Marigold Gold, Vivid Orchid and Dream Purple—tested live on the Hyundai Ioniq 5.
- Added 3-band Auto-BPM tempo tracking with CarPlay track-skip silence reset and RAM-buffered 20-second USB audio/CSV tuning captures (`Light Vibes tuning/`).

### USB-only LED testing & color capture
- Removed the **Send feedback** screen and all external/GitHub QR code links.
- Redesigned **LED testing & color capture** (`⋮ > LED testing & color capture`) as a two-card USB-only tool:
  - **15-second LED & brightness test** with live step tracking, on-screen diagnosis, and direct **Save to USB** (`Light Vibes reports/`).
  - **Capture current**, **Auto-capture** (1 Hz background watcher while tapping colours in the car's Ambient Light settings or cycling Drive Modes), and **Probe presets** (`setSMLConfiguration` preset scan with automatic baseline restoration), logging full 16-bit RGB + hex values to `Light Vibes reports/captured-colors.txt`.

## 0.6.0

### New cars
- **Hyundai Ioniq 5 and Ioniq 6** (US Gen5W), alongside the Kia EV6. Light Vibes detects the car and
  uses its own 64 factory ambient colours, which differ between Kia and Hyundai. Thanks to
  [lemoyneiv](https://github.com/lemoyneiv).
- **Launch from NEMESIS Manager:** tapping **Launch** on Light Vibes opens the app directly, also on cars
  without the home screen tile, such as the Ioniq 6.

### New modes
- **Power:** the light follows the total power the motors are using, with separate colours for regen,
  zero and full power.
- **Efficiency:** the light follows mi/kWh (or km/kWh), based on the **Current Trip** or the **Car
  Average** the cluster shows.
- **Speed** gets the same new look, and follows the head unit's miles or kilometres setting.

### Pulse
- A **Pulse** card shows the colour in use, and the picker ends with **Drive Mode**: the car's own
  drive-mode colours, which change live with Eco, Normal and Sport while the music plays.
- Picking Drive Mode turns on the car's **Link to Drive Mode** setting, and any other colour turns it
  off, so the colour shows. Colour changes apply instantly.
- **Set as ambient light color** makes the picked colour your car's own ambient colour.

### Your settings stay yours
- When Light Vibes is turned on, it keeps a copy of your ambient light settings (colour, brightness,
  Dimmed in Dark, Link to Drive Mode). They come back when it's turned off, uninstalled or updated.

### Faster, tighter response
- Lights react to the music sooner: twice as many analysis steps a second, an instant response to
  beats, and lamp commands sent through a helper that stays connected instead of starting a new
  command each time.
- **Fastest light change** now goes down to 50 ms, and the built-in presets are quicker.

### Easier to use
- Every mode has **Color**, **Brightness** and **Tuning** tabs.
- New brightness choices: **Follow the music** (with **Average loudness** or **Flash on beat**) or a
  **Fixed level**, and the car's **Dimmed in Dark** on every mode.
- One slider with two handles for each range (speed, power, efficiency, brightness).
- The app follows the head unit's light and dark themes.
- Switching presets with unsaved changes asks whether to save, save as new, or discard. Presets can
  have any name.

### Help and feedback
- **Report a problem** runs a lamp test while you watch, lists each command live, and gives a code
  you scan to open a filled-in GitHub issue. **Test again** runs it once more.
- **Feedback** gives a code for suggesting an idea on GitHub.

## 0.5.0

- First release: Spectrum, Beat and Pulse music modes, Speed mode, presets, and a live meter.
