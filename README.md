<!--suppress ALL -->

# LyricProvider - 歌词提供器

#### 基于Xposed 的歌词提供器

![Platform](https://img.shields.io/badge/Platform-Android-brightgreen?style=flat&logo=android)
![Release](https://img.shields.io/github/v/release/tomakino/LyricProvider?style=flat&color=blue&logo=github)
![Size](https://img.shields.io/github/repo-size/tomakino/LyricProvider)
![Downloads](https://img.shields.io/github/downloads/tomakino/LyricProvider/total?style=flat&color=orange)
![License](https://img.shields.io/github/license/tomakino/LyricProvider?style=flat)
![Last Commit](https://img.shields.io/github/last-commit/tomakino/LyricProvider?style=flat)

<p align="left">
  <a href="README-English.md"><img src="https://img.shields.io/badge/Document-English-red.svg" alt="EN"></a>
</p>

## 🎵 支持平台

### 核心适配列表

| 平台名称                                                                 | 标识符          | 功能说明                                  |
|:-------------------------------------------------------------------------|:----------------|:------------------------------------------|
| Apple Music                                                              | `apple-music`   | 支持逐字、翻译、背景人声、对唱格式歌词    |
| 网易云音乐/荣耀版                                                        | `163-music`     | 支持逐字歌词、翻译歌词                    |
| QQ 音乐                                                                  | `qq-music`      | 支持逐字歌词、翻译歌词                    |
| QQ 音乐简洁版                                                            | `qq-music-free` | 支持逐字歌词、翻译歌词                    |
| QQ 音乐 HD                                                               | `qq-music-hd`   | 支持逐字歌词、翻译歌词                    |
| 波点音乐                                                                 | `bodian`        | 支持逐字歌词，翻译歌词                    |
| [LX 音乐](https://github.com/lyswhut/lx-music-mobile)                    | `lx`            | 支持普通歌词，翻译歌词                    |
| 酷狗音乐/概念版                                                          | `kugou`         | 支持逐字歌词，翻译歌词                    |
| 酷我音乐                                                                 | `kuwo`          | 支持普通歌词，需在 App 内开启车载歌词模式 |
| Spotify                                                                  | `spotify`       | 支持普通歌词                              |
| [Poweramp](https://powerampapp.com)                                      | `poweramp`      | 支持网络匹配，普通歌词/逐字歌词           |
| 汽水音乐                                                                 | `qishui`        | 支持逐字歌词、翻译歌词                    |
| [MusicFree](https://github.com/maotoumao/MusicFree)                      | `music-free`    | 支持普通歌词，翻译歌词                    |
| 飞牛音乐                                                                 | `feiniu`        | 支持普通歌词                              |
| [Gramophone](https://github.com/FoedusProgramme/Gramophone)              | `gramophone`    | 支持普通歌词/逐字歌词                     |
| [Symfonium](https://symfonium.app)                                       | `symfonium`     | 支持普通歌词/逐字歌词                     |
| [Splayer Android](https://github.com/SPlayer-Dev/SPlayer-for-Android/)   | `splayer`       | 支持普通歌词/逐字歌词                     |
| [RetroMusicPlayer](https://github.com/RetroMusicPlayer/RetroMusicPlayer) | `retro`         | 支持普通歌词/逐字歌词                     |

### 通用/特殊模块

| 模块名称            | 标识符 (ID)      | 适用场景                           |
|:--------------------|:-----------------|:-----------------------------------|
| ☁️ **云音乐提供者** | `cloud-provider` | 通用型，通过搜索匹配在线歌词库     |
| 📱 **魅族歌词支持** | `meizu-provider` | 适用于已适配魅族状态栏歌词的播放器 |
| 🧂 **车载歌词支持** | `car-provider`   | 适用于已适配车载歌词适配的播放器   |

### 🤩 已原生适配的应用

- [光锥音乐](https://coneplayer.trantor.ink/)
- Flamingo
- [BBPlayer](https://bbplayer.roitium.com/)
- MobiMusic
- [Kanade](https://github.com/rcmiku/Kanade)
- Sollin Player
- [QZ Music](https://github.com/lqtmcstudio/QZMusic)
- [棉花音乐](https://github.com/pure-music/PureMusic)
- [Salt Player](https://moriafly.com/saltplayer/)
- [MD3Music](https://github.com/zzyoxml/md3Music)
- [NeriPlayer](https://github.com/cwuom/NeriPlayer)
- [流声音乐](https://github.com/bileizhen/CurrentMusicX)
- [CPPlayer](https://github.com/Aurora-Nasa-1/CPPlayer-KMP)

#### 已适配了但没有你的播放器？请[提交 issue](https://github.com/tomakino/LyricProvider/issues)。
   
---

## 📥 快速安装

1. **下载**：前往 [Releases 页面](https://github.com/tomakino/LyricProvider/releases) 获取最新的 APK
   安装包。
2. **激活**：安装后进入 **LSPosed 管理器**，勾选启用 **对应提供者**。
3. **配置作用域**：在 LSPosed 中勾选你需要获取歌词的音乐 App（如 Apple Music、网易云等）。
4. **生效**：强行停止并重新打开对应的音乐 App 即可体验。

---

## 🛠️ 开发者指南

我们非常欢迎社区提交 Pull Request 来适配更多音乐 App。

请阅读 [开发文档](https://github.com/tomakino/lyricon-api)

[开发文档（旧版本）](https://tomakino.github.io/lyricon/zh-cn/developer/subscriber/)

---

## 👥 贡献者

[![Contributors](https://contrib.rocks/image?repo=tomakino/LyricProvider)](https://github.com/tomakino/LyricProvider/graphs/contributors)

### ⭐ Star 增长

<a href="https://star-history.dera.page/#tomakino/LyricProvider&Date">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://star-history.dera.page/svg?repos=tomakino/LyricProvider&type=Date&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://star-history.dera.page/svg?repos=tomakino/LyricProvider&type=Date" />
   <img alt="Star History Chart" src="https://star-history.dera.page/svg?repos=tomakino/LyricProvider&type=Date" />
 </picture>
</a>

### 👀 访问趋势

![Visitors](https://count.getloli.com/get/@tomakino_LyricProvider?theme=minecraft)
