package com.lampremote

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Screen scaffold: top bar + scrolling content, optionally with the sidebar (hamburger). */
abstract class BaseActivity : PermActivity() {

    private var drawer: DrawerLayout? = null

    /** Builds the screen and returns the column to fill with content. */
    protected fun scaffold(title: String, withDrawer: Boolean): LinearLayout {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(40))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val toolbar = MaterialToolbar(this).apply {
            this.title = title
            setNavigationIcon(if (withDrawer) R.drawable.ic_menu else R.drawable.ic_back)
            setNavigationIconTint(cText)
            setTitleTextColor(cText)
            setBackgroundColor(themed(com.google.android.material.R.attr.colorSurface))
            setNavigationOnClickListener { if (withDrawer) drawer?.openDrawer(GravityCompat.START) else finish() }
        }
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        if (!withDrawer) {
            setContentView(main)
        } else {
            val root = DrawerLayout(this)
            root.addView(main, DrawerLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            root.addView(buildDrawer(), DrawerLayout.LayoutParams(dp(310), ViewGroup.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.START })
            drawer = root
            setContentView(root)
        }
        return content
    }

    private fun buildDrawer(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themed(com.google.android.material.R.attr.colorSurface))
            setPadding(dp(8), dp(28), dp(8), dp(16))
        }
        col.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), dp(8))
                addView(iconBadge(R.drawable.ic_bulb, 56))
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), 0, 0, 0)
                        addView(text(getString(R.string.app_name), 20f, true))
                        addView(hintText(getString(R.string.app_tagline), 14f))
                    },
                )
            },
        )
        col.addView(divider())
        fun item(res: Int, label: Int, action: () -> Unit) = col.addView(drawerRow(res, getString(label)) {
            drawer?.closeDrawers()
            action()
        })
        item(R.drawable.ic_language, R.string.menu_language) { showLanguageDialog() }
        item(R.drawable.ic_help, R.string.menu_instructions) { openPage("instructions") }
        item(R.drawable.ic_bulb, R.string.menu_compat) { openPage("compat") }
        item(R.drawable.ic_bluetooth, R.string.menu_pairing) { openPage("pairing") }
        item(R.drawable.ic_info, R.string.menu_about) { openPage("about") }
        item(R.drawable.ic_doc, R.string.menu_license) { openPage("license") }
        return col
    }

    private fun drawerRow(iconRes: Int, label: String, onClick: () -> Unit): View {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            minimumHeight = dp(62)
            setBackgroundResource(tv.resourceId)
            isClickable = true
            isFocusable = true
            addView(icon(iconRes, 26, cTextDim))
            addView(text(label, 18f).apply { setPadding(dp(18), 0, 0, 0) })
            setOnClickListener { onClick() }
        }
    }

    protected fun openPage(page: String) {
        startActivity(Intent(this, InfoActivity::class.java).putExtra(InfoActivity.EXTRA_PAGE, page))
    }

    private fun showLanguageDialog() {
        val locales = AppCompatDelegate.getApplicationLocales()
        val lang = if (!locales.isEmpty) locales[0]?.language else resources.configuration.locales[0].language
        val current = if (lang == "zh") 1 else 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lang_title)
            .setSingleChoiceItems(arrayOf(getString(R.string.lang_en), getString(R.string.lang_zh)), current) { d, which ->
                d.dismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (which == 1) "zh" else "en"))
            }
            .show()
    }
}
