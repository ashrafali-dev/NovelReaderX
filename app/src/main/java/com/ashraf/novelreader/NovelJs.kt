package com.ashraf.novelreader

import org.json.JSONObject

object NovelJs {
    fun extract() = WebNovelAdapter.extractScript()

    fun clearTranslation() = "document.getElementById('nr-trans')?.remove();'ok'"

    fun insertTranslation(text: String): String {
        val q=JSONObject.quote(text)
        return """
(()=>{const t=$q;let x=document.getElementById('nr-trans');if(!x){x=document.createElement('section');x.id='nr-trans';x.style.cssText='margin:24px auto;padding:20px;max-width:900px;border-top:3px solid #6c63ff;background:rgba(108,99,255,.08);font-size:1.05em;line-height:1.9;white-space:pre-wrap;word-break:break-word;';const c=document.querySelector('#chapter-content,.chapter-content,.chapter_content,.cha-content,.j_readContent,.chapter-body,article,main');(c||document.body).appendChild(x)}x.textContent=t;window.scrollTo({top:x.offsetTop-20,behavior:'smooth'});return 'inserted'})()
""".trimIndent()
    }

    fun siteNext(dir:String) = """
(()=>{const next=${dir=="next"};const els=[...document.querySelectorAll('a[href],button,[role="button"],mov-button')];const re=next?/next|next chapter|continue/i:/prev|previous|previous chapter/i;let best=null,score=0;for(const e of els){const s=[e.innerText,e.getAttribute('aria-label'),e.getAttribute('title'),e.id,e.className].join(' ');if(!re.test(s))continue;const r=e.getBoundingClientRect();if(r.width<2||r.height<2)continue;let n=/chapter/i.test(s)?4:1;if(e.tagName==='A'&&e.href)n+=2;if(n>score){score=n;best=e}}if(!best)return '';if(best.href)return best.href;best.click();return 'clicked'})()
""".trimIndent()
}