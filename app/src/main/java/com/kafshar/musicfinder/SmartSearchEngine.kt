package com.kafshar.musicfinder

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLEncoder

/**
 * Independent web-discovery engine.
 *
 * It does not replace ParallelSearchEngine. It sends the user's original query
 * to Google-like web search, extracts the top real result URLs, and hands those
 * pages back to the existing music-page extractor.
 */
class SmartSearchEngine(
    private val activity: Activity,
    private val onResults: (generation: Int, urls: List<String>) -> Unit
) {

    private var webView: WebView? = null
    private var destroyed = false

    init {
        activity.runOnUiThread {
            if (destroyed) return@runOnUiThread

            val root = activity.findViewById<ViewGroup>(android.R.id.content)
                ?: return@runOnUiThread

            val view = WebView(activity)
            view.layoutParams = ViewGroup.LayoutParams(2, 2)
            view.alpha = 0f
            view.setBackgroundColor(Color.TRANSPARENT)
            view.visibility = View.VISIBLE

            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.mediaPlaybackRequiresUserGesture = false
            view.settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"

            view.addJavascriptInterface(
                Bridge(),
                "SmartSearch"
            )

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    if (destroyed) return
                    if (!url.contains("google.com/search", true)) return

                    view.evaluateJavascript(EXTRACT_SCRIPT, null)
                }
            }

            root.addView(view)
            webView = view
        }
    }

    fun search(query: String, generation: Int) {
        if (query.isBlank()) return

        activity.runOnUiThread {
            if (destroyed) return@runOnUiThread

            val encoded = try {
                URLEncoder.encode(query.trim(), "UTF-8")
            } catch (_: Exception) {
                return@runOnUiThread
            }

            try {
                webView?.stopLoading()
                webView?.loadUrl(
                    "https://www.google.com/search?q=$encoded&num=20&hl=fa&gbv=1"
                )
            } catch (_: Exception) {
            }
        }
    }

    fun destroy() {
        destroyed = true

        activity.runOnUiThread {
            try {
                webView?.stopLoading()
                webView?.removeJavascriptInterface("SmartSearch")

                val parent = webView?.parent as? ViewGroup
                parent?.removeView(webView)

                webView?.destroy()
            } catch (_: Exception) {
            } finally {
                webView = null
            }
        }
    }

    private inner class Bridge {

        @JavascriptInterface
        fun results(raw: String?) {
            if (destroyed) return

            val generation = pendingGeneration
            if (generation < 0) return

            val urls = raw.orEmpty()
                .split("###")
                .map { it.trim() }
                .filter { it.startsWith("http", true) }
                .map { unwrapGoogleUrl(it) }
                .filter { ServerConfig.isDiscoverablePageUrl(it) }
                .distinctBy { it.substringBefore("#").trimEnd("/").lowercase() }
                .take(30)

            if (urls.isNotEmpty()) {
                activity.runOnUiThread {
                    if (!destroyed) {
                        onResults(generation, urls)
                    }
                }
            }
        }
    }

    private var pendingGeneration: Int = -1

    private fun unwrapGoogleUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val query = uri.rawQuery.orEmpty()
            val params = query.split("&")
                .mapNotNull {
                    val pair = it.split("=", limit = 2)
                    if (pair.size == 2) pair[0] to pair[1] else null
                }
                .toMap()

            val target = params["url"] ?: params["q"]
            if (target != null && target.startsWith("http", true)) {
                java.net.URLDecoder.decode(target, "UTF-8")
            } else {
                url
            }
        } catch (_: Exception) {
            url
        }
    }

    companion object {
        private const val EXTRACT_SCRIPT = """
            (function(){
              try{
                var links=document.querySelectorAll('a');
                var blocked=[
                  'google.com',
                  'googleusercontent.com',
                  'gstatic.com',
                  'accounts.google.com',
                  'support.google.com',
                  'policies.google.com',
                  'translate.google.com',
                  'webcache.googleusercontent.com'
                ];

                function hostOf(u){
                  try{
                    return new URL(u,location.href).hostname
                      .toLowerCase()
                      .replace(/^www\./,'');
                  }catch(e){ return ''; }
                }

                function blockedHost(h){
                  for(var i=0;i<blocked.length;i++){
                    if(h===blocked[i] || h.endsWith('.'+blocked[i])) return true;
                  }
                  return false;
                }

                function real(h){
                  try{
                    var x=new URL(h,location.href);
                    if(x.hostname.toLowerCase().indexOf('google.')>=0){
                      var q=x.searchParams.get('q') || x.searchParams.get('url');
                      if(q && /^https?:/i.test(q)) return decodeURIComponent(q);
                    }
                    return x.href;
                  }catch(e){ return h; }
                }

                var out=[];
                var seen={};

                for(var i=0;i<links.length && out.length<30;i++){
                  var raw=links[i].href || '';
                  if(!/^https?:/i.test(raw)) continue;

                  var u=real(raw).split('#')[0];
                  var h=hostOf(u);
                  if(!h || blockedHost(h)) continue;
                  if(seen[u]) continue;

                  var text=(
                    links[i].innerText ||
                    links[i].textContent ||
                    links[i].getAttribute('aria-label') ||
                    links[i].getAttribute('title') ||
                    ''
                  ).replace(/[\r\n\t]+/g,' ')
                   .replace(/\s+/g,' ')
                   .trim();

                  if(!text && !u) continue;

                  seen[u]=true;
                  out.push(u);
                }

                if(window.SmartSearch){
                  window.SmartSearch.results(out.join('###'));
                }
              }catch(e){}
            })();
        """
    }
}
