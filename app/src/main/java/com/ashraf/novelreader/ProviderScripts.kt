package com.ashraf.novelreader

import android.net.Uri
import org.json.JSONObject

object ProviderScripts {
    private const val PRELUDE = """
var __nrProfiles={
 'chatgpt.com':{a:'[data-message-author-role="assistant"],[data-message-role="assistant"],article[data-turn="assistant"]',b:'.markdown',stop:'[data-testid="stop-button"],button[aria-label*="Stop" i]',send:'[data-testid="send-button"],button[aria-label*="Send" i]'},
 'chat.openai.com':{a:'[data-message-author-role="assistant"],[data-message-role="assistant"],article[data-turn="assistant"]',b:'.markdown',stop:'[data-testid="stop-button"],button[aria-label*="Stop" i]',send:'[data-testid="send-button"],button[aria-label*="Send" i]'},
 'gemini.google.com':{a:'model-response,.model-response-text,message-content',b:'.markdown',stop:'button[aria-label*="Stop" i]',send:'button[aria-label*="Send" i],button.send-button'},
 'claude.ai':{a:'.font-claude-message,[data-testid="assistant-message"]',b:'',stop:'button[aria-label*="Stop" i],[data-is-streaming="true"]',send:'button[aria-label*="Send" i]'},
 'deepseek.com':{a:'.ds-markdown',b:'',stop:'',send:'button[aria-label*="Send" i],button[type="submit"]'},
 'grok.com':{a:'[class*="message-bubble"],[class*="response-content-markdown"]',b:'',stop:'button[aria-label*="Stop" i]',send:'button[type="submit"],button[aria-label*="Submit" i]'}
};
var __nrDefault={a:'[data-message-author-role="assistant"],.markdown,.prose',b:'',stop:'button[aria-label*="Stop" i]',send:'button[aria-label*="Send" i],button[type="submit"]'};
function __nrProfile(){
 var h=location.hostname.toLowerCase();
 for(var k in __nrProfiles) if(h===k||h.endsWith('.'+k)) return __nrProfiles[k];
 return __nrDefault;
}
function __nrVisible(e){
 if(!e)return false;
 var r=e.getBoundingClientRect(),s=getComputedStyle(e);
 return r.width>2&&r.height>2&&s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0';
}
function __nrAssistants(p){
 var s=[
  '[data-message-author-role="assistant"]','[data-message-role="assistant"]',
  '[data-message-author="assistant"]','[data-role="assistant"]',
  'article[data-turn="assistant"]','section[data-turn="assistant"]',
  '[data-testid^="conversation-turn-"][data-turn="assistant"]',
  '.agent-turn',p.a,'[data-testid*="assistant" i]','model-response',
  '.font-claude-message','.ds-markdown','message-content',
  '[class*="response-content" i]','[class*="assistant-message" i]'
 ].filter(Boolean).join(',');
 var all=[].slice.call(document.querySelectorAll(s));
 return all.filter(function(e){
   var t=(e.innerText||e.textContent||'').trim();
   return __nrVisible(e)&&t.length>0&&!all.some(function(o){return o!==e&&o.contains(e);});
 });
}
function __nrReply(p){
 var all=__nrAssistants(p);
 if(all.length){
   var e=all[all.length-1];
   var inner=e.querySelector&&e.querySelector('.markdown,.prose,[class*="markdown"],[class*="prose"]');
   return inner||e;
 }
 return null;
}
function __nrBox(){
 var all=[].slice.call(document.querySelectorAll(
   '#prompt-textarea,textarea,input,div[contenteditable="true"],div[contenteditable="plaintext-only"],[role="textbox"]'
 )).filter(__nrVisible);
 all.sort(function(a,b){return b.getBoundingClientRect().bottom-a.getBoundingClientRect().bottom;});
 return all[0]||null;
}
"""

    private fun run(body:String)=PRELUDE+"\n;"+body

