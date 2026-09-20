package dev.lightcopy.browser.reader

import dev.lightcopy.browser.copy.ExtractionResultDecoder
import dev.lightcopy.browser.copy.ExtractionKind

data class ReaderContent(val title: String, val byline: String, val text: String)
data class ReaderResult(val content: ReaderContent? = null, val error: String? = null)

object ReaderScripts {
    /** Fixed app-owned script. It exposes no Java object to page code. */
    const val EXTRACT = """
        (function(){try{
          function clean(value){return (value||'').replace(/\u00a0/g,' ').replace(/[ \t]+/g,' ').replace(/\n{3,}/g,'\n\n').trim();}
          var candidates=Array.prototype.slice.call(document.querySelectorAll('article,main,[role="main"],.article,.post,.content'));
          candidates.push(document.body);
          var best=null,bestLength=0;
          candidates.forEach(function(node){if(!node)return;var clone=node.cloneNode(true);clone.querySelectorAll('script,style,noscript,nav,aside,footer,form,button').forEach(function(n){n.remove();});var text=clean(clone.innerText||clone.textContent);if(text.length>bestLength){bestLength=text.length;best=text;}});
          if(!best||bestLength<80)return JSON.stringify({ok:false,error:'Reader mode could not identify enough readable content. Try Visible text instead.'});
          var author=document.querySelector('[rel="author"],[itemprop="author"],meta[name="author"]');
          var byline=author?(author.content||author.innerText||author.textContent||''):'';
          return JSON.stringify({ok:true,value:JSON.stringify({title:clean(document.title),byline:clean(byline),text:best})});
        }catch(error){return JSON.stringify({ok:false,error:'Reader mode is unavailable for this page. Try Visible text instead.'});}})()
    """

    const val ENABLE_DARK = """(function(){try{var id='lightcopy-page-dark';var style=document.getElementById(id);if(!style){style=document.createElement('style');style.id=id;style.textContent='html{background:#111!important;filter:invert(.9) hue-rotate(180deg)!important}img,video,picture,canvas,svg{filter:invert(1) hue-rotate(180deg)!important}';(document.head||document.documentElement).appendChild(style);}return true;}catch(e){return false;}})()"""
    const val DISABLE_DARK = """(function(){try{var style=document.getElementById('lightcopy-page-dark');if(style)style.remove();return true;}catch(e){return false;}})()"""
}

object ReaderResultDecoder {
    fun decode(encoded: String?): ReaderResult {
        val envelope = ExtractionResultDecoder.decode(ExtractionKind.VisibleText, encoded)
        val json = envelope.content ?: return ReaderResult(error = envelope.error)
        fun field(name: String): String {
            val match = Regex("\\\"$name\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(json) ?: return ""
            val raw = match.groupValues[1]
            val result = StringBuilder()
            var index = 0
            while (index < raw.length) {
                val char = raw[index++]
                if (char != '\\' || index >= raw.length) result.append(char) else when (val escaped = raw[index++]) {
                    '"', '\\', '/' -> result.append(escaped)
                    'b' -> result.append('\b'); 'f' -> result.append('\u000C'); 'n' -> result.append('\n')
                    'r' -> result.append('\r'); 't' -> result.append('\t')
                    'u' -> if (index + 4 <= raw.length) { result.append(raw.substring(index, index + 4).toIntOrNull(16)?.toChar() ?: return ""); index += 4 } else return ""
                    else -> return ""
                }
            }
            return result.toString()
        }
        val text = field("text")
        return if (text.isBlank()) ReaderResult(error = "Reader mode could not decode this page.")
        else ReaderResult(ReaderContent(field("title"), field("byline"), text))
    }
}
