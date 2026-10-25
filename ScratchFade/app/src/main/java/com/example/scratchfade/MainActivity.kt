package com.example.scratchfade

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var scratchView: ScratchView
    private lateinit var hint: TextView
    private lateinit var subtitle: TextView
    private lateinit var chooseButton: Button
    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(Color.rgb(17, 24, 39))
        }
        val title = TextView(this).apply {
            text = "ScratchFade"
            textSize = 29f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            // Hidden shortcut so the picker stays out of sight during the trick.
            setOnLongClickListener {
                openPhotoPicker()
                true
            }
        }
        subtitle = TextView(this).apply {
            text = "Choose a photo, then scratch to reveal it"
            textSize = 15f
            setTextColor(Color.rgb(203, 213, 225))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(20))
        }
        scratchView = ScratchView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        hint = TextView(this).apply {
            text = "Choose a photo to begin"
            textSize = 14f
            setTextColor(Color.rgb(203, 213, 225))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(14))
        }
        scratchView.setOnRevealStateChanged { revealed ->
            hint.text = when {
                revealed -> "Revealed! It will fade away and reset shortly."
                photoUri == null -> "Choose a photo to begin"
                else -> "Scratch the silver cover with your finger"
            }
        }
        chooseButton = Button(this).apply {
            text = "Choose a photo"
            isAllCaps = false
            setOnClickListener { openPhotoPicker() }
        }
        val reset = Button(this).apply {
            text = "Reset scratch card"
            isAllCaps = false
            setOnClickListener { scratchView.resetCard() }
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(scratchView)
        root.addView(hint, LinearLayout.LayoutParams(-1, -2))
        root.addView(chooseButton, LinearLayout.LayoutParams(-1, -2))
        root.addView(reset, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        savedInstanceState?.getString(STATE_PHOTO_URI)?.let { saved ->
            showPickedPhoto(Uri.parse(saved))
        }
    }

    private fun openPhotoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, PICK_PHOTO)
    }

    private fun showPickedPhoto(uri: Uri): Boolean {
        val previous = photoUri
        // Set this before loading so the reveal listener reports the right hint.
        photoUri = uri
        if (!scratchView.loadPhoto(uri)) {
            photoUri = previous
            return false
        }
        chooseButton.visibility = View.GONE
        subtitle.text = "Scratch the silver cover to reveal the secret"
        return true
    }

    @Deprecated("Deprecated in Android but supported for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PHOTO && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            if (showPickedPhoto(uri)) {
                Toast.makeText(this, "Photo loaded", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not load that photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        photoUri?.let { outState.putString(STATE_PHOTO_URI, it.toString()) }
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PICK_PHOTO = 42
        const val STATE_PHOTO_URI = "photo_uri"
    }
}
