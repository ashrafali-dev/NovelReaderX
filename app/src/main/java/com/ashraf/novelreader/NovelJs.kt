package com.ashraf.novelreader

import org.json.JSONObject

object NovelJs {
    fun extract() = WebNovelAdapter.extractScript()

    fun insertTranslation(text:String):String {
        val q=JSONObject.quote(text)
        return """
(function(){
  const text=$q;
  const old=document.getElementById('nr-translation');
  if(old) old.remove();

  const clean=s=>(s||'').replace(/\u00a0/g,' ').trim();
  const textLen=e=>clean(e?.innerText||e?.textContent||'').length;

  const selectors=[
    '#chapter-content','.chapter-content','.chapter_content','.cha-content',
    '.chapter-body','.j_readContent','.txt','#chaptercontent',
    '.chapter-c','.chapter__content','.chapter-content-wrap',
    '[class*="chapter-content"]','article'
  ];

  let container=null,best=0;
  for(const s of selectors){
    try{
      for(const e of document.querySelectorAll(s)){
        const n=textLen(e);
        if(n>best){best=n;container=e}
      }
    }catch(_){}
  }

  if(!container){
    const all=[...document.querySelectorAll('main,article,section,div')];
    for(const e of all){
      const n=textLen(e);
      if(n>best && n>300){best=n;container=e}
    }
  }

  if(!container) return 'not-found';

  const box=document.createElement('section');
  box.id='nr-translation';
  box.setAttribute('data-novelreaderx','translation');
  box.style.cssText=[
    'display:block','box-sizing:border-box','width:100%',
    'margin:28px 0 60px','padding:20px',
    'border-top:3px solid #6c63ff',
    'background:rgba(108,99,255,.10)',
    'color:inherit','font-size:1em','line-height:1.9',
    'white-space:pre-wrap','word-break:break-word'
  ].join(';');

  const heading=document.createElement('div');
  heading.textContent='বাংলা অনুবাদ';
  heading.style.cssText='font-weight:700;margin-bottom:14px;font-size:1.05em';

  const body=document.createElement('div');
  body.textContent=text;

  box.append(heading,body);
  container.appendChild(box);
  box.scrollIntoView({behavior:'smooth',block:'start'});
  return 'inserted';
})()
""".trimIndent()
    }

    fun siteNext(dir:String)= """
(()=>{const next=${dir=="next"};const els=[...document.querySelectorAll('a[href],button,[role="button"],mov-button')];const re=next?/next|next chapter|continue/i:/prev|previous|previous chapter/i;let best=null,score=0;for(const e of els){const s=[e.innerText,e.getAttribute('aria-label'),e.getAttribute('title'),e.id,e.className].join(' ');if(!re.test(s))continue;const r=e.getBoundingClientRect();if(r.width<2||r.height<2)continue;let n=/chapter/i.test(s)?4:1;if(e.tagName==='A'&&e.href)n+=2;if(n>score){score=n;best=e}}if(!best)return '';if(best.href)return best.href;best.click();return 'clicked'})()
""".trimIndent()
}