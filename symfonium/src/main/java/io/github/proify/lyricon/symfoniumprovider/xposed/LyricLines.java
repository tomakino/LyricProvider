package io.github.proify.lyricon.symfoniumprovider.xposed;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import io.github.proify.lyricon.lyric.model.LyricWord;
import io.github.proify.lyricon.lyric.model.RichLyricLine;

final class LyricLines {
    private LyricLines() {
    }

    private static final class Line {
        final int begin;
        final int end;
        final String text;
        final List<?> cues;

        Line(int begin, int end, String text, List<?> cues) {
            this.begin = begin;
            this.end = end;
            this.text = text;
            this.cues = cues;
        }
    }

    static boolean isLineList(List<?> lines, long currentDuration) {
        /*
         * A real lyric container has a homogeneous list of lyric lines. Sampling up
         * to three entries keeps the check cheap while still filtering accidental
         * List fields from similarly shaped classes.
         */
        if (lines.isEmpty()) {
            return false;
        }

        int checked = 0;
        for (Object line : lines) {
            if (line == null) {
                continue;
            }
            if (read(line, currentDuration) == null) {
                return false;
            }
            checked++;
            if (checked >= 3) {
                return true;
            }
        }
        return checked > 0;
    }

    static String signature(Object rawSignature, List<?> rawLines, long currentDuration) {
        /*
         * Prefer Symfonium's own signature when present. Otherwise build a content
         * signature from the normalized structural read so duplicate constructor
         * calls do not repeatedly send the same lyrics to Lyricon.
         */
        String signature = rawSignature instanceof String ? (String) rawSignature : null;
        if (!isBlank(signature)) {
            return signature;
        }

        StringBuilder builder = new StringBuilder();
        for (Object rawLine : rawLines) {
            Line line = read(rawLine, currentDuration);
            if (line == null) {
                continue;
            }

            builder.append(line.begin)
                    .append(':')
                    .append(line.end)
                    .append(':')
                    .append(line.text)
                    .append('\n');
            LyricCues.appendSignature(builder, line.cues, line.text.length());
            builder.append('\n');
        }
        return builder.toString();
    }

