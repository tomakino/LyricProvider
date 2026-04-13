<!--suppress ALL -->

# LyricProvider - Lyrics Provider

![Platform](https://img.shields.io/badge/Platform-Android-brightgreen?style=flat&logo=android)
![Release](https://img.shields.io/github/v/release/tomakino/LyricProvider?style=flat&color=blue&logo=github)
![Size](https://img.shields.io/github/repo-size/tomakino/LyricProvider)
![Downloads](https://img.shields.io/github/downloads/tomakino/LyricProvider/total?style=flat&color=orange)
![License](https://img.shields.io/github/license/tomakino/LyricProvider?style=flat)
![Last Commit](https://img.shields.io/github/last-commit/tomakino/LyricProvider?style=flat)

<p align="left">
  <a href="README.md"><img src="https://img.shields.io/badge/Document-中文-red.svg" alt="中文"></a>
</p>

## 🎵 Supported Platforms

### Core Adaptations

| Platform                             | Identifier          | Description                                        |
|:-------------------------------------|:--------------------|:---------------------------------------------------|
| 🍎 **Apple Music**                   | `apple-music`       | Supports dynamic lyrics, translated lyrics         |
| ☁️ **Netease Music**                 | `163-music`         | Supports dynamic lyrics, translated lyrics         |
| 🐧 **QQ Music**                      | `qq-music`          | Supports dynamic lyrics, translated lyrics         |
| 🐧 **QQ Music HD**                   | `qq-music-hd`       | Supports dynamic lyrics, translated lyrics         |
| 🧊 **LX Music**                      | `lx-music`          | Supports translated lyrics display                 |
| 🐶 **Kugou Music / Concept Edition** | `kugou-music`       | **Requires enabling car lyrics mode in the app**   |
| 📻 **Kuwo Music**                    | `kuwo-music`        | **Requires enabling car lyrics mode in the app**   |
| 🎧 **Spotify**                       | `spotify-music`     | Currently only supports standard lyrics            |
| ⚡ **Poweramp**                       | `poweramp-music`    | Supports online matching and embedded local lyrics |
| 🧂 **Salt Music**                    | `salt-player-music` | Adapted based on Meizu standard lyric interface    |
| 🎵 **Qishui Music**                  | `qishui-music`      | Supports dynamic lyrics, translated lyrics         |
| 🎵 **MusicFree**                     | `music-free`        | Support translation                                |

### Universal / Special Modules

| Module Name                 | Identifier (ID)  | Applicable Scenario                                   |
|:----------------------------|:-----------------|:------------------------------------------------------|
| ☁️ **Cloud Provider**       | `cloud-provider` | Universal, matches online lyric libraries via search  |
| 📱 **Meizu Lyrics Support** | `meizu-provider` | For players that have adapted Meizu status bar lyrics |
| 🧂 **Car Lyrics Support**   | `car-provider`   | For players that have adapted car lyrics              |

### 🚀 Native Support (No Plugin Needed)

The following players have natively integrated this protocol and can be used directly with Lyricon:

* **Cone Music**: [Official Website](https://coneplayer.trantor.ink/)
* **Flamingo**

---

## 📥 Quick Installation

> [!IMPORTANT]
> This plugin is an extension component and must be used together with the *
*[LyriconCore](https://github.com/tomakino/lyricon/releases/tag/core)** main application.

1. **Download**: Go to the [Releases page](https://github.com/tomakino/LyricProvider/releases) and
   get the latest APK.
2. **Activate**: After installation, open **LSPosed Manager** and enable the **corresponding
   provider**.
3. **Configure Scope**: In LSPosed, check the music apps you want to fetch lyrics for (e.g., Apple
   Music, Netease Music, etc.).
4. **Apply**: Force stop and reopen the respective music app to start enjoying lyrics.

---

## 🛠️ Developer Guide

We warmly welcome community Pull Requests to adapt more music apps.

Please read
the [Development Documentation](https://github.com/tomakino/lyricon/blob/master/lyric/bridge/provider/README.md)

---

## 👥 Contributors

[![Contributors](https://contrib.rocks/image?repo=tomakino/LyricProvider)](https://github.com/tomakino/LyricProvider/graphs/contributors)

### ⭐ Star History

<a href="https://star-history.com/#tomakino/LyricProvider&Date">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=tomakino/LyricProvider&type=Date&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=tomakino/LyricProvider&type=Date" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=tomakino/LyricProvider&type=Date" />
 </picture>
</a>

### 👀 Visitor Trends

![Visitors](https://count.getloli.com/get/@tomakino_LyricProvider?theme=minecraft)