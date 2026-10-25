package com.example.scratchfade

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var root: View
    private lateinit var setupRoot: ScrollView
    private lateinit var performanceRoot: View
    private lateinit var wipeView: MagicWipeView
    private lateinit var heroPreview: HeroPreviewView
    private lateinit var screenPreview: PreviewImageView
    private lateinit var messagePreview: PreviewImageView
    private lateinit var startButton: Button

    private var screenBitmap: Bitmap? = null
    private var messageBitmap: Bitmap? = null
    private var performanceActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        setupRoot = findViewById(R.id.setupRoot)
        performanceRoot = findViewById(R.id.performanceRoot)
        wipeView = findViewById(R.id.wipeView)
        heroPreview = findViewById(R.id.heroPreview)
        screenPreview = findViewById(R.id.screenPreview)
        messagePreview = findViewById(R.id.messagePreview)
        startButton = findViewById(R.id.startButton)

        applyWindowInsets()

        findViewById<TextView>(R.id.infoButton).setOnClickListener { showInfo() }
        startButton.setOnClickListener { startPerformance() }
        findViewById<Button>(R.id.setScreenButton).setOnClickListener { pickImage(REQUEST_SCREEN) }
        findViewById<Button>(R.id.setMessageButton).setOnClickListener { pickImage(REQUEST_MESSAGE) }

        wipeView.onRevealed = {
            wipeView.announceForAccessibility(getString(R.string.tap_to_replay))
        }
        wipeView.onExitRequested = {
            root.post { exitPerformance() }
        }

        loadCards()
        registerBackHandler()
    }

    private fun registerBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBack() }
        }
    }

    private fun handleBack() {
        if (performanceActive) {
            exitPerformance()
        } else {
            finish()
        }
    }

    private fun configureEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun applyWindowInsets() {
        setupRoot.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }
    }

    private fun loadCards() {
        screenBitmap = CardStore.load(this, CardStore.SCREEN) ?: MagicArt.defaultScreen()
        messageBitmap = CardStore.load(this, CardStore.MESSAGE) ?: MagicArt.defaultMessage()
        updatePreviews()
    }

    private fun updatePreviews() {
        screenPreview.setBitmap(screenBitmap)
        messagePreview.setBitmap(messageBitmap)
        heroPreview.setContent(screenBitmap, messageBitmap)
        startButton.isEnabled = screenBitmap != null && messageBitmap != null
    }

    private fun startPerformance() {
        val screen = screenBitmap
        val message = messageBitmap
        if (screen == null || message == null) {
            Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show()
            return
        }
        if (performanceActive) return

        wipeView.setCards(screen, message)
        performanceRoot.alpha = 0f
        performanceRoot.visibility = View.VISIBLE
        setupRoot.visibility = View.GONE
        performanceActive = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        performanceRoot.animate().alpha(1f).setDuration(180L).start()
        root.requestApplyInsets()
        wipeView.post {
            if (performanceActive) {
                wipeView.prepare()
                wipeView.announceForAccessibility(getString(R.string.wipe_announcement))
            }
        }
    }

    private fun exitPerformance() {
        if (!performanceActive) return
        performanceActive = false
        performanceRoot.animate().cancel()
        performanceRoot.alpha = 1f
        wipeView.stopPerformance()
        performanceRoot.visibility = View.GONE
        setupRoot.visibility = View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showSystemBars()
        setupRoot.post { setupRoot.requestApplyInsets() }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    private fun showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    private fun showInfo() {
        AlertDialog.Builder(this, R.style.AppDialog)
            .setTitle(R.string.how_title)
            .setMessage(R.string.how_body)
            .setPositiveButton(R.string.got_it, null)
            .setNeutralButton(R.string.reset_images) { _, _ -> resetDefaults() }
            .show()
    }

    private fun resetDefaults() {
        CardStore.clear(this)
        screenBitmap = MagicArt.defaultScreen()
        messageBitmap = MagicArt.defaultMessage()
        updatePreviews()
        Toast.makeText(this, R.string.defaults_restored, Toast.LENGTH_SHORT).show()
    }

    private fun pickImage(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, requestCode)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.no_picker, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode != REQUEST_SCREEN && requestCode != REQUEST_MESSAGE) return
        val uri = data?.data ?: return

        val bitmap = PhotoLoader.load(this, uri)
        if (bitmap == null) {
            Toast.makeText(this, R.string.image_error, Toast.LENGTH_SHORT).show()
            return
        }

        val isScreen = requestCode == REQUEST_SCREEN
        val kind = if (isScreen) CardStore.SCREEN else CardStore.MESSAGE
        if (isScreen) {
            screenBitmap = bitmap
        } else {
            messageBitmap = bitmap
        }
        updatePreviews()

        if (CardStore.save(this, kind, bitmap)) {
            Toast.makeText(
                this,
                if (isScreen) R.string.screen_updated else R.string.message_updated,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, R.string.image_unsaved, Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && performanceActive) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        if (performanceActive) hideSystemBars()
    }

    override fun onDestroy() {
        if (::wipeView.isInitialized) wipeView.stopPerformance()
        super.onDestroy()
    }

    private companion object {
        const val REQUEST_SCREEN = 41
        const val REQUEST_MESSAGE = 42
    }
}
