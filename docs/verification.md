# Verification

Verified on 2026-10-29 on a TCL Fire TV running Android 9 / Fire OS 7, ARMv7.

- English public source compiles through aapt2, javac and D8; APK alignment and signing verification pass.
- Home installation retains its existing language and private settings. Public source has an English ambient clock and contains no personal schedule, device address, account tokens or signing keys.
- All six dock images are publisher-supplied 512 × 512 assets, fitted into the same 116 × 116 logical-pixel area. Original aspect ratio and background are retained. The focus ring sits outside the artwork.
- TV-native refresh completed for all six destinations. The first pass completed five; a subsequent run completed PlayStation while retaining the other successful downloads.
- A downloaded Netflix clip was independently checked: H.264, 1920 × 1080, 18.08 seconds, no audio stream.
- TV player logs confirm 1920 × 1080 playback, including PlayStation. Low-resolution legacy clips are bypassed once HD material is available for that destination.
- The HD pool starts with one new clip per destination and fills up to three across daily refreshes. The configured network schedule is best-effort Android JobScheduler timing, not an exact wall-clock promise.
- The optional runtime bootstrap has been syntax checked and its pinned Maven checksum / EJS metadata endpoints checked. A completely fresh bootstrap download and build has not been run end to end.
- HDMI fallback input selection in the public edition compiles but has not been exercised on multiple manufacturers. The home installation uses its existing HDMI companion shortcut.
- Cold boot after unplugging was not benchmarked. A three-minute post-boot refresh guard is present; no boot-time improvement figure is claimed.
- No system apps were disabled or uninstalled.

Screenshots are real 1920 × 1080 UI captures. Social images place these captures in an English presentation layout; they are not AI-generated UI mockups.
