# 飞牛音乐歌词提供

给 [词幕 Lyricon](https://github.com/tomakino/lyricon) 提供飞牛音乐当前歌词的 LSPosed 模块。

飞牛音乐把当前歌词行放在媒体会话的副标题里。本模块在播放器进程里原样读取这一行，并同步播放、暂停。译文如果写在方括号里，会和原文留在同一句里，例如 `原文 [译文]`。

按飞牛提供的顺序显示全部当前行，包括开场的歌名、歌手、词曲信息和正文，不过滤内容，也不等待下一句才发送首行。

## 使用

1. 安装本模块，在 LSPosed 中启用「飞牛音乐歌词提供」。
2. 作用域选择飞牛音乐（`com.trim.music`）。
3. 强行停止飞牛音乐后重新打开，再播放。

模块包名是 `io.github.proify.lyricon.feiniuprovider`，最低系统版本 Android 8.1。

## 构建

使用仓库根目录的 Gradle Wrapper，JDK 17 或更高版本。签名配置沿用 [LyricProvider](https://github.com/tomakino/LyricProvider) 其他模块的环境变量，通过 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` 指定自己的签名密钥。

```bash
gradlew :feiniu-music:assembleDebug
```

## 许可

Apache License 2.0
