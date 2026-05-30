package io.github.proify.lyricon.symfoniumprovider.xposed;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import io.github.proify.lyricon.lyric.model.LyricWord;

final class LyricCues {
    private LyricCues() {
    }

    private static final class Cue {
        final int begin;
        final Integer end;
        final int charStart;
        final int charEnd;

        Cue(int begin, Integer end, int charStart, int charEnd) {
            this.begin = begin;
            this.end = end;
            this.charStart = charStart;
            this.charEnd = charEnd;
        }
    }

    static boolean isCueList(List<?> cues, int textLength) {
        // Empty cue lists are valid for line-synced lyrics.
        if (cues.isEmpty()) {
            return true;
        }

        int checked = 0;
        for (Object cue : cues) {
            if (cue == null) {
                continue;
            }
            if (read(cue, textLength) == null) {
                return false;
            }
            checked++;
            if (checked >= 3) {
                return true;
            }
        }
        return checked > 0;
    }

    static Cue read(Object rawCue, int textLength) {
        /*
         * Cue objects are detected by shape instead of name. A cue has at least
         * three primitive int values: cue start time, character start, character end.
         * A nullable Integer, when present, is treated as the explicit cue end time.
         */
        if (rawCue == null) {
            return null;
        }

        List<Field> intFields = new ArrayList<>();
        List<Field> integerFields = new ArrayList<>();
        for (Field field : ReflectionAccess.instanceFields(rawCue.getClass())) {
            Class<?> type = ReflectionAccess.fieldType(field);
            if (type == int.class) {
                intFields.add(field);
            } else if (type == Integer.class) {
                integerFields.add(field);
            }
        }

        if (intFields.size() < 3) {
            return null;
        }

        Integer end = null;
        for (Field field : integerFields) {
            end = integerFieldValue(field, rawCue);
            if (end != null) {
                break;
            }
        }

        int[] mapping = chooseMapping(rawCue, intFields, textLength);
        if (mapping == null) {
            return null;
        }
        return new Cue(mapping[0], end, mapping[1], mapping[2]);
    }

    static void appendSignature(StringBuilder builder, List<?> cues, int textLength) {
        for (Object rawCue : cues) {
            Cue cue = read(rawCue, textLength);
            if (cue == null) {
                continue;
            }
            builder.append(cue.begin)
                    .append(',')
                    .append(cue.end)
                    .append(',')
                    .append(cue.charStart)
                    .append(',')
                    .append(cue.charEnd)
                    .append(';');
        }
    }

    static List<LyricWord> convertWords(List<?> cues, String text, long lineEnd) {
        ArrayList<LyricWord> words = new ArrayList<>();
        for (int i = 0; i < cues.size(); i++) {
            Cue cue = read(cues.get(i), text.length());
            if (cue == null) {
                continue;
            }

            int charStart = clamp(cue.charStart, 0, text.length());
            int charEnd = clamp(cue.charEnd, charStart, text.length());
            String wordText = text.substring(charStart, charEnd);
            if (isBlank(wordText)) {
                continue;
            }

            long end = cue.end != null ? cue.end : nextCueStart(cues, i, lineEnd, text.length());
            if (end <= cue.begin) {
                continue;
            }

            words.add(new LyricWord(cue.begin, end, end - cue.begin, wordText, null));
        }

        return words;
    }

    private static long nextCueStart(List<?> cues, int index, long fallback, int textLength) {
        for (int i = index + 1; i < cues.size(); i++) {
            Cue cue = read(cues.get(i), textLength);
            if (cue != null && cue.begin >= 0) {
                return cue.begin;
            }
        }
        return fallback;
    }

    private static int[] chooseMapping(Object rawCue, List<Field> intFields, int textLength) {
        /*
         * Lyricon already supports word-level lyrics, so this scorer is not deciding
         * whether cues should become words. Cues always map to LyricWord when they
         * can be read. The scorer only answers a different problem caused by dynamic
         * reflection: after obfuscation, the cue object exposes three primitive int
         * fields, but field names no longer tell us which one is start, charStart, or
         * charEnd.
         *
         * Current cue declaration order is start/charStart/charEnd, and that order is
         * preferred when valid. If a future R8 pass changes field layout, the fallback
         * tries all mappings and prefers values that form valid text ranges. Time is
         * expected to be much larger than a character index, which helps distinguish
         * the cue start from char offsets after field reordering.
         */
        int declaredStart = intFieldValue(intFields.get(0), rawCue, 0);
        int declaredCharStart = intFieldValue(intFields.get(1), rawCue, 0);
        int declaredCharEnd = intFieldValue(intFields.get(2), rawCue, declaredCharStart);

        int bestStart = declaredStart;
        int bestCharStart = declaredCharStart;
        int bestCharEnd = declaredCharEnd;
        int bestScore = cueMappingScore(
                declaredStart,
                declaredCharStart,
                declaredCharEnd,
                textLength,
                0,
                1,
                2
        );

        for (int startIndex = 0; startIndex < intFields.size(); startIndex++) {
            for (int charStartIndex = 0; charStartIndex < intFields.size(); charStartIndex++) {
                if (charStartIndex == startIndex) {
                    continue;
                }
                for (int charEndIndex = 0; charEndIndex < intFields.size(); charEndIndex++) {
                    if (charEndIndex == startIndex || charEndIndex == charStartIndex) {
                        continue;
                    }

                    int start = intFieldValue(intFields.get(startIndex), rawCue, 0);
                    int charStart = intFieldValue(intFields.get(charStartIndex), rawCue, 0);
                    int charEnd = intFieldValue(intFields.get(charEndIndex), rawCue, charStart);
                    int score = cueMappingScore(
                            start,
                            charStart,
                            charEnd,
                            textLength,
                            startIndex,
                            charStartIndex,
                            charEndIndex
                    );
                    if (score > bestScore) {
                        bestStart = start;
                        bestCharStart = charStart;
                        bestCharEnd = charEnd;
                        bestScore = score;
                    }
                }
            }
        }

        return bestScore >= 0 ? new int[]{bestStart, bestCharStart, bestCharEnd} : null;
    }

    private static int cueMappingScore(
            int begin,
            int charStart,
            int charEnd,
            int textLength,
            int beginIndex,
            int charStartIndex,
            int charEndIndex
    ) {
        /*
         * Cue mapping has the same ambiguity as line timing, but with three int
         * fields instead of two: cue begin time, text char start, and text char end.
         * The only stable information after obfuscation is the runtime value shape.
         * Character offsets must form a valid substring of the lyric line, while the
         * cue begin is a playback timestamp and is usually much larger than a text
         * index. The score therefore rejects impossible text ranges, keeps current
         * field order as a preference, favors ordered char offsets, and gives a small
         * boost when the timestamp-like value is larger than the line length.
         */
        if (begin < 0 || charStart < 0 || charEnd < charStart || charEnd > textLength) {
            return -1;
        }

        int score = 10;
        if (beginIndex == 0) {
            score += 4;
        }
        if (charStartIndex == 1 && charEndIndex == 2) {
            score += 4;
        }
        if (charStartIndex < charEndIndex) {
            score += 2;
        }
        if (begin > textLength) {
            score += 1;
        }
        return score;
    }

    private static int intFieldValue(Field field, Object instance, int fallback) {
        Object value = ReflectionAccess.fieldValue(field, instance);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static Integer integerFieldValue(Field field, Object instance) {
        Object value = ReflectionAccess.fieldValue(field, instance);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
