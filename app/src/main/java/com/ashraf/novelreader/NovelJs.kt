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

  // Strip provider UI labels accidentally included in the copied answer.
  text=text.replace(/^\s*(?:ChatGPT|Gemini|Claude|DeepSeek|Grok)\s+said\s*[:：]?\s*/i,'').trim();

  let el=window.__nrContentElement;
  if(!el || !document.contains(el)){
    const remembered=window.__nrContentSelector;
    if(remembered){try{el=document.querySelector(remembered)}catch(_){}}
  }

  const selectors=[
    '.j_readContent','.cha-content','#chapter-content','.chapter-content','.chapter_content',
    '#chr-content','.chr-c','.reading-content','.chapter-body','.chapter-c','.txt',
    '#chaptercontent','.novel_content','.cha-words','.entry-content','.article-content',
    '#article','.text-left','#content','.content','article'
  ];

  if(!el){
    for(const sel of selectors){
      try{
        const found=[...document.querySelectorAll(sel)].find(e=>{
          const t=clean(e.innerText||e.textContent);
          const ps=e.querySelectorAll('p').length;
          return t.length>=300 && (ps>=2 || sel==='.j_readContent' || sel==='.cha-content');
        });
        if(found){el=found;break;}
      }catch(_){}
    }
  }

  if(!el)return 'not-found';

  // A previous translation may still be present. Restore the real site DOM
  // before applying a fresh translation.
  if(el.getAttribute('data-novelreaderx-translated')==='1' &&
     window.__nrOriginalElement===el && window.__nrOriginalHtml){
    el.innerHTML=window.__nrOriginalHtml;
    el.removeAttribute('data-novelreaderx-translated');
  }

  // Capture the exact site DOM once. Restoration returns this unchanged.
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

  // Preserve the site's existing typography instead of turning the page into
  // a new generic HTML reader.
  let sample=null;
  const sampleCandidates=[...el.querySelectorAll('p,h1,h2,h3,div,span')];
  for(const e of sampleCandidates){
    const t=clean(e.innerText||e.textContent);
    if(t.length>=20){
      const r=e.getBoundingClientRect();
      if(r.width>0 && r.height>0){sample=e;break;}
    }
  }

  // Remove only normal text nodes. Interactive/site controls are skipped and
  // remain exactly where the site placed them.
  const skip='SCRIPT,STYLE,NOSCRIPT,SVG,IMG,VIDEO,AUDIO,CANVAS,BUTTON,A,INPUT,SELECT,TEXTAREA,[role="button"],[onclick],[contenteditable="true"],.j_readTool,.j_catalog_list';
  const walker=document.createTreeWalker(el,NodeFilter.SHOW_TEXT);
  const textNodes=[];
  let n;
  while(n=walker.nextNode()){
    if(!clean(n.nodeValue))continue;
    const p=n.parentElement;
    if(!p)continue;
    try{if(p.closest(skip))continue}catch(_){}
    textNodes.push(n);
  }
  textNodes.forEach(x=>x.nodeValue='');

  const trans=document.createElement('div');
  trans.setAttribute('data-nr-translation','1');
  trans.setAttribute('data-novelreaderx-translated','1');
  trans.style.width='100%';
  trans.style.boxSizing='border-box';
  trans.style.textAlign='left';

  if(sample){
    const cs=getComputedStyle(sample);
    ['fontFamily','fontSize','fontWeight','lineHeight','letterSpacing','color','textAlign']
      .forEach(k=>{if(cs[k])trans.style[k]=cs[k]});
  }

  finalParts.forEach((part,index)=>{
    const p=document.createElement('p');
    p.textContent=part;
    p.style.width='100%';
    p.style.boxSizing='border-box';
    p.style.textAlign='left';
    if(index===0)p.className='nr-title';
    if(sample){
      const cs=getComputedStyle(sample);
      if(cs.marginTop)p.style.marginTop=cs.marginTop;
      if(cs.marginBottom)p.style.marginBottom=cs.marginBottom;
      if(cs.fontFamily)p.style.fontFamily=cs.fontFamily;
      if(cs.fontSize)p.style.fontSize=cs.fontSize;
      if(cs.lineHeight)p.style.lineHeight=cs.lineHeight;
      if(cs.fontWeight)p.style.fontWeight=cs.fontWeight;
      if(cs.letterSpacing)p.style.letterSpacing=cs.letterSpacing;
      if(cs.color)p.style.color=cs.color;
    }else{
      p.style.margin='0 0 1em 0';
      p.style.lineHeight='1.75';
    }
    trans.appendChild(p);
  });

  // Put translation before the site's own controls/content shell. The shell
  // itself is preserved; only its ordinary text nodes were emptied.
  el.insertBefore(trans,el.firstChild);
  el.setAttribute('data-novelreaderx-translated','1');
  window.__nrTranslatedElement=trans;
  window.__nrContentElement=el;

  trans.scrollIntoView({behavior:'smooth',block:'start'});
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