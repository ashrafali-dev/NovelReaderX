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
            AiProvider.GEMINI -> gemini(q)
            AiProvider.CHATGPT -> chatgpt(q)
            AiProvider.DEEPSEEK -> generic(q, listOf("textarea","[contenteditable=\"true\"]","[role=\"textbox\"]"), listOf("button[aria-label*='Send' i]","button[data-testid*='send' i]"))
            AiProvider.CLAUDE -> generic(q, listOf("[contenteditable=\"true\"]","textarea","[role=\"textbox\"]"), listOf("button[aria-label*='Send' i]","button[type='submit']"))
            AiProvider.GROK -> generic(q, listOf("textarea","[contenteditable=\"true\"]","[role=\"textbox\"]"), listOf("button[aria-label*='Send' i]","button[type='submit']"))
        }
    }

    private fun generic(q:String, inputs:List<String>, sends:List<String>) = """
(function(){
 const text=$q;
 const vis=e=>{if(!e)return false;const r=e.getBoundingClientRect();return r.width>2&&r.height>2};
 let box=null; for(const s of ${JSONObject.quote(inputs.joinToString("\u0000"))}.split('\u0000')){try{box=[...document.querySelectorAll(s)].find(vis);if(box)break}catch(e){}}
 if(!box)return 'nobox';
 if(box.matches('textarea,input')){const p=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')||Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');try{p.set.call(box,text)}catch(e){box.value=text}}
 else{box.focus();document.execCommand('selectAll');document.execCommand('insertText',false,text);if(!(box.innerText||'').trim())box.textContent=text}
 box.dispatchEvent(new Event('input',{bubbles:true}));box.dispatchEvent(new Event('change',{bubbles:true}));
 setTimeout(()=>{let b=null;for(const s of ${JSONObject.quote(sends.joinToString("\u0000"))}.split('\u0000')){try{b=[...document.querySelectorAll(s)].find(x=>vis(x)&&!x.disabled&&x.getAttribute('aria-disabled')!=='true');if(b)break}catch(e){}}if(b)b.click();else box.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));},20);
 return 'sent';
})()
""".trimIndent()

    private fun chatgpt(q:String) = """
(function(){const text=$q;const vis=e=>{if(!e)return false;const r=e.getBoundingClientRect();return r.width>2&&r.height>2};let b=[...document.querySelectorAll('#prompt-textarea,textarea,[contenteditable=\"true\"],[role=\"textbox\"]')].find(vis);if(!b)return 'nobox';b.focus();if(b.matches('textarea')){const p=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');p.set.call(b,text)}else{document.execCommand('selectAll');document.execCommand('insertText',false,text);if(!(b.innerText||'').trim())b.textContent=text}b.dispatchEvent(new Event('input',{bubbles:true}));b.dispatchEvent(new Event('change',{bubbles:true}));setTimeout(()=>{let s=[...document.querySelectorAll('button')].find(x=>vis(x)&&!x.disabled&&(x.getAttribute('aria-label')||'').match(/send/i));if(s)s.click();else b.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}))},10);return 'sent'})()
""".trimIndent()

    private fun gemini(q:String) = """
(function(){const text=$q;const vis=e=>{if(!e)return false;const r=e.getBoundingClientRect();return r.width>2&&r.height>2};let b=[...document.querySelectorAll('rich-textarea .ql-editor,rich-textarea [contenteditable=\"true\"],div.ql-editor[contenteditable=\"true\"],[aria-label="Enter a prompt here"],[contenteditable=\"true\"][role=\"textbox\"],textarea,[role=\"textbox\"]')].find(vis);if(!b)return 'nobox';b.focus();if(b.matches('textarea')){const p=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');p.set.call(b,text)}else{document.execCommand('selectAll');document.execCommand('insertText',false,text);if(!(b.innerText||b.textContent||'').trim())b.textContent=text}b.dispatchEvent(new Event('input',{bubbles:true}));b.dispatchEvent(new Event('change',{bubbles:true}));setTimeout(()=>{let s=[...document.querySelectorAll('button')].find(x=>vis(x)&&!x.disabled&&(x.getAttribute('aria-label')||'').match(/send|submit/i));if(s)s.click();else b.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}))},10);return 'sent'})()
""".trimIndent()

    fun responseTextScript(provider: AiProvider): String = when(provider) {
        AiProvider.GEMINI -> """(()=>{const a=[...document.querySelectorAll('model-response,message-content,.model-response-text,.response-content')];return (a.at(-1)?.innerText||'').trim()})()"""
        AiProvider.CHATGPT -> """(()=>{const a=[...document.querySelectorAll('[data-message-author-role="assistant"],div.markdown')];return (a.at(-1)?.innerText||'').trim()})()"""
        AiProvider.DEEPSEEK -> """(()=>{const a=[...document.querySelectorAll('.ds-markdown,.markdown,div[class*="markdown"]')];return (a.at(-1)?.innerText||'').trim()})()"""
        AiProvider.CLAUDE -> """(()=>{const a=[...document.querySelectorAll('[data-is-streaming],.font-claude-message,.prose')];return (a.at(-1)?.innerText||'').trim()})()"""
        AiProvider.GROK -> """(()=>{const a=[...document.querySelectorAll('[data-testid*="message"],.message-bubble,.markdown')];return (a.at(-1)?.innerText||'').trim()})()"""
    }
}