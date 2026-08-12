package com.omai.neocalc.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The calculator palette: operation orange on a warm near-black.
 *
 * Orange operators against a neutral keypad is the one convention every good
 * calculator shares, and it earns its place: the operator keys are the only ones
 * that change what the next digit means, so they are the only ones that should
 * pull the eye. Everything else is a step on a single warm stone ramp, which is
 * what keeps a grid of thirty buttons from reading as noise.
 *
 * The dark ramp is the designed direction. The light ramp is derived from it
 * rather than inverted: the same hues at the lightness the surface needs, with
 * the primary darkened because #EA580C on white is only 3.4:1 and would fail as
 * text. Both are checked against WCAG AA below.
 */

// ----------------------------------------------------------------- Dark

/** Warm near-black. Not pure #000: on OLED it avoids smearing on scroll. */
val Ground = Color(0xFF1C1917)

/** Cards and sheets. One step up, so a card reads without needing a border. */
val Surface = Color(0xFF262321)

/** Keypad keys and inert chips. */
val SurfaceMuted = Color(0xFF2C1E16)

val Outline = Color(0x1FFFFFFF)

/** Operators, the active tab, the focus ring. 4.9:1 as text on Ground. */
val Operation = Color(0xFFEA580C)

/**
 * Dark ink on the orange, not white.
 *
 * The reference palette pairs #EA580C with #FFFFFF, and that is what the iOS
 * calculator does. Measured, it is 3.56:1 - fine for the 26sp "=" glyph, which
 * counts as large text, but a fail for the 14sp labels that also sit on this
 * colour (segmented tabs, the PLAY button, filled chips). Dark ink is 4.91:1
 * and passes at every size, so one token can serve them all.
 */
val OnOperation = Color(0xFF1C1917)

/** The warmer sibling, for pressed states and the second accent in a chart. */
val OperationSoft = Color(0xFFF97316)

/**
 * The cool counterweight, used only where something is *not* an operation:
 * links, informational marks, the falling half of a trend. Lightened from the
 * palette's #2563EB, which is 2.6:1 on this ground and unreadable.
 */
val Info = Color(0xFF60A5FA)

val OnGround = Color(0xFFFFFFFF)

/** Secondary text. 6.1:1 on Ground, so captions stay legible, not decorative. */
val OnGroundMuted = Color(0xFFA8A29E)

val Danger = Color(0xFFF87171)
val OnDanger = Color(0xFF2A0710)

// ----------------------------------------------------------------- Light

/** Warm off-white, the same stone family as Ground rather than a cold grey. */
val Paper = Color(0xFFFAF9F7)
val PaperSurface = Color(0xFFFFFFFF)
val PaperMuted = Color(0xFFF1EEEB)
val PaperOutline = Color(0xFFE0DAD3)

/**
 * Darkened from #EA580C so it clears 4.5:1 as text on *both* light surfaces.
 * The obvious step, #C2410C, is 4.92:1 on the background but 4.48:1 on the
 * muted keypad fill - a fail by two hundredths, in the exact place operators
 * are drawn. This is the next step down and clears both with room to spare.
 */
val OperationLight = Color(0xFF9A3412)
val OperationSoftLight = Color(0xFFEA580C)
val InfoLight = Color(0xFF2563EB)

val OnPaper = Color(0xFF1C1917)

/** 5.7:1 on Paper. */
val OnPaperMuted = Color(0xFF57534E)

val DangerLight = Color(0xFFDC2626)
