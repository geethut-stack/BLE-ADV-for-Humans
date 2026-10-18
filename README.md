<div align="center">

# BLE ADV for Humans · BLE ADV 人话版

**A friendly Android remote for Bluetooth-broadcast lamps and ceiling-fan lights**
LampSmart Pro · FanLamp Pro · Zhi Jia · Zhi Guang · Zhi Mei · and more

No account · No internet · No server · English + [简体中文](README.zh-CN.md)

![License](https://img.shields.io/badge/license-MIT-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-green)
[![Languages](https://img.shields.io/badge/UI-English%20%7C%20%E4%B8%AD%E6%96%87-orange)](README.zh-CN.md)
![Status](https://img.shields.io/badge/status-early%20release-yellow)

<p align="center">
  <a href="https://github.com/geethut-stack/BLE-ADV-for-Humans/releases/download/0.2.0/app-debug.apk">
    <img alt="Download APK" src="https://img.shields.io/badge/Download-APK_0.1.0-9A5B00?style=for-the-badge&logo=android&logoColor=white">
  </a>
</p>

</div>

**English** · [简体中文](README.zh-CN.md)


---

## Table of contents

1. [Why this project exists](#why-this-project-exists)
2. [What it does](#what-it-does)
3. [Supported lamps / protocols](#supported-lamps--protocols)
4. [Getting started](#getting-started)
5. [How pairing works](#how-pairing-works)
6. [Troubleshooting & FAQ](#troubleshooting--faq)
7. [How it works (technical overview)](#how-it-works-technical-overview)
8. [Problems we ran into (and what they mean for real use)](#problems-we-ran-into-and-what-they-mean-for-real-use)
9. [How we verify correctness](#how-we-verify-correctness)
10. [Build from source](#build-from-source)
11. [Project structure](#project-structure)
12. [Limitations & roadmap](#limitations--roadmap)
13. [Contributing / reporting your lamp](#contributing--reporting-your-lamp)
14. [Security & privacy](#security--privacy)
15. [Credits, license & disclaimer](#credits-license--disclaimer)

---

## Why this project exists

Millions of cheap ceiling-fan lights, bedside lamps and LED strips are sold with a **tiny remote and a companion phone app**
(LampSmart Pro, FanLamp Pro, Zhi Jia, Zhi Guang, Zhi Mei Deng Kong, ...). They do not use Wi-Fi and they do not use a normal
Bluetooth connection. Instead the remote or app **broadcasts short Bluetooth messages** and the lamp obeys the ones that carry an
ID it has memorised.

That design is cheap, but it makes life hard for the person who just bought the lamp:

- **Pairing is fragile and badly explained.** The lamp accepts a new controller only for a few seconds after power-up. The LampSmart Pro
  store listing itself tells users to press the sync button within **5 seconds** of connecting the lamp to power. Miss the window and the
  app simply "does not see the device".
- **The apps are Chinese-first and often poorly translated.** Reviews of the Zhi Mei Deng Kong app on the App Store describe the app not
  finding the device, instructions that are unclear, parts of the UI still in Chinese, and one user who only got it working after
  power-cycling the lamp and pressing pair within about ten seconds.
- **Many of the apps are abandoned or gone.** Several apps in the list below are no longer on Google Play (the reference project calls
  Smart Light / Argrace "abandoned"; the old FanLamp / ControlSwitch apps were removed). If your lamp came with one of them, your
  remote's companion app may no longer exist.
- **Store ratings reflect the frustration.** At the time of writing, the LampSmart Pro listing on the Google Play Store showed **1.9 / 5
  from 19 ratings** (one storefront, a small sample - but it is a public signal, not an invention).

### How many people are affected?

Nobody publishes a number, and we do not want to invent one. What we can point to is public:

| Signal | Value (when we checked, Oct 2026) |
|---|---|
| Open-source **ha-ble-adv** project (the only serious open-source work for these lamps) | **263 stars, 33 forks** in ~19 months |
| Issues opened on that project | **152** in total, **~90** of which mention pairing (keyword search, so an upper estimate) |
| LampSmart Pro App Store rating (Google Play Store) | **1.9 / 5**, 19 ratings |

If you own one of these lamps, you probably already know the feeling.

### Is there an alternative?

As far as we could find, **no standalone phone app exists** for these lamps besides the manufacturers' own. The only open alternative is
the [**ha-ble-adv**](https://github.com/NicoIIT/ha-ble-adv) integration for **Home Assistant** - an excellent piece of work (this project
stands on it, see [Credits](#credits-license--disclaimer)) - but it needs a running Home Assistant server (usually a Raspberry Pi, mini-PC
or NAS running Linux/Docker) plus a Bluetooth adapter or an ESPHome Bluetooth proxy. That is a lot to ask of someone who only wants to
dim a bedside lamp.

> **This project aims to be the missing piece: a simple, free, offline Android app that anyone can install, pair in a minute, and
> use - in their own language - without owning a smart-home server.**

---

## What it does

- 💡 **Several lamps**, each with one or more *connections* (many lamp apps broadcast two protocol versions at once; we keep every one that works).
- ➕ **Three ways to add a lamp**
  1. **Copy from a remote** - listen while you press a button on the lamp's own remote (or its app on *another* phone). Nothing changes on the lamp.
  2. **Paste a captured packet** - for advanced users (nRF Connect, Bluetooth capture).
  3. **Pair as a new remote** - power-cycle the lamp, tap **Pair**. Best if you only have a phone.
- ✅ **Blink test** to find out which connections really control the lamp (the lamp never reports back).
- 🎛️ **Controls:** ON / OFF, brightness, color temperature, **RGB colors** (where the protocol has them), second light, **auto-off timer**.
- 📱 **Home-screen widgets** (ON/OFF and timer) and **launcher shortcuts** (toggle and 1-5 min timers) per lamp.
- 🌐 **English and 简体中文**, switchable from the sidebar; sidebar also has instructions, compatible lamps, how pairing works, about and licenses.
- 👓 **Large, high-contrast UI** designed to be readable by everyone, including older users.
- 🔒 **No data collection, no account, no internet permission.**

## Screenshots

<p align="center">
  <img width="49%" alt="Weixin Image_20261016033634_176_12" src="https://github.com/user-attachments/assets/f1e000dc-5845-47c6-b474-ffc7c8bba66f" />
  <img width="49%" alt="Weixin Image_20261016033633_175_12" src="https://github.com/user-attachments/assets/64104740-e5ce-4a8c-a96e-f1b54d3b6da4" />
</p>

---

<p align="center">
  <img width="49%" alt="Sidebar" src="https://github.com/user-attachments/assets/38c08262-d590-486c-8a05-de33baadffa4">
  <img width="49%" alt="Add a lamp" src="https://github.com/user-attachments/assets/6c733319-1ea3-406f-9111-437a0d3f21f4">
</p>




---

## Supported lamps / protocols

The protocols supported are the ones used by the following Android phone apps (and iOS for most of them). This list is **filtered
from the [ha-ble-adv](https://github.com/NicoIIT/ha-ble-adv) list to what this app can actually control today** (lights; see the
fan note below).

**Legend**

| Mark | Meaning |
|---|---|
| ✅ | **Tested on a real lamp** (LampSmart Pro, by the author) |
| 🧪 | **Protocol verified** byte-for-byte against the reference implementation's test data, but **not yet tried on a real lamp** — please [report](#contributing--reporting-your-lamp) |
| 🔗 | Can be **paired** as a new remote from this app |
| 📋 | Add by **copying a remote** / pasting a packet (no working pairing step) |

**Apps**

| App | Status | Add by | Notes |
|---|---|---|---|
| [LampSmart Pro](https://play.google.com/store/apps/details?id=com.jingyuan.lamp) | ✅ | 🔗 | The reference lamp, tested on real hardware |
| [FanLamp Pro](https://play.google.com/store/apps/details?id=com.jingyuan.fan_lamp) | 🧪 | 🔗 | Same protocol family as LampSmart Pro |
| [ApplianceSmart](https://play.google.com/store/apps/details?id=com.jingyuan.smart_home) | 🧪 | 🔗 | Same vendor; use the LampSmart / FanLamp option |
| [Vmax smart](https://play.google.com/store/apps/details?id=com.jingyuan.vmax_smart) | 🧪 | 🔗 | Same vendor; use the LampSmart / FanLamp option |
| [Zhi Jia](https://play.google.com/store/apps/details?id=com.cxw.cxwblelight) | 🧪 | 🔗 | |
| [Zhi Guang](https://play.google.com/store/apps/details?id=com.cxw.zhiguang) | 🧪 | 🔗 | Two versions: one based on Zhimei, one on Zhijia |
| [Zhi Mei Deng Kong](http://mihuan.iotworkshop.com/zhiguang/) | 🧪 | 🔗 | Not on Play Store |
| [Mantra Lighting](https://play.google.com/store/apps/details?id=com.newenergy.baolilan) | 🧪 | 📋 | Models Coin, Groenland |
| [Smart Light / Argrace Smart](https://apkpure.com/argrace-smart/ai.argrace.oem) | 🧪 | 🔗 | No RGB; partial remote support; abandoned |
| [LE Light Pro / 乐智光Pro](https://openapi.lelight.top/dl/cqan) | 🧪 | 🔗 | Not on Play Store |
| [RuiXin](https://rx-etech.com/rxzn.html) | 🧪 | 🔗 | Sanweyter devices; no RGB, no timer |
| [RW.LIGHT](https://play.google.com/store/apps/details?id=com.rw.rwblelight) | 🧪 | 🔗 | No group control; no AURA / IR / FIBER |
| [Smart Elfin](https://play.google.com/store/apps/details?id=com.warpfuture.wfiot.g) | 🧪 | 🔗 | |
| [GMIMA](https://www.jasonghost.com/lampSmartGmima/) | 🧪 | 📋 | Not on Play Store |
| [SmartHomeLight](https://wosk.top/zd.html) | 🧪 | 📋 | Not on Play Store |
| [eWeLink](https://play.google.com/store/apps/details?id=com.coolkit) | 🧪 | 📋 | **BLE ADV devices only** — will *never* work with standard BLE GATT. [Check first](https://github.com/NicoIIT/ha-ble-adv/wiki/How-to-know-if-my-Physical-Remote-is-using-BLE-Advertising-to-control-my-Device) |
| [Hxlight](https://play.google.com/store/apps/details?id=com.dxc.delight) / [LED-Spots](https://play.google.com/store/apps/details?id=com.hmwin.bt.broadcast) | 🧪 | 📋 | On/off, brightness, CCT only; no night mode |
| *Legacy* — 'FanLamp', 'ControlSwitch', 'Lamp Smart Pro - Soft Lighting' | 🧪 | 🔗 | Removed from Play Store; same LampSmart / FanLamp protocols |
**Filtered out / not usable yet**

* [Dudugu](https://app.dudugu.com/duduguapp/app/) — **fan-only** device with no direct pairing. The protocol is ported and tested, but this app has no fan controls yet, so it cannot be used for now.

**Ceiling fans:** for apps that control a *fan + light* (FanLamp Pro, Zhi Mei Deng Kong "Fan", ...), this version controls the **light part only**.
Fan speed / direction screens are the next planned feature - the fan protocols are already ported and verified.

**Not supported at all:** lamps controlled by **RF (433 MHz) or infrared** remotes, and devices that use a normal Bluetooth **GATT connection**.
Not sure which kind you have? Read the ha-ble-adv wiki page linked in the eWeLink entry above.

---

## Getting started

### Install

<p>
  <a href="https://github.com/geethut-stack/BLE-ADV-for-Humans/releases/download/0.1.0/app-debug.apk">
    <img alt="Download APK" src="https://img.shields.io/badge/Download-APK_0.1.0-9A5B00?style=for-the-badge&logo=android&logoColor=white">
  </a>
</p>

1. Download the latest APK from the repository's [Releases](../../releases) page (or build it yourself - see [Build from source](#build-from-source)).
2. Allow "install from unknown sources" for your browser/file manager when Android asks.
3. Open **BLE ADV for Humans**. Android 8.0+ is required.

### Permissions (and why)
| Permission | Why |
|---|---|
| **Nearby devices → Bluetooth advertise** (Android 12+) | needed to *send* commands to the lamp |
| **Nearby devices → Bluetooth scan** (Android 12+) · **Location** (Android 8-11) | only for *Copy from a remote* (listen mode). Android requires location permission for Bluetooth scanning on older versions; the app does not use your location. |

There is **no internet permission** - the app cannot send any data anywhere.

### Add your first lamp
1. Open the app → **Add a lamp**.
2. Pick **one** method:
   - **Pair as a new remote** *(easiest)*: unplug the lamp (or switch it off at the wall), wait 5 seconds, plug it back in, then immediately tap **Pair** with the phone next to the lamp. Choose the app your lamp came with in the "Lamp app" dialog (try *LampSmart Pro* or *FanLamp Pro* if unsure).
   - **Copy from a remote**: tap **Start listening**, then press a button on the lamp's physical remote (or use its app on a different phone).
   - **Paste a captured packet**: paste the hex bytes of one advertisement and tap **Decode**.
3. In the **Found** list, tap **Blink** on each connection. **Keep the ones that make the lamp flash**, untick the others.
4. Name the lamp, choose its type (brightness + color temperature / RGB / on-off), tap **Save lamp**.
5. Tap the lamp to control it. Use the **sidebar (☰)** for instructions, language and more.

### Widgets and shortcuts
- Long-press the home screen → **Widgets** → *BLE ADV for Humans*. Widgets control the lamp marked **★** (set it on the lamp's page with *Use for widgets*).
- Inside a lamp you can add an **ON/OFF shortcut** and **1-5 minute timer shortcuts** to your home screen. Long-pressing the app icon also lists shortcuts.

---

## How pairing works

These lamps have **no feedback channel**: the app shouts, the lamp listens, and nothing comes back.

- A lamp only obeys messages that contain an **ID it has memorised**. "Pairing" simply teaches the lamp a new ID.
- For a few seconds after power-up, most lamps accept a new ID. The remote/app then sends a *pair* message that contains its ID.
- Most lamps remember several controllers, so pairing this app normally **keeps your old remote working**. A few lamps forget the oldest one.
- **Copying** a remote's ID (listen mode) avoids pairing completely: both keep working.
- Many lamp apps send **two protocol versions at once** (V1 and V2) with different IDs. That is why one lamp can have several *connections*; the app keeps every one that works.
- Because the lamp never answers, the app uses the **Blink test** and remembers the **last command sent** (it cannot know the real state).

---

## Troubleshooting & FAQ

**The lamp does nothing.**
Stand close. Close the lamp's original app (it can interfere). Open the lamp → *Edit & test* → tap **Blink** on every connection. Power-cycle the lamp and pair again.

**Pairing "didn't work".**
You probably missed the short pairing window. Unplug, wait ~5 s, plug in, and tap **Pair** immediately. Keep the phone next to the lamp. If you have several lamps, fully unplug the others while pairing.

**Why can't I copy the original app running on the same phone?**
Android cannot receive its own phone's Bluetooth broadcasts. Use the lamp's physical remote, a second phone, a pasted packet, or pairing instead.

**Does pairing remove my old remote?**
Usually not (most lamps keep several). Some lamps forget the oldest; if the original remote stops working, pair it again.

**The ON/OFF toggle shows the wrong state.**
The lamp never reports its state; the app remembers the last command it sent. If someone used the physical remote, press ON or OFF once to resync.

**The timer does nothing.**
The timer sends the lamp's *own* timer command, which depends on the lamp's firmware. It worked on the author's LampSmart Pro; other lamps may not support it (the timer card is hidden when no connection supports it).

**Does it work on iPhone?**
No. iOS does not allow apps to broadcast arbitrary advertisement data; this is Android-only.

**Is root needed? Does it drain the battery?**
No root. The phone only transmits for a second or so when you press something.

**My brand isn't in the list.**
Open an issue - if it uses Bluetooth advertising it may be a variation of a supported protocol.

---

## How it works (technical overview)

```

[ UI ] -> LampController -> entity logic -> command translators -> brand codec -> advertisement -> Android BLE advertiser -> lamp
(ON, level, RGB, timer)   (ble_adv rules)   (generated tables)   (crypto/layout)   (UUID list / service data / manufacturer data)

```

1. **No connection:** the app broadcasts BLE *advertisements*; the lamp scans and obeys matching ones.
2. **Codecs:** every brand wraps a small command (cmd + args + ID + rolling counter) with its own checksum, whitening/XOR or AES. All 53 codecs of ha-ble-adv are ported to Kotlin (`core/`).
3. **Generated tables:** each codec's header/length parameters and **command translators** ("brightness 50% → these bytes") are generated from the Python source into `codec_specs.txt`, so nothing is retyped by hand.
4. **Android encoding:** Android only lets apps build UUID lists, service data and manufacturer data, so each codec's payload is mapped onto those (e.g. a 26-byte payload becomes 13 16-bit UUIDs).
5. **Identity per lamp:** each *connection* stores codec + ID + rolling counter/seed (updated after every send).

---

## Problems we ran into (and what they mean for real use)

Building this showed why the OEM apps are so hard to live with. The issues that matter in real life:

1. **There is nothing to connect to.** These lamps expose no Bluetooth connection (GATT) and no Wi-Fi, so ordinary "scan and connect" tools find nothing useful. We had to record the phone's Bluetooth traffic (an HCI snoop log) and decode the broadcasts. *Real-life effect:* any tool for these lamps must speak the broadcast protocol exactly.
2. **Every lamp has its own secret ID.** A lamp ignores everything that does not carry the ID it memorised, so a generic "universal remote" cannot work - each lamp needs its identity learned or paired. *Effect:* the app is built around lamps + identities, with three ways to obtain one.
3. **One lamp, several identities.** The stock app broadcasts two protocol versions alternately (V1 and V2) with *different* IDs. *Effect:* a lamp can have multiple *connections*, and the **Blink test** is how you find the ones that count.
4. **Replaying a recording is not enough.** Packets contain a rolling counter, a seed, checksums and obfuscation/AES. A copied packet is ignored when repeated. *Effect:* we implemented the full encoders and verify them byte-for-byte.
5. **Pairing is a time window with no feedback.** Lamps accept new IDs only briefly after power-up, and never confirm. *Effect:* guided steps, a Pair button, and Blink to verify.
6. **Android cannot hear itself.** The obvious "learn from the stock app on the same phone" idea is impossible. *Effect:* Listen mode needs a remote or a second phone; pasting and pairing cover the rest.
7. **Android restricts what can be broadcast.** Only UUID lists, service data and manufacturer data are allowed; the small "flags" byte is controlled by the system, a legacy advertisement holds 31 bytes, and one remote format uses raw radio bytes that cannot be sent at all. *Effect:* we map payloads onto what Android allows, retry non-connectable if a packet is too large, and mark unsendable codecs as "can't be sent from Android". The tested lamp ignores the flags byte, as the reference decoder does.
8. **Scan results are zero-padded.** Android scan records carry trailing zeros that break length checks. *Effect:* the decoder retries after trimming padding.
9. **The state is unknown.** The lamp never reports on/off, brightness or color. *Effect:* the toggle uses the last command sent and can drift if you use another remote.
10. **The timer is not standard.** The reference project does not send lamp-side timer commands (it only sees them from remotes). We reuse the observed commands; they worked on the author's lamp but depend on firmware.
11. **Hardware is scarce.** Only one lamp family was available for real tests. *Effect:* other brands are verified against the reference code's test data, not hardware - hence the ✅ / 🧪 labels and the request for [lamp reports](#contributing--reporting-your-lamp).
12. **Privacy of identities.** Early code contained the author's own lamp ID. It was removed before release; IDs are now stored only on the user's phone.
13. **Language and accessibility.** The OEM apps are Chinese-first with partial English. *Effect:* full English + 简体中文 UI, large text, thick controls and high-contrast sections.

---

## How we verify correctness

- **Golden vectors from the real implementation.** `tools/gen_vectors2.py` runs the *actual Python codecs* of ha-ble-adv and records what they produce. The Kotlin port must match:
  **318 packet vectors** (encode → decode → full scan pipeline), **236 command-translator vectors** (entity change → command bytes) and **40 color-temperature vectors**, covering all 53 codecs.
- **A real capture.** The author's LampSmart Pro capture is decoded and re-encoded byte-for-byte.
- **Generated data is reproducible.** `tools/gen_specs.py` regenerates `codec_specs.txt`; both tools reproduce the committed files exactly.
- **What this does *not* prove:** that a given real lamp reacts. That is what hardware reports are for.

Run `CoreVectorsTest` (Android Studio) to execute the checks.

---

## Build from source

Requirements: **Android Studio** (Koala or newer), JDK 17, an Android 8.0+ phone (a real phone is needed - emulators cannot transmit BLE).

```bash
git clone <this repository>
# open the folder in Android Studio, let Gradle sync, then Run
```

Regenerate the protocol data/tests (optional, Python 3.12+):

```
git clone https://github.com/NicoIIT/ha-ble-adv
pip install pycryptodome
HA_BLE_ADV=./ha-ble-adv python3 tools/gen_specs.py       # -> app/src/main/resources/codec_specs.txt
HA_BLE_ADV=./ha-ble-adv python3 tools/gen_vectors2.py    # -> app/src/test/.../GoldenVectors.kt
```

---

## Project structure

```
app/src/main/java/com/BLE ADV for Humans/
  MainActivity / ControlActivity / AddDeviceActivity / InfoActivity / BaseActivity   screens + sidebar
  Ui.kt                         themed UI toolkit (cards, thick sliders, dividers)
  LampController.kt             user actions -> entity logic -> codecs -> advertiser
  LampAdvertiser.kt             Android BLE advertising (UUID lists, service data, manufacturer data)
  LampDevice.kt                 lamps, connections, persistence
  Shortcuts / ShortcutActivity / LampWidgets / LampActionReceiver   shortcuts + widgets
  core/                         pure Kotlin protocol layer (no Android dependencies)
    Spec.kt  Translators.kt  SpecCodec.kt  Models.kt  Bits.kt
    FanLampCodecs.kt  Brands1.kt  Brands2.kt  Brands3.kt     brand codecs
app/src/main/resources/codec_specs.txt    GENERATED codec + translator tables
app/src/main/res/                         resources, English (values) + Chinese (values-zh)
app/src/test/.../core/                    golden vectors (GENERATED) + checks
tools/                                    generators (Python)
```

---

## Limitations & roadmap

**Known limitations**

- Android only; cannot learn from the stock app on the same phone; cannot set the advertisement flags byte; cannot send raw payloads (one Zhi Mei fan remote format).
- Lamp state is assumed, never read back.
- Widgets control one lamp (the ★ one).
- Only LampSmart Pro was tested on real hardware.

**Roadmap**
- [ ] Fan controls (speed, direction) and fan-only devices such as Dudugu
- [ ] Light effects / scenes / night mode
- [ ] Choose the lamp per widget
- [ ] "Copy debug info" button for easier bug reports
- [ ] Confirmed-lamp table filled in from community reports
- [ ] Screenshots, signed release APKs, CI (build + tests on every commit)
- [ ] Chinese README (`README.zh-CN.md`)

---

## Contributing / reporting your lamp

The most valuable contribution is a **lamp report**: tell us your lamp's brand, which app it came with, how you added it, and what worked.
Use the *Lamp report* issue template. We will mark brands as ✅ confirmed once someone verifies them on real hardware.

Other ways to help: translations, screenshots, UI polish, fan/effect support, hardware testing.

> ⚠️ **Do not post raw captured packets or lamp IDs publicly.** They let anyone nearby control your lamp.

Contact: [github.com/geethut-stack](https://github.com/geethut-stack)

---

## Security & privacy

- **No data leaves your phone.** No internet permission, no analytics, no account.
- **IDs are stored locally** (app storage) and only used to talk to your own lamps.
- **Bluetooth broadcasts are unauthenticated.** Anyone within range who records your lamp's ID can control it. These lamps were never designed to be secure - do not use them for anything safety-critical.

---

## Credits, license & disclaimer

- **Protocols:** this project is a Kotlin port of the codecs in [**ha-ble-adv**](https://github.com/NicoIIT/ha-ble-adv) by **NicoIIT** (MIT License). Without your work, none of this would exist - thank you.
- **License:** MIT © 2026 geethut-stack (see LICENSE); third-party notices in THIRD_PARTY_LICENSES.md. AndroidX and Material Components are Apache-2.0.
- **Disclaimer:** independent project, **not affiliated with** LampSmart, FanLamp, Jingyuan, Zhi Jia, Zhi Guang, Zhi Mei, or any other lamp maker or app developer. Product names are used only to describe compatibility. Provided "as is", without warranty. Reverse engineering was done for interoperability with devices the author owns.

