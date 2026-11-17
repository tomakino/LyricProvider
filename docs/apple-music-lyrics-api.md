# Apple Music 歌词接口逆向

## 接口

```
GET https://amp-api.music.apple.com/v1/catalog/{storefront}/songs/{songId}/syllable-lyrics
```

### 请求头

| Header | 值 |
|---|---|
| `Authorization` | `Bearer {developerToken}` |
| `Cookie` | `X-Dsid=...; hsaccnt=1; ...` |
| `x-dsid` | Apple ID 数字 ID |
| `User-Agent` | `Music/6.5.2 Android/16 model/android build/1586 (dt:66)` |
| `Accept` | `application/json` |

三项认证缺任何一项返回 404，与「曲目不存在」不可区分。

## 查询参数

| 参数 | 取值 | 作用 |
|---|---|---|
| `extend=ttmlLocalizations` | 固定 | **译文开关**。不带时字段为 `ttml`，带时字段为 `ttmlLocalizations` |
| `l[lyrics]` | `zh-Hans` / `zh-CN` / `zh-Hant` / `zh-TW` / `zh-HK` / `en` / `ja` ... | 选哪种译文 |
| `l[script]` | `zh-Hani` / `ja-Jpan` / `ja-Latn` / `en-Latn` ... | 书写系统 |

## 穷举测试结果

### 中文歌（songId=339414579，storefront=cn）

固定 `extend=ttmlLocalizations`，遍历 `l[lyrics]` × `l[script]`（8 × 4 = 32 组合）：

| `l[lyrics]` | 结果 |
|---|---|
| `zh` / `zh-Hans` / `zh-CN` | 24,422 字节，含简体 replacement 译文 |
| `zh-Hant` / `zh-TW` | 10,527 字节，无译文（原文即繁体） |
| `en` / `ja` | 10,527 字节，无译文 |
| 空 | 10,527 字节，无译文 |

`l[script]` 取 `zh-Hani` / `zh-Hans` / `zh-Hant` / 空，**结果完全相同**，对中文歌无影响。

不带 `extend` 时，所有组合都是 10,527 字节的 `ttml`，无译文。

### 日文歌（songId=1490256995，storefront=jp）

固定 `extend=ttmlLocalizations`，遍历 `l[lyrics]` × `l[script]`（2 × 4 = 8 组合）：

| `l[script]` | 结果 |
|---|---|
| `ja-Latn` | **56,533 字节**，含 `ja-Latn` 罗马音 |
| `ja-Jpan` / `zh-Hani` / 空 | 22,659 字节，无罗马音 |

`l[lyrics]` 取 `ja` 或空，**结果完全相同**，对日文歌无影响。

不带 `extend` 时，所有组合都是 22,659 字节的 `ttml`，无罗马音。

## 结论

1. **`extend=ttmlLocalizations` 是所有附加内容（译文、罗马音）的总开关**。不带它，`l[lyrics]` 和 `l[script]` 都不生效。
2. **`l[lyrics]` 控制译文语言**，只对有多语言译文的歌有效（中文歌的简繁互转）。
3. **`l[script]` 控制书写系统**，只对有转写内容的歌有效（日文歌的 `ja-Latn` 罗马音）。
4. 中文的 `zh` ≡ `zh-Hans` ≡ `zh-CN`（简体），`zh-Hant` ≡ `zh-TW` ≡ `zh-HK`（繁体）。
5. 非中文语言传变体码不会报错（200），但没有变体内容，效果与纯语言码相同。
6. 所有组合都返回成功（64/64 HTTP 200），参数错误不会导致 4xx。

## 响应结构

```json
{
  "data": [
    {
      "id": "339414579",
      "type": "syllable-lyrics",
      "attributes": {
        "playParams": { },
        "ttml": "<tt ...>",
        "ttmlLocalizations": "<tt ...>"
      }
    }
  ]
}
```

`ttml` 与 `ttmlLocalizations` **互斥**：有 extend 时只有 `ttmlLocalizations`，无 extend 时只有 `ttml`。

## 常见错误

| 现象 | 原因 |
|---|---|
| 404 | 认证缺失（Authorization / Cookie / x-dsid 任一）或 storefront 与曲目不匹配 |
| 200 但无译文 | 漏了 `extend=ttmlLocalizations`，或 `l[lyrics]` 对应的译文不存在 |
| 200 但无 TTML | 曲目没有逐字歌词（syllable lyrics） |
