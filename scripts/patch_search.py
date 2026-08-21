from pathlib import Path
import re

p = Path('app/src/main/java/com/kafshar/musicfinder/MainActivity.kt')
s = p.read_text(encoding='utf-8')

def replace_method(source: str, signature: str, replacement: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f'method not found: {signature}')
    brace = source.find('{', start)
    if brace < 0:
        raise SystemExit(f'opening brace not found: {signature}')
    depth = 0
    in_string = False
    escaped = False
    for i in range(brace, len(source)):
        c = source[i]
        if in_string:
            if escaped:
                escaped = False
            elif c == '\\':
                escaped = True
            elif c == '"':
                in_string = False
            continue
        if c == '"':
            in_string = True
        elif c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return source[:start] + replacement + source[i + 1:]
    raise SystemExit(f'unbalanced braces: {signature}')

extract = '''    private fun extractGoogleResults() {

        if (destroyed) return

        val generation = searchGeneration
        if (generation <= 0) return

        val hosts = ServerConfig.MUSIC_SITES.joinToString(
            prefix = "[",
            postfix = "]"
        ) { "\\\"$it\\\"" }

        val script = """
            (function() {
                try {
                    var links = document.querySelectorAll("a");
                    var found = [];
                    var hosts = $hosts;

                    for (var i = 0; i < links.length; i++) {
                        var href = links[i].href || "";
                        var text = links[i].innerText || "";
                        var lower = href.toLowerCase();
                        var allowed = false;

                        for (var h = 0; h < hosts.length; h++) {
                            if (lower.indexOf(hosts[h]) >= 0) {
                                allowed = true;
                                break;
                            }
                        }

                        if (allowed && lower.indexOf("google.com") < 0 && found.indexOf(href) < 0) {
                            found.push(href + "|||" + text.replace(/[\\r\\n]+/g, " "));
                        }
                    }

                    MusicFinder.results(found.join("###"));
                } catch (e) {
                    MusicFinder.results("");
                }
            })();
        """.trimIndent()

        try {
            web.evaluateJavascript(script, null)
        } catch (_: Exception) {
            if (!destroyed) status.text = "خطا در استخراج نتایج"
        }
    }'''

s = replace_method(s, '    private fun extractGoogleResults()', extract)

start = s.find('    private fun searchMusic()')
if start < 0:
    raise SystemExit('searchMusic not found')
end = s.find('    private fun ', start + 10)
if end < 0:
    end = len(s)
method = s[start:end]

new_query_block = '''        val searchQuery = SearchEngine.buildGoogleQuery(text)
        val encoded = try {
            java.net.URLEncoder.encode(searchQuery, "UTF-8")
        } catch (_: Exception) {
            return
        }

        val url = "https://www.google.com/search?q=$encoded&num=50"
'''

# Remove any existing query/url block, including an earlier partially patched version.
pattern = re.compile(
    r'(?s)\n\s*val searchQuery\s*=.*?(?=\n\s*try\s*\{\n\s*web\.stopLoading\(\))'
)
if not pattern.search(method):
    raise SystemExit('search query block not found in searchMusic')
method = pattern.sub('\n\n' + new_query_block + '\n', method, count=1)

# Idempotent cleanup for duplicate declarations left by an earlier patch attempt.
method = re.sub(
    r'(?m)^\s*val searchQuery = SearchEngine\.buildGoogleQuery\(text\)\n\s*val searchQuery = SearchEngine\.buildGoogleQuery\(text\)\n',
    '        val searchQuery = SearchEngine.buildGoogleQuery(text)\n',
    method
)
method = re.sub(
    r'(?m)^(\s*val url = "https://www\.google\.com/search\?q=\$encoded&num=50")\n\s*"https://www\.google\.com/search\?q=\$encoded&num=50"\n',
    r'\1\n',
    method
)

s = s[:start] + method + s[end:]
p.write_text(s, encoding='utf-8')
print('patched MainActivity search integration')
