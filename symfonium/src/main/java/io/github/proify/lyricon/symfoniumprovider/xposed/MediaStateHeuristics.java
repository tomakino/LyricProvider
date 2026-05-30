package io.github.proify.lyricon.symfoniumprovider.xposed;

import android.os.Parcelable;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

final class MediaStateHeuristics {
    private MediaStateHeuristics() {
    }

    static boolean isCurrentMediaStateClass(Class<?> candidate) {
        try {
            /*
             * The current renderer state is not identified by name. In the current
             * Symfonium APK it is vd9, but the stable signal is the field layout:
             * playback booleans, position/duration longs, volume ints, speed floats,
             * and exactly one playable-media field. The playable-media field is checked
             * separately for the MediaItem+lyrics shape.
             */
            int modifiers = candidate.getModifiers();
            if (candidate.isAnnotation()
                    || candidate.isAnonymousClass()
                    || candidate.isArray()
                    || candidate.isEnum()
                    || candidate.isInterface()
                    || candidate.isPrimitive()
                    || Modifier.isAbstract(modifiers)) {
                return false;
            }

            int playableFields = 0;
            int booleanFields = 0;
            int longFields = 0;
            int intFields = 0;
            int floatFields = 0;
            List<Field> fields = ReflectionAccess.instanceFields(candidate);
            for (Field field : fields) {
                Class<?> type = ReflectionAccess.fieldType(field);
                if (isPlayingMediaClass(type)) {
                    playableFields++;
                } else if (type == boolean.class || type == Boolean.class) {
                    booleanFields++;
                } else if (type == long.class || type == Long.class) {
                    longFields++;
                } else if (type == int.class || type == Integer.class) {
                    intFields++;
                } else if (type == float.class || type == Float.class) {
                    floatFields++;
                }
            }

            return fields.size() >= 10
                    && fields.size() <= 20
                    && playableFields == 1
                    && booleanFields >= 3
                    && longFields >= 2
                    && intFields >= 2
                    && floatFields >= 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Object currentPlayingMedia(Object state) {
        if (state == null) {
            return null;
        }

        for (Field field : ReflectionAccess.instanceFields(state.getClass())) {
            if (!isPlayingMediaClass(ReflectionAccess.fieldType(field))) {
                continue;
            }
            Object value = ReflectionAccess.fieldValue(field, state);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static boolean isPlayingMediaClass(Class<?> candidate) {
        try {
            /*
             * The prepared playback item is currently fp7. Its name is obfuscated, but
             * its shape is distinctive: Parcelable/Serializable, one stable MediaItem
             * model field, and a small number of List fields, one of which contains
             * parsed lyric containers.
             */
            if (candidate == null) {
                return false;
            }
            int modifiers = candidate.getModifiers();
            if (candidate.isAnnotation()
                    || candidate.isAnonymousClass()
                    || candidate.isArray()
                    || candidate.isEnum()
                    || candidate.isInterface()
                    || candidate.isPrimitive()
                    || Modifier.isAbstract(modifiers)
                    || !Parcelable.class.isAssignableFrom(candidate)
                    || !Serializable.class.isAssignableFrom(candidate)) {
                return false;
            }

            int mediaItemFields = 0;
            int listFields = 0;
            List<Field> fields = ReflectionAccess.instanceFields(candidate);
            for (Field field : fields) {
                Class<?> type = ReflectionAccess.fieldType(field);
                if (isMediaItemType(type)) {
                    mediaItemFields++;
                } else if (type != null && List.class.isAssignableFrom(type)) {
                    listFields++;
                }
            }

            return fields.size() <= 40
                    && mediaItemFields == 1
                    && listFields >= 1
                    && listFields <= 3;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isMediaItemType(Class<?> type) {
        return type != null && "app.symfonik.api.model.MediaItem".equals(type.getName());
    }
}
