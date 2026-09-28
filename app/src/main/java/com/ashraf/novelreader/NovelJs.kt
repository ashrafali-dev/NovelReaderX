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
  const old=document.getElementById('nr-translation');
  if(old) old.remove();

  const selectors=[
    '#chapter-content','.chapter-content','.chapter_content','#chr-content','.chr-c',
    '.reading-content','.text-left','#content','.entry-content','.cha-content','.cha-words',
    '.chapter-body','.novel_content','.j_readContent','.txt','#chaptercontent','.chapter-c',
    '#article','.article-content','.content','article'
  ];

  let el=null,best=0;
  for(const sel of selectors){
    try{
      for(const e of document.querySelectorAll(sel)){
        const n=clean(e.innerText||e.textContent).length;
        if(n>best&&n>500){best=n;el=e;}
      }
    }catch(_){}
  }
  if(!el){
    for(const e of document.querySelectorAll('main,article,section,div')){
      const n=clean(e.innerText||e.textContent).length;
      if(n>best&&n>500&&e.querySelectorAll('p').length>=3){best=n;el=e;}
    }
  }
  if(!el)return 'not-found';

  // Keep the site's actual chapter element and all of its outer attributes.
  // Only replace its readable children, matching the old NovelStudio behavior.
  const parts=text.replace(/\r/g,'').split(/\n\s*\n+/).map(x=>x.trim()).filter(Boolean);
  const frag=document.createDocumentFragment();
  for(const part of parts){
    const p=document.createElement('p');
    p.textContent=part;
    p.style.margin='0 0 1em 0';
    p.style.lineHeight='1.75';
    frag.appendChild(p);
  }

  if(!window.__nrOriginalHtml || window.__nrOriginalElement!==el){
    window.__nrOriginalHtml=el.innerHTML;
    window.__nrOriginalElement=el;
  }
  el.innerHTML='';
  el.appendChild(frag);
  el.setAttribute('data-novelreaderx-translated','1');
  el.setAttribute('data-nr-original-length',String(best));
  window.__nrTranslatedElement=el;

  // Do not inject a separate floating translation box; the translation now
  // occupies the same WebNovel chapter-content element.
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