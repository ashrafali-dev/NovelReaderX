package com.ashraf.novelreader

import android.net.Uri
import org.json.JSONObject

object ProviderScripts {
    fun hostMatches(provider: AiProvider, url: String): Boolean = runCatching {
        val h = Uri.parse(url).host?.lowercase() ?: return false
        h == provider.host || h.endsWith(".${provider.host}")
    }.getOrDefault(false)

    fun inputAndSend(provider: AiProvider, text: String): String {
        val q = JSONObject.quote(text)
        val selector = when (provider) {
            AiProvider.CHATGPT -> "#prompt-textarea + button,button[data-testid='send-button'],button[aria-label*='Send' i],button[type='submit']"
            AiProvider.GEMINI -> "button[aria-label*='Send' i],button.send-button,button[mattooltip*='Send' i]"
            AiProvider.GROK -> "button[type='submit'],button[aria-label*='Submit' i],button[aria-label*='Send' i]"
            else -> "button[aria-label*='Send' i],button[data-testid*='send' i],button[type='submit']"
        }
        return """
(function(){
 const text=$q;
 const sendSelector=${JSONObject.quote(selector)};
 const visible=function(e){
   if(!e)return false;
   var r=e.getBoundingClientRect(),s=getComputedStyle(e);
   return r.width>2&&r.height>2&&s.display!=='none'&&s.visibility!=='hidden';
 };
 var boxes=[].slice.call(document.querySelectorAll('#prompt-textarea,textarea,input,div[contenteditable="true"],div[contenteditable="plaintext-only"],[role="textbox"]')).filter(visible);
 boxes.sort(function(a,b){return b.getBoundingClientRect().bottom-a.getBoundingClientRect().bottom});
 var box=boxes[0];
 if(!box)return 'nobox';
 function clearBox(){
   box.focus();
   try{
     if(box.matches('textarea,input')){
       var p=box.matches('textarea')?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
       Object.getOwnPropertyDescriptor(p,'value').set.call(box,'');
     }else{
       var sel=window.getSelection(),range=document.createRange();
       range.selectNodeContents(box);sel.removeAllRanges();sel.addRange(range);
       document.execCommand('delete',false,null);box.innerHTML='';
     }
   }catch(e){}
   box.dispatchEvent(new Event('input',{bubbles:true}));
   box.dispatchEvent(new Event('change',{bubbles:true}));
 }
 clearBox(); box.focus();
 if(box.matches('textarea,input')){
   var p=box.matches('textarea')?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
   Object.getOwnPropertyDescriptor(p,'value').set.call(box,text);
 }else{
   var sel=window.getSelection(),range=document.createRange();
   range.selectNodeContents(box);sel.removeAllRanges();sel.addRange(range);
   document.execCommand('insertText',false,text);
   if(!(box.innerText||'').trim())box.textContent=text;
 }
 box.dispatchEvent(new Event('input',{bubbles:true}));
 box.dispatchEvent(new Event('change',{bubbles:true}));
 setTimeout(function(){
   var btn=null;
   try{btn=[].slice.call(document.querySelectorAll(sendSelector)).find(function(x){return visible(x)&&!x.disabled&&x.getAttribute('aria-disabled')!=='true';});}catch(e){}
   if(btn)btn.click();
   else{
     box.focus();
     box.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));
     box.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));
   }
   setTimeout(function(){
     var cur=box.matches('textarea,input')?box.value:(box.innerText||'');
     if(cur.trim()===text.trim())clearBox();
   },1200);
 },150);
 return 'sent';
})()
""".trimIndent()
    }

    fun clearComposerScript(): String = """
(function(){
 var boxes=[].slice.call(document.querySelectorAll('#prompt-textarea,textarea,input,div[contenteditable="true"],[role="textbox"]'));
 boxes.forEach(function(b){
   try{
     b.focus();
     if(b.matches('textarea,input')){
       var p=b.matches('textarea')?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
       Object.getOwnPropertyDescriptor(p,'value').set.call(b,'');
     }else{
       var s=window.getSelection(),r=document.createRange();
       r.selectNodeContents(b);s.removeAllRanges();s.addRange(r);
       document.execCommand('delete',false,null);b.innerHTML='';
     }
     b.dispatchEvent(new Event('input',{bubbles:true}));
     b.dispatchEvent(new Event('change',{bubbles:true}));
   }catch(e){}
 });
 return 'cleared';
})()
""".trimIndent()

    fun responseTextScript(provider: AiProvider): String {
        val selector = when (provider) {
            AiProvider.CHATGPT -> "[data-message-author-role='assistant'],[data-message-role='assistant'],article[data-turn='assistant'],.markdown"
            AiProvider.GEMINI -> "model-response,.model-response-text,message-content,.markdown"
            AiProvider.DEEPSEEK -> ".ds-markdown"
            AiProvider.CLAUDE -> ".font-claude-message,[data-testid='assistant-message'],.prose"
            AiProvider.GROK -> "[class*='message-bubble'],[class*='response-content-markdown'],.markdown"
        }
        return """
(function(){
 var a=[].slice.call(document.querySelectorAll(\${JSONObject.quote(selector)})).filter(function(e){
   var r=e.getBoundingClientRect();
   return r.width>0&&r.height>0&&(e.innerText||e.textContent||'').trim().length>0;
 });
 if(!a.length)return '';
 var e=a[a.length-1];
 var inner=e.querySelector?e.querySelector('.markdown,.prose,[class*="markdown"]'):null;
 return (inner||e).innerText.trim();
})()
""".trimIndent()
    }
}
