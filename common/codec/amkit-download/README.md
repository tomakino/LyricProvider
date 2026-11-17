# amkit-download

Apple Music TTML 歌词的**下载**（Amp API）。**只返回原文，不做解析**。

解析请用 [amkit-parser](../amkit-parser)。

## 安装

```kotlin
dependencies {
    implementation("io.github.tomakino:amkit-download:0.1.0")
}
```

## 下载原文

```kotlin
import io.github.tomakino.amkit.download.AppleMusicLyricsDownloader
import io.github.tomakino.amkit.download.LyricsCredentials

val downloader = AppleMusicLyricsDownloader(httpClient, credentials)
val ttml: String = downloader.download("1440855459", "cn")
```

## 配合解析

```kotlin
val ttml = downloader.download(songId, storefront)
val document = TtmlLyricsParser().parse(ttml)      // 来自 amkit-parser
val lines = document.displayLines()
```

## 参数

| 参数 | 协议参数 | 说明 |
|---|---|---|
| `lyricsLanguage` | `l[lyrics]` | 译文语言；默认按系统区域推导 |
| `lyricsScript` | `l[script]` | 书写系统；非原生脚本会触发转写 |
| `userAgent` | `User-Agent` | 必须为 Apple 客户端格式，否则一律 404 |
| `baseUrl` | — | 接口根地址，默认 Amp API |

## 凭据

```kotlin
LyricsCredentials(
    developerToken = "...",   // Apple Music API JWT
    cookie = "...",           // apple.com 域下完整 Cookie 集合
    dsid = "...",
)
```

## 注意事项

- **`storefront` 必须与曲目区域一致**，否则 404 与「凭证无效」报错完全相同
- **`l[script]` 必须是 `language-Script` 形式**，只传 `Latn` 会被拒绝（HTTP 400）
- **Cookie 必须是完整集合**，只带 `X-Dsid` 会 404
- **UA 必须是 Apple 客户端格式**，否则一律 404

## 演示

```bash
AMKIT_TOKEN=... AMKIT_COOKIE="..." AMKIT_DSID=... \
  ./gradlew :common:codec:amkit-download:run
```

## 许可证

Apache-2.0
