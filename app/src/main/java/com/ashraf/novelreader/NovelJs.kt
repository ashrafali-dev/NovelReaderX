package com.ashraf.novelreader

import org.json.JSONObject

object NovelJs {
    fun extract() = WebNovelAdapter.extractScript()

    fun insertTranslation(text:String):String {
        val q=JSONObject.quote(text)
        return """
(function(){
  let text=$q;
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

  const skip='SCRIPT,STYLE,NOSCRIPT,SVG,IMG,VIDEO,AUDIO,CANVAS,BUTTON,INPUT,SELECT,TEXTAREA,[role="button"],[onclick],[contenteditable="true"],.j_readTool,.j_catalog_list';
  const blocks=[];
  const seen=new Set();
  for(const e of el.querySelectorAll('p,blockquote,li')){
    if(seen.has(e))continue;
    try{if(e.matches(skip)||e.closest(skip))continue}catch(_){}
    const t=(e.textContent||'').replace(/\u00a0/g,' ').trim();
    if(t.length<2)continue;
    if(e.querySelector('p,blockquote,li'))continue;
    blocks.push(e);seen.add(e);
  }
  if(!blocks.length){
    const walker=document.createTreeWalker(el,NodeFilter.SHOW_TEXT);
    let n;
    while(n=walker.nextNode()){
      if(!(n.nodeValue||'').trim())continue;
      const p=n.parentElement;
      if(!p)continue;
      try{if(p.matches(skip)||p.closest(skip))continue}catch(_){}
      if(!seen.has(p)){blocks.push(p);seen.add(p)}
    }
  }
  if(!blocks.length)return 'no-targets';

  const nodeSets=[];
  const originals=[];
  for(const block of blocks){
    const nodes=[];
    const walker=document.createTreeWalker(block,NodeFilter.SHOW_TEXT);
    let n;
    while(n=walker.nextNode()){
      const p=n.parentElement;
      if(!p)continue;
      try{if(p.matches(skip)||p.closest(skip))continue}catch(_){}
      if((n.nodeValue||'').length)nodes.push(n);
    }
    if(!nodes.length)continue;
    nodeSets.push(nodes);
    originals.push(nodes.map(n=>n.nodeValue||''));
  }
  if(!nodeSets.length)return 'no-text-nodes';

  const usableCount=nodeSets.length;
  const translated=[];
  if(finalParts.length===usableCount){
    for(let i=0;i<usableCount;i++)translated.push(finalParts[i]);
  }else if(usableCount===1){
    translated.push(finalParts.join('\n\n'));
  }else{
    for(let i=0;i<usableCount;i++){
      const start=Math.floor(i*finalParts.length/usableCount);
      const end=Math.max(start+1,Math.floor((i+1)*finalParts.length/usableCount));
      translated.push(finalParts.slice(start,end).join('\n\n'));
    }
  }

  function splitForNodes(value,nodes){
    if(nodes.length===1)return [value];
    const weights=nodes.map(n=>Math.max(1,(n.nodeValue||'').length));
    const total=weights.reduce((a,b)=>a+b,0);
    const out=[];let from=0,weightSum=0;
    for(let i=0;i<nodes.length;i++){
      if(i===nodes.length-1){out.push(value.slice(from));break}
      weightSum+=weights[i];
      let target=Math.round(value.length*weightSum/total);
      if(target>from && target<value.length){
        const windowStart=Math.max(from,target-18),windowEnd=Math.min(value.length,target+18);
        let cut=target;
        for(let j=target;j>=windowStart;j--){if(/\s/.test(value[j])){cut=j;break}}
        if(cut===target){
          for(let j=target;j<=windowEnd;j++){if(/\s/.test(value[j])){cut=j;break}}
        }
        target=cut;
      }
      out.push(value.slice(from,target).trimStart());
      from=target;
    }
    while(out.length<nodes.length)out.push('');
    return out;
  }

  function applyTranslated(){
    for(let i=0;i<nodeSets.length;i++){
      const vals=splitForNodes(translated[i]||'',nodeSets[i]);
      for(let j=0;j<nodeSets[i].length;j++)nodeSets[i][j].nodeValue=vals[j]||'';
    }
  }
  function applyOriginal(){
    for(let i=0;i<nodeSets.length;i++){
      for(let j=0;j<nodeSets[i].length;j++)nodeSets[i][j].nodeValue=originals[i][j]||'';
    }
  }

  window.__nrTranslationState={element:el,nodeSets:nodeSets,originals:originals,translated:translated,active:true};
  applyTranslated();

  let host=document.getElementById('novelreaderx-toggle-host');
  if(!host){
    host=document.createElement('div');
    host.id='novelreaderx-toggle-host';
    host.style.cssText='position:fixed;right:12px;top:12px;z-index:2147483647;';
    document.documentElement.appendChild(host);
    const shadow=host.attachShadow({mode:'open'});
    const style=document.createElement('style');
    style.textContent='button{border:0;border-radius:18px;padding:7px 12px;background:rgba(20,20,24,.88);color:#fff;font:600 13px system-ui,sans-serif;box-shadow:0 2px 10px rgba(0,0,0,.28);cursor:pointer}button:active{transform:scale(.96)}';
    const btn=document.createElement('button');
    btn.id='b';btn.type='button';btn.textContent='English';
    shadow.appendChild(style);shadow.appendChild(btn);
    btn.addEventListener('click',()=>{
      const s=window.__nrTranslationState;
      if(!s||!s.nodeSets?.length)return;
      s.active=!s.active;
      if(s.active){
        for(let i=0;i<s.nodeSets.length;i++){
          const vals=splitForNodes(s.translated[i]||'',s.nodeSets[i]);
          for(let j=0;j<s.nodeSets[i].length;j++)s.nodeSets[i][j].nodeValue=vals[j]||'';
        }
      }else{
        for(let i=0;i<s.nodeSets.length;i++){
          for(let j=0;j<s.nodeSets[i].length;j++)s.nodeSets[i][j].nodeValue=s.originals[i][j]||'';
        }
      }
      btn.textContent=s.active?'English':'বাংলা';
    });
  }
  const btn=host.shadowRoot?.getElementById('b');
  if(btn)btn.textContent='English';
  window.__nrContentElement=el;
  window.__nrTranslatedElement=el;
  return 'inserted';
})()
""".trimIndent()

    fun restoreOriginal():String = """
(()=>{const s=window.__nrTranslationState;
if(!s||!s.nodeSets?.length)return 'none';
for(let i=0;i<s.nodeSets.length;i++)
  for(let j=0;j<s.nodeSets[i].length;j++)
    s.nodeSets[i][j].nodeValue=s.originals[i][j]||'';
s.active=false;
const b=document.getElementById('novelreaderx-toggle-host')?.shadowRoot?.getElementById('b');
if(b)b.textContent='বাংলা';
return 'restored';})()
""".trimIndent()

    fun siteNext(dir:String)= """
(()=>{const next=${dir=="next"};const els=[...document.querySelectorAll('a[href],button,[role="button"],mov-button')];const re=next?/next|next chapter|continue/i:/prev|previous|previous chapter/i;let best=null,score=0;for(const e of els){const s=[e.innerText,e.getAttribute('aria-label'),e.getAttribute('title'),e.id,e.className].join(' ');if(!re.test(s))continue;const r=e.getBoundingClientRect();if(r.width<2||r.height<2)continue;let n=/chapter/i.test(s)?4:1;if(e.tagName==='A'&&e.href)n+=2;if(n>score){score=n;best=e}}if(!best)return '';if(best.href)return best.href;best.click();return 'clicked'})()
""".trimIndent()
}
