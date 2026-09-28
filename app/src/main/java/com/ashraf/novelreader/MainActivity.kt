package com.ashraf.novelreader

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.security.MessageDigest

class MainActivity : Activity() {
    private lateinit var novel: WebView
    private lateinit var ai: WebView
    private lateinit var split: SplitLayout
    private lateinit var status: TextView
    private lateinit var promptStore: PromptStore
    private val handler=Handler(Looper.getMainLooper())

    private var provider=AiProvider.CHATGPT
    private var current: Chapter?=null
    private var session=0L
    private var aiJob=0L
    private var baselineResponse=""
    private var baselineHash=""
    private var stableSince=0L
    private var lastObserved=""
    private var viewMode=0 // 0 split, 1 novel, 2 chatbot
    private var autoSendOnLoad=false
    private var searchEngine="Google"
    private var readerNight=false
    private var readerFontSize=20
    private var pulseButton:Button?=null
    private var nextButton:Button?=null
    private val pulse=object:Runnable{
        override fun run(){
            pulseButton?.animate()?.scaleX(1.06f)?.scaleY(1.06f)?.setDuration(280)?.withEndAction{
                pulseButton?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(280)?.start()
            }?.start()
            nextButton?.animate()?.scaleX(1.04f)?.scaleY(1.04f)?.setDuration(280)?.withEndAction{
                nextButton?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(280)?.start()
            }?.start()
            handler.postDelayed(this,2400)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        promptStore=PromptStore(this)
        searchEngine=getPreferences(0).getString("search_engine","Google") ?: "Google"
        readerNight=getPreferences(0).getBoolean("reader_night",false)
        readerFontSize=getPreferences(0).getInt("reader_font_size",20).coerceIn(18,24)
        buildUi()
        configureWebViews()
        novel.loadUrl("https://www.webnovel.com/")
        ai.loadUrl(provider.home)
    }

    private fun buildUi(){
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16,16,20))
        }

        val urlBar=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(4),dp(2),dp(4),dp(2))
        }
        val url=EditText(this).apply {
            hint="Novel URL"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            layoutParams=LinearLayout.LayoutParams(0,dp(40),1f)
        }
        urlBar.addView(url)
        urlBar.addView(btn("GO"){ val u=url.text.toString().trim(); if(u.isNotBlank()) loadNovelOrSearch(u) })
        root.addView(urlBar)

        status=TextView(this).apply {
            setTextColor(Color.LTGRAY)
            setPadding(dp(8),0,dp(8),dp(2))
            text="Ready"
            textSize=12f
            maxLines=1
        }
        root.addView(status)

        novel=WebView(this)
        ai=WebView(this)
        split=SplitLayout(this)
        split.setPanes(novel,ai)
        root.addView(split,LinearLayout.LayoutParams(-1,0,1f))

        val bottom=FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(24,24,29))
            setPadding(dp(4),dp(2),dp(4),dp(2))
        }
        val more=iconBtn("⋮","More options"){showMenu()}.apply{
            layoutParams=FrameLayout.LayoutParams(dp(46),dp(42),Gravity.START or Gravity.CENTER_VERTICAL)
        }
        val center=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            layoutParams=FrameLayout.LayoutParams(-2,-1,Gravity.CENTER)
        }
        pulseButton=iconBtn("⚡","Instant Extract"){instant()}.apply{
            layoutParams=LinearLayout.LayoutParams(dp(52),dp(42)).apply{setMargins(dp(4),0,dp(4),0)}
            textSize=22f
        }
        nextButton=iconBtn("›","Next chapter"){navigate("next")}.apply{
            layoutParams=LinearLayout.LayoutParams(dp(52),dp(42)).apply{setMargins(dp(4),0,dp(4),0)}
            textSize=23f
        }
        center.addView(pulseButton!!)
        center.addView(nextButton!!)

        val right=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            layoutParams=FrameLayout.LayoutParams(-2,-1,Gravity.END or Gravity.CENTER_VERTICAL)
        }
        right.addView(iconBtn("G","ChatGPT"){switchProvider(AiProvider.CHATGPT)})
        right.addView(iconBtn("✦","Gemini"){switchProvider(AiProvider.GEMINI)})
        right.addView(iconBtn("◫","Split / Novel / Chatbot"){cycleView()})
        // Left: view controls. Right: menu.
        bottom.addView(right)
        bottom.addView(more)
        root.addView(bottom,LinearLayout.LayoutParams(-1,dp(48)))
        handler.postDelayed(pulse,1200)

        setContentView(root)
    }

    private fun configureWebViews(){
        CookieStore.configure(novel)
        CookieStore.configure(ai)
        novel.webChromeClient=WebChromeClient()
        ai.webChromeClient=WebChromeClient()

        novel.webViewClient=object:WebViewClient(){
            override fun onPageFinished(v:WebView,url:String){
                super.onPageFinished(v,url)
                if(AdBlock.enabled(this@MainActivity)) v.evaluateJavascript(AdBlock.cosmeticJs(),null)
                v.evaluateJavascript(ReaderStyle.apply(readerNight,readerFontSize),null)
                session++
                aiJob++
                current=null
                baselineResponse=""
                baselineHash=""
                lastObserved=""
                stableSince=0L
                val token=session
                if(autoSendOnLoad){
                    autoSendOnLoad=false
                    handler.postDelayed({fastExtract(0,token,url)},180)
                }else{
                    status("Ready")
                }
            }
        }
        ai.webViewClient=object:WebViewClient(){
            override fun onPageFinished(v:WebView,url:String){
                super.onPageFinished(v,url)
                if(ProviderScripts.hostMatches(provider,url)) status("${provider.label} ready")
            }
            override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=false
            override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse? {
                if(AdBlock.enabled(this@MainActivity) && AdBlock.blocked(r.url.toString())){
                    return WebResourceResponse("text/plain","utf-8",null)
                }
                return super.shouldInterceptRequest(v,r)
            }
        }
    }

    private fun instant(){
        ai.evaluateJavascript(ProviderScripts.clearComposerScript(),null)
        val token=session
        novel.evaluateJavascript(NovelJs.extract()){raw->
            if(token!=session)return@evaluateJavascript
            val ch=WebNovelAdapter.buildChapter(raw?.unquoteJs().orEmpty())
            if(ch!=null)handleChapter(ch,true) else status("Chapter text not found")
        }
    }

    private fun fastExtract(attempt:Int,token:Long,url:String){
        if(token!=session)return
        novel.evaluateJavascript(NovelJs.extract()){raw->
            if(token!=session)return@evaluateJavascript
            val ch=WebNovelAdapter.buildChapter(raw?.unquoteJs().orEmpty())
            if(ch!=null){
                handleChapter(ch,true)
            }else if(attempt<10){
                handler.postDelayed({fastExtract(attempt+1,token,url)},120)
            }
        }
    }

    private fun handleChapter(ch:Chapter,auto:Boolean){
        if(ch.text.length<120)return
        if(current?.id==ch.id && auto)return
        current=ch
        status("✓ ${ch.title.ifBlank{"Chapter ${ch.number}"}} • ${ch.text.length} chars")
        if(auto)sendChapter(ch)
    }

    private fun sendChapter(ch:Chapter){
        val request=++aiJob
        val targetProvider=provider
        val prompt=promptStore.get().replace("{{CHAPTER}}",ch.text)
        status("Waiting for ${targetProvider.label}…")
        waitAiIdle(request,session,ch,targetProvider,0,prompt)
    }

    private fun waitAiIdle(request:Long,token:Long,ch:Chapter,p:AiProvider,attempt:Int,prompt:String){
        if(request!=aiJob||token!=session)return
        ai.evaluateJavascript(ProviderScripts.readLen()){raw->
            if(request!=aiJob||token!=session)return@evaluateJavascript
            val parts=raw?.unquoteJs().orEmpty().split("|")
            val streaming=parts.getOrNull(1)=="1"
            if(streaming && attempt<12){
                handler.postDelayed({waitAiIdle(request,token,ch,p,attempt+1,prompt)},1000)
            }else{
                if(streaming) ai.evaluateJavascript(ProviderScripts.stop(),null)
                ai.evaluateJavascript(ProviderScripts.send(p,prompt,true)){sentRaw->
                    if(request!=aiJob||token!=session)return@evaluateJavascript
                    val sent=sentRaw?.unquoteJs().orEmpty()
                    if(!sent.startsWith("ok:")){
                        status(if(sent=="nobox") "⚠ ${p.label}: chat box not found" else "⚠ ${p.label}: message send failed")
                        return@evaluateJavascript
                    }
                    val parts2=sent.substringAfter("ok:").split(":")
                    val n0=parts2.getOrNull(0)?.toIntOrNull()?:0
                    val baseLen=parts2.getOrNull(1)?.toIntOrNull()?:0
                    status("✓ Sent • waiting for ${p.label} response…")
                    handler.postDelayed({
                        if(request==aiJob&&token==session)
                            pollResponse(request,token,ch,p,0,n0,baseLen,System.currentTimeMillis(),0,0)
                    },1800)
                }
            }
        }
    }

    private fun pollResponse(
        request:Long,token:Long,ch:Chapter,p:AiProvider,attempt:Int,n0:Int,baseLen:Int,
        started:Long,lastLen:Int,stable:Int
    ){
        if(request!=aiJob||token!=session)return
        ai.evaluateJavascript(ProviderScripts.readLen()){raw->
            if(request!=aiJob||token!=session)return@evaluateJavascript
            val parts=raw?.unquoteJs().orEmpty().split("|")
            val n=parts.getOrNull(0)?.toIntOrNull()?:0
            val streaming=parts.getOrNull(1)=="1"
            val len=parts.getOrNull(2)?.toIntOrNull()?:0
            val got=n>n0 || (n==n0 && len>baseLen)
            val nextStable=if(got && len==lastLen)stable+1 else 0
            val elapsed=System.currentTimeMillis()-started

            if(got && !streaming && nextStable>=3 && len>=30){
                finishResponse(request,token,ch,p)
                return@evaluateJavascript
            }
            if(got && nextStable>=15 && len>50){
                finishResponse(request,token,ch,p)
                return@evaluateJavascript
            }
            if(elapsed>6*60_000){
                status("Timed out waiting for ${p.label}")
                return@evaluateJavascript
            }
            if(!got && elapsed>60_000){
                status("⚠ ${p.label} did not start a response")
                return@evaluateJavascript
            }
            if(attempt<360){
                handler.postDelayed({
                    pollResponse(request,token,ch,p,attempt+1,n0,baseLen,started,len,nextStable)
                },1000)
            }
        }
    }

    private fun finishResponse(request:Long,token:Long,ch:Chapter,p:AiProvider){
        if(request!=aiJob||token!=session)return
        ai.evaluateJavascript(ProviderScripts.readText()){raw->
            if(request!=aiJob||token!=session)return@evaluateJavascript
            val text=raw?.unquoteJs()?.trim().orEmpty()
            if(text.length<30){
                status("⚠ ${p.label}: response could not be read")
                return@evaluateJavascript
            }
            insertResult(ch,text)
        }
    }

    private fun insertResult(ch:Chapter,text:String){
        if(ch.id!=current?.id)return
        novel.evaluateJavascript(NovelJs.insertTranslation(text)){result->
            val ok=result?.contains("inserted") == true
            if(ok) novel.evaluateJavascript(ReaderStyle.apply(readerNight,readerFontSize),null)
            status(if(ok) "✓ Translation inserted into ${ch.number.ifBlank{"chapter"}}" else "⚠ Translation could not be inserted")
        }
        CookieStore.flush()
    }

    private fun navigate(dir:String){
        val old=current
        session++
        aiJob++
        current=null
        baselineResponse=""
        baselineHash=""
        lastObserved=""
        stableSince=0L
        status("Loading ${if(dir=="next")"next" else "previous"} chapter…")
        // Next only changes the novel page. Translation starts when Instant Extract is pressed.
        autoSendOnLoad=false

        ai.evaluateJavascript(ProviderScripts.clearComposerScript(),null)

        if(old==null){
            handler.postDelayed({fastExtract(0,session,novel.url.orEmpty())},100)
            return
        }
        val target=if(dir=="next")old.nextUrl else old.prevUrl
        if(WebNovelAdapter.isWebNovel(old.url)){
            novel.evaluateJavascript(WebNovelAdapter.navigateFromCatalogScript(dir,old.title,old.url)){raw->
                val result=raw?.unquoteJs().orEmpty()
                if(result.startsWith("http")){
                    novel.loadUrl(result)
                }else{
                    novel.evaluateJavascript(NovelJs.siteNext(dir)){fallbackRaw->
                        val fallback=fallbackRaw?.unquoteJs().orEmpty()
                        if(fallback.startsWith("http")){
                            novel.loadUrl(fallback)
                        }else if(fallback=="clicked"){
                        }else{
                            autoSendOnLoad=false
                            status("WebNovel chapter link not found")
                        }
                    }
                }
            }
            return
        }
        novel.evaluateJavascript(NovelJs.siteNext(dir)){raw->
            val result=raw?.unquoteJs().orEmpty()
            if(result.startsWith("http")){
                novel.loadUrl(result)
            }else if(result=="clicked"){
            }else{
                autoSendOnLoad=false
                status("Chapter navigation link not found")
            }
        }
    }

    private fun loadNovelOrSearch(q:String){
        val u=when{
            q.startsWith("http://",true)||q.startsWith("https://",true)->q
            searchEngine=="Bing"->"https://www.bing.com/search?q="+java.net.URLEncoder.encode(q,"UTF-8")
            searchEngine=="DuckDuckGo"->"https://duckduckgo.com/?q="+java.net.URLEncoder.encode(q,"UTF-8")
            else->"https://www.google.com/search?q="+java.net.URLEncoder.encode(q,"UTF-8")
        }
        novel.loadUrl(u)
    }

    private fun cycleView(){
        viewMode=(viewMode+1)%3
        when(viewMode){
            0 -> { split.showSplit(); status("Split view") }
            1 -> { split.showNovelOnly(); status("Novel view") }
            2 -> { split.showAiOnly(); status("Chatbot view") }
        }
    }

    private fun providerIcon(p:AiProvider)=when(p){
        AiProvider.CHATGPT -> "G"
        AiProvider.GEMINI -> "✦"
        AiProvider.CLAUDE -> "C"
        AiProvider.DEEPSEEK -> "D"
        AiProvider.GROK -> "X"
    }

    private fun iconBtn(icon:String,description:String,action:()->Unit)=Button(this).apply{
        text=icon
        contentDescription=description
        setTextColor(Color.WHITE)
        textSize=18f
        minWidth=0
        minimumWidth=0
        minHeight=0
        minimumHeight=0
        setPadding(0,0,0,0)
        layoutParams=LinearLayout.LayoutParams(dp(42),dp(40)).apply{
            setMargins(dp(2),0,dp(2),0)
        }
        setOnClickListener{action()}
    }

    private fun showMenu(){
        val ad=if(AdBlock.enabled(this)) "ON" else "OFF"
        val items=arrayOf(
            "‹ Previous chapter","↻ Reload novel page","↻ Reload chatbot",
            "⌫ Clear chatbot input","▣ Split view","📖 Novel only","💬 Chatbot only",
            "↩ Restore original chapter","✎ Translation prompt","Aa Reader style",
            "AI provider: ${provider.label}","Search engine: $searchEngine","Ad blocker: $ad"
        )
        AlertDialog.Builder(this).setTitle("NovelReaderX").setItems(items){_,which->
            when(which){
                0->navigate("prev");1->novel.reload();2->ai.reload()
                3->ai.evaluateJavascript(ProviderScripts.clearComposerScript(),null)
                4->{viewMode=0;split.showSplit()};5->{viewMode=1;split.showNovelOnly()}
                6->{viewMode=2;split.showAiOnly()}
                7->novel.evaluateJavascript(NovelJs.restoreOriginal()){status("Original chapter restored")}
                8->editPrompt();9->readerSettings();10->chooseProvider();11->chooseSearchEngine()
                12->{AdBlock.setEnabled(this,!AdBlock.enabled(this));status("Ad blocker "+if(AdBlock.enabled(this))"ON" else "OFF");novel.reload()}
            }
        }.show()
    }

    private fun readerSettings(){
        val sizes=arrayOf("18 px","20 px","22 px","24 px")
        val current=sizes.indexOf("${readerFontSize} px").coerceAtLeast(0)
        val box=CheckBox(this).apply{
            text="Night mode"
            isChecked=readerNight
            setTextColor(Color.WHITE)
            setPadding(dp(8),dp(12),dp(8),dp(12))
        }
        val panel=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),0,dp(8),0)
            addView(box)
        }
        AlertDialog.Builder(this)
            .setTitle("Reader style")
            .setSingleChoiceItems(sizes,current){_,which->
                readerFontSize=18+which*2
                getPreferences(0).edit().putInt("reader_font_size",readerFontSize).apply()
                novel.evaluateJavascript(ReaderStyle.apply(readerNight,readerFontSize),null)
            }
            .setView(panel)
            .setPositiveButton("Apply"){_,_->
                readerNight=box.isChecked
                getPreferences(0).edit().putBoolean("reader_night",readerNight).apply()
                novel.evaluateJavascript(ReaderStyle.apply(readerNight,readerFontSize),null)
                status(if(readerNight)"Night reader • ${readerFontSize}px" else "Day reader • ${readerFontSize}px")
            }
            .show()
    }

    private fun chooseProvider(){
        val labels=AiProvider.entries.map{it.label}.toTypedArray()
        AlertDialog.Builder(this).setTitle("AI provider")
            .setSingleChoiceItems(labels,AiProvider.entries.indexOf(provider)){d,w->switchProvider(AiProvider.entries[w]);d.dismiss()}.show()
    }

    private fun chooseSearchEngine(){
        val engines=arrayOf("Google","Bing","DuckDuckGo")
        AlertDialog.Builder(this).setTitle("Search engine")
            .setSingleChoiceItems(engines,engines.indexOf(searchEngine).coerceAtLeast(0)){d,w->
                searchEngine=engines[w];getPreferences(0).edit().putString("search_engine",searchEngine).apply();d.dismiss()
            }.show()
    }

    private fun switchProvider(p:AiProvider){
        if(provider==p)return
        provider=p
        aiJob++
        status("Opening ${p.label}…")
        ai.loadUrl(p.home)
    }

    private fun editPrompt(){
        val input=EditText(this).apply{
            setText(promptStore.get())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            minLines=10
            gravity=Gravity.TOP
        }
        AlertDialog.Builder(this)
            .setTitle("Translation prompt")
            .setView(input)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save"){_,_->promptStore.save(input.text.toString());status("Prompt saved")}
            .show()
    }

    private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun String.unquoteJs():String=runCatching{JSONObjectCompat.unquote(this)}.getOrDefault(this)
    private fun status(s:String){status.text=s}

    private fun btn(text:String,action:()->Unit)=Button(this).apply{
        this.text=text
        setTextColor(Color.WHITE)
        minWidth=dp(72)
        layoutParams=LinearLayout.LayoutParams(dp(78),dp(44))
        setOnClickListener{action()}
    }
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()

    override fun onPause(){CookieStore.flush();super.onPause()}
    override fun onDestroy(){handler.removeCallbacks(pulse);aiJob++;session++;novel.destroy();ai.destroy();super.onDestroy()}
}

object JSONObjectCompat {
    fun unquote(s:String):String=runCatching{org.json.JSONTokener(s).nextValue() as String}.getOrDefault(s)
}