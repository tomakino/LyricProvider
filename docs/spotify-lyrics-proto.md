# Spotify 歌词 Protobuf 协议

来源：Spotify 9.1.94.1929（versionCode 147600575）反编译类
`com.spotify.lyrics.serviceretrofit.proto.*`，字段编号取自各类的 `*_FIELD_NUMBER` 常量。

## 顶层响应：`ColorLyricsResponse`

`GET /color-lyrics/v2/track/{id}` 的响应体。

| 字段 | 编号 | 类型 |
|---|---|---|
| lyrics | 1 | LyricsResponse |
| colors | 2 | ColorData |
| has_vocal_removal | 3 | bool |

```proto
message ColorLyricsResponse {
  LyricsResponse lyrics = 1;
  ColorData colors = 2;
  bool has_vocal_removal = 3;
}
```

## `ColorData`

| 字段 | 编号 | 类型 |
|---|---|---|
| background | 1 | int32 |
| text | 2 | int32 |
| highlight_text | 3 | int32 |

```proto
message ColorData {
  int32 background = 1;
  int32 text = 2;
  int32 highlight_text = 3;
}
```

## `LyricsResponse`

| 字段 | 编号 | 类型 |
|---|---|---|
| sync_type | 1 | int32（1=行同步，2=逐字） |
| lines | 2 | repeated LyricsLine |
| provider | 3 | string |
| provider_lyrics_id | 4 | string |
| provider_display_name | 5 | string |
| sync_lyrics_uri | 7 | string |
| alternatives | 9 | repeated Alternative |
| language | 10 | string |
| is_rtl_language | 11 | bool |
| cap_status | 14 | int32 |
| translation_languages | 19 | repeated string |

```proto
message LyricsResponse {
  int32 sync_type = 1;
  repeated LyricsLine lines = 2;
  string provider = 3;
  string provider_lyrics_id = 4;
  string provider_display_name = 5;
  string sync_lyrics_uri = 7;
  repeated Alternative alternatives = 9;
  string language = 10;
  bool is_rtl_language = 11;
  int32 cap_status = 14;
  repeated string translation_languages = 19;

  message LyricsLine {
    int64 start_time_ms = 1;
    string words = 2;
    repeated Syllable syllables = 3;

    message Syllable {
      int64 start_time_ms = 1;
      int32 num_chars = 2;
    }
  }

  message Alternative {
    string language = 1;
    repeated LyricsLine lines = 2;
    bool is_rtl_language = 3;
  }
}
```

**注意**：`Syllable` 只有 `start_time_ms` 和 `num_chars`，没有结束时间。行结束时间与逐字结束时间只能由后续音节/下一行推断。

## v3 响应：`LyricsV3Response`

| 字段 | 编号 | 类型 |
|---|---|---|
| sync_type | 1 | int32 |
| lines | 2 | repeated LyricsLine |
| preview_lines | 3 | repeated LyricsLine |
| provider | 4 | Provider |
| translation | 5 | Translation |
| language | 6 | Language |
| cap_status | 7 | int32 |

```proto
message LyricsV3Response {
  int32 sync_type = 1;
  repeated LyricsLine lines = 2;
  repeated LyricsLine preview_lines = 3;
  Provider provider = 4;
  Translation translation = 5;
  Language language = 6;
  int32 cap_status = 7;

  message LyricsLine {
    int64 start_time_ms = 1;
    int64 end_time_ms = 2;
    string words = 3;
    repeated SyncData sync_data = 4;

    message SyncData {
      int64 start_time_ms = 1;
      int64 end_time_ms = 2;
      int32 num_chars = 3;
    }
  }

  message Language {
    string code = 1;
    bool is_rtl = 2;
  }

  message Provider {
    string name = 1;
    string lyrics_id = 2;
    string display_name = 3;
  }

  message Translation {
    Language language = 1;
    repeated LyricsLine lines = 2;
  }
}
```

v3 与 v2 的差别：

- 行与逐字块都有显式 `end_time_ms`，不需要按下一段推断。
- `provider`、`language`、`translation` 从扁平字符串升级为嵌套 message。
- 新增 `preview_lines`、`translation.lines`。

## 现有实现对照

`platform/spotify/.../api/proto/ProtoModels.kt`：

| 现有模型 | 对应协议 | 缺口 |
|---|---|---|
| `ProtoLyricResponse` | `ColorLyricsResponse` | 缺 `hasVocalRemoval` |
| `ProtoLyricsData` | `LyricsResponse` | 缺 `language`、`isRtlLanguage`、`capStatus`、`syncLyricsUri`、`alternatives`、`translationLanguages` |
| `ProtoLyricLine` | `LyricsLine` | 无 `endTimeMs`（协议里确实没有，靠推断） |
| `ProtoSyllable` | `Syllable` | 多出 `endTimeMs`（协议里没有，属推断值） |
| `ProtoColorData` | `ColorData` | 一致 |
| — | `LyricsV3Response` | 未实现 |
