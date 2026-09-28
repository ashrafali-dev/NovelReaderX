package com.ashraf.novelreader

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
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

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        promptStore=PromptStore(this)
        buildUi()
        configureWebViews()
        novel.loadUrl("https://www.webnovel.com/")
        ai.loadUrl(provider.home)
    }

    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(16,16,20))}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(4,4,4,4)}
        val url=EditText(this).apply{hint="Novel URL";setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);layoutParams=LinearLayout.LayoutParams(0,dp(44),1f)}
        val go=btn("GO"){novel.loadUrl(url.text.toString().trim())}
        top.addView(url);top.addView(go)
        root.addView(top)

        val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(4,4,4,4)}
        controls.addView(btn("⚡ Extract"){instant()})
        controls.addView(btn("‹"){navigate("prev")})
        controls.addView(btn("›"){navigate("next")})
        controls.addView(btn("½"){split.resetHalf()})
        controls.addView(btn("Prompt"){editPrompt()})
        root.addView(controls)

        val providers=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(4,0,4,4)}
        AiProvider.entries.forEach{p->providers.addView(btn(p.label){switchProvider(p)})}
        root.addView(providers)

        status=TextView(this).apply{setTextColor(Color.LTGRAY);setPadding(dp(8),0,dp(8),dp(4));text="Ready";textSize=12f}
        root.addView(status)

        novel=WebView(this);ai=WebView(this)
        split=SplitLayout(this)
        split.setPanes(novel,ai)
        root.addView(split,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    private fun configureWebViews(){
        CookieStore.configure(novel);CookieStore.configure(ai)
        novel.webChromeClient=WebChromeClient();ai.webChromeClient=WebChromeClient()
        novel.settings.userAgentString=novel.settings.userAgentString
        novel.webViewClient=object:WebViewClient(){
            override fun onPageFinished(v:WebView,url:String){
                super.onPageFinished(v,url);handler.post{fastExtract(0,session,url)}
            }
        }
        ai.webViewClient=object:WebViewClient(){
            override fun onPageFinished(v:WebView,url:String){super.onPageFinished(v,url);if(ProviderScripts.hostMatches(provider,url)) status("$provider ready")}
            override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean=false
        }
    }

    private fun instant(){
        val token=session
        novel.evaluateJavascript(NovelJs.extract()){raw->{if(token!=session)return@evaluateJavascript;val ch=WebNovelAdapter.buildChapter(raw?.unquoteJs() ?: "");if(ch!=null) handleChapter(ch, true) else status("Chapter text not found")}}
    }

    private fun fastExtract(attempt:Int,token:Long,url:String){
        if(token!=session)return
        novel.evaluateJavascript(NovelJs.extract()){raw->{
            if(token!=session)return@evaluateJavascript
            val ch=WebNovelAdapter.buildChapter(raw?.unquoteJs() ?: "")
            if(ch!=null){handleChapter(ch,true);return@evaluateJavascript}
            if(attempt<24)handler.postDelayed({fastExtract(attempt+1,token,url)},80)
        }}
    }

    private fun handleChapter(ch:Chapter,auto:Boolean){
        if(ch.text.length<120)return
        val id=ch.id
        if(current?.id==id && !auto)return
        current=ch
        status("✓ ${ch.title.ifBlank{"Chapter ${ch.number}"}} • ${ch.text.length} chars")
        if(auto)sendChapter(ch)
    }

    private fun sendChapter(ch:Chapter){
        val request=++aiJob
        stableSince=0L;lastObserved=""
        val prompt=promptStore.get().replace("{{CHAPTER}}",ch.text)
        status("Preparing ${ch.number.ifBlank{"chapter"}} for ${provider.label}…")
        val targetProvider=provider
        // Snapshot the current answer BEFORE sending. This prevents an old answer
        // from being mistaken for the new chapter's answer.
        ai.evaluateJavascript(ProviderScripts.responseTextScript(targetProvider)){beforeRaw->
            if(request!=aiJob)return@evaluateJavascript
            baselineResponse=beforeRaw?.unquoteJs()?.trim().orEmpty()
            baselineHash=hash(baselineResponse)
            ai.evaluateJavascript(ProviderScripts.inputAndSend(targetProvider,prompt)){res->
                if(request!=aiJob)return@evaluateJavascript
                if(res?.contains("nobox") == true){status("${targetProvider.label}: input box not found");return@evaluateJavascript}
                status("✓ Sent • waiting for ${targetProvider.label}…")
                pollResponse(request,session,ch,0,targetProvider)
            }
        }
    }

    private fun pollResponse(request:Long,token:Long,ch:Chapter,attempt:Int,p:AiProvider){
        if(request!=aiJob||token!=session)return
        ai.evaluateJavascript(ProviderScripts.responseTextScript(p)){raw->{
            if(request!=aiJob||token!=session)return@evaluateJavascript
            val text=raw?.unquoteJs()?.trim().orEmpty()
            val h=hash(text)
            val changed=text.isNotEmpty() && h!=baselineHash && text!=baselineResponse
            if(changed){if(text==lastObserved){if(stableSince==0L)stableSince=System.currentTimeMillis();if(System.currentTimeMillis()-stableSince>=500){insertResult(ch,text);return@evaluateJavascript}}else{lastObserved=text;stableSince=System.currentTimeMillis()}}
            if(attempt<480)handler.postDelayed({pollResponse(request,token,ch,attempt+1,p)},250)
            else status("Timed out waiting for ${p.label}")
        }}
    }

    private fun insertResult(ch:Chapter,text:String){
        if(ch.id!=current?.id)return
        novel.evaluateJavascript(NovelJs.insertTranslation(text),null)
        status("✓ Translation inserted into ${ch.number.ifBlank{"chapter"}}")
        CookieStore.flush()
    }

    private fun navigate(dir:String){
        val old=current
        session++
        aiJob++
        current=null
        baselineResponse="";baselineHash="";lastObserved="";stableSince=0L
        status("Loading ${if(dir=="next")"next" else "previous"} chapter…")
        ai.evaluateJavascript("document.querySelectorAll('textarea,[contenteditable=\"true\"],[role=\"textbox\"]').forEach(e=>{try{e.value=''}catch(x){};try{e.textContent=''}catch(x){}});'ok'",null)
        if(old==null){fastExtract(0,session,novel.url);return}
        val target=if(dir=="next")old.nextUrl else old.prevUrl
        if(!target.isNullOrBlank()){novel.loadUrl(target);return}
        if(WebNovelAdapter.isWebNovel(old.url)){
            // The catalog is fetched inside the current WebView with its own cookies.
            novel.evaluateJavascript(WebNovelAdapter.navigateFromCatalogScript(dir,old.title)){r->
                if(r?.contains("error") == true) status("WebNovel catalog error")
                handler.postDelayed({fastExtract(0,session,novel.url)},120)
            }
            return
        }
        novel.evaluateJavascript(NovelJs.siteNext(dir)){raw->{
            val result=raw?.unquoteJs().orEmpty()
            if(result.startsWith("http"))novel.loadUrl(result)
            else handler.postDelayed({fastExtract(0,session,novel.url)},150)
        }}
    }

    private fun switchProvider(p:AiProvider){
        if(provider==p)return
        provider=p;aiJob++;status("Opening ${p.label}…");ai.loadUrl(p.home)
    }

    private fun editPrompt(){
        val input=EditText(this).apply{setText(promptStore.get());setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);minLines=10;gravity=Gravity.TOP;setPadding(dp(12),dp(8),dp(12),dp(8))}
        AlertDialog.Builder(this).setTitle("Translation prompt").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->promptStore.save(input.text.toString());status("Prompt saved")}.show()
    }

    private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun String.unquoteJs():String=runCatching{JSONObjectCompat.unquote(this)}.getOrDefault(this)
    private fun status(s:String){status.text=s}
    private fun btn(text:String,action:()->Unit)=Button(this).apply{this.text=text;setTextColor(Color.WHITE);setOnClickListener{action()};layoutParams=LinearLayout.LayoutParams(0,dp(44),1f)}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()

    override fun onPause(){CookieStore.flush();super.onPause()}
    override fun onDestroy(){aiJob++;session++;novel.destroy();ai.destroy();super.onDestroy()}
}

object JSONObjectCompat {
    fun unquote(s:String):String = runCatching { org.json.JSONTokener(s).nextValue() as String }.getOrDefault(s)
}