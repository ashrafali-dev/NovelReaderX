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

  // Remove provider UI labels, never show them in the reader.
  text=text.replace(/^\s*(?:ChatGPT|Gemini|Claude|DeepSeek|Grok)\s+said\s*[:：]?\s*/i,'').trim();

  let el=window.__nrContentElement;
  if(!el || !document.contains(el)){
    const remembered=window.__nrContentSelector;
    if(remembered){try{el=document.querySelector(remembered)}catch(_){}}
  }
  if(!el)return 'not-found';

  if(el.getAttribute('data-novelreaderx-translated')==='1' &&
     window.__nrOriginalElement===el && window.__nrOriginalHtml){
    el.innerHTML=window.__nrOriginalHtml;
    el.removeAttribute('data-novelreaderx-translated');
  }

  window.__nrOriginalHtml=el.innerHTML;
  window.__nrOriginalElement=el;

  const normalized=text.replace(/\r/g,'').replace(/[ \t]+\n/g,'\n').trim();
  const parts=normalized.split(/\n\s*\n+/).map(x=>x.trim()).filter(Boolean);
  const finalParts=[];
  for(const part of parts){
    const lines=part.split(/\n+/).map(x=>x.trim()).filter(Boolean);
    if(lines.length>1 && lines.every(x=>x.length>1)) finalParts.push(...lines);
    else finalParts.push(part);
  }
  if(!finalParts.length)return 'empty';

  // Hide only the site's ordinary text nodes. Links/buttons/forms and all
  // their ancestors remain in the real DOM, so the original page stays
  // interactive instead of becoming a fake HTML reader.
  const skip='SCRIPT,STYLE,NOSCRIPT,SVG,IMG,VIDEO,AUDIO,CANVAS,BUTTON,A,INPUT,SELECT,TEXTAREA,[role="button"],[onclick],[contenteditable="true"],.j_readTool,.j_catalog_list';
  const walker=document.createTreeWalker(el,NodeFilter.SHOW_TEXT);
  const nodes=[];
  let n;
  while(n=walker.nextNode()){
    if(!clean(n.nodeValue))continue;
    const p=n.parentElement;
    if(!p)continue;
    try{if(p.closest(skip))continue}catch(_){}
    nodes.push(n);
  }
  nodes.forEach(x=>{
    const s=document.createElement('span');
    s.setAttribute('data-nr-hidden-original','1');
    s.style.display='none';
    x.parentNode.insertBefore(s,x);
    s.appendChild(x);
  });

  // Collapse now-empty text-only blocks, but never collapse a block that
  // contains a link/button/control. This preserves the site's own controls.
  [...el.querySelectorAll('p,div,section,article,li')].reverse().forEach(e=>{
    if(e===el || e.hasAttribute('data-nr-translation'))return;
    const visibleText=clean(e.innerText||'');
    const interactive=e.querySelector('a,button,[role="button"],input,select,textarea,.j_readTool,.j_catalog_list');
    if(!interactive && !visibleText){
      const cs=getComputedStyle(e);
      if(cs.position!=='fixed' && cs.position!=='sticky') e.style.display='none';
    }
  });

  const trans=document.createElement('div');
  trans.setAttribute('data-nr-translation','1');
  trans.style.boxSizing='border-box';
  trans.style.display='block';
  trans.style.width='100%';
  trans.style.maxWidth='100%';
  trans.style.paddingLeft='18px';
  trans.style.paddingRight='18px';
  trans.style.marginLeft='0';
  trans.style.marginRight='0';
  trans.style.textAlign='left';

  finalParts.forEach((part,index)=>{
    const p=document.createElement('p');
    p.textContent=part;
    p.style.boxSizing='border-box';
    p.style.width='100%';
    p.style.margin='0 0 1em 0';
    p.style.padding='0';
    p.style.textAlign='left';
    if(index===0)p.className='nr-title';
    trans.appendChild(p);
  });

  // Do not scroll or replace the site shell. Insert translation into the
  // actual chapter container and leave its navigation/buttons in place.
  el.insertBefore(trans,el.firstChild);
  el.setAttribute('data-novelreaderx-translated','1');
  window.__nrTranslatedElement=trans;
  window.__nrContentElement=el;
  return 'inserted';
})()
""".trimIndent()
    }


    fun restoreOriginal():String = """
(()=>{const e=window.__nrOriginalElement,h=window.__nrOriginalHtml;
if(!e||!h||!document.contains(e))return 'none';
e.innerHTML=h;e.removeAttribute('data-novelreaderx-translated');return 'restored';})()
""".trimIndent()

    fun siteNext(dir:String)= """
(()=>{const next=${dir=="next"};const els=[...document.querySelectorAll('a[href],button,[role="button"],mov-button')];const re=next?/next|next chapter|continue/i:/prev|previous|previous chapter/i;let best=null,score=0;for(const e of els){const s=[e.innerText,e.getAttribute('aria-label'),e.getAttribute('title'),e.id,e.className].join(' ');if(!re.test(s))continue;const r=e.getBoundingClientRect();if(r.width<2||r.height<2)continue;let n=/chapter/i.test(s)?4:1;if(e.tagName==='A'&&e.href)n+=2;if(n>score){score=n;best=e}}if(!best)return '';if(best.href)return best.href;best.click();return 'clicked'})()
""".trimIndent()
}