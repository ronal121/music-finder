from pathlib import Path
import re

path = Path("app/src/main/java/com/kafshar/musicfinder/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# The repair script is intentionally idempotent: every run produces exactly
# one finishSearch() and one onStart(), so CI can safely rerun it.

def remove_functions(src, name):
    pattern = re.compile(r'(?m)^\s*private\s+fun\s+' + re.escape(name) + r'\s*\([^)]*\)\s*\{')
    while True:
        m = pattern.search(src)
        if not m:
            return src
        start = m.start()
        brace = src.find('{', m.start(), m.end())
        depth = 0
        i = brace
        quote = False
        escaped = False
        line_comment = False
        block_comment = False
        while i < len(src):
            c = src[i]
            n = src[i + 1] if i + 1 < len(src) else ''
            if line_comment:
                if c == '\n':
                    line_comment = False
            elif block_comment:
                if c == '*' and n == '/':
                    block_comment = False
                    i += 1
            elif quote:
                if escaped:
                    escaped = False
                elif c == '\\':
                    escaped = True
                elif c == '"':
                    quote = False
            else:
                if c == '/' and n == '/':
                    line_comment = True
                    i += 1
                elif c == '/' and n == '*':
                    block_comment = True
                    i += 1
                elif c == '"':
                    quote = True
                elif c == '{':
                    depth += 1
                elif c == '}':
                    depth -= 1
                    if depth == 0:
                        return src[:start] + src[i + 1:]
            i += 1
        raise SystemExit(f"unterminated {name} function")

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

# Remove every existing finishSearch(), including corrupted/duplicated copies.
text = remove_functions(text, "finishSearch")
marker = "    private fun saveSearchResults()"
if marker in text:
    text = text.replace(marker, finish + "\n" + marker, 1)
else:
    m = re.search(r'(?m)^\s*private\s+fun\s+', text)
    if not m:
        raise SystemExit("no valid Kotlin method insertion point found")
    text = text[:m.start()] + finish + "\n" + text[m.start():]

count = len(re.findall(r'(?m)^\s*private\s+fun\s+finishSearch\s*\(', text))
if count != 1:
    raise SystemExit(f"finishSearch declaration count is {count}, expected 1")

# Replace every malformed/duplicated onStart block with one valid lifecycle method.
onstart = re.compile(
    r'(?ms)^\s*override\s+fun\s+onStart\s*\(\)\s*\{.*?(?=^\s*override\s+fun\s+onStop\s*\(\))'
)
clean_onstart = '''    override fun onStart() {
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
text, replaced = onstart.subn(clean_onstart, text, count=1)
if replaced != 1:
    raise SystemExit(f"onStart declaration count is {replaced}, expected 1")

# ContextCompat is required by the canonical onStart implementation.
import_line = "import androidx.core.content.ContextCompat"
if import_line not in text:
    marker = "import android.widget.Toast\n"
    if marker not in text:
        raise SystemExit("Toast import marker not found")
    text = text.replace(marker, marker + import_line + "\n", 1)

path.write_text(text, encoding="utf-8")
print("MainActivity.kt repaired")
