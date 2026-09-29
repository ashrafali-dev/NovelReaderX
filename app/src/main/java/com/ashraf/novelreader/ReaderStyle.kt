package com.ashraf.novelreader

object ReaderStyle {
    fun apply(night: Boolean, fontSize: Int): String {
        val bg = if (night) "#101114" else "transparent"
        val fg = if (night) "#e7e5e2" else "inherit"
        return """
(function(){
  const fs=Math.max(18,Math.min(22,$fontSize));
  const bg='$bg', fg='$fg';
  let s=document.getElementById('novelreaderx-reader-style');
  if(!s){s=document.createElement('style');s.id='novelreaderx-reader-style';document.head.appendChild(s);}

  // Only style our translation. Never touch body/.content/article or the
  // site's buttons, links, margins, reader shell, or native typography.
  s.textContent=
    '[data-nr-translation="1"]{' +
      'box-sizing:border-box !important;display:block !important;width:100% !important;' +
      'max-width:100% !important;padding:0 18px !important;margin:0 !important;' +
      'background:'+bg+' !important;color:'+fg+' !important;' +
      'font-family:"Noto Serif Bengali","Noto Serif",Georgia,serif !important;' +
      'font-size:'+fs+'px !important;line-height:1.85 !important;text-align:left !important;' +
    '}' +
    '[data-nr-translation="1"] p{' +
      'box-sizing:border-box !important;width:100% !important;padding:0 !important;' +
      'margin:0 0 1.05em 0 !important;' +
      'font-family:"Noto Serif Bengali","Noto Serif",Georgia,serif !important;' +
      'font-size:'+fs+'px !important;line-height:1.85 !important;' +
      'font-weight:400 !important;color:'+fg+' !important;text-align:left !important;' +
    '}' +
    '[data-nr-translation="1"] p.nr-title{' +
      'font-size:20px !important;line-height:1.5 !important;font-weight:700 !important;' +
      'margin:0 0 1.2em 0 !important;' +
    '}';

  const el=window.__nrTranslatedElement;
  if(el){
    el.style.setProperty('font-family','"Noto Serif Bengali","Noto Serif",Georgia,serif','important');
    el.style.setProperty('font-size',fs+'px','important');
    el.style.setProperty('line-height','1.85','important');
    el.style.setProperty('padding-left','18px','important');
    el.style.setProperty('padding-right','18px','important');
  }
  return 'styled';
})()
""".trimIndent()
    }
}
