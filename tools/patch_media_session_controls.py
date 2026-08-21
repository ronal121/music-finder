from pathlib import Path

path = Path("app/src/main/java/com/kafshar/musicfinder/MusicService.kt")
s = path.read_text(encoding="utf-8")

if "@OptIn(androidx.media3.common.util.UnstableApi::class)\nclass MusicService" not in s:
    class_anchor = "class MusicService : MediaSessionService() {"
    if class_anchor not in s:
        raise SystemExit("MusicService class anchor not found")
    s = s.replace(
        class_anchor,
        "@OptIn(androidx.media3.common.util.UnstableApi::class)\n" + class_anchor,
        1,
    )

if "MediaSessionControls.layout()" not in s or "MediaSessionControls.callback()" not in s:
    needle = '''                .setSessionActivity(
                    createOpenAppPendingIntent()
                )
                .build()'''
    replacement = '''                .setSessionActivity(
                    createOpenAppPendingIntent()
                )
                .setCallback(
                    MediaSessionControls.callback()
                )
                .setCustomLayout(
                    MediaSessionControls.layout()
                )
                .build()'''
    if needle not in s:
        raise SystemExit("MediaSession builder anchor not found")
    s = s.replace(needle, replacement, 1)

path.write_text(s, encoding="utf-8")
print("Media3 notification controls are ready")
