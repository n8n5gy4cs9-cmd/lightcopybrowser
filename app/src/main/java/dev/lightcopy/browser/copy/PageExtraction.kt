package dev.lightcopy.browser.copy

enum class ExtractionKind(val title: String) {
    OriginalHtml("Original HTML"),
    RenderedDom("Rendered DOM"),
    VisibleText("Visible text"),
    Url("URL"),
    Title("Title"),
    Links("Links"),
    Images("Image URLs"),
    Markdown("Markdown"),
    SelectedHtml("Selected HTML"),
}

data class ExtractionResult(
    val kind: ExtractionKind,
    val content: String? = null,
    val error: String? = null,
) {
    val isSuccess: Boolean get() = content != null
}

/** Scripts are fixed app code and return only a JSON-encoded string through evaluateJavascript. */
object PageExtractionScripts {
    fun forKind(kind: ExtractionKind): String = when (kind) {
        ExtractionKind.OriginalHtml -> ORIGINAL_HTML
        ExtractionKind.RenderedDom -> RENDERED_DOM
        ExtractionKind.VisibleText -> VISIBLE_TEXT
        ExtractionKind.Url -> URL
        ExtractionKind.Title -> TITLE
        ExtractionKind.Links -> LINKS
        ExtractionKind.Images -> IMAGES
        ExtractionKind.Markdown -> MARKDOWN
        ExtractionKind.SelectedHtml -> SELECTED_HTML
    }

    private const val ORIGINAL_HTML = """
        (function() {
          try {
            if (location.protocol !== 'http:' && location.protocol !== 'https:') {
              return JSON.stringify({ok:false,error:'Original HTML is available only for HTTP(S) pages.'});
            }
            var request = new XMLHttpRequest();
            request.open('GET', location.href, false);
            request.withCredentials = true;
            request.send(null);
            if (request.status < 200 || request.status >= 300) {
              return JSON.stringify({ok:false,error:'Original HTML could not be fetched (HTTP ' + request.status + ').'});
            }
            return JSON.stringify({ok:true,value:request.responseText});
          } catch (error) {
            return JSON.stringify({ok:false,error:'Original HTML is unavailable for this page.'});
          }
        })()
    """

    private const val RENDERED_DOM = """
        (function() {
          try {
            var root = document.documentElement;
            return JSON.stringify(root
              ? {ok:true,value:'<!DOCTYPE html>\n' + root.outerHTML}
              : {ok:false,error:'This page has no rendered document.'});
          } catch (error) {
            return JSON.stringify({ok:false,error:'Rendered DOM is unavailable for this page.'});
          }
        })()
    """

    private const val VISIBLE_TEXT = """
        (function() {
          try {
            var body = document.body;
            if (!body) return JSON.stringify({ok:false,error:'This page has no visible text.'});
            var text = (body.innerText || '').replace(/\u00a0/g, ' ')
              .split('\n').map(function(line) { return line.replace(/[ \t]+$/g, ''); })
              .join('\n').replace(/\n{3,}/g, '\n\n').trim();
            return JSON.stringify({ok:true,value:text});
          } catch (error) {
            return JSON.stringify({ok:false,error:'Visible text is unavailable for this page.'});
          }
        })()
    """