    static List<RichLyricLine> convert(List<?> rawLines, long currentDuration) {
        ArrayList<RichLyricLine> result = new ArrayList<>();

        for (int i = 0; i < rawLines.size(); i++) {
            Line line = read(rawLines.get(i), currentDuration);
            if (line == null || isBlank(line.text)) {
                continue;
            }

            long normalizedBegin = Math.max(0L, line.begin);
            long normalizedEnd = normalizeEnd(rawLines, i, normalizedBegin, line.end, currentDuration);
            List<LyricWord> words = LyricCues.convertWords(line.cues, line.text, normalizedEnd);
            result.add(new RichLyricLine(
                    normalizedBegin,
                    normalizedEnd,
                    normalizedEnd - normalizedBegin,
                    false,
                    null,
                    line.text,
                    words.isEmpty() ? null : words,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }

        return result;
    }

    static int selectionScore(List<?> rawLines, long currentDuration) {
        /*
         * A current playable media object can carry multiple lyric candidates.
         * Symfonium's own selector prefers richer lyrics, roughly: word-cued lyrics,
         * then line-synchronized lyrics, then the candidate with more lines. We use
         * the same runtime structure instead of class or field names so the selected
         * object remains stable when obfuscation changes.
         */
        int readableLines = 0;
        int timedLines = 0;
        int cueLines = 0;
        for (Object rawLine : rawLines) {
            Line line = read(rawLine, currentDuration);
            if (line == null || isBlank(line.text)) {
                continue;
            }

            readableLines++;
            if (line.begin > 0) {
                timedLines++;
            }
            if (line.cues != null && !line.cues.isEmpty()) {
                cueLines++;
            }
        }

        if (readableLines == 0) {
            return 0;
        }
        return (cueLines > 0 ? 1000000 : 0)
                + (timedLines > 0 ? 100000 : 0)
                + readableLines;
    }

    private static Line read(Object rawLine, long currentDuration) {
        /*
         * Line fields are discovered structurally:
         *
         * - int/Integer fields are candidate begin/end times
         * - a String field is the lyric text
         * - a List field is accepted only if its contents look like word cues
         *
         * Field order is used as a preference because R8 usually preserves data-class
         * declaration order, but value validation below allows renamed fields and
         * limited field reordering.
         */
        List<Field> intFields = new ArrayList<>();
        List<Field> stringFields = new ArrayList<>();
        List<Field> listFields = new ArrayList<>();

        for (Field field : ReflectionAccess.instanceFields(rawLine.getClass())) {
            Class<?> type = ReflectionAccess.fieldType(field);
            if (type == int.class || type == Integer.class) {
                intFields.add(field);
            } else if (type == String.class) {
                stringFields.add(field);
            } else if (type != null && List.class.isAssignableFrom(type)) {
                listFields.add(field);
            }
        }

        if (intFields.isEmpty() || stringFields.isEmpty() || listFields.isEmpty()) {
            return null;
        }

        String text = null;
        for (Field field : stringFields) {
            Object value = ReflectionAccess.fieldValue(field, rawLine);
            if (value != null) {
                text = String.valueOf(value);
                break;
            }
        }
        if (text == null) {
            return null;
        }

        List<?> cues = null;
        for (Field field : listFields) {
            Object value = ReflectionAccess.fieldValue(field, rawLine);
            if (value instanceof List && LyricCues.isCueList((List<?>) value, text.length())) {
                cues = (List<?>) value;
                break;
            }
        }
        if (cues == null) {
            return null;
        }

        int[] timing = chooseTiming(rawLine, intFields, currentDuration);
        if (timing == null) {
            return null;
        }
        return new Line(timing[0], timing[1], text, cues);
    }

    private static long normalizeEnd(
            List<?> rawLines,
            int index,
            long begin,
            int end,
            long currentDuration
    ) {
        if (end > begin) {
            return end;
        }

        for (int i = index + 1; i < rawLines.size(); i++) {
            Line nextLine = read(rawLines.get(i), currentDuration);
            if (nextLine != null && nextLine.begin > begin) {
                return nextLine.begin;
            }
        }

        long durationEnd = currentDuration > begin ? currentDuration : 0L;
        if (durationEnd > begin) {
            return Math.min(durationEnd, begin + 5000L);
        }
        return begin + 5000L;
    }

    private static int[] chooseTiming(Object rawLine, List<Field> intFields, long currentDuration) {
        /*
         * Current Symfonium lines store begin/end as the first two int fields. If
         * that stops validating, score every pair and choose the pair that looks like
         * a lyric interval: nonnegative begin, end either -1 or after begin, short
         * enough to be a line, and preferably within the known track duration.
         */
        int first = intFieldValue(intFields.get(0), rawLine, 0);
        int second = intFields.size() > 1 ? intFieldValue(intFields.get(1), rawLine, -1) : -1;
        if (intFields.size() == 1) {
            return isValidLineTiming(first, second) ? new int[]{first, second} : null;
        }
        if (isValidLineTiming(first, second)) {
            return new int[]{first, second};
        }

        int bestStart = first;
        int bestEnd = second;
        int bestScore = -1;
        for (int i = 0; i < intFields.size(); i++) {
            for (int j = 0; j < intFields.size(); j++) {
                if (i == j) {
                    continue;
                }
                int start = intFieldValue(intFields.get(i), rawLine, 0);
                int end = intFieldValue(intFields.get(j), rawLine, -1);
                int score = lineTimingScore(start, end, i, j, currentDuration);
                if (score > bestScore) {
                    bestStart = start;
                    bestEnd = end;
                    bestScore = score;
                }
            }
        }

        return bestScore >= 0 ? new int[]{bestStart, bestEnd} : null;
    }

    private static boolean isValidLineTiming(int begin, int end) {
        return begin >= 0 && (end == -1 || end > begin);
    }

    private static int lineTimingScore(
            int begin,
            int end,
            int beginIndex,
            int endIndex,
            long currentDuration
    ) {
        /*
         * The scoring pass exists because all candidate timing fields have the same
         * erased type after obfuscation: int. Reflection can tell us "these are ints",
         * but not which int means begin and which one means end. Relying only on
         * declaration order would work for the current APK and fail if R8 changes
         * field layout, so each possible pair is ranked by semantic constraints:
         *
         * - invalid intervals are rejected outright
         * - current declaration order is preferred when still valid
         * - adjacent fields are preferred because data classes commonly keep related
         *   properties together
         * - very long line durations are less plausible than short lyric lines
         * - begin times inside the known track duration are more plausible
         *
         * This keeps the hook tolerant to renaming and limited field reordering while
         * avoiding a brittle hard-coded "field 0 is begin, field 1 is end" contract.
         */
        if (!isValidLineTiming(begin, end)) {
            return -1;
        }

        int score = 10;
        if (beginIndex == 0) {
            score += 3;
        }
        if (endIndex == beginIndex + 1) {
            score += 3;
        }
        if (end == -1 || end - begin <= 120000) {
            score += 2;
        }
        if (currentDuration <= 0 || begin <= currentDuration) {
            score += 1;
        }
        return score;
    }

    private static int intFieldValue(Field field, Object instance, int fallback) {
        Object value = ReflectionAccess.fieldValue(field, instance);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
