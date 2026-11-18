/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.decrypt

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object QrcCodec {
    private fun String.bytes() = chunked(2).map { it.toInt(16) }.toIntArray()
    private fun u32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
                ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun put32(b: ByteArray, o: Int, v: Int) {
        b[o] = v.toByte()
        b[o + 1] = (v ushr 8).toByte()
        b[o + 2] = (v ushr 16).toByte()
        b[o + 3] = (v ushr 24).toByte()
    }

    private val HEXV = IntArray(128) { c ->
        when (c) {
            in 48..57 -> c - 48
            in 97..102 -> c - 87
            in 65..70 -> c - 55
            else -> -1
        }
    }

    private fun hexToBytes(s: String): ByteArray {
        val t = s.trim()
        val n = t.length
        require(n and 1 == 0) { "odd hex" }
        val out = ByteArray(n / 2)
        for (i in out.indices) {
            out[i] = ((HEXV[t[i * 2].code] shl 4) or HEXV[t[i * 2 + 1].code]).toByte()
        }
        return out
    }

    private val VAL = IntArray(64) { Int.MIN_VALUE ushr (it and 31) }
    private val IP =
        ("3a322a221a120a023c342c241c140c043e362e261e160e06403830282018100839312921191109013b332b231b130b033d352d251d150d053f372f271f170f07").bytes()
    private val FP = IntArray(64) { IP.indexOf(it + 1) + 1 }
    private val P = ("100714151d0c1c11010f171a05121f0a0208180e201b0309130d1e06160b0419").bytes()
    private val PC1 =
        ("39312921191109013a322a221a120a023b332b231b130b033c342c243f372f271f170f073e362e261e160e063d352d251d150d051c140c04").bytes()
    private val PC2 =
        ("0e110b180105031c0f06150a17130c041a0810071b140d0229341f252f371e28332d21302c31273822352e2a32241d20").bytes()
    private val SH = ("01010202020202020102020202020201").bytes()
    private val SB =
        ("0e00040f0d070104020e0f020b0d0801030a0a06060c0c0b0509090500030708040f010c0e0808020d04060902010b070f050c0b0903070e030a0a000506000d0f03010d08040e07060f0b020308040f090c070002010d0a0c060009050b0a05000d0e08070a0b010a03040f0d040102050b08060c07060c09000305020e0f09" +
                "0a0d000709000e09060303040f06050a01020d080c05070e0b0c040b020f08010d01060a040d090008060f09030800070b04010f020e0c03050b0a050e02070c070d0d080e0b03050006060f09000a03010402070802050c0b010c0a040e0f090a03060f090000060c0a0b0a070d0d080f09010403050e0b050c02070802040e" +
                "020e0c0b0402010c07040a070b0d060108050500030f0f0a0d0300090e080906040b0208010c0b070a010d0e0702080d0f06090f0c000509060a030400050e030c0a010f0a040f020907020c0609080500060d01030d040e0e00070b05030b0809040e030f02050c020908050c0f030a070b000e04010a0701060d000b08060d" +
                "040d0b00020b0e070f04000908010d0a030e0c030905070c05020a0f060801060106040b0b0d0d080c010304070a0e070a090f050600080f000e05020903020c0d01020f080d0408060a0f030b0701040a0c090503060e0b0500000e0c09070207020b01040e010709040c0a0e08020d000f060c0a090d000f0303050506080b").bytes()
    private val K = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)

    // S 盒 + P 置换合并成 8×64 查找表；每项已是 32 位 f 输出
    private val SP = Array(8) { i ->
        IntArray(64) { x ->
            val s = SB[i * 64 + x]
            var v = 0
            for (j in 0 until 4) {
                if ((s and (1 shl (3 - j))) != 0) v =
                    v or (1 shl (32 - (P.indexOf(i * 4 + j + 1) + 1)))
            }
            v
        }
    }

    // IP 表：8 个输入字节 → 输出低/高 32 位（每项只含该字节的贡献）
    private val IP_LO = Array(8) { bi ->
        IntArray(256) { v ->
            var x = 0
            for (bit in 0 until 8) {
                if ((v and (1 shl bit)) != 0) {
                    val b = if (bi < 4) bi * 8 + bit else (bi - 4) * 8 + bit
                    val k = if (bi < 4) 32 - b else 64 - b
                    val q = IP.indexOf(k) + 1
                    if (q <= 32) x = x or (1 shl (32 - q))
                }
            }
            x
        }
    }
    private val IP_HI = Array(8) { bi ->
        IntArray(256) { v ->
            var x = 0
            for (bit in 0 until 8) {
                if ((v and (1 shl bit)) != 0) {
                    val b = if (bi < 4) bi * 8 + bit else (bi - 4) * 8 + bit
                    val k = if (bi < 4) 32 - b else 64 - b
                    val q = IP.indexOf(k) + 1
                    if (q > 32) x = x or (1 shl (64 - q))
                }
            }
            x
        }
    }

    // FP 表：8 个输入字节（r 的低 4 字节 + l 的低 4 字节）→ 输出低/高 32 位
    private val FP_LO = Array(8) { bi ->
        IntArray(256) { v ->
            var x = 0
            for (bit in 0 until 8) {
                if ((v and (1 shl bit)) != 0) {
                    val b = if (bi < 4) bi * 8 + bit else (bi - 4) * 8 + bit
                    val k = if (bi < 4) 32 - b else 64 - b
                    val q = FP.indexOf(k)
                    if (q < 32) x = x or (1 shl (31 - q))
                }
            }
            x
        }
    }
    private val FP_HI = Array(8) { bi ->
        IntArray(256) { v ->
            var x = 0
            for (bit in 0 until 8) {
                if ((v and (1 shl bit)) != 0) {
                    val b = if (bi < 4) bi * 8 + bit else (bi - 4) * 8 + bit
                    val k = if (bi < 4) 32 - b else 64 - b
                    val q = FP.indexOf(k)
                    if (q >= 32) x = x or (1 shl (31 - (q - 32)))
                }
            }
            x
        }
    }

    private fun sched(off: Int, dec: Boolean): IntArray {
        val k0 = u32(K, off)
        val k1 = u32(K, off + 4)
        var c = 0
        var d = 0
        for (i in 0 until 28) {
            if (((if (PC1[i] > 32) k1 else k0) and VAL[PC1[i] - 1]) != 0) c = c or VAL[i]
            if (((if (PC1[i + 28] > 32) k1 else k0) and VAL[PC1[i + 28] - 1]) != 0) d = d or VAL[i]
        }
        val s = IntArray(32)
        for (r in 0 until 16) {
            val sh = SH[r]
            val mask = if (sh == 1) Int.MIN_VALUE else 0xC0000000.toInt()
            val w = if (((0x8103 ushr r) and 1) != 0) 27 else 26
            c = (c shl sh) or (((c and mask) ushr w) and 0xFFFFFFF0.toInt())
            d = (d shl sh) or (((d and mask) ushr w) and 0x30)
            var lo = 0
            var hi = 0
            for (i in 0 until 48) {
                if (i <= 23) {
                    if ((c and VAL[PC2[i] - 1]) != 0) lo = lo or VAL[i]
                } else {
                    if ((d and VAL[PC2[i] - 28]) != 0) hi = hi or VAL[i - 24]
                }
            }
            val x = if (dec) 15 - r else r
            s[x * 2] = lo
            s[x * 2 + 1] = hi
        }
        return s
    }

    private val SCH_DEC = arrayOf(sched(16, true), sched(8, false), sched(0, true))
    private val SCH_ENC = arrayOf(sched(0, false), sched(8, true), sched(16, false))

    private fun f(r: Int, s: IntArray, rnd: Int): Int {
        val kLo = s[rnd * 2]
        val kHi = s[rnd * 2 + 1]
        val g0 = ((((r and 1) shl 5) or ((r ushr 27) and 0x1F)) xor ((kLo ushr 26) and 0x3F))
        val g1 = (((r ushr 23) and 0x3F) xor ((kLo ushr 20) and 0x3F))
        val g2 = (((r ushr 19) and 0x3F) xor ((kLo ushr 14) and 0x3F))
        val g3 = (((r ushr 15) and 0x3F) xor ((kLo ushr 8) and 0x3F))
        val g4 = (((r ushr 11) and 0x3F) xor ((kHi ushr 26) and 0x3F))
        val g5 = (((r ushr 7) and 0x3F) xor ((kHi ushr 20) and 0x3F))
        val g6 = (((r ushr 3) and 0x3F) xor ((kHi ushr 14) and 0x3F))
        val g7 = ((((r and 0x1F) shl 1) or (r ushr 31)) xor ((kHi ushr 8) and 0x3F))
        return SP[0][g0] xor SP[1][g1] xor SP[2][g2] xor SP[3][g3] xor
                SP[4][g4] xor SP[5][g5] xor SP[6][g6] xor SP[7][g7]
    }

    private fun block(d: ByteArray, o: Int, s: IntArray) {
        var l = IP_LO[0][d[o].toInt() and 0xFF] or IP_LO[1][d[o + 1].toInt() and 0xFF] or
                IP_LO[2][d[o + 2].toInt() and 0xFF] or IP_LO[3][d[o + 3].toInt() and 0xFF] or
                IP_LO[4][d[o + 4].toInt() and 0xFF] or IP_LO[5][d[o + 5].toInt() and 0xFF] or
                IP_LO[6][d[o + 6].toInt() and 0xFF] or IP_LO[7][d[o + 7].toInt() and 0xFF]
        var r = IP_HI[0][d[o].toInt() and 0xFF] or IP_HI[1][d[o + 1].toInt() and 0xFF] or
                IP_HI[2][d[o + 2].toInt() and 0xFF] or IP_HI[3][d[o + 3].toInt() and 0xFF] or
                IP_HI[4][d[o + 4].toInt() and 0xFF] or IP_HI[5][d[o + 5].toInt() and 0xFF] or
                IP_HI[6][d[o + 6].toInt() and 0xFF] or IP_HI[7][d[o + 7].toInt() and 0xFF]
        for (rnd in 0 until 16) {
            val nr = l xor f(r, s, rnd)
            l = r
            r = nr
        }
        val o0 = FP_LO[0][r and 0xFF] or FP_LO[1][(r ushr 8) and 0xFF] or
                FP_LO[2][(r ushr 16) and 0xFF] or FP_LO[3][(r ushr 24) and 0xFF] or
                FP_LO[4][l and 0xFF] or FP_LO[5][(l ushr 8) and 0xFF] or
                FP_LO[6][(l ushr 16) and 0xFF] or FP_LO[7][(l ushr 24) and 0xFF]
        val o1 = FP_HI[0][r and 0xFF] or FP_HI[1][(r ushr 8) and 0xFF] or
                FP_HI[2][(r ushr 16) and 0xFF] or FP_HI[3][(r ushr 24) and 0xFF] or
                FP_HI[4][l and 0xFF] or FP_HI[5][(l ushr 8) and 0xFF] or
                FP_HI[6][(l ushr 16) and 0xFF] or FP_HI[7][(l ushr 24) and 0xFF]
        put32(d, o, o0)
        put32(d, o + 4, o1)
    }

    private fun transform(d: ByteArray, ss: Array<IntArray>) {
        var o = 0
        val n = d.size
        while (o + 8 <= n) {
            block(d, o, ss[0])
            block(d, o, ss[1])
            block(d, o, ss[2])
            o += 8
        }
    }

    private fun pipe(d: ByteArray, inflate: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        val b = ByteArray(4096)
        if (inflate) {
            val z = Inflater()
            z.setInput(d)
            while (!z.finished()) {
                val n = z.inflate(b)
                if (n <= 0) break
                out.write(b, 0, n)
            }
            z.end()
        } else {
            val z = Deflater()
            z.setInput(d)
            z.finish()
            while (!z.finished()) {
                val n = z.deflate(b)
                if (n <= 0) break
                out.write(b, 0, n)
            }
            z.end()
        }
        return out.toByteArray()
    }

    fun decrypt(hex: String): String {
        val d = hexToBytes(hex)
        transform(d, SCH_DEC)
        return pipe(d, true).toString(Charsets.UTF_8)
    }

    fun encrypt(xml: String): String {
        val z = pipe(xml.toByteArray(Charsets.UTF_8), false)
        val d = z.copyOf((z.size + 7) / 8 * 8)
        transform(d, SCH_ENC)
        return d.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }
}