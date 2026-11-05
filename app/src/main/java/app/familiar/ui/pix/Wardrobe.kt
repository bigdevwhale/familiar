package app.familiar.ui.pix

import app.familiar.domain.Form
import app.familiar.domain.Season
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What a familiar wears: a grown one takes on the colours of its form, the bloom one a wreath and the
 * astral one a little moon; on midsummer every familiar gets a wreath. No hats: on the drawn familiars
 * they never sat right on ears, crests and crowns.
 */
internal object Wardrobe {
    private fun tint(f: Form): Int = (f.argb and 0xFFFFFF).toInt() or (0xFF shl 24)

    /** Tints the body toward its form and puts its wreath or moon on its head. */
    fun dress(body: Px, face: Face, look: Look, f: Int) {
        // The drawn familiars are recoloured by their form's own shade before they're painted (Drawn.colours).
        if (look.form != Form.NONE && !face.drawn) {
            val c = tint(look.form)
            val k = when (look.form) { Form.SHADOW -> 0.4f; Form.SAGE -> 0.24f; else -> 0.32f }
            for (i in body.c.indices) {
                val p = body.c[i]
                if (p.al() == 0 || p and 0xFFFFFF == OUT and 0xFFFFFF || p and 0xFFFFFF == EYE and 0xFFFFFF || lum(p) > 0.9f) continue
                body.c[i] = (p.al() shl 24) or (mixc(p, c, k) and 0xFFFFFF)
            }
        }
        val hx = face.headX
        // Sits on the head, or on whatever is lower right above the face (the mimic's lid).
        var drawn = face.headY
        for (y in 0 until face.headY) if ((hx - 1..hx + 1).any { body.has(it, y) }) { drawn = y; break }
        val top = maxOf(face.headY - face.headR, drawn + 1)
        val r = face.headR.coerceIn(7, 20)
        when {
            look.season == Season.MIDSUMMER -> wreath(body, hx, top, r, listOf(WHITE, R.gold[5], WHITE, R.gold[4], WHITE))
            look.form == Form.BLOOM -> wreath(body, hx, top, r, listOf(R.pink[3], WHITE, R.pink[2], R.lilac[5], R.pink[3]))
            look.form == Form.ASTRAL -> crescent(body, hx + r * 2 / 3, top + 1)
            else -> {}
        }
    }

    /** Behind the familiar: the mist of a shadow form. */
    fun behind(out: Px, look: Look, f: Int) {
        if (look.form != Form.SHADOW) return
        for (i in 0 until 7) {
            val x = 12f + i * 9.5f + sin((f / 12f + i * 0.37f) * 2 * PI.toFloat()) * 2f
            out.glow(x, out.h - 6f, 8f, rgb(120, 92, 170), 120, 3)
        }
    }

    /** Around the familiar: stars, fireflies, floating hearts. */
    fun around(out: Px, face: Face, look: Look, f: Int) {
        val ph = f / Sprites.BEATS.toFloat()
        when (look.form) {
            Form.ASTRAL -> for (i in 0 until 6) {
                if ((f + i * 3) % Sprites.BEATS >= 9) continue
                val x = (out.w * (0.12f + 0.76f * (noise(i, 3, 61) + 0.5f))).toInt()
                val y = (out.h * (0.1f + 0.55f * (noise(i, 5, 61) + 0.5f))).toInt()
                star(out, x, y, if (i % 2 == 0) R.gold[5] else R.sky[5])
            }
            Form.WILD -> for (i in 0 until 4) {
                val a = (ph + i / 4f) * 2 * PI.toFloat()
                val x = (out.w / 2 + cos(a + i) * out.w * 0.38f).toInt()
                val y = (out.h * 0.5f + sin(a * 2 + i) * out.h * 0.22f).toInt()
                if ((f + i) % 3 != 0) { out.set(x, y, rgb(220, 250, 140)); out.set(x + 1, y, rgb(170, 220, 90), 140); out.set(x, y + 1, rgb(170, 220, 90), 140) }
            }
            Form.SHADOW -> for (i in 0 until 2) {
                val a = (ph * 360f + i * 180f) * PI / 180
                val x = (face.headX + cos(a) * (face.headR + 9)).toFloat()
                val y = (face.headY + sin(a) * 4).toFloat()
                out.glow(x, y, 3.5f, rgb(170, 140, 230), 120, 3)
            }
            else -> {}
        }
        if (look.season == Season.VALENTINE) for (i in 0 until 2) {
            val p = (ph + i * 0.5f) % 1f
            val x = face.headX + if (i == 0) face.headR + 3 else -face.headR - 7
            val y = (face.headY - p * 16).toInt()
            Sprites.stamp(out, Sprites.HEART, x, y, R.red[3], R.pink[3], (255 * sin(p * PI).toFloat()).toInt().coerceAtLeast(70))
        }
        if (look.season == Season.MIDSUMMER) for (i in 0 until 3) {
            val a = (ph + i / 3f) * 2 * PI.toFloat()
            val x = (out.w / 2 + cos(a * 1.3f + i) * out.w * 0.4f).toInt()
            val y = (out.h * 0.45f + sin(a + i) * out.h * 0.25f).toInt()
            if ((f + i) % 3 != 1) out.glow(x + 0.5f, y + 0.5f, 3f, R.gold[5], 160, 2)
        }
    }

    private fun star(px: Px, x: Int, y: Int, c: Int) {
        px.set(x, y, WHITE); px.set(x - 1, y, c, 200); px.set(x + 1, y, c, 200); px.set(x, y - 1, c, 200); px.set(x, y + 1, c, 200)
    }




    private fun wreath(px: Px, hx: Int, top: Int, r: Int, colours: List<Int>) {
        for (i in 0 until 7) {
            val a = (200f + i * 140f / 6f) * PI.toFloat() / 180f
            val x = (hx + cos(a) * r * 0.82f).roundToInt()
            val y = (top + r * 0.32f + sin(a) * r * 0.36f).roundToInt()
            px.set(x, y + 1, R.leaf[3]); px.set(x + 1, y + 1, R.leaf[2])
            if (i % 2 == 0 || r >= 12) {
                val c = colours[i % colours.size]
                for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) px.set(x + dx, y + dy, c)
                px.set(x, y, R.gold[3])
            }
        }
    }



    private fun crescent(px: Px, x: Int, y: Int) {
        val m = minus(ellipse(x.toFloat(), y.toFloat(), 3.6f, 3.6f), ellipse(x + 1.8f, y - 1f, 3f, 3f))
        px.shade(m, facing(-0.3f, -0.3f, 1f), R.gold, lo = 3, spec = true)
    }
}
