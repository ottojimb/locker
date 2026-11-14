package com.ottojimb.locker

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Settings screen. Built in code with platform views only: large rounded cards, switches and
 * pill buttons, high contrast and no shadows or ripples so it renders cleanly on e-ink.
 */
class MainActivity : Activity() {

    private lateinit var palette: Palette
    private lateinit var serviceDot: View
    private lateinit var serviceStatus: TextView
    private lateinit var serviceButton: Button
    private lateinit var overlayStatus: TextView
    private lateinit var preview: ImageView
    private lateinit var previewEmpty: TextView
    private lateinit var clearButton: Button

    /** Monochrome palette; dark mode swaps it. */
    private class Palette(
        val background: Int,
        val surface: Int,
        val onSurface: Int,
        val muted: Int,
        val outline: Int,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        palette = if (night) {
            Palette(0xFF000000.toInt(), 0xFF1C1C1C.toInt(), 0xFFFFFFFF.toInt(), 0xFFBDBDBD.toInt(), 0xFFFFFFFF.toInt())
        } else {
            Palette(0xFFFFFFFF.toInt(), 0xFFF2F2F2.toInt(), 0xFF000000.toInt(), 0xFF4A4A4A.toInt(), 0xFF000000.toInt())
        }
        window.statusBarColor = palette.background
        window.navigationBarColor = palette.background
        if (!night) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
            addView(header())
            addView(serviceCard(), cardParams())
            addView(sectionLabel(R.string.section_sleep))
            addView(sleepCard(), cardParams(top = 0))
            addView(sectionLabel(R.string.section_image))
            addView(imageCard(), cardParams(top = 0))
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(palette.background)
                isVerticalScrollBarEnabled = false
                addView(content)
            },
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_PICK -> OverlayStore.save(this, uri)
            REQUEST_FOLDER -> OverlayStore.setFolder(this, uri)
        }
        onOverlayChanged()
    }

    // region Sections

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), 0, dp(4), dp(20))
        addView(text(getString(R.string.app_name), 44f, bold = true).apply {
            letterSpacing = -0.02f
        })
        addView(text(getString(R.string.tagline), 17f, color = palette.muted))
    }

    private fun serviceCard() = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        serviceDot = View(this@MainActivity)
        addView(serviceDot, LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(14) })
        serviceStatus = text("", 18f, bold = true)
        addView(serviceStatus, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        serviceButton = pill(R.string.open_accessibility, filled = false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addView(serviceButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(12) })
    }

    private fun sleepCard() = card(vertical = 8).apply {
        addView(
            switchRow(R.string.enabled, R.string.enabled_summary, OverlayStore.isEnabled(this@MainActivity)) {
                OverlayStore.setEnabled(this@MainActivity, it)
            },
        )
        addView(divider())
        addView(
            switchRow(R.string.invert_dark, R.string.invert_dark_summary, OverlayStore.invertInDarkMode(this@MainActivity)) {
                OverlayStore.setInvertInDarkMode(this@MainActivity, it)
            },
        )
        addView(divider())
        addView(
            switchRow(R.string.keep_colors, R.string.keep_colors_summary, OverlayStore.keepOriginalColors(this@MainActivity)) {
                OverlayStore.setKeepOriginalColors(this@MainActivity, it)
            },
        )
    }

    private fun imageCard() = card().apply {
        val frame = FrameLayout(this@MainActivity).apply {
            background = rounded(palette.background, dp(20), stroke = palette.outline)
            clipToOutline = true
            minimumHeight = dp(180)
        }
        preview = ImageView(this@MainActivity).apply {
            adjustViewBounds = true
            maxHeight = dp(420)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        previewEmpty = text(getString(R.string.preview_empty), 16f, color = palette.muted).apply {
            gravity = Gravity.CENTER
        }
        frame.addView(preview, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        frame.addView(previewEmpty, FrameLayout.LayoutParams(MATCH_PARENT, dp(180), Gravity.CENTER))
        addView(frame, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        overlayStatus = text("", 16f, color = palette.muted).apply { setPadding(dp(4), dp(14), dp(4), dp(16)) }
        addView(overlayStatus)

        addView(
            pill(R.string.pick_overlay, filled = true) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*")
                startActivityForResult(intent, REQUEST_PICK)
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
        )
        addView(
            pill(R.string.pick_folder, filled = false) {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_FOLDER)
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) },
        )
        clearButton = pill(R.string.clear_overlay, filled = false, borderless = true) {
            OverlayStore.clear(this@MainActivity)
            onOverlayChanged()
        }
        addView(clearButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })
    }

    // endregion

    private fun onOverlayChanged() {
        LockerService.instance?.reloadOverlay()
        refresh()
    }

    private fun refresh() {
        val running = LockerService.instance != null
        serviceStatus.setText(if (running) R.string.status_service_on else R.string.status_service_off)
        serviceDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (running) setColor(palette.onSurface) else setStroke(dp(2), palette.onSurface)
        }
        serviceButton.visibility = if (running) View.GONE else View.VISIBLE

        Thread {
            val folder = OverlayStore.folder(this)
            val count = if (folder != null) OverlayStore.folderImages(this).size else 0
            val image = OverlayStore.preview(this)
            runOnUiThread {
                overlayStatus.text = when {
                    folder != null -> getString(R.string.status_folder, folderName(folder), count)
                    image != null -> getString(R.string.status_overlay_on, image.width, image.height)
                    else -> getString(R.string.status_overlay_off)
                }
                preview.setImageBitmap(image)
                previewEmpty.visibility = if (image == null) View.VISIBLE else View.GONE
                clearButton.visibility = if (folder != null || image != null) View.VISIBLE else View.GONE
            }
        }.start()
    }

    /** "primary:Pictures/Locker" -> "Pictures/Locker". */
    private fun folderName(tree: Uri) = tree.lastPathSegment.orEmpty().substringAfter(':')

    // region View helpers

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        if (stroke != null) setStroke(dp(2), stroke)
    }

    private fun card(vertical: Int = 20) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(palette.surface, dp(28))
        setPadding(dp(20), dp(vertical), dp(20), dp(vertical))
    }

    private fun cardParams(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        topMargin = dp(top)
    }

    private fun sectionLabel(textRes: Int) = text(getString(textRes), 15f, bold = true).apply {
        isAllCaps = true
        letterSpacing = 0.08f
        setPadding(dp(8), dp(28), dp(8), dp(10))
    }

    private fun text(value: String, size: Float, bold: Boolean = false, color: Int = palette.onSurface) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.SANS_SERIF
        }

    private fun divider() = View(this).apply {
        setBackgroundColor(palette.muted)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1))
        alpha = 0.4f
    }

    private fun switchRow(titleRes: Int, summaryRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            val labels = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(getString(titleRes), 18f, bold = true))
                addView(text(getString(summaryRes), 15f, color = palette.muted).apply { setPadding(0, dp(2), 0, 0) })
            }
            addView(labels, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(16) })
            val toggle = Switch(this@MainActivity).apply {
                isChecked = checked
                background = null
                showText = false
                switchMinWidth = dp(56)
                thumbDrawable = switchState(
                    on = InsetDrawable(oval(palette.background, dp(24)), dp(4)),
                    off = InsetDrawable(oval(palette.onSurface, dp(16)), dp(8)),
                )
                trackDrawable = switchState(
                    on = rounded(palette.onSurface, dp(16)).apply { setSize(dp(56), dp(32)) },
                    off = rounded(palette.background, dp(16), stroke = palette.onSurface).apply { setSize(dp(56), dp(32)) },
                )
                setOnCheckedChangeListener { _, value -> onChange(value) }
            }
            addView(toggle)
            // The whole row toggles, a bigger target than the switch alone.
            setOnClickListener { toggle.toggle() }
        }

    private fun oval(fill: Int, size: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setSize(size, size)
    }

    /** M3-style switch part: a large filled thumb when on, a small one when off. */
    private fun switchState(on: Drawable, off: Drawable) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_checked), on)
        addState(intArrayOf(), off)
    }

    private fun pill(textRes: Int, filled: Boolean, borderless: Boolean = false, onClick: () -> Unit) =
        Button(this).apply {
            setText(textRes)
            textSize = 17f
            isAllCaps = false
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            stateListAnimator = null
            minHeight = dp(56)
            setPadding(dp(24), 0, dp(24), 0)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(if (filled) palette.background else palette.onSurface)
            background = when {
                filled -> rounded(palette.onSurface, dp(28))
                borderless -> null
                else -> rounded(palette.surface, dp(28), stroke = palette.outline)
            }
            setOnClickListener { onClick() }
        }

    // endregion

    private companion object {
        const val REQUEST_PICK = 1
        const val REQUEST_FOLDER = 2
    }
}
