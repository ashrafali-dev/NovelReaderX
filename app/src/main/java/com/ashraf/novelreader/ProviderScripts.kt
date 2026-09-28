package com.ashraf.novelreader

import android.net.Uri
import org.json.JSONObject

object ProviderScripts {
    fun hostMatches(provider: AiProvider, url: String): Boolean = runCatching {
        val h=Uri.parse(url).host?.lowercase() ?: return false
        h==provider.host || h.endsWith(".${provider.host}")
    }.getOrDefault(false)

    fun inputAndSend(provider: AiProvider, text: String): String {
        val q=JSONObject.quote(text)
        return when(provider){
            AiProvider.CHATGPT -> robust(q, "chatgpt")
            AiProvider.GEMINI -> robust(q, "gemini")
            AiProvider.DEEPSEEK -> robust(q, "other")
            AiProvider.CLAUDE -> robust(q, "other")
            AiProvider.GROK -> robust(q, "grok")
        }
    }

    private fun robust(q:String, site:String):String = """
(function(){
 const text=$q;
 const vis=e=>{if(!e)return false;const r=e.getBoundingClientRect();return r.width>2&&r.height>2&&getComputedStyle(e).visibility!=='hidden'&&getComputedStyle(e).display!=='none'};
 const boxes=[...document.querySelectorAll('#prompt-textarea,textarea,input,div[contenteditable="true"],div[contenteditable="plaintext-only"],[role="textbox"]')].filter(vis);
 boxes.sort((a,b)=>b.getBoundingClientRect().bottom-a.getBoundingClientRect().bottom);
 const box=boxes[0];
 if(!box)return 'nobox';
 const clear=()=>{
   box.focus();
   try{
     if(box.matches('textarea,input')){
       const proto=box.matches('textarea')?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
       Object.getOwnPropertyDescriptor(proto,'value').set.call(box,'');
     }else{
       const sel=window.getSelection(),range=document.createRange();
       range.selectNodeContents(box);sel.removeAllRanges();sel.addRange(range);
       document.execCommand('delete',false,null);box.innerHTML='';
     }
   }catch(e){try{box.value='';box.textContent=''}catch(_){}}
   box.dispatchEvent(new Event('input',{bubbles:true}));
   box.dispatchEvent(new Event('change',{bubbles:true}));
 };
 clear(); box.focus();
 if(box.matches('textarea,input')){
   const proto=box.matches('textarea')?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
   Object.getOwnPropertyDescriptor(proto,'value').set.call(box,text);
 }else{
   const sel=window.getSelection(),range=document.createRange();
   range.selectNodeContents(box);sel.removeAllRanges();sel.addRange(range);
   document.execCommand('insertText',false,text);
   if(!(box.innerText||'').trim())box.textContent=text;
   try{box.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text}))}catch(_){}
 }
 box.dispatchEvent(new Event('input',{bubbles:true}));
 box.dispatchEvent(new Event('change',{bubbles:true}));
 setTimeout(()=>{
   let btn=null;
   const sendSelectors=site==='chatgpt'?"#prompt-textarea + button,button[data-testid=\"send-button\"],button[aria-label*=\"Send\" i],button[type=\"submit\"]":site==='gemini'?"button[aria-label*=\"Send\" i],button.send-button,button[mattooltip*=\"Send\" i]":site==='grok'?"button[type=\"submit\"],button[aria-label*=\"Submit\" i],button[aria-label*=\"Send\" i]":"button[aria-label*=\"Send\" i],button[data-testid*=\"send\" i],button[type=\"submit\"]";
   try{btn=[...document.querySelectorAll(sendSelectors)].find(x=>vis(x)&&!x.disabled&&x.getAttribute('aria-disabled')!=='true')}catch(_){}
   if(btn)btn.click();
   else{
     box.focus();
     box.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));
     box.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));
   }
   setTimeout(()=>{const cur=box.matches('textarea,input')?box.value:(box.innerText||'');if(cur.trim()===text.trim())clear()},1200);
 },150);
 return 'sent';
})()
""".trimIndent()
    }
    fun clearComposerScript(): String = """
(()=>{const boxes=[...document.querySelectorAll('#prompt-textarea,textarea,input,div[contenteditable="true"],[role="textbox"]')];boxes.forEach(b=>{try{b.focus();if(b.matches('textarea,input')){const p=Object.getOwnPropertyDescriptor(b.matches('textarea')?HTMLTextAreaElement.prototype:HTMLInputElement.prototype,'value');p.set.call(b,'')}else{document.execCommand('selectAll');document.execCommand('delete');b.innerHTML='';}b.dispatchEvent(new Event('input',{bubbles:true}));b.dispatchEvent(new Event('change',{bubbles:true}));}catch(_){}});return 'cleared'})()
""".trimIndent()

    fun responseTextScript(provider: AiProvider): String = """
(()=>{
 const sels={
  CHATGPT:'[data-message-author-role="assistant"],[data-message-role="assistant"],article[data-turn="assistant"],.markdown',
  GEMINI:'model-response,.model-response-text,message-content,.markdown',
  DEEPSEEK:'.ds-markdown',
  CLAUDE:'.font-claude-message,[data-testid="assistant-message"],.prose',
  GROK:'[class*="message-bubble"],[class*="response-content-markdown"],.markdown'
 };
 const q=sels['${provider.name}']||'[data-message-author-role="assistant"],.markdown,.prose';
 const a=[...document.querySelectorAll(q)].filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.height>0&&(e.innerText||e.textContent||'').trim().length>0});
 if(!a.length)return '';
 let e=a[a.length-1];
 const inner=e.querySelector&&e.querySelector('.markdown,.prose,[class*="markdown"]');
 return (inner||e).innerText?.trim()||'';
})()
""".trimIndent()

}