    fun hostMatches(provider:AiProvider,url:String):Boolean=runCatching{
        val h=Uri.parse(url).host?.lowercase() ?: return false
        h==provider.host || h.endsWith(".${provider.host}")
    }.getOrDefault(false)

    fun send(provider:AiProvider,text:String,doSend:Boolean):String{
        val q=JSONObject.quote(text)
        return run("""
(function(text,doSend){
 var p=__nrProfile();
 var before=__nrAssistants(p);
 var n0=before.length;
 var old=__nrReply(p);
 var len0=old?((old.innerText||old.textContent||'').trim().length):0;
 var box=__nrBox();
 if(!box)return 'nobox';

 box.focus();
 if(box.tagName==='TEXTAREA'||box.tagName==='INPUT'){
   var proto=box.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
   var setter=Object.getOwnPropertyDescriptor(proto,'value').set;
   setter.call(box,text);
 }else{
   var sel=window.getSelection(),range=document.createRange();
   range.selectNodeContents(box);sel.removeAllRanges();sel.addRange(range);
   document.execCommand('insertText',false,text);
   if(!(box.innerText||'').trim())box.textContent=text;
 }
 box.dispatchEvent(new Event('input',{bubbles:true}));
 try{box.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text}));}catch(e){}
 box.dispatchEvent(new Event('change',{bubbles:true}));

 if(doSend){
   var delay=Math.min(2500,Math.max(900,900+Math.floor(text.length/60)));
   setTimeout(function(){
     if(!document.contains(box))return;
     var btn=null;
     try{
       btn=p.send?document.querySelector(p.send):null;
       if(btn&&!__nrVisible(btn))btn=null;
     }catch(e){btn=null;}
     if(!btn){
       try{
         btn=[].slice.call(document.querySelectorAll(
           'button[data-testid*="send" i],button[aria-label*="send" i],button[aria-label*="submit" i],button[type="submit"]'
         )).find(function(x){return __nrVisible(x)&&!x.disabled&&x.getAttribute('aria-disabled')!=='true';});
       }catch(e){}
     }
     if(btn){btn.click();return;}
     box.focus();
     box.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));
     box.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));
   },delay);
 }
 return 'ok:'+n0+':'+len0;
})(__TEXT__,__SEND__)
""".trimIndent()
            .replace("__TEXT__",q)
            .replace("__SEND__",doSend.toString()))
    }

    fun readLen():String=run("""
(function(){
 var p=__nrProfile(),e=__nrReply(p),l=__nrAssistants(p);
 var len=e?((e.innerText||e.textContent||'').trim().length):0;
 var streaming=(p.stop&&document.querySelector(p.stop))?1:0;
 return l.length+'|'+streaming+'|'+len;
})()
""".trimIndent())

    fun readText():String=run("""
(function(){
 var p=__nrProfile(),e=__nrReply(p);
 if(!e)return '';
 return (e.innerText||e.textContent||'').trim();
})()
""".trimIndent())

    fun stop():String=run("""
(function(){
 var p=__nrProfile(),b=null;
 try{b=p.stop?document.querySelector(p.stop):null}catch(e){}
 if(b&&b.tagName==='BUTTON')b.click();
 return 'ok';
})()
""".trimIndent())

    fun clearComposerScript():String=run("""
(function(){
 var b=__nrBox();
 if(!b)return 'none';
 try{
   b.focus();
   if(b.tagName==='TEXTAREA'||b.tagName==='INPUT'){
     var p=b.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
     Object.getOwnPropertyDescriptor(p,'value').set.call(b,'');
   }else{
     var s=window.getSelection(),r=document.createRange();
     r.selectNodeContents(b);s.removeAllRanges();s.addRange(r);
     document.execCommand('delete',false,null);
     b.innerHTML='';
   }
   b.dispatchEvent(new Event('input',{bubbles:true}));
   b.dispatchEvent(new Event('change',{bubbles:true}));
 }catch(e){}
 return 'cleared';
})()
""".trimIndent())
}
