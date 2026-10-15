package com.lampremote

import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Instructions / compatible lamps / pairing / about / licenses (text lives in strings.xml, EN + ZH). */
class InfoActivity : BaseActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val GITHUB = "https://github.com/geethut-stack"
        const val EMAIL = "1840861172@qq.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.getStringExtra(EXTRA_PAGE) ?: "about"
        val (titleRes, bodyRes) = when (page) {
            "instructions" -> Pair(R.string.page_instructions_title, R.string.page_instructions)
            "compat" -> Pair(R.string.page_compat_title, R.string.page_compat)
            "pairing" -> Pair(R.string.page_pairing_title, R.string.page_pairing)
            "license" -> Pair(R.string.page_license_title, R.string.page_license)
            else -> Pair(R.string.page_about_title, R.string.page_about)
        }
        val col = scaffold(getString(titleRes), false)
        if (page == "about") {
            val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "1.0" }
            col.addTo(card {
                addView(iconBadge(R.drawable.ic_bulb, 64))
                addTo(titleText(getString(R.string.app_name)), 14)
                addTo(hintText(getString(R.string.app_tagline)), 4)
                addTo(hintText(getString(R.string.version_fmt, version)), 4)
            }, 8)
            col.addView(divider())
        }
        col.addRichText(getString(bodyRes))
        if (page == "about") {
            col.addView(divider())
            col.addTo(bigButton(getString(R.string.open_github), R.drawable.ic_info, true) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB)))
            }, 0)
            col.addTo(bigButton(getString(R.string.send_email), R.drawable.ic_doc, false) {
                startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL")))
            }, 12)
        }
    }
}
