package com.ashraf.novelreader

import android.webkit.WebView
import org.json.JSONObject

object WebNovelAdapter {
    fun isWebNovel(url: String) = SiteProfiles.forUrl(url).navigationMode == NavigationMode.WEBNOVEL_CATALOG

    /** Fast DOM extraction. It does not wait for a timer: it reads the live reader DOM immediately. */
    fun extractScript(): String = """
(function(){
 const host=(location.hostname||'').toLowerCase();
 const isWebNovel=host==='webnovel.com'||host.endsWith('.webnovel.com');
 const clean=s=>(s||'').replace(/\u00a0/g,' ').replace(/[ \t]+/g,' ').replace(/\n[ \t]+/g,'\n').replace(/\n{3,}/g,'\n\n').trim();
 const BAD='script,style,noscript,iframe,nav,header,footer,aside,form,button,svg,img,video,[aria-hidden="true"],.ads,.ad,[class*="advert" i],[id*="advert" i],[class*="comment" i],[id*="comment" i],[class*="sidebar" i],[class*="toolbar" i],[class*="reader-nav" i],[class*="chapter-nav" i],.j_catalog_list,.j_readTool';
 const textOf=e=>{
   if(!e)return '';
   const c=e.cloneNode(true);
   try{c.querySelectorAll(BAD).forEach(x=>x.remove())}catch(_){}
   c.querySelectorAll('br').forEach(x=>x.replaceWith('\n'));
   const ps=[...c.querySelectorAll('p')].map(x=>clean(x.innerText||x.textContent)).filter(x=>x.length>0);
   if(ps.length>=2)return ps.join('\n\n');
   const blocks=[...c.querySelectorAll(':scope > div, :scope > section, :scope > article')].map(x=>clean(x.innerText||x.textContent)).filter(x=>x.length>0);
   if(blocks.length>=2)return blocks.join('\n\n');
   return clean(c.innerText||c.textContent||'');
 };
 const noise=s=>(s.match(/closechapters|prevnext|download app|read offline|lora|roboto|10\.4%/gi)||[]).length;
 const candidates=isWebNovel
   ? ['.j_readContent','.cha-content','.chapter-content','#chapter-content','.chapter-body','.chapter-c','.txt','#chaptercontent','.content','article']
   : ['#chapter-content','.chapter-content','.chapter_content','#chr-content','.chr-c','.reading-content','.text-left','#content','.entry-content','.cha-content','.cha-words','.chapter-body','.novel_content','.j_readContent','.txt','#chaptercontent','.chapter-c','#article','.article-content','.content','article'];
 let best=null,bestLen=0,bestScore=-Infinity,bestSelector='';

 // WebNovel keeps its own known reader selectors. Do not alter this path.
 if(isWebNovel){
   for(const sel of candidates){
     try{
       for(const e of document.querySelectorAll(sel)){
         const t=textOf(e);
         if(t.length<300)continue;
         const pc=e.querySelectorAll('p').length;
         const n=noise(t);
         const score=t.length+Math.min(pc,30)*450-n*1200+(sel==='.j_readContent'?3000:0);
         if((pc>=2||sel==='.j_readContent'||sel==='.cha-content')&&score>bestScore){
           best=e;bestLen=t.length;bestScore=score;bestSelector=sel;
         }
       }
     }catch(_){}
   }
 }else{
   // For every other site use the old NovelStudio-style selector priority:
   // first find a real chapter container with substantial text; do not let a
   // generic outer .content/article win merely because it is larger.
   for(const sel of candidates){
     try{
       const e=document.querySelector(sel);
       if(!e)continue;
       const t=textOf(e);
       if(t.length>=500){
         best=e;bestLen=t.length;bestScore=t.length;bestSelector=sel;
         break;
       }
     }catch(_){}
   }

   // Last resort: choose the text-richest block, but require actual paragraph
   // structure so a title/header container cannot be mistaken for the chapter.
   if(!best){
     for(const e of document.querySelectorAll('main,article,section,div')){
       const t=textOf(e),pc=e.querySelectorAll('p').length;
       if(t.length<500||pc<3)continue;
       const score=t.length+Math.min(pc,30)*450-noise(t)*1200;
       if(score>bestScore){best=e;bestLen=t.length;bestScore=score;bestSelector='';}
     }
   }
 }
 if(!best||bestLen<120)return JSON.stringify({ok:false});
 const titleSelectors=isWebNovel
   ? ['.chapter-title','.j_chapterName','.chapter-name','.chr-title','#chapter-heading','h1','h2']
   : ['.chapter-title','.chr-title','#chapter-heading','.j_chapterName','.chapter-name','h1','h2'];
 let title='';
 for(const sel of titleSelectors){
   try{const e=document.querySelector(sel),t=clean(e?.innerText||e?.textContent);if(t&&t.length<180&&!/close chapters|prev|next|download app/i.test(t)){title=t;break;}}catch(_){}
 }
 if(!title)title=clean(document.title||'');
 let body=textOf(best);
 const titleNorm=clean(title).toLowerCase();
 body=body.split(/\n+/).map(x=>x.trim()).filter(x=>{
   const n=clean(x).toLowerCase();
   if(!n)return false;
   if(titleNorm && n===titleNorm)return false;
   if(/(?:webweb\+ai|download app|read offline)/i.test(n))return false;
   if(/^(?:chapter|অধ্যায়)\s*\d+\s*[^\n]*(?:\/|#|%)/i.test(n))return false;
   if(/^\s*(?:chapter|অধ্যায়)\s*\d+\s*[:：]\s*[^\n]+$/i.test(n) && titleNorm && n!==titleNorm)return false;
   return true;
 }).join('\n\n').trim();
 if(title&&body.toLowerCase().startsWith(title.toLowerCase()))body=body.slice(title.length).trim();

 // Do not send a chapter containing only its title/metadata. Some SPA sites
 // render the heading first and the chapter body a moment later.
 if(body.length<120)return JSON.stringify({ok:false});

 const num=(title.match(/(?:chapter|chap|ch|episode|ep)\.?\s*[-#:.]?\s*(\d+(?:\.\d+)?)/i)||title.match(/第\s*(\d+)\s*[章话話節回]/)||[])[1]||'';
 const selectorFor=e=>{
   if(!e)return '';
   if(e.id&&/^[A-Za-z_][A-Za-z0-9_-]*$/.test(e.id))return '#'+e.id;
   const cls=[...e.classList].filter(x=>/^[A-Za-z_][A-Za-z0-9_-]*$/.test(x)).slice(0,3);
   return e.tagName.toLowerCase()+(cls.length?'.'+cls.join('.'):'' );
 };
 const rememberedSelector=bestSelector||selectorFor(best);
 window.__nrContentElement=best;
 window.__nrContentSelector=rememberedSelector;
 const links=[...document.querySelectorAll('a[href],button,[role="button"]')];
 function pick(next){
   const re=next?/next|next chapter|continue|›|»|→/i:/prev|previous|previous chapter|‹|«|←/i;
   let target=null,score=0;
   for(const e of links){
     const meta=[e.innerText,e.getAttribute('aria-label'),e.getAttribute('title'),e.id,e.className].join(' ');
     if(!re.test(meta))continue;
     const r=e.getBoundingClientRect();if(r.width<2||r.height<2)continue;
     let s=/chapter/i.test(meta)?4:1;if(e.tagName==='A'&&e.href)s+=2;
     if(s>score){score=s;target=e;}
   }
   return target?.href||null;
 }
 const chapterText=title?(title+'\n\n'+body):body;
 return JSON.stringify({ok:true,url:location.href,title,num,text:chapterText,next:pick(true),prev:pick(false)});
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