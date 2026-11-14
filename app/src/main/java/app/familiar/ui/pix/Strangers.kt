package app.familiar.ui.pix

import app.familiar.domain.Species
import app.familiar.domain.Stage

/**
 * The nine familiars that came last, drawn in code: a plain round little figure each, told apart by its colour,
 * ears and what it carries. This is what shows while a familiar's drawn picture is missing (art/familiars),
 * and in the classic look, which they never had one of.
 */
internal object Strangers {
    private val jade = Ramp(rgb(14, 40, 52), rgb(20, 78, 84), rgb(30, 122, 112), rgb(54, 166, 136), rgb(104, 204, 160), rgb(170, 232, 190), rgb(224, 248, 220))
    private val smoke = Ramp(rgb(30, 30, 52), rgb(52, 56, 88), rgb(80, 90, 128), rgb(112, 126, 166), rgb(150, 164, 198), rgb(192, 204, 226), rgb(228, 234, 244))
    private val butter = Ramp(rgb(104, 70, 50), rgb(160, 120, 62), rgb(208, 170, 82), rgb(238, 210, 112), rgb(250, 232, 152), rgb(255, 246, 200))
    private val brass = Ramp(rgb(60, 32, 30), rgb(104, 62, 34), rgb(152, 100, 42), rgb(196, 144, 58), rgb(228, 186, 88), rgb(246, 222, 140), rgb(255, 244, 198))
    private val indigo = Ramp(rgb(14, 14, 40), rgb(24, 28, 68), rgb(38, 46, 100), rgb(58, 70, 136), rgb(88, 104, 172), rgb(132, 148, 206), rgb(186, 198, 234))

    private enum class Ears { NONE, CAT, TUFT, HORNS, ANTENNA, CROWN }

    private class Shape(val body: Ramp, val belly: Ramp?, val ears: Ears, val beak: Boolean = false, val wings: Ramp? = null, val tail: Boolean = false)

    private fun shapeOf(sp: Species): Shape = when (sp) {
        Species.HYDRA -> Shape(jade, R.belly, Ears.HORNS, tail = true)
        Species.BAYUN -> Shape(smoke, R.cream, Ears.CAT, tail = true)
        Species.SNAIL -> Shape(butter, null, Ears.ANTENNA)
        Species.SALAMANDER -> Shape(R.ember, R.belly, Ears.NONE, tail = true)
        Species.AUTOMATON -> Shape(brass, R.iron, Ears.ANTENNA)
        Species.PIXIE -> Shape(R.lilac, null, Ears.NONE, wings = R.glass)
        Species.RAVEN -> Shape(indigo, null, Ears.NONE, beak = true, wings = indigo)
        Species.SPHINX -> Shape(R.tan, R.cream, Ears.CROWN, tail = true)
        else -> Shape(R.owl, R.disc, Ears.TUFT, beak = true, wings = R.owl)
    }

