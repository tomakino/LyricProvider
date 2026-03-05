# LyricProvider - 歌词提供器

![Platform](https://img.shields.io/badge/Platform-Android-brightgreen?style=flat&logo=android)
![Release](https://img.shields.io/github/v/release/tomakino/LyricProvider?style=flat&color=blue&logo=github)
![Size](https://img.shields.io/github/repo-size/tomakino/LyricProvider)
![Downloads](https://img.shields.io/github/downloads/tomakino/LyricProvider/total?style=flat&color=orange)
![License](https://img.shields.io/github/license/tomakino/LyricProvider?style=flat)
![Last Commit](https://img.shields.io/github/last-commit/tomakino/LyricProvider?style=flat)

<!--suppress ALL -->
<p align="left">
  <a href="README-English.md"><img src="https://img.shields.io/badge/Document-English-red.svg" alt="EN"></a>
</p>

## 🎵 支持平台

### 核心适配列表

| 平台名称              | 标识符              | 功能说明                        |
| :-------------------- | :------------------ | :------------------------------ |
| 🍎 **Apple Music**     | `apple-music`       | 支持动态歌词、翻译歌词          |
| ☁️ **网易云音乐**      | `163-music`         | 支持动态歌词、翻译歌词          |
| 🐧 **QQ 音乐**         | `qq-music`          | 支持动态歌词、翻译歌词          |
| 🐧 **QQ 音乐 HD**      | `qq-music-hd`       | 支持动态歌词、翻译歌词          |
| 🧊 **LX 音乐**         | `lx-music`          | 支持翻译歌词显示                |
| 🐶 **酷狗音乐/概念版** | `kugou-music`       | **需在 App 内开启车载歌词模式** |
| 📻 **酷我音乐**        | `kuwo-music`        | **需在 App 内开启车载歌词模式** |
| 🎧 **Spotify**         | `spotify-music`     | 目前仅支持标准歌词              |
| ⚡ **Poweramp**        | `poweramp-music`    | 支持网络匹配及本地内嵌歌词      |
| 🧂 **Salt 音乐**       | `salt-player-music` | 基于魅族标准歌词接口适配        |
| 🎵 **汽水音乐**        | `qishui-music`      | 支持动态歌词、翻译歌词          |

### 通用/特殊模块

| 模块名称          | 标识符 (ID)         | 适用场景              |
|:--------------|:-----------------|:------------------|
| ☁️ **云音乐提供者** | `cloud-provider` | 通用型，通过搜索匹配在线歌词库   |
| 📱 **魅族歌词支持** | `meizu-provider` | 适用于已适配魅族状态栏歌词的播放器 |
| 🧂 **车载歌词支持** | `car-provider`   | 适用于已适配车载歌词适配的播放器  |

### 🚀 原生支持 (无需插件)

以下播放器已原生集成此协议，可直接配合词幕使用：

* **光锥音乐**: [官方主页](https://coneplayer.trantor.ink/)
* **Flamingo**

---

## 📥 快速安装

> [!IMPORTANT]
> 本插件属于扩展组件，必须配合 **[词幕](https://github.com/tomakino/lyricon)** 主程序方可运行。

1. **下载**：前往 [Releases 页面](https://github.com/tomakino/LyricProvider/releases) 获取最新的 APK
   安装包。
2. **激活**：安装后进入 **LSPosed 管理器**，勾选启用 **对应提供者**。
3. **配置作用域**：在 LSPosed 中勾选你需要获取歌词的音乐 App（如 Apple Music、网易云等）。
4. **生效**：强行停止并重新打开对应的音乐 App 即可体验。

---

## 🛠️ 开发者指南

我们非常欢迎社区提交 Pull Request 来适配更多音乐 App。

请阅读 [开发文档](https://github.com/tomakino/lyricon/blob/master/lyric/bridge/provider/README.md)

---

## 🤝 贡献者

[![Contributors](https://contrib.rocks/image?repo=tomakino/LyricProvider)](https://github.com/tomakino/LyricProvider/graphs/contributors)

### Star 增长

<a href="https://star-history.com/#tomakino/LyricProvider&Date">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=tomakino/LyricProvider&type=Date&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=tomakino/LyricProvider&type=Date" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=tomakino/LyricProvider&type=Date" />
 </picture>
</a>

### 访问趋势

![Visitors](https://count.getloli.com/get/@tomakino_LyricProvider?theme=minecraft)