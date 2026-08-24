from pathlib import Path
import re

path = Path("app/src/main/java/com/kafshar/musicfinder/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# Replace the whole damaged regions instead of trying to delete individual
# methods. Earlier repairs left orphaned braces/duplicate method bodies behind.
finish = '''    private fun finishSearch() {
        if (destroyed) return

        cancelSearchCallbacks()

        status.text =
            if (songs.isEmpty()) {
                "آهنگ قابل پخش پیدا نشد"
            } else {
                "${songs.size} نتیجه پیدا شد"
            }

        if (songs.isNotEmpty() && currentIndex == -1) {
            currentIndex = 0
        }

        saveSearchResults()
    }

'''

# Canonical finishSearch -> saveSearchResults boundary.
finish_pattern = re.compile(
    r'(?ms)^\s*private\s+fun\s+finishSearch\s*\(\)\s*\{.*?(?=^\s*private\s+fun\s+saveSearchResults\s*\()'
)
if finish_pattern.search(text):
    text = finish_pattern.sub(finish + '    private fun saveSearchResults()', text, count=1)
else:
    raise SystemExit("Could not locate finishSearch/saveSearchResults region")

onstart = '''    override fun onStart() {
        super.onStart()

        if (receiverRegistered) return

        val filter = IntentFilter(MusicService.UPDATE)

        try {
            ContextCompat.registerReceiver(
                this,
                playerReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (_: Exception) {
            receiverRegistered = false
        }
    }

'''

# Canonical onStart -> onStop boundary. This removes every orphaned duplicate
# body that previous repair passes could leave behind.
onstart_pattern = re.compile(
    r'(?ms)^\s*override\s+fun\s+onStart\s*\(\)\s*\{.*?(?=^\s*override\s+fun\s+onStop\s*\(\))'
)
if onstart_pattern.search(text):
    text = onstart_pattern.sub(onstart, text, count=1)
else:
    raise SystemExit("Could not locate onStart/onStop region")

import_line = "import androidx.core.content.ContextCompat"
if import_line not in text:
    marker = "import android.widget.Toast\n"
    if marker not in text:
        raise SystemExit("Toast import marker not found")
    text = text.replace(marker, marker + import_line + "\n", 1)

# Structural sanity checks before writing.
if len(re.findall(r'(?m)^\s*private\s+fun\s+finishSearch\s*\(', text)) != 1:
    raise SystemExit("Expected exactly one finishSearch")
if len(re.findall(r'(?m)^\s*override\s+fun\s+onStart\s*\(', text)) != 1:
    raise SystemExit("Expected exactly one onStart")
if len(re.findall(r'(?m)^\s*override\s+fun\s+onStop\s*\(', text)) != 1:
    raise SystemExit("Expected exactly one onStop")

path.write_text(text, encoding="utf-8")
print("MainActivity.kt repaired")
