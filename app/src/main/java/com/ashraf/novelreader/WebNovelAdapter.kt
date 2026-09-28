package com.ashraf.novelreader

import android.webkit.WebView
import org.json.JSONObject

object WebNovelAdapter {
    fun isWebNovel(url: String) = runCatching { android.net.Uri.parse(url).host?.lowercase()?.endsWith("webnovel.com") == true }.getOrDefault(false)

    /** Fast DOM extraction. It does not wait for a timer: it reads the live reader DOM immediately. */
    fun extractScript(): String = """
(function(){
 const clean=s=>(s||'').replace(/\u00a0/g,' ').replace(/[ \t]+/g,' ').replace(/\n{3,}/g,'\n\n').trim();
 const textOf=e=>{if(!e)return '';const c=e.cloneNode(true);c.querySelectorAll('script,style,noscript,button,svg,img,video,iframe,[aria-hidden="true"]').forEach(x=>x.remove());c.querySelectorAll('br').forEach(x=>x.replaceWith('\n'));return clean(c.innerText||c.textContent||'');};
 const sels=['#chapter-content','.chapter-content','.chapter_content','.cha-content','.chapter-body','.j_readContent','.txt','#chaptercontent','.chapter-c','article .chapter-content','main article','article'];
 let best=null,bestLen=0;
 for(const s of sels){try{for(const e of document.querySelectorAll(s)){const t=textOf(e);if(t.length>bestLen){best=e;bestLen=t.length;}}}catch(e){}}
 if(!best){for(const e of document.querySelectorAll('main,article,section,div')){const t=textOf(e);if(t.length>bestLen&&t.length>500){best=e;bestLen=t.length;}}}
 if(!best||bestLen<120)return JSON.stringify({ok:false});
 const title=clean((document.querySelector('.chapter-title,.chr-title,#chapter-heading,h1,h2')||{}).innerText||document.title||'');
 const num=(title.match(/(?:chapter|chap|ch|episode|ep)\.?\s*[-#:.]?\s*(\d+(?:\.\d+)?)/i)||title.match(/第\s*(\d+)\s*[章话節回]/)||[])[1]||'';
 const links=[...document.querySelectorAll('a[href],button,[role="button"]')];
 function pick(next){
   const re=next?/next|next chapter|continue/i:/prev|previous|previous chapter/i;
   let best=null,score=0;
   for(const e of links){const meta=[e.innerText,e.getAttribute('aria-label'),e.getAttribute('title'),e.id,e.className].join(' ');if(!re.test(meta))continue;const r=e.getBoundingClientRect();if(r.width<2||r.height<2)continue;let s=0;if(/chapter/i.test(meta))s+=3;if(e.tagName==='A')s+=2;if(s>score){score=s;best=e;}}
   return best?.href||null;
 }
 return JSON.stringify({ok:true,url:location.href,title,num,text:textOf(best),next:pick(true),prev:pick(false)});
})()
""".trimIndent()

    fun buildChapter(raw: String): Chapter? = runCatching {
        val o = JSONObject(raw)
        if (!o.optBoolean("ok")) return null
        val url=o.optString("url")
        val title=o.optString("title")
        Chapter(
            id = "${url}#${o.optString("num")}", url=url, title=title,
            number=o.optString("num"), text=o.optString("text"),
            nextUrl=o.optString("next").takeIf { it.isNotBlank() && it != "null" },
            prevUrl=o.optString("prev").takeIf { it.isNotBlank() && it != "null" }
        )
    }.getOrNull()

    fun catalogUrl(chapterUrl: String): String? = runCatching {
        val u=android.net.Uri.parse(chapterUrl)
        val seg=u.pathSegments
        val i=seg.indexOfFirst { it.equals("book",true) }
        if(i>=0 && i+1<seg.size) "${u.scheme}://${u.host}/book/${seg[i+1]}/catalog" else null
    }.getOrNull()

    fun navigateFromCatalogScript(dir: String, title: String, currentUrl: String): String {
        val t=JSONObject.quote(title)
        val u=JSONObject.quote(currentUrl)
        val d=JSONObject.quote(dir)
        return """
(async function(){
 const dir=$d,title=$t;
 const norm=s=>(s||'').replace(/\s+/g,' ').trim().toLowerCase();
 const strip=s=>norm(s).replace(/^\s*\d+\s*[-.:)]?\s*/, '');
 const clean=u=>{try{return new URL(u,location.href).pathname.replace(/\/+$/,'')}catch(e){return String(u||'').split('?')[0].split('#')[0].replace(/\/+$/,'')}};
 const book=(location.pathname.match(/^\/book\/[^/]+/i)||[])[0]; if(!book)return 'none';
 const html=await (await fetch(location.origin+book+'/catalog',{credentials:'include',cache:'no-store'})).text();
 const doc=new DOMParser().parseFromString(html,'text/html');
 const links=[...doc.querySelectorAll('.j_catalog_list .volume-item li a[href], .j_catalog_list a[href], a[href]')].map(a=>({p:clean(a.href),t:strip(a.getAttribute('title')||a.getAttribute('aria-label')||a.textContent)})).filter(x=>x.t&&x.p.startsWith(book+'/')&&!/\/catalog$/i.test(x.p));
 if(!links.length)return 'none';
 const cur=strip(title); const currentPath=new URL(currentUrl,location.href).pathname.replace(/\/+$/,""); let idx=links.findIndex(x=>new URL(x.p,location.href).pathname.replace(/\/+$/,"")===currentPath); if(idx<0)idx=links.findIndex(x=>x.t===cur);
 if(idx<0){let best=-1,bs=0;const words=cur.split(/\s+/).filter(x=>x.length>=3);links.forEach((x,i)=>{let s=words.reduce((n,w)=>n+(x.t.includes(w)?(w.length>=5?3:1):0),0);if(s>bs){bs=s;best=i}});if(bs>=3)idx=best;}
 if(idx<0)return 'no-current'; const ni=dir==='next'?idx+1:idx-1;if(ni<0||ni>=links.length)return 'edge';
 return new URL(links[ni].p,location.origin).href;
})().catch(e=>'error:'+e)
""".trimIndent()
    }
}