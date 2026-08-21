from pathlib import Path

path = Path("app/src/main/java/com/kafshar/musicfinder/MusicService.kt")
s = path.read_text(encoding="utf-8")

needle = '''                .setSessionActivity(
                    createOpenAppPendingIntent()
                )
                .build()'''
replacement = '''                .setSessionActivity(
                    createOpenAppPendingIntent()
                )
                .setCustomLayout(
                    MediaSessionControls.layout()
                )
                .build()'''

if ".setCustomLayout(\n                    MediaSessionControls.layout()\n                )" in s:
    print("MediaSession controls already applied")
    raise SystemExit(0)

if needle not in s:
    raise SystemExit("MediaSession builder anchor not found")

s = s.replace(needle, replacement, 1)
path.write_text(s, encoding="utf-8")
print("Applied Media3 notification controls")
