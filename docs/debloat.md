# Device cleanup research

A package inventory is not a safe deletion list. Start with unused user-installed launchers and apps. Optional features such as Photos, Music, AirPlay, Alexa, Echo audio and live TV should be kept if used. Retail demo content is a candidate for staged evaluation on a home TV.

Do not bulk-disable packages by an `amazon` or `mediatek` prefix. Keep the stock launcher, settings, TV input routing, Bluetooth/remote services, network stack, WebView, DRM, authentication, package installer and software updates. Removing a system app for one user does not reclaim its read-only system image space.

Prefer reversible, one-at-a-time disable tests to system deletion. Record the initial state and verify Home, settings, network, audio, streaming apps and HDMI after each change. No cleanup automation is included here.

There is no representative package-level popularity study behind this project. “Unused in this household” is not “useless for everyone.” Background RSS is not a measured RAM saving, and fewer packages do not prove a shorter cold boot.

Research references:

- [Homestead's community package catalogue](https://github.com/Sarg338/Homestead/blob/main/src/com/homestead/launcher/Debloat.java), reviewed as comparative research; no catalogue code copied. Its device coverage is not certification for every Fire TV.
- [TCL Power On Settings](https://support.tcl.com/en_US/tcl-fire-tv-power-on-settings): selects Home or Last Input, not a guaranteed fast-boot switch.
- [Amazon Photos on Fire TV](https://digprjsurvey.amazon.com/csad/help/node/TMlFkNlI8OsVODP4xs).
- [AirPlay on Fire TV](https://digprjsurvey.amazon.co.uk/csad/help/node/GFAK8J5CYN5T76ET).
- [Fire TV settings and system functions](https://digprjsurvey.amazon.co.uk/csad/help/node/GQBKCHXW3J2FJM8P).
