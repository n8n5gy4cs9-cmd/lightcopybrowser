package dev.lightcopy.browser.pageinfo

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageInfoWebViewTest {
    private lateinit var server: FixtureServer
    private lateinit var webView: WebView
    @Before fun setUp(){server=FixtureServer();val latch=CountDownLatch(1);InstrumentationRegistry.getInstrumentation().runOnMainSync{webView=WebView(ApplicationProvider.getApplicationContext()).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView,url:String){if(url==server.url)latch.countDown()}};loadUrl(server.url)}};Assert.assertTrue(latch.await(10,TimeUnit.SECONDS))}
    @After fun tearDown(){InstrumentationRegistry.getInstrumentation().runOnMainSync{webView.destroy()};server.close()}

    @Test fun metadataAndStorageAreReadAndClearedForExpectedOrigin(){
        val first=inspect();Assert.assertEquals("Fixture info",first.title);Assert.assertEquals("summary",first.meta.single{it.name=="description"}.value);Assert.assertEquals("Card",first.openGraph.single().value);Assert.assertEquals("dark",first.localStorage.single().value);Assert.assertEquals("yes",first.sessionStorage.single().value)
        Assert.assertTrue(clear(StorageKind.LocalStorage,first.origin));val second=inspect();Assert.assertTrue(second.localStorage.isEmpty());Assert.assertEquals("yes",second.sessionStorage.single().value)
        Assert.assertFalse(clear(StorageKind.SessionStorage,"http://wrong.test"));Assert.assertEquals("yes",inspect().sessionStorage.single().value)
    }
    private fun inspect():PageInfo{val latch=CountDownLatch(1);var result:PageInfoResult?=null;InstrumentationRegistry.getInstrumentation().runOnMainSync{webView.evaluateJavascript(PageInfoScripts.inspect){result=PageInfoDecoder.decode(it,SslState.Insecure,25,CookieManager.getInstance().getCookie(webView.url));latch.countDown()}};Assert.assertTrue(latch.await(10,TimeUnit.SECONDS));return requireNotNull(result?.info)}
    private fun clear(kind:StorageKind,origin:String):Boolean{val latch=CountDownLatch(1);var ok=false;InstrumentationRegistry.getInstrumentation().runOnMainSync{webView.evaluateJavascript(PageInfoScripts.clear(kind,origin)){ok=PageInfoDecoder.clearSucceeded(it);latch.countDown()}};Assert.assertTrue(latch.await(10,TimeUnit.SECONDS));return ok}
    private class FixtureServer:AutoCloseable{private val socket=ServerSocket(0);private val thread=Thread{while(!socket.isClosed)runCatching{socket.accept().use{client->val reader=BufferedReader(InputStreamReader(client.getInputStream()));while(!reader.readLine().isNullOrEmpty())Unit;val html="""<!doctype html><html><head><title>Fixture info</title><meta name="description" content="summary"><meta property="og:title" content="Card"><link rel="icon" href="/icon.png"><meta name="viewport" content="width=device-width"></head><body><script>localStorage.setItem('theme','dark');sessionStorage.setItem('draft','yes');document.cookie='mode=dev; path=/'</script></body></html>""";val response="HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html";client.getOutputStream().write(response.toByteArray())}}}.apply{isDaemon=true;start()};val url="http://127.0.0.1:${socket.localPort}/fixture.html";override fun close()=socket.close()}
}
