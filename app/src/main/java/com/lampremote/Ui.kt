package com.lampremote

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.google.android.material.R as MR

/** UI toolkit: themed, large and easy to read. All screens are built in code with these helpers. */


fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.themed(@AttrRes attr: Int): Int = MaterialColors.getColor(this, attr, Color.MAGENTA)
val Context.cPrimary get() = themed(MR.attr.colorPrimary)
val Context.cOnPrimary get() = themed(MR.attr.colorOnPrimary)
val Context.cContainer get() = themed(MR.attr.colorPrimaryContainer)
val Context.cOnContainer get() = themed(MR.attr.colorOnPrimaryContainer)
val Context.cText get() = themed(MR.attr.colorOnSurface)
val Context.cTextDim get() = themed(MR.attr.colorOnSurfaceVariant)
val Context.cLine get() = themed(MR.attr.colorOutlineVariant)
val Context.cCard get() = themed(MR.attr.colorSurfaceContainer)

// ------------------------------------------------------------------ text

fun Context.text(text: CharSequence, sp: Float, bold: Boolean = false, color: Int = cText): TextView =
    TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(0f, 1.12f)
    }

fun Context.titleText(t: CharSequence) = text(t, 30f, true)
fun Context.bodyText(t: CharSequence, sp: Float = 18f) = text(t, sp)
fun Context.hintText(t: CharSequence, sp: Float = 16f) = text(t, sp, false, cTextDim)

fun Context.icon(@DrawableRes res: Int, sizeDp: Int = 28, tint: Int = cPrimary): ImageView =
    ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(tint)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

/** Round tinted badge with an icon (used on lamp cards / empty state). */
fun Context.iconBadge(@DrawableRes res: Int, sizeDp: Int = 52): View =
    LinearLayout(this).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cContainer) }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        addView(icon(res, sizeDp / 2, cOnContainer))
    }

/** Round number badge ("1", "2", "3"). */
fun Context.numberBadge(n: Int): View =
    TextView(this).apply {
        text = n.toString()
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(cOnPrimary)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cPrimary) }
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
    }

fun Context.sectionHeader(@DrawableRes iconRes: Int, title: CharSequence, badge: View? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(badge ?: icon(iconRes, 30))
        addView(text(title, 21f, true).apply { setPadding(dp(14), 0, 0, 0) })
    }

// ------------------------------------------------------------------ containers

/** A rounded, outlined card holding a vertical column. */
fun Context.card(onClick: (() -> Unit)? = null, build: LinearLayout.() -> Unit): MaterialCardView {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        build()
    }
    return MaterialCardView(this).apply {
        radius = dp(24).toFloat()
        cardElevation = 0f
        strokeWidth = dp(2)
        setStrokeColor(ColorStateList.valueOf(cLine))
        setCardBackgroundColor(cCard)
        addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (onClick != null) {
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }
}

/** Very visible separator between sections. */
fun Context.divider(): View = View(this).apply {
    setBackgroundColor(cLine)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).apply {
        topMargin = dp(18); bottomMargin = dp(18)
    }
}

/** Separator with a centered label, e.g. "— OR —". */
fun Context.labelDivider(label: String): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    val line = { View(context).apply { setBackgroundColor(context.cLine); layoutParams = LinearLayout.LayoutParams(0, dp(3), 1f) } }
    addView(line())
    addView(text(label, 18f, true, cPrimary).apply { setPadding(dp(16), 0, dp(16), 0) })
    addView(line())
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(18); bottomMargin = dp(18)
    }
}

// ------------------------------------------------------------------ buttons / sliders

/** Big rounded button. [filled] = solid primary, otherwise outlined. */
fun Context.bigButton(label: CharSequence, @DrawableRes iconRes: Int? = null, filled: Boolean = true, heightDp: Int = 64, onClick: () -> Unit): MaterialButton {
    val b = if (filled) MaterialButton(this) else MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
    b.text = label
    b.textSize = 19f
    b.setAllCaps(false)
    b.minimumHeight = dp(heightDp)
    b.cornerRadius = dp(20)
    b.insetTop = 0
    b.insetBottom = 0
    if (!filled) b.strokeWidth = dp(2)
    if (iconRes != null) {
        b.icon = ContextCompat.getDrawable(this, iconRes)
        b.iconSize = dp(26)
        b.iconPadding = dp(10)
        b.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
    }
    b.setOnClickListener { onClick() }
    return b
}

/** Small square-ish number button (timer shortcuts). */
fun Context.chipButton(label: String, onClick: () -> Unit): MaterialButton =
    bigButton(label, null, false, 52, onClick).apply {
        textSize = 18f
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
    }

/** Thick slider (easy to grab). */
fun Context.thickSlider(from: Float, to: Float, value: Float, discrete: Boolean = false): Slider = Slider(this).apply {
    valueFrom = from
    valueTo = to
    if (discrete) stepSize = 1f // continuous otherwise: no step validation, no tick dots
    this.value = value.coerceIn(from, to)
    trackHeight = dp(20)
    thumbRadius = dp(17)
    haloRadius = dp(28)
    labelBehavior = LabelFormatter.LABEL_GONE
}

// ------------------------------------------------------------------ layout helpers

fun <T : View> LinearLayout.addTo(v: T, topDp: Int = 12): T {
    addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = context.dp(topDp)
    })
    return v
}

/** Horizontal row giving every child the same width. */
fun Context.row(vararg views: View, gapDp: Int = 6): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    views.forEach {
        addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(gapDp / 2); marginEnd = dp(gapDp / 2)
        })
    }
}

/** Renders simple markup: "# Heading", "- bullet", blank-line separated paragraphs. */
fun LinearLayout.addRichText(markup: String) {
    var first = true
    for (raw in markup.split("\n")) {
        val line = raw.trimEnd()
        if (line.isBlank()) continue
        when {
            line.startsWith("# ") -> {
                if (!first) addView(context.divider())
                addTo(context.text(line.removePrefix("# "), 23f, true, context.cPrimary), 0)
            }
            line.startsWith("- ") -> addTo(linked(context.bodyText("•  " + line.removePrefix("- "), 18f)), 10)
            else -> addTo(linked(context.bodyText(line, 17f)), 10)
        }
        first = false
    }
}

private fun linked(tv: TextView): TextView {
    tv.autoLinkMask = Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES
    tv.setLinkTextColor(tv.context.cPrimary)
    return tv
}
