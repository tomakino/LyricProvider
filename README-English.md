# Lyric Extensions for Lyricon

![Platform](https://img.shields.io/badge/Platform-Android-brightgreen?style=flat&logo=android)
![Release](https://img.shields.io/github/v/release/tomakino/LyricProvider?style=flat&color=blue&logo=github)
![Size](https://img.shields.io/github/repo-size/tomakino/LyricProvider)
![Downloads](https://img.shields.io/github/downloads/tomakino/LyricProvider/total?style=flat&color=orange)
![License](https://img.shields.io/github/license/tomakino/LyricProvider?style=flat)
![Last Commit](https://img.shields.io/github/last-commit/tomakino/LyricProvider?style=flat)

## 🎵 Supported Platforms

These providers use **Xposed Hooking** to extract real-time lyric data directly from the following
music applications.

### Core Integrations (Global & Major)

| Platform                  | Identifier          | Capabilities                           |
| :------------------------ | :------------------ | :------------------------------------- |
| 🎧 **Spotify**             | `spotify-music`     | Standard lyrics (static)               |
| 🍎 **Apple Music**         | `apple-music`       | Dynamic lyrics, Translations           |
| ☁️ **NetEase Cloud Music** | `163-music`         | Dynamic lyrics, Translations           |
| 🐧 **QQ Music**            | `qq-music`          | Dynamic lyrics, Translations           |
| 🐧 **QQ Music HD**         | `qq-music-hd`       | Dynamic lyrics, Translations           |
| ⚡ **Poweramp**            | `poweramp-music`    | Online matching & Embedded lyrics      |
| 🧊 **LX Music**            | `lx-music`          | Lyric translations                     |
| 🐶 **Kugou / Lite**        | `kugou-music`       | **Requires "Car Mode" enabled in-app** |
| 📻 **Kuwo Music**          | `kuwo-music`        | **Requires "Car Mode" enabled in-app** |
| 🧂 **Salt Player**         | `salt-player-music` | Uses Flyme (Meizu) Lyric standard      |
| 🎵 **汽水音乐**            | `qishui-music`      | Dynamic lyrics, Translations           |

### Universal & Special Modules

| Module Name                  | Identifier (ID)  | Use Case                                                               |
|:-----------------------------|:-----------------|:-----------------------------------------------------------------------|
| ☁️ **Cloud Provider**        | `cloud-provider` | Generic matching via online lyric databases                            |
| 📱 **Meizu Support**         | `meizu-provider` | Works with any player supporting Meizu Status Bar lyrics               |
| 🧂 **In-car lyrics Support** | `car-provider`   | Suitable for players that have been adapted for in-car lyrics display. |

### 🚀 Natively Supported (No Plugin Required)

The following players have built-in support for the Lyricon protocol and work out of the box:

* **ConePlayer (光锥音乐)**: [Official Homepage](https://coneplayer.trantor.ink/)
* **Flamingo**

---

## 📥 Installation

> [!IMPORTANT]
> This is an **extension package**. You must have the [Lyricon](https://github.com/tomakino/lyricon)
> main application installed for this to function.

1. **Download**: Grab the latest APK from
   the [Releases page](https://github.com/tomakino/LyricProvider/releases).
2. **Activate**: Install the APK, open your **LSPosed Manager**, and enable the specific Provider
   module.
3. **Configure Scope**: In LSPosed, select the target music apps you wish to hook (e.g., Spotify,
   Apple Music).
4. **Apply**: Force stop and restart your music app to activate the lyrics.

---

## 🛠️ Developer Guide

We welcome community contributions! If you'd like to help adapt more music players, please check our
documentation.

Refer to
the [Development Guide](https://github.com/tomakino/lyricon/blob/master/lyric/bridge/provider/README-English.md).

---

## 🤝 Contributors

[![Contributors](https://contrib.rocks/image?repo=tomakino/LyricProvider)](https://github.com/tomakino/LyricProvider/graphs/contributors)

### Star Growth

<a href="https://star-history.com/#tomakino/LyricProvider&Date">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=tomakino/LyricProvider&type=Date&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=tomakino/LyricProvider&type=Date" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=tomakino/LyricProvider&type=Date" />
 </picture>
</a>

### Traffic Trends

![Visitors](https://count.getloli.com/get/@tomakino_LyricProvider?theme=minecraft)