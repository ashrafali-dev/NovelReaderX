package com.ashraf.novelreader

import android.content.Context
import android.net.Uri

object AdBlock {
    private const val PREF = "novelreaderx_settings"
    private const val KEY = "adblock_enabled"

    private val hosts = setOf(
        "doubleclick.net","googlesyndication.com","googleadservices.com",
        "adnxs.com","taboola.com","outbrain.com","popads.net","popcash.net",
        "propellerads.com","exoclick.com","trafficjunky.com","adsterra.com",
        "mgid.com","revcontent.com","pubmatic.com","rubiconproject.com",
        "criteo.com","criteo.net","amazon-adsystem.com","scorecardresearch.com",
        "moatads.com","adform.net","smartadserver.com","yieldmo.com",
        "adsrvr.org","bidswitch.net","advertising.com","media.net",
        "adcash.com","hilltopads.net","admaven.com","clickadu.com"
    )

    private val paths = listOf(
        "/pagead/","/adserver/","/adservice","/adclick","/ads/",
        "/advert/","/advertising/","/banner-ad","/banners/","/sponsor/",
        "doubleclick","googlesyndication","googleadservices","popunder","prebid"
    )

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, value).apply()
    }

    fun blocked(url: String): Boolean {
        val lower = url.lowercase()
        if (paths.any(lower::contains)) return true
        val host = runCatching { Uri.parse(url).host?.lowercase().orEmpty() }.getOrDefault("")
        var h = host
        while (h.isNotEmpty()) {
            if (hosts.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }
        return false
    }

    fun cosmeticJs(): String = """
(function(){
 try{
   var css='.adsbygoogle,ins.adsbygoogle,[id*=google_ads],[id*=div-gpt-ad],[class*=advert],[id*=advert],[class*=ad-banner],[class*=ad-container],[class*=ad-slot],[class*=popup],[class*=sponsored],iframe[src*=doubleclick],iframe[src*=googlesyndication]{display:none!important;visibility:hidden!important}';
   var s=document.getElementById('__nrxAdStyle');
   if(!s){s=document.createElement('style');s.id='__nrxAdStyle';(document.head||document.documentElement).appendChild(s);}
   s.textContent=css;
 }catch(e){}
})()
""".trimIndent()
}
