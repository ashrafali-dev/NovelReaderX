package com.ashraf.novelreader

import org.json.JSONObject

object NovelJs {
    fun extract() = WebNovelAdapter.extractScript()

    fun insertTranslation(text:String):String {
        val q=JSONObject.quote(text)
        return """
(function(){
  const text=$q;
  const clean=s=>(s||'').replace(/\u00a0/g,' ').trim();

  let el=window.__nrContentElement;
  if(!el || !document.contains(el)){
    const remembered=window.__nrContentSelector;
    if(remembered){try{el=document.querySelector(remembered)}catch(_){}}
  }

  const selectors=[
    '#chapter-content','.chapter-content','.chapter_content','#chr-content','.chr-c',
    '.reading-content','.text-left','#content','.entry-content','.cha-content','.cha-words',
    '.chapter-body','.novel_content','.j_readContent','.txt','#chaptercontent','.chapter-c',
    '#article','.article-content','.content','article'
  ];

  if(!el){
    let best=null,bestScore=0;
    for(const sel of selectors){
      try{
        for(const e of document.querySelectorAll(sel)){
          const t=clean(e.innerText||e.textContent);
          if(t.length<300)continue;
          const ps=[...e.querySelectorAll('p')].filter(p=>clean(p.innerText||p.textContent).length>0);
          const score=t.length+ps.length*500;
          if(score>bestScore){bestScore=score;best=e;}
        }
      }catch(_){}
    }
    el=best;
  }
  if(!el)return 'not-found';

  if(el.getAttribute('data-novelreaderx-translated')!=='1' ||
     window.__nrOriginalElement!==el ||
     !window.__nrOriginalHtml){
    window.__nrOriginalHtml=el.innerHTML;
    window.__nrOriginalElement=el;
  }

  const normalized=text.replace(/\r/g,'').replace(/[ \t]+\n/g,'\n').trim();
  const parts=normalized
    .split(/\n\s*\n+/)
    .map(x=>x.trim())
    .filter(Boolean);

  const finalParts=[];
  for(const part of parts){
    const lines=part.split(/\n+/).map(x=>x.trim()).filter(Boolean);
    if(lines.length>1 && lines.every(x=>x.length>1)) finalParts.push(...lines);
    else finalParts.push(part);
  }

  const oldParagraph=el.querySelector('p');
  const frag=document.createDocumentFragment();
  for(const part of finalParts){
    const p=document.createElement('p');
    p.textContent=part;
    if(oldParagraph){
      const cs=getComputedStyle(oldParagraph);
      if(cs.marginTop)p.style.marginTop=cs.marginTop;
      if(cs.marginBottom)p.style.marginBottom=cs.marginBottom;
      if(cs.lineHeight)p.style.lineHeight=cs.lineHeight;
      if(cs.fontSize)p.style.fontSize=cs.fontSize;
    }else{
      p.style.margin='0 0 1em 0';
      p.style.lineHeight='1.75';
    }
    frag.appendChild(p);
  }

  el.innerHTML='';
  el.appendChild(frag);
  el.setAttribute('data-novelreaderx-translated','1');
  el.setAttribute('data-nr-original-length',String((window.__nrOriginalHtml||'').length));
  window.__nrTranslatedElement=el;
  el.scrollIntoView({behavior:'smooth',block:'start'});
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