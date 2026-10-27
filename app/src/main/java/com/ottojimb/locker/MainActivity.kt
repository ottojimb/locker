package com.ottojimb.locker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var serviceStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var preview: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        serviceStatus = TextView(this).apply { textSize = 18f }
        overlayStatus = TextView(this).apply { textSize = 18f }
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFFDDDDDD.toInt())
        }

        val enabled = CheckBox(this).apply {
            text = getString(R.string.enabled)
            textSize = 18f
            isChecked = OverlayStore.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> OverlayStore.setEnabled(this@MainActivity, checked) }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            addView(serviceStatus)
            addView(button(R.string.open_accessibility) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            addView(enabled)
            addView(overlayStatus)
            addView(button(R.string.pick_overlay) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*")
                startActivityForResult(intent, REQUEST_PICK)
            })
            addView(button(R.string.pick_folder) {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_FOLDER)
            })
            addView(button(R.string.clear_overlay) {
                OverlayStore.clear(this@MainActivity)
                onOverlayChanged()
            })
            addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = pad })
        }
        setContentView(root)
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

    private fun onOverlayChanged() {
        LockerService.instance?.reloadOverlay()
        refresh()
    }

    private fun refresh() {
        serviceStatus.setText(
            if (LockerService.instance != null) R.string.status_service_on else R.string.status_service_off,
        )
        Thread {
            val folder = OverlayStore.folder(this)
            val count = if (folder != null) OverlayStore.folderImages(this).size else 0
            val overlay = if (folder == null) OverlayStore.load(this) else null
            runOnUiThread {
                overlayStatus.text = when {
                    folder != null -> getString(R.string.status_folder, folder.lastPathSegment, count)
                    overlay != null -> getString(R.string.status_overlay_on, overlay.width, overlay.height)
                    else -> getString(R.string.status_overlay_off)
                }
                preview.setImageBitmap(overlay)
            }
        }.start()
    }

    private fun button(textRes: Int, onClick: () -> Unit) = Button(this).apply {
        setText(textRes)
        textSize = 18f
        setOnClickListener { onClick() }
    }

    private companion object {
        const val REQUEST_PICK = 1
        const val REQUEST_FOLDER = 2
    }
}