    private const val URL = """(function(){return JSON.stringify({ok:true,value:location.href});})()"""
    private const val TITLE = """(function(){return JSON.stringify({ok:true,value:document.title||''});})()"""
    private const val LINKS = """
        (function(){try{var seen={};var values=[];document.querySelectorAll('a[href]').forEach(function(a){var url=a.href;if(url&&!seen[url]){seen[url]=true;var text=(a.textContent||'').replace(/\s+/g,' ').trim();values.push(url+'\u0001'+text);}});return JSON.stringify({ok:true,value:values.join('\n')});}catch(error){return JSON.stringify({ok:false,error:'Links are unavailable for this page.'});}})()
    """
    private const val IMAGES = """
        (function(){try{var seen={};var values=[];document.querySelectorAll('img').forEach(function(img){var url=img.currentSrc||img.src;if(url&&!seen[url]){seen[url]=true;var alt=(img.alt||'').replace(/\s+/g,' ').trim();values.push(url+'\u0001'+alt);}});return JSON.stringify({ok:true,value:values.join('\n')});}catch(error){return JSON.stringify({ok:false,error:'Image URLs are unavailable for this page.'});}})()
    """
    private const val MARKDOWN = """
        (function(){try{
          function clean(text){return (text||'').replace(/\s+/g,' ').trim();}
          function render(node){
            if(node.nodeType===3)return node.nodeValue;
            if(node.nodeType!==1)return '';
            var tag=node.tagName.toLowerCase();if(tag==='script'||tag==='style'||tag==='noscript')return '';
            var body=Array.prototype.map.call(node.childNodes,render).join('');
            if(/^h[1-6]$/.test(tag))return '\n'+Array(parseInt(tag.charAt(1))+1).join('#')+' '+clean(body)+'\n\n';
            if(tag==='a')return '['+clean(body)+']('+node.href+')';
            if(tag==='img')return '!['+(node.alt||'')+']('+(node.currentSrc||node.src)+')';
            if(tag==='li')return '\n- '+clean(body);
            if(tag==='br')return '\n';
            if(tag==='p'||tag==='div'||tag==='section'||tag==='article'||tag==='ul'||tag==='ol'||tag==='pre'||tag==='blockquote')return '\n'+body+'\n';
            if(tag==='strong'||tag==='b')return '**'+body+'**';if(tag==='em'||tag==='i')return '*'+body+'*';if(tag==='code')return '`'+body+'`';
            return body;
          }
          var value=render(document.body).replace(/[ \t]+\n/g,'\n').replace(/\n{3,}/g,'\n\n').trim();return JSON.stringify({ok:true,value:value});
        }catch(error){return JSON.stringify({ok:false,error:'Markdown is unavailable for this page.'});}})()
    """
    private const val SELECTED_HTML = """
        (function(){try{var selector=window.__lightcopySelectedSelector;if(!selector)return JSON.stringify({ok:false,error:'Long-press an element first.'});var element=document.querySelector(selector);if(!element)return JSON.stringify({ok:false,error:'The selected element is no longer on the page.'});return JSON.stringify({ok:true,value:element.outerHTML||''});}catch(error){return JSON.stringify({ok:false,error:'Selected HTML is unavailable.'});}})()
    """
}

object ExtractionResultDecoder {
    fun decode(kind: ExtractionKind, encoded: String?): ExtractionResult {
        val envelope = decodeJsonString(encoded)
            ?: return ExtractionResult(kind, error = "Page extraction returned no result.")
        val ok = Regex("\\\"ok\\\"\\s*:\\s*true").containsMatchIn(envelope)
        val key = if (ok) "value" else "error"
        val value = readJsonStringProperty(envelope, key)
        return if (ok && value != null) ExtractionResult(kind, content = value)
        else ExtractionResult(kind, error = value ?: "Page extraction failed.")
    }

    private fun readJsonStringProperty(json: String, key: String): String? {
        val marker = "\"$key\""
        var index = json.indexOf(marker)
        if (index < 0) return null
        index = json.indexOf(':', index + marker.length) + 1
        while (index in json.indices && json[index].isWhitespace()) index++
        if (index !in json.indices || json[index] != '"') return null
        return decodeQuoted(json, index)
    }

    private fun decodeJsonString(value: String?): String? {
        if (value == null || value == "null" || value.length < 2 || value.first() != '"') return null
        return decodeQuoted(value, 0)
    }

    private fun decodeQuoted(value: String, start: Int): String? {
        val result = StringBuilder()
        var index = start + 1
        while (index < value.length) {
            when (val char = value[index++]) {
                '"' -> return result.toString()
                '\\' -> {
                    if (index >= value.length) return null
                    when (val escaped = value[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            if (index + 4 > value.length) return null
                            val code = value.substring(index, index + 4).toIntOrNull(16) ?: return null
                            result.append(code.toChar())
                            index += 4
                        }
                        else -> return null
                    }
                }
                else -> result.append(char)
            }
        }
        return null
    }
}
