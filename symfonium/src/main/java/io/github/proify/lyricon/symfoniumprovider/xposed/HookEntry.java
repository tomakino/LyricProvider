package io.github.proify.lyricon.symfoniumprovider.xposed;

import android.app.Application;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dalvik.system.DexFile;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.proify.lyricon.lyric.model.RichLyricLine;
import io.github.proify.lyricon.lyric.model.Song;
import io.github.proify.lyricon.provider.LyriconFactory;
import io.github.proify.lyricon.provider.LyriconProvider;
import io.github.proify.lyricon.provider.ProviderConstants;
import io.github.proify.lyricon.provider.RemotePlayer;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "SymfoniumLyricProvider";
    private static final String TARGET_PACKAGE = "app.symfonik.music.player";
    private static final String PROVIDER_PACKAGE = "io.github.proify.lyricon.symfoniumprovider";

    private static volatile LyriconProvider provider;
    private static String trackKey;
    private static String currentId;
    private static String currentTitle;
    private static String currentArtist;
    private static long currentDuration;
    private static List<RichLyricLine> currentLyrics;
    private static String lastLyricsSignature;

    private static final class LyricContainerData {
        final List<?> lines;
        final Object signature;

        LyricContainerData(List<?> lines, Object signature) {
            this.lines = lines;
            this.signature = signature;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName) || !TARGET_PACKAGE.equals(lpparam.processName)) {
            return;
        }

        log("loading hooks for " + lpparam.packageName);
        hookApplication(
                lpparam.classLoader,
                lpparam.appInfo != null ? lpparam.appInfo.className : null
        );
        hookMediaSession();
        hookSymfoniumLyrics(lpparam.classLoader);
    }

    private static void hookApplication(ClassLoader classLoader, String applicationClassName) {
        String className = normalizeApplicationClassName(applicationClassName);
        try {
            Class<?> applicationClass = !isBlank(className)
                    ? XposedHelpers.findClass(className, classLoader)
                    : Application.class;
            XposedBridge.hookAllMethods(
                    applicationClass,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            initProvider((Application) param.thisObject);
                        }
                    }
            );
        } catch (Throwable t) {
            log("failed to hook manifest Application.onCreate", t);
            hookFrameworkApplication();
        }
    }

    private static String normalizeApplicationClassName(String className) {
        if (isBlank(className)) {
            return className;
        }
        if (className.startsWith(".")) {
            return TARGET_PACKAGE + className;
        }
        if (className.indexOf('.') >= 0) {
            return className;
        }
        return TARGET_PACKAGE + "." + className;
    }

    private static void hookFrameworkApplication() {
        try {
            XposedBridge.hookAllMethods(
                    Application.class,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof Application) {
                                initProvider((Application) param.thisObject);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            log("failed to hook framework Application.onCreate", t);
        }
    }

    private static void hookMediaSession() {
        try {
            XposedBridge.hookAllMethods(
                    MediaSession.class,
                    "setMetadata",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object arg = param.args != null && param.args.length > 0 ? param.args[0] : null;
                            if (arg instanceof MediaMetadata) {
                                onMetadata((MediaMetadata) arg);
                            }
                        }
                    }
            );

            XposedBridge.hookAllMethods(
                    MediaSession.class,
                    "setPlaybackState",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object arg = param.args != null && param.args.length > 0 ? param.args[0] : null;
                            if (arg instanceof PlaybackState) {
                                onPlaybackState((PlaybackState) arg);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            log("failed to hook MediaSession", t);
        }
    }

    private static void hookSymfoniumLyrics(ClassLoader classLoader) {
        /*
         * Symfonium can parse lyrics for queue items before they start playing. Raw
         * lyric constructors are therefore not a safe publish point: the same model
         * shape appears for both the current item and preloaded items.
         *
         * Instead this scan hooks the current renderer state object. In the current
         * Symfonium APK that object is vd9 and contains one current playable-media
         * field. That playable-media object is fp7 and carries both MediaItem and a
         * List of parsed Lyrics. Both class names are obfuscated, so the hook finds
         * the objects by runtime shape:
         *
         * - renderer state: playback booleans, position/duration longs, volume ints,
         *   speed floats, and exactly one playable-media field
         * - playable media: Parcelable/Serializable object with one MediaItem field
         *   and one to three List fields, one of which contains Lyrics
         *
         * This follows the object Symfonium marks as currently playing, so preloaded
         * lyrics for the next song can exist without being sent under the current
         * MediaSession metadata.
         */
        int hookedStates = 0;
        for (String className : enumerateClassNames(classLoader)) {
            Class<?> candidate = loadClass(classLoader, className);
            if (candidate == null || !MediaStateHeuristics.isCurrentMediaStateClass(candidate)) {
                continue;
            }

            try {
                XposedBridge.hookAllConstructors(candidate, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        onCurrentMediaState(param.thisObject, param.thisObject.getClass().getName() + ".constructor");
                    }
                });
                hookedStates++;
            } catch (Throwable t) {
                log("failed to hook current media state candidate " + className, t);
            }
        }
        log("hooked " + hookedStates + " current media state candidate(s)");
    }

    private static Set<String> enumerateClassNames(ClassLoader classLoader) {
        LinkedHashSet<String> classNames = new LinkedHashSet<>();
        addClassLoaderDexEntries(classLoader, classNames);
        return classNames;
    }

    private static void addClassLoaderDexEntries(ClassLoader classLoader, Set<String> classNames) {
        try {
            Object pathList = XposedHelpers.getObjectField(classLoader, "pathList");
            Object dexElements = XposedHelpers.getObjectField(pathList, "dexElements");
            if (!(dexElements instanceof Object[])) {
                return;
            }

            for (Object element : (Object[]) dexElements) {
                Object dexFile = XposedHelpers.getObjectField(element, "dexFile");
                if (dexFile instanceof DexFile) {
                    addDexEntries((DexFile) dexFile, classNames);
                }
            }
        } catch (Throwable t) {
            log("failed to enumerate classloader dex entries", t);
        }
    }

    private static void addDexEntries(DexFile dexFile, Set<String> classNames) {
        Enumeration<String> entries = dexFile.entries();
        while (entries.hasMoreElements()) {
            String className = entries.nextElement();
            if (shouldScanClassName(className)) {
                classNames.add(className);
            }
        }
    }

    private static boolean shouldScanClassName(String className) {
        // Keep the expensive structural scan focused on Symfonium/app classes.
        return !className.contains("$")
                && !className.startsWith("android.")
                && !className.startsWith("androidx.")
                && !className.startsWith("com.google.")
                && !className.startsWith("com.squareup.")
                && !className.startsWith("dalvik.")
                && !className.startsWith("io.github.proify.")
                && !className.startsWith("java.")
                && !className.startsWith("javax.")
                && !className.startsWith("kotlin.")
                && !className.startsWith("kotlinx.")
                && !className.startsWith("okhttp3.")
                && !className.startsWith("okio.")
                && !className.startsWith("org.");
    }

    private static Class<?> loadClass(ClassLoader classLoader, String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static synchronized void initProvider(Application app) {
        if (provider != null) {
            return;
        }

        String processName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? Application.getProcessName()
                : TARGET_PACKAGE;
        provider = LyriconFactory.INSTANCE.createProvider(
                app,
                PROVIDER_PACKAGE,
                TARGET_PACKAGE,
                null,
                null,
                processName,
                null,
                ProviderConstants.SYSTEM_UI_PACKAGE_NAME
        );
        provider.getPlayer().setDisplayTranslation(true);
        provider.register();
        sendCurrentSong();
        log("provider initialized");
    }

    private static synchronized void onMetadata(MediaMetadata metadata) {
        try {
            String title = firstNonBlank(
                    metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            );
            String artist = firstNonBlank(
                    metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                    metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR),
                    metadata.getString(MediaMetadata.METADATA_KEY_COMPOSER)
            );
            String id = firstNonBlank(
                    metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                    metadata.getDescription() != null ? metadata.getDescription().getMediaId() : null
            );
            long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            String nextTrackKey = firstNonBlank(id, title + "\n" + artist + "\n" + duration);

            if (!Objects.equals(trackKey, nextTrackKey)) {
                currentLyrics = null;
                lastLyricsSignature = null;
            }

            trackKey = nextTrackKey;
            currentId = id;
            currentTitle = title;
            currentArtist = artist;
            currentDuration = duration;
            sendCurrentSong();
        } catch (Throwable t) {
            log("failed to process MediaMetadata", t);
        }
    }

    private static void onPlaybackState(PlaybackState state) {
        try {
            RemotePlayer player = player();
            if (player != null) {
                player.setPlaybackState(state);
            }
        } catch (Throwable t) {
            log("failed to process PlaybackState", t);
        }
    }

    private static synchronized void onCurrentMediaState(Object state, String source) {
        if (state == null) {
            return;
        }

        try {
            Object currentMedia = MediaStateHeuristics.currentPlayingMedia(state);
            if (currentMedia == null) {
                return;
            }

            LyricContainerData container = findBestLyricContainer(currentMedia);
            if (container != null) {
                applyLyrics(container, source);
            }
        } catch (Throwable t) {
            log("failed to process current media state", t);
        }
    }

    private static LyricContainerData findBestLyricContainer(Object currentMedia) {
        LyricContainerData best = null;
        int bestScore = 0;
        for (Field field : ReflectionAccess.instanceFields(currentMedia.getClass())) {
            Object value = ReflectionAccess.fieldValue(field, currentMedia);
            if (!(value instanceof List)) {
                continue;
            }

            for (Object possibleLyrics : (List<?>) value) {
                LyricContainerData container = readLyricContainer(possibleLyrics);
                if (container == null) {
                    continue;
                }

                int score = LyricLines.selectionScore(container.lines, currentDuration);
                if (score > bestScore) {
                    best = container;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static boolean applyLyrics(LyricContainerData container, String source) {
        String signature = LyricLines.signature(container.signature, container.lines, currentDuration);
        if (Objects.equals(signature, lastLyricsSignature)) {
            return false;
        }

        List<RichLyricLine> converted = LyricLines.convert(container.lines, currentDuration);
        if (converted.isEmpty()) {
            return false;
        }

        currentLyrics = converted;
        lastLyricsSignature = signature;
        sendCurrentSong();
        log("sent " + converted.size() + " lyric lines from current media state " + source);
        return true;
    }

    private static LyricContainerData readLyricContainer(Object lyricsObject) {
        /*
         * Once a structural candidate is constructed, validate the actual instance.
         * The container is accepted only if one reflected List field contains line-
         * shaped objects. Any nonblank String field is treated as a reusable lyric
         * signature; if it disappears or changes type after obfuscation, a signature
         * is rebuilt from line/cue contents later.
         */
        if (lyricsObject == null) {
            return null;
        }

        Object signature = null;
        List<Field> fields = ReflectionAccess.instanceFields(lyricsObject.getClass());
        for (Field field : fields) {
            Object value = ReflectionAccess.fieldValue(field, lyricsObject);
            if (signature == null && value instanceof String && !isBlank((String) value)) {
                signature = value;
            }
        }

        for (Field field : fields) {
            Object value = ReflectionAccess.fieldValue(field, lyricsObject);
            if (value instanceof List && LyricLines.isLineList((List<?>) value, currentDuration)) {
                return new LyricContainerData((List<?>) value, signature);
            }
        }
        return null;
    }

    private static synchronized void sendCurrentSong() {
        RemotePlayer player = player();
        if (player == null) {
            return;
        }
        if (isBlank(currentId) && isBlank(currentTitle) && isBlank(currentArtist)
                && (currentLyrics == null || currentLyrics.isEmpty())) {
            return;
        }

        Song song = new Song(currentId, currentTitle, currentArtist, currentDuration, null, currentLyrics);
        player.setSong(song);
    }

    private static RemotePlayer player() {
        return provider != null ? provider.getPlayer() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static void log(String message, Throwable throwable) {
        XposedBridge.log(TAG + ": " + message + "\n" + Log.getStackTraceString(throwable));
    }
}
