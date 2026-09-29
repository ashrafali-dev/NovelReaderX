package com.ashraf.novelreader

import org.json.JSONObject

object NovelJs {
    fun extract() = WebNovelAdapter.extractScript()

    fun insertTranslation(text:String):String {
        val q=JSONObject.quote(text)
        return """
(function(){
  let text=$q;
  const clean=s=>(s||'').replace(/\u00a0/g,' ').trim();
  text=text.replace(/^\s*(?:ChatGPT|Gemini|Claude|DeepSeek|Grok)\s+said\s*[:：]?\s*/i,'').trim();

  let el=window.__nrContentElement;
  if(!el || !document.contains(el)){
    const remembered=window.__nrContentSelector;
    if(remembered){try{el=document.querySelector(remembered)}catch(_){}}
  }
  if(!el)return 'not-found';

  const normalized=text.replace(/\r/g,'').replace(/[ \t]+\n/g,'\n').trim();
  const parts=normalized.split(/\n\s*\n+/).map(x=>x.trim()).filter(Boolean);
  const finalParts=[];
  for(const part of parts){
    const lines=part.split(/\n+/).map(x=>x.trim()).filter(Boolean);
    if(lines.length>1 && lines.every(x=>x.length>1)) finalParts.push(...lines);
    else finalParts.push(part);
  }
  if(!finalParts.length)return 'empty';

  const skip='SCRIPT,STYLE,NOSCRIPT,SVG,IMG,VIDEO,AUDIO,CANVAS,BUTTON,A,INPUT,SELECT,TEXTAREA,[role="button"],[onclick],[contenteditable="true"],.j_readTool,.j_catalog_list';
  const blocks=[];
  const seen=new Set();
  const candidates=[...el.querySelectorAll('p,blockquote,li')];
  for(const e of candidates){
    if(seen.has(e))continue;
    try{if(e.matches(skip)||e.closest(skip))continue}catch(_){}
    const t=clean(e.textContent||'');
    if(t.length<2)continue;
    if(e.querySelector('p,blockquote,li'))continue;
    blocks.push(e); seen.add(e);
  }
  if(!blocks.length){
    const walker=document.createTreeWalker(el,NodeFilter.SHOW_TEXT);
    let n;
    while(n=walker.nextNode()){
      if(!clean(n.nodeValue))continue;
      const p=n.parentElement;
      if(!p)continue;
      try{if(p.closest(skip))continue}catch(_){}
      if(!seen.has(p)){blocks.push(p);seen.add(p)}
    }
  }
  if(!blocks.length)return 'no-targets';

  const originals=blocks.map(e=>e.textContent||'');
  window.__nrTranslationState={element:el,blocks:blocks,originals:originals,translated:[],active:true};

  const translated=[];
  const count=blocks.length;
  if(finalParts.length===count){
    for(let i=0;i<count;i++) translated.push(finalParts[i]);
  }else if(count===1){
    translated.push(finalParts.join('\n\n'));
  }else{
    for(let i=0;i<count;i++){
      const start=Math.floor(i*finalParts.length/count);
      const end=Math.max(start+1,Math.floor((i+1)*finalParts.length/count));
      translated.push(finalParts.slice(start,end).join('\n\n'));
    }
  }

  for(let i=0;i<blocks.length;i++) blocks[i].textContent=translated[i]||'';
  window.__nrTranslationState.translated=translated;

  let host=document.getElementById('novelreaderx-toggle-host');
  if(!host){
    host=document.createElement('div');
    host.id='novelreaderx-toggle-host';
    host.style.cssText='position:fixed;right:12px;top:12px;z-index:2147483647;';
    document.documentElement.appendChild(host);
    const shadow=host.attachShadow({mode:'open'});
    const style=document.createElement('style');
    style.textContent='button{border:0;border-radius:18px;padding:7px 12px;background:rgba(20,20,24,.88);color:#fff;font:600 13px system-ui,sans-serif;box-shadow:0 2px 10px rgba(0,0,0,.28);cursor:pointer}button:active{transform:scale(.96)}';
    const b=document.createElement('button');
    b.id='b'; b.type='button'; b.textContent='বাংলা';
    shadow.appendChild(style); shadow.appendChild(b);
    b.addEventListener('click',()=>{
      const s=window.__nrTranslationState;
      if(!s||!s.blocks?.length)return;
      s.active=!s.active;
      for(let i=0;i<s.blocks.length;i++) s.blocks[i].textContent=(s.active?s.translated[i]:s.originals[i])||'';
      b.textContent=s.active?'English':'বাংলা';
    });
  }
  const button=host.shadowRoot?.getElementById('b');
  if(button)button.textContent='English';

  window.__nrContentElement=el;
  window.__nrTranslatedElement=el;
  return 'inserted';
})()
""".trimIndent()
    }

    fun restoreOriginal():String = """
(()=>{const s=window.__nrTranslationState;
if(!s||!s.blocks?.length)return 'none';
for(let i=0;i<s.blocks.length;i++)s.blocks[i].textContent=s.originals[i]||'';
s.active=false;
const b=document.getElementById('novelreaderx-toggle-host')?.shadowRoot?.getElementById('b');
if(b)b.textContent='বাংলা';
return 'restored';})()
""".trimIndent()

    fun siteNext(dir:String)= """
(()=>{const next=${dir=="next"};const els=[...document.querySelectorAll('a[href],button,[role="button"],mov-button')];const re=next?/next|next chapter|continue/i:/prev|previous|previous chapter/i;let best=null,score=0;for(const e of els){const s=[e.innerText,e.getAttribute('aria-label'),e.getAttribute('title'),e.id,e.className].join(' ');if(!re.test(s))continue;const r=e.getBoundingClientRect();if(r.width<2||r.height<2)continue;let n=/chapter/i.test(s)?4:1;if(e.tagName==='A'&&e.href)n+=2;if(n>score){score=n;best=e}}if(!best)return '';if(best.href)return best.href;best.click();return 'clicked'})()
""".trimIndent()
}
