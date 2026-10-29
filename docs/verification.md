# Verification

Verified on 2026-10-29 on a TCL Fire TV running Android 9 / Fire OS 7, ARMv7.

- English public source compiles through aapt2, javac and D8; APK alignment and signing verification pass.
- Home installation retains its existing language and private settings. Public source has an English ambient clock and contains no personal schedule, device address, account tokens or signing keys.
- Equal-width landscape cards sit in a floating panel over a full-screen preview. Four installed apps provide TV banners; two shortcuts use official promotional artwork. Original backgrounds and aspect ratios are retained.
- TV-native refresh completed for all six destinations. The first pass completed five; a subsequent run completed PlayStation while retaining the other successful downloads.
- A downloaded Netflix clip was independently checked: H.264, 1920 × 1080, 18.08 seconds, no audio stream.
- TV player logs confirm 1920 × 1080 playback, including PlayStation. Low-resolution legacy clips are bypassed once HD material is available for that destination.
- The HD pool starts with one new clip per destination and fills up to three across daily refreshes. The configured network schedule is best-effort Android JobScheduler timing, not an exact wall-clock promise.
- The optional runtime bootstrap has been syntax checked and its pinned Maven checksum / EJS metadata endpoints checked. A completely fresh bootstrap download and build has not been run end to end.
- HDMI fallback input selection in the public edition compiles but has not been exercised on multiple manufacturers. The home installation uses its existing HDMI companion shortcut.
- Cold boot after unplugging was not benchmarked. Controlled reboot reached Android boot completion in 42.7 s before cleanup and 48.9 s afterward (one sample each; ADB polling adds uncertainty). This does not demonstrate a speed improvement. The first post-cleanup launcher timing was invalidated by a missing accessibility setting and manual restoration. A three-minute post-boot refresh guard remains enabled.
- A device-specific, reversible cleanup disabled 13 unused launcher, tutorial, promotional and optional media packages. No packages were uninstalled; this is not a universal debloat recommendation.

Screenshots are real 1920 × 1080 UI captures. Social images place these captures in an English presentation layout; they are not AI-generated UI mockups.

The optional nature loop was verified as H.264, 1920 × 1080, 30.03 seconds, without audio. The clock uses bold type, text shadow and a fixed 70% black overlay. Public builds retain an English date header.

A subsequent reboot with the full accessibility component name retained Home routing and automatically resumed the launcher in 41.8 s (baseline 46.5 s), with boot completion at 37.6 s. This is a single successful comparison, not proof of a general cold-boot speedup.

1080p describes the selected stream, not the detail in every shot: official documentary trailers may contain soft archival footage. No synthetic upscaling is presented as recovered detail.
