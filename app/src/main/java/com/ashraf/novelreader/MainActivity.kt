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

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        promptStore=PromptStore(this)
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
        urlBar.addView(btn("GO"){ val u=url.text.toString().trim(); if(u.isNotBlank()) novel.loadUrl(u) })
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

        val bottom=HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false
            setBackgroundColor(Color.rgb(28,28,34))
        }
        val controls=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(2),dp(2),dp(2),dp(2))
        }
        controls.addView(iconBtn("‹","Previous"){navigate("prev")})
        controls.addView(iconBtn("⚡","Extract"){instant()})
        controls.addView(iconBtn("›","Next"){navigate("next")})
        controls.addView(iconBtn("◫","Cycle novel / chatbot / split"){cycleView()})
        controls.addView(iconBtn("✎","Prompt"){editPrompt()})
        controls.addView(iconBtn("⋮","More options"){showMenu()})
        AiProvider.entries.forEach { p ->
            controls.addView(iconBtn(providerIcon(p),"Use ${p.label}"){switchProvider(p)})
        }
        bottom.addView(controls)
        root.addView(bottom,LinearLayout.LayoutParams(-1,dp(46)))

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
                session++
                aiJob++
                current=null
                baselineResponse=""
                baselineHash=""
                lastObserved=""
                stableSince=0L
                val token=session
                handler.postDelayed({fastExtract(0,token,url)},120)
            }
        }
        ai.webViewClient=object:WebViewClient(){
            override fun onPageFinished(v:WebView,url:String){
                super.onPageFinished(v,url)
                if(ProviderScripts.hostMatches(provider,url)) status("${provider.label} ready")
            }
            override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=false
        }
    }

    private fun instant(){
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
        stableSince=0L
        lastObserved=""
        val prompt=promptStore.get().replace("{{CHAPTER}}",ch.text)
        val targetProvider=provider
        status("Sending ${ch.number.ifBlank{"chapter"}} → ${targetProvider.label}…")
        ai.evaluateJavascript(ProviderScripts.responseTextScript(targetProvider)){beforeRaw->
            if(request!=aiJob)return@evaluateJavascript
            baselineResponse=beforeRaw?.unquoteJs()?.trim().orEmpty()
            baselineHash=hash(baselineResponse)
            ai.evaluateJavascript(ProviderScripts.inputAndSend(targetProvider,prompt)){res->
                if(request!=aiJob)return@evaluateJavascript
                if(res?.contains("nobox")==true){
                    status("${targetProvider.label}: input box not found")
                }else{
                    status("✓ Sent • waiting for ${targetProvider.label}…")
                    pollResponse(request,session,ch,0,targetProvider)
                }
            }
        }
    }

    private fun pollResponse(request:Long,token:Long,ch:Chapter,attempt:Int,p:AiProvider){
        if(request!=aiJob||token!=session)return
        ai.evaluateJavascript(ProviderScripts.responseTextScript(p)){raw->
            if(request!=aiJob||token!=session)return@evaluateJavascript
            val text=raw?.unquoteJs()?.trim().orEmpty()
            val h=hash(text)
            val changed=text.isNotEmpty() && h!=baselineHash && text!=baselineResponse
            var done=false
            if(changed){
                if(text==lastObserved){
                    if(stableSince==0L)stableSince=System.currentTimeMillis()
                    if(System.currentTimeMillis()-stableSince>=700){
                        insertResult(ch,text)
                        done=true
                    }
                }else{
                    lastObserved=text
                    stableSince=System.currentTimeMillis()
                }
            }
            if(!done && attempt<360){
                handler.postDelayed({pollResponse(request,token,ch,attempt+1,p)},300)
            }else if(!done){
                status("Timed out waiting for ${p.label}")
            }
        }
    }

    private fun insertResult(ch:Chapter,text:String){
        if(ch.id!=current?.id)return
        novel.evaluateJavascript(NovelJs.insertTranslation(text)){result->
            val ok=result?.contains("inserted") == true
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
                    status("WebNovel chapter link not found")
                }
            }
            return
        }
        novel.evaluateJavascript(NovelJs.siteNext(dir)){raw->
            val result=raw?.unquoteJs().orEmpty()
            if(result.startsWith("http"))novel.loadUrl(result)
            else handler.postDelayed({fastExtract(0,session,novel.url.orEmpty())},120)
        }
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
        val items=arrayOf(
            "↻ Reload novel page",
            "↻ Reload chatbot",
            "⌫ Clear chatbot input",
            "▣ Split view",
            "📖 Novel only",
            "💬 Chatbot only",
            "↩ Restore original chapter",
            "✎ Translation prompt"
        )
        AlertDialog.Builder(this)
            .setTitle("NovelReaderX")
            .setItems(items){_,which->
                when(which){
                    0 -> novel.reload()
                    1 -> ai.reload()
                    2 -> ai.evaluateJavascript(ProviderScripts.clearComposerScript(),null)
                    3 -> {viewMode=0;split.showSplit()}
                    4 -> {viewMode=1;split.showNovelOnly()}
                    5 -> {viewMode=2;split.showAiOnly()}
                    6 -> novel.evaluateJavascript(NovelJs.restoreOriginal()){status("Original chapter restored")}
                    7 -> editPrompt()
                }
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
    override fun onDestroy(){aiJob++;session++;novel.destroy();ai.destroy();super.onDestroy()}
}

object JSONObjectCompat {
    fun unquote(s:String):String=runCatching{org.json.JSONTokener(s).nextValue() as String}.getOrDefault(s)
}