    fun draw(pen: Pen, ctx: Sprites.Ctx): Face {
        val px = pen.px
        val sp = ctx.look.species
        val st = ctx.look.stage
        val s = shapeOf(sp)
        val sway = ctx.sw * 3f
        if (s.tail) {
            val tail = pen.path(sway, 66f, 82f).m(62f, 84f).c(78f, 88f, 90f, 78f, 86f, 64f).c(84f, 58f, 78f, 60f, 78f, 66f).c(78f, 74f, 70f, 78f, 62f, 78f).shape()
            px.shade(tail, pen.turnedDome(78f, 74f, 12f, 12f, sway, 66f, 82f), s.body, rim = s.body[5])
            // The salamander's tail ends in a little flame.
            if (sp == Species.SALAMANDER) px.shade(pen.ell(87f + sway * 0.2f, 60f, 4f, 6f), pen.dome(87f, 60f, 4f, 6f), R.flame, outline = false, lo = 3)
        }
        s.wings?.let { ramp ->
            for (side in listOf(-1f, 1f)) {
                val flap = ctx.sw * 5f * side
                val wing = pen.path(flap, 50f + 14f * side, 60f).m(50f + 14f * side, 56f).q(50f + 36f * side, 44f, 50f + 38f * side, 62f).q(50f + 30f * side, 76f, 50f + 14f * side, 70f).shape()
                px.shade(wing, pen.turnedDome(50f + 26f * side, 62f, 12f, 12f, flap, 50f + 14f * side, 60f), ramp, rim = ramp[ramp.size - 2], lo = 1)
            }
        }
        // The snail's house, behind and above it.
        if (sp == Species.SNAIL) {
            px.shade(pen.ell(60f, 58f, 19f, 18f), pen.dome(58f, 56f, 19f, 18f), R.wood, spec = true, rim = R.wood[5])
            for (r in listOf(12f, 6f)) px.shade(minus(pen.ell(60f, 58f, r, r), pen.ell(60f, 58f, r - 2f, r - 2f)), facing(), R.wood, outline = false, lo = 1, hi = 2)
        }
        val body = pen.path().m(50f, 48f).c(66f, 48f, 73f, 66f, 71f, 84f).q(50f, 91f, 29f, 84f).c(27f, 66f, 34f, 48f, 50f, 48f).shape()
        px.shade(body, pen.dome(50f, 68f, 22f, 21f), s.body, rim = s.body[5], texture = if (sp == Species.AUTOMATON) null else Sprites.fur(61, 0.1f))
        s.belly?.let { px.shade(pen.ell(50f, 73f, 12f, 12f), pen.dome(49f, 71f, 12f, 12f), it, outline = false, lo = 2) }
        for (fx in listOf(40f, 60f)) px.shade(pen.ell(fx, 86.5f, 6f, 3.2f), pen.dome(fx, 85.5f, 6f, 3.2f), s.body, lo = 0, hi = 3)
        when (s.ears) {
            Ears.CAT -> for (side in listOf(-1f, 1f)) {
                val ear = pen.path().m(50f + 8f * side, 26f).l(50f + 17f * side, 10f).l(50f + 21f * side, 30f).shape()
                px.shade(ear, facing(0.3f * side, -0.3f, 1f), s.body, lo = 1, hi = 4)
            }
            Ears.TUFT -> for (side in listOf(-1f, 1f)) {
                val ear = pen.path().m(50f + 10f * side, 24f).l(50f + 20f * side, 14f).l(50f + 19f * side, 30f).shape()
                px.shade(ear, facing(0.3f * side, -0.3f, 1f), s.body, lo = 0, hi = 3)
            }
            Ears.HORNS -> for (side in listOf(-1f, 1f)) {
                // Two smaller heads on long necks, one on either side: it is a hydra after all.
                if (st >= Stage.YOUNG) px.shade(pen.ell(50f + 26f * side, 34f, 8f, 7.4f), pen.dome(50f + 26f * side, 33f, 8f, 7.4f), s.body, rim = s.body[5])
                val horn = pen.path().m(50f + 7f * side, 22f).l(50f + 12f * side, 12f).l(50f + 14f * side, 24f).shape()
                px.shade(horn, facing(0.3f * side, -0.2f, 1f), R.horn, lo = 1)
            }
            Ears.ANTENNA -> for (side in listOf(-1f, 1f)) {
                val (x0, y0) = pen.at(50f + 7f * side, 24f)
                val (x1, y1) = pen.at(50f + 12f * side + ctx.sw, 11f)
                px.line(x0, y0, x1, y1, s.body[1])
                px.shade(pen.ell(50f + 12f * side + ctx.sw, 10f, 2.4f, 2.4f), facing(), if (sp == Species.AUTOMATON) R.red else s.body, lo = 3)
            }
            Ears.CROWN -> {
                // The striped headcloth falling to the shoulders.
                val cloth = pen.path().m(50f, 16f).c(70f, 16f, 74f, 36f, 72f, 56f).l(62f, 56f).l(62f, 30f).l(38f, 30f).l(38f, 56f).l(28f, 56f).c(26f, 36f, 30f, 16f, 50f, 16f).shape()
                px.shade(cloth, pen.dome(50f, 36f, 23f, 22f), R.gold, texture = { _, y -> if (y % 4 < 2) -0.3f else 0f }, rim = R.gold[5])
            }
            Ears.NONE -> {}
        }
        px.shade(pen.ell(50f, 36f, 17f, 15f), pen.dome(50f, 34f, 17f, 15f), s.body, rim = s.body[5], texture = if (sp == Species.AUTOMATON) null else Sprites.fur(62, 0.08f))
        if (s.beak) {
            val beak = pen.path().m(46f, 40f).l(54f, 40f).l(50f, 47f).shape()
            px.shade(beak, facing(-0.2f, -0.3f, 1f), R.beak, lo = 1)
        } else {
            val mx = pen.x(50f).toInt(); val my = pen.y(43f).toInt()
            px.set(mx - 1, my, EYE); px.set(mx, my + 1, EYE); px.set(mx + 1, my, EYE)
            if (ctx.look.chew) px.rect(mx - 1, my + 1, 3, 2, R.mouth[2])
        }
        // A lamp in the automaton's chest, a flame on the salamander's back: both flicker.
        if (sp == Species.AUTOMATON) px.glow(pen.x(50f), pen.y(70f), pen.r(if (ctx.f % 2 == 0) 6f else 5f), R.gold[5], 150, 3, onlyDrawn = true)
        return Sprites.face(px, pen, ctx, listOf(43f to 36f, 57f to 36f), 3.6f, s.body[1], s.body[4], 50f to 35f, 16f)
    }

    /** The colour of each one's egg. */
    fun eggRamp(sp: Species): Ramp = shapeOf(sp).body
}
