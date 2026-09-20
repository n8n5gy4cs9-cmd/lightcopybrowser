package dev.lightcopy.browser.pageinfo

enum class SslState(val label: String) { Secure("Secure"), Insecure("Not secure"), Unavailable("Not applicable") }
enum class StorageKind(val label: String) { Cookies("Cookies"), LocalStorage("localStorage"), SessionStorage("sessionStorage") }
data class NamedValue(val name: String, val value: String)
data class PageInfo(
    val title: String, val url: String, val origin: String, val sslState: SslState, val loadTimeMillis: Long?,
    val viewport: String, val meta: List<NamedValue>, val openGraph: List<NamedValue>, val faviconUrl: String,
    val cookies: List<NamedValue>, val localStorage: List<NamedValue>, val sessionStorage: List<NamedValue>,
)
data class PageInfoResult(val info: PageInfo? = null, val error: String? = null)
data class ClearStorageResult(val kind: StorageKind, val success: Boolean, val message: String)

/** Fixed application code. Page values only leave WebView as a JSON-encoded callback string. */
object PageInfoScripts {
    val inspect = """
        (function(){try{
          if(location.protocol!=='http:'&&location.protocol!=='https:')return JSON.stringify({ok:false,error:'Page info is available only for HTTP(S) pages.'});
          function pack(items){return items.map(function(item){return item.name+'\u001f'+item.value;}).join('\u001e');}
          function storage(store){var values=[];for(var i=0;i<store.length;i++){var key=store.key(i);values.push({name:key,value:store.getItem(key)||''});}values.sort(function(a,b){return a.name.localeCompare(b.name);});return pack(values);}
          var meta=[],og=[];document.querySelectorAll('meta').forEach(function(node){var name=node.getAttribute('name')||node.getAttribute('http-equiv')||node.getAttribute('property');var value=node.getAttribute('content');if(!name||value===null)return;var item={name:name,value:value};if(name.toLowerCase().indexOf('og:')===0)og.push(item);else meta.push(item);});
          var icon=document.querySelector('link[rel~="icon"]');
          return JSON.stringify({ok:true,title:document.title||'',url:location.href,origin:location.origin,viewport:window.innerWidth+' × '+window.innerHeight+' CSS px ('+(window.devicePixelRatio||1)+'×)',meta:pack(meta),og:pack(og),favicon:icon?icon.href:(location.origin+'/favicon.ico'),local:storage(localStorage),session:storage(sessionStorage)});
        }catch(error){return JSON.stringify({ok:false,error:'Page information is unavailable for this page.'});}})()
    """.trimIndent()

    fun clear(kind: StorageKind, expectedOrigin: String): String {
        val safeOrigin = expectedOrigin.replace("\\", "\\\\").replace("'", "\\'")
        val operation = when (kind) {
            StorageKind.Cookies -> "document.cookie.split(';').forEach(function(c){var n=c.split('=')[0].trim();if(n)document.cookie=n+'=; Max-Age=0; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';});"
            StorageKind.LocalStorage -> "localStorage.clear();"
            StorageKind.SessionStorage -> "sessionStorage.clear();"
        }
        return "(function(){try{if(location.origin!=='$safeOrigin')return JSON.stringify({ok:false,error:'The active page origin changed.'});$operation return JSON.stringify({ok:true});}catch(error){return JSON.stringify({ok:false,error:'Storage could not be cleared.'});}})()"
    }
}

object PageInfoDecoder {
    fun decode(encoded: String?, ssl: SslState, loadTimeMillis: Long?, cookies: String?): PageInfoResult {
        val json = encoded?.let { decodeQuoted(it, 0) } ?: return PageInfoResult(error = "Page info returned no result.")
        if (!Regex("\\\"ok\\\"\\s*:\\s*true").containsMatchIn(json)) return PageInfoResult(error = property(json, "error") ?: "Page info failed.")
        return PageInfoResult(PageInfo(
            property(json,"title").orEmpty(), property(json,"url").orEmpty(), property(json,"origin").orEmpty(), ssl, loadTimeMillis,
            property(json,"viewport").orEmpty(), unpack(property(json,"meta")), unpack(property(json,"og")), property(json,"favicon").orEmpty(),
            parseCookies(cookies), unpack(property(json,"local")), unpack(property(json,"session")),
        ))
    }
    fun clearSucceeded(encoded: String?): Boolean = encoded?.let { decodeQuoted(it,0) }?.let { Regex("\\\"ok\\\"\\s*:\\s*true").containsMatchIn(it) } == true
    private fun unpack(value: String?): List<NamedValue> = value.orEmpty().split('\u001e').filter(String::isNotEmpty).map { val p=it.split('\u001f',limit=2); NamedValue(p[0],p.getOrElse(1){""}) }
    private fun parseCookies(value: String?): List<NamedValue> = value.orEmpty().split(';').mapNotNull { val v=it.trim(); if(v.isEmpty()) null else NamedValue(v.substringBefore('='),v.substringAfter('=',"")) }.sortedBy { it.name }
    private fun property(json:String,key:String):String? { val marker="\"$key\"";var i=json.indexOf(marker);if(i<0)return null;i=json.indexOf(':',i+marker.length)+1;while(i in json.indices&&json[i].isWhitespace())i++;return if(i in json.indices&&json[i]=='"')decodeQuoted(json,i)else null }
    private fun decodeQuoted(value:String,start:Int):String? { if(start !in value.indices||value[start]!='"')return null;val out=StringBuilder();var i=start+1;while(i<value.length)when(val c=value[i++]){'"'->return out.toString();'\\'->{if(i>=value.length)return null;when(val e=value[i++]){'"','\\','/'->out.append(e);'b'->out.append('\b');'f'->out.append('\u000C');'n'->out.append('\n');'r'->out.append('\r');'t'->out.append('\t');'u'->{if(i+4>value.length)return null;out.append(value.substring(i,i+4).toIntOrNull(16)?.toChar()?:return null);i+=4};else->return null}};else->out.append(c)};return null }
}

object PageInfoFormatter {
    fun format(info: PageInfo): String = buildString {
        appendLine("Title: ${info.title}"); appendLine("URL: ${info.url}"); appendLine("SSL: ${info.sslState.label}")
        appendLine("Load time: ${info.loadTimeMillis?.let { "${it} ms" } ?: "Unavailable"}"); appendLine("Viewport: ${info.viewport}"); appendLine("Favicon: ${info.faviconUrl}")
        fun section(label:String,values:List<NamedValue>){appendLine();appendLine("[$label]");values.forEach{appendLine("${it.name}=${it.value}")}}
        section("Meta",info.meta);section("Open Graph",info.openGraph);section("Cookies",info.cookies);section("localStorage",info.localStorage);section("sessionStorage",info.sessionStorage)
    }.trimEnd()
}
