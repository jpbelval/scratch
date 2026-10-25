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
    private val pickPhoto = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(17, 24, 39)
        window.navigationBarColor = Color.rgb(17, 24, 39)

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
        }
        val subtitle = TextView(this).apply {
            text = "Choose a photo, then scratch to reveal it"
            textSize = 15f
            setTextColor(Color.rgb(203, 213, 225))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(20))
        }
        scratchView = ScratchView(this)
        scratchView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        scratchView.setOnRevealStateChanged { revealed ->
            hint.text = if (revealed) "Revealed! It will fade away and reset shortly." else "Scratch the silver cover with your finger"
        }
        hint = TextView(this).apply {
            text = "Scratch the silver cover with your finger"
            textSize = 14f
            setTextColor(Color.rgb(203, 213, 225))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(14))
        }
        val choose = Button(this).apply {
            text = "Choose a photo"
            isAllCaps = false
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, pickPhoto)
            }
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
        root.addView(choose, LinearLayout.LayoutParams(-1, -2))
        root.addView(reset, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    @Deprecated("Deprecated in Android but supported for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickPhoto && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            scratchView.loadPhoto(uri)
            Toast.makeText(this, "Photo loaded", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
