package dev.lightcopy.browser.inspect

data class ElementInspection(
    val tag: String,
    val id: String,
    val classes: String,
    val selector: String,
    val html: String,
)

data class InspectionResult(val inspection: ElementInspection? = null, val error: String? = null)

/** Fixed application scripts; no page-controlled code is interpolated. */
object ElementInspectionScripts {
    fun selectAt(x: Float, y: Float): String = SCRIPT
        .replace("__X__", x.toString())
        .replace("__Y__", y.toString())

    val clear: String = """
        (function(){
          var overlay=document.getElementById('__lightcopy_selection_overlay');
          if(overlay) overlay.remove();
          delete window.__lightcopySelectedSelector;
        })()
    """.trimIndent()

    private const val SCRIPT = """
        (function(){
          try {
            var element=document.elementFromPoint(__X__,__Y__);
            if(!element) return JSON.stringify({ok:false,error:'No element was found at that point.'});
            if(element.id==='__lightcopy_selection_overlay') return JSON.stringify({ok:false,error:'No page element was found at that point.'});
            function cssEscape(value){
              if(window.CSS&&CSS.escape) return CSS.escape(value);
              return value.replace(/[^a-zA-Z0-9_-]/g,function(c){return '\\'+c.charCodeAt(0).toString(16)+' ';});
            }
            function unique(candidate,node){
              try { var matches=document.querySelectorAll(candidate); return matches.length===1&&matches[0]===node; }
              catch(error){ return false; }
            }
            function selectorFor(node){
              if(node.id){ var byId='#'+cssEscape(node.id); if(unique(byId,node)) return byId; }
              var parts=[];
              while(node&&node.nodeType===1){
                var part=node.tagName.toLowerCase();
                if(node.id){ part+='#'+cssEscape(node.id); parts.unshift(part); break; }
                var siblings=node.parentElement ? Array.prototype.filter.call(node.parentElement.children,function(child){return child.tagName===node.tagName;}) : [];
                if(siblings.length>1) part+=':nth-of-type('+(siblings.indexOf(node)+1)+')';
                parts.unshift(part);
                var candidate=parts.join(' > ');
                if(unique(candidate,element)) return candidate;
                node=node.parentElement;
              }
              return parts.join(' > ');
            }
            var selector=selectorFor(element);
            if(!selector||!unique(selector,element)) return JSON.stringify({ok:false,error:'A stable selector could not be generated.'});
            window.__lightcopySelectedSelector=selector;
            var overlay=document.getElementById('__lightcopy_selection_overlay');
            if(!overlay){
              overlay=document.createElement('div'); overlay.id='__lightcopy_selection_overlay';
              overlay.setAttribute('aria-hidden','true');
              overlay.style.cssText='position:absolute;z-index:2147483647;pointer-events:none;border:2px solid #20d9f5;background:rgba(32,217,245,.16);box-sizing:border-box;';
              document.documentElement.appendChild(overlay);
            }
            var rect=element.getBoundingClientRect();
            overlay.style.left=(rect.left+window.scrollX)+'px'; overlay.style.top=(rect.top+window.scrollY)+'px';
            overlay.style.width=rect.width+'px'; overlay.style.height=rect.height+'px';
            return JSON.stringify({ok:true,tag:element.tagName.toLowerCase(),id:element.id||'',classes:element.className&&typeof element.className==='string'?element.className:'',selector:selector,html:element.outerHTML||''});
          } catch(error){ return JSON.stringify({ok:false,error:'This element could not be inspected.'}); }
        })()
    """
}

object InspectionResultDecoder {
    fun decode(encoded: String?): InspectionResult {
        val json = JsonStrings.decodeCallback(encoded)
            ?: return InspectionResult(error = "Element inspection returned no result.")
        val error = JsonStrings.property(json, "error")
        if (!Regex("\\\"ok\\\"\\s*:\\s*true").containsMatchIn(json))
            return InspectionResult(error = error ?: "Element inspection failed.")
        val selector = JsonStrings.property(json, "selector")
            ?: return InspectionResult(error = "Element inspection returned no selector.")
        return InspectionResult(ElementInspection(
            tag = JsonStrings.property(json, "tag").orEmpty(),
            id = JsonStrings.property(json, "id").orEmpty(),
            classes = JsonStrings.property(json, "classes").orEmpty(),
            selector = selector,
            html = JsonStrings.property(json, "html").orEmpty(),
        ))
    }
}

internal object JsonStrings {
    fun decodeCallback(value: String?): String? = value?.takeUnless { it == "null" }?.let { decodeQuoted(it, 0) }
    fun property(json: String, key: String): String? {
        val marker = "\"$key\""
        var index = json.indexOf(marker)
        if (index < 0) return null
        index = json.indexOf(':', index + marker.length) + 1
        while (index in json.indices && json[index].isWhitespace()) index++
        return if (index in json.indices && json[index] == '"') decodeQuoted(json, index) else null
    }
    private fun decodeQuoted(value: String, start: Int): String? {
        if (start !in value.indices || value[start] != '"') return null
        val result = StringBuilder(); var index = start + 1
        while (index < value.length) when (val char = value[index++]) {
            '"' -> return result.toString()
            '\\' -> {
                if (index >= value.length) return null
                when (val escaped = value[index++]) {
                    '"', '\\', '/' -> result.append(escaped)
                    'b' -> result.append('\b'); 'f' -> result.append('\u000C')
                    'n' -> result.append('\n'); 'r' -> result.append('\r'); 't' -> result.append('\t')
                    'u' -> { if (index + 4 > value.length) return null; result.append(value.substring(index,index+4).toIntOrNull(16)?.toChar() ?: return null); index += 4 }
                    else -> return null
                }
            }
            else -> result.append(char)
        }
        return null
    }
}
