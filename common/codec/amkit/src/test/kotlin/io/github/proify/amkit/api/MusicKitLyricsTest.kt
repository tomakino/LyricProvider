/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.api

import io.github.proify.amkit.api.lyric.AppleMusicLyricsDownloader
import io.github.proify.amkit.api.lyric.LyricsCredentials
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class MusicKitLyricsTest {

    @Test
    fun `lyrics 返回非空数据`() = runBlocking {


        val authorization =
            """
eyJraWQiOiJOWktGRzJEUzBKIiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJVREsyOFNOMTBQIiwiaWF0IjoxNzk1Njk0ODM3LCJleHAiOjE3OTgyODY4MzcsIm1pZCI6ImJjOWd5UncwNWtBZ1FXYS9ERmgzcWlFcm5tQT0ifQ.FSz2k02Xe96yzhmry5gGFul8rFIxsnv9ltQ0eebYTmWdUE7gFgdGOFmJA1ZnJjQyJ7d-ue_CeSaPOnn4FqtKdg
            """.trim()

        val mediaUserToken =
            "AwIAAAFCAAERcwAAAABrCCDKlfYA1pFQhGtXXKRuvLj+mTxtNbE="

        val cookie =
            """
                itspod=49; xp_ab=1#KVpIiom+-2+isB6vdu01#BtPAq2U+-2+YlIAk_F01#MoU451v+-2+V8_KhWp00#fNPb5Km+-2+xSb5Dsb17#WTckLqP+-2+dWrDbVk01#zsliDSj+-2+0prnB_r01#HnW1xce+-2+b3CsmSz00#tT4eyCv+-2+64gGe2a01#8g9QAxE+-2+hmv2eLm01#G8QCKvz+-2+WuBwpMm01#cf0QHwj+-2+l_F7dPb01#Kb_23XK+-2+ym6UX1j00#WCPQz9n+-2+kl2ijQq00#HGwnF7S+-2+cAm4ibH01#Zh4zkDd+-2+6S03XJf01#QfpM7yc+-2+Ql0XIZe01#7JrPZ1t+-2+NX9ULlL01#a02N3sy+-2+u4Yh5yX01#HqvSCXm+-2+zueK6fq03#m0henFo+-2+UrouLNS00#cGP6Wqk+-2+9weWCvj01#yNFpB6B+-2+PiDFkbY01#QVJEGsa+-2+PCZR8GN02#UJdLlmb+-2+ry8n3Ab00#UA65KfY+-2+5p4qS6K01#vjUgZdR+-2+tvB6XcM01; xp_abc=UrouLNS00; dslang=CN-ZH; site=CHN; Option.X -Dsid=20204813444; mz_at0-20204813444=AwQAAAFCAAERcwAAAABrCCDK/kiwXAXGSU3QzCJF0ZELi7zaeWs=; mz_at_ssl-20204813444=AwUAAAFCAAERcwAAAABrCCDKuoF5tlXY++DlRp1XC0NYEPWxv08=; pldfltcid=652870e4877646d29cc50fecb323ac6f049; tv-pldfltcid=652870e4877646d29cc50fecb323ac6f049; wosid-lite=fkXWvl6Ae2hM3fItHxL8uM; TrPod=8; amp=t1VZ8o6Wsnsxa/fU50nsq2zz+Rv+a0WrFnVvgAAXpwMRi5fWT7NE5nb8mNPXIwQUw5kc/BtBka3m+XNVijG9FktwOW0lSZersGJXUZRKMgcx+iB0Nfb4lHe63cMlQ6W2fbl0DY0I/OdeTE+dFja+SlfIYHBrsoeDsobyLHPT/Fw=
                """.trim()

        val dsid = "20204813444"

        val ttml = AppleMusicLyricsDownloader(
            credentials = LyricsCredentials(
                developerToken = authorization,
                cookie = cookie,
                userToken = mediaUserToken,
                dsid = dsid
            )
        )
            .download("339414579", "cn")
        print(ttml)

    }
}