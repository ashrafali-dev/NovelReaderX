package com.ashraf.novelreader

object ReaderStyle {
    fun apply(night: Boolean, fontSize: Int): String {
        val bg = if (night) "#101114" else "#f7f7f7"
        val fg = if (night) "#e7e5e2" else "#252525"
        return """
(function(){
  const fs=$fontSize;
  const bg='$bg', fg='$fg';
  let s=document.getElementById('novelreaderx-reader-style');
  if(!s){s=document.createElement('style');s.id='novelreaderx-reader-style';document.head.appendChild(s);}

  // Reader styling belongs to our translation layer only. Do not rewrite the
  // site's .content/article/body layout or its navigation controls.
  s.textContent=
    '[data-nr-translation="1"]{' +
      'box-sizing:border-box !important;width:100% !important;' +
      'text-align:left !important;background:'+bg+' !important;color:'+fg+' !important;' +
      'font-size:'+fs+'px !important;line-height:1.9 !important;' +
      'font-synthesis:none !important;' +
    '}' +
    '[data-nr-translation="1"] p{' +
      'box-sizing:border-box !important;width:100% !important;' +
      'text-align:left !important;color:'+fg+' !important;' +
      'font-size:'+fs+'px !important;line-height:1.9 !important;' +
      'font-family:inherit !important;' +
      'margin:0 0 1.05em 0 !important;' +
    '}' +
    '[data-nr-translation="1"] p.nr-title{' +
      'font-size:24px !important;line-height:1.45 !important;font-weight:700 !important;' +
      'margin:4px 0 1.25em 0 !important;' +
    '}';

  const el=window.__nrTranslatedElement;
  if(el){
    el.style.setProperty('background',bg,'important');
    el.style.setProperty('color',fg,'important');
    el.style.setProperty('font-size',fs+'px','important');
    el.style.setProperty('line-height','1.9','important');
    el.style.setProperty('text-align','left','important');
  }
  return 'styled';
})()
""".trimIndent()
    }
}
