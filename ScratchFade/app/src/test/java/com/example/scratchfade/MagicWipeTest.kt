package com.example.scratchfade

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MagicWipeTest {

    @Test
    fun setupStartsWithUsableDefaults() {
        val activity = createActivity()

        assertTrue(activity.findViewById<View>(R.id.startButton).isEnabled)
        assertEquals(
            activity.getString(R.string.title),
            activity.findViewById<TextView>(R.id.title).text.toString()
        )
        assertNotNull(activity.findViewById<View>(R.id.screenPreview).contentDescription)
        assertNotNull(activity.findViewById<View>(R.id.messagePreview).contentDescription)
    }

    @Test
    fun infoDialogOpens() {
        val activity = createActivity()
        activity.findViewById<View>(R.id.infoButton).performClick()
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test
    fun startAndBackReturnToSetup() {
        val activity = createActivity()
        val setup = activity.findViewById<View>(R.id.setupRoot)
        val performance = activity.findViewById<View>(R.id.performanceRoot)

        activity.findViewById<View>(R.id.startButton).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, setup.visibility)
        assertEquals(View.VISIBLE, performance.visibility)

        activity.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, setup.visibility)
        assertEquals(View.GONE, performance.visibility)
    }

    @Test
    fun wipingRevealsTheMessageAndTapReplays() {
        val activity = createActivity()
        activity.findViewById<View>(R.id.startButton).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        layout(activity.findViewById(android.R.id.content))

        val wipe = activity.findViewById<MagicWipeView>(R.id.wipeView)
        assertTrue(wipe.width > 0 && wipe.height > 0)
        assertFalse(wipe.isRevealed)

        val height = wipe.height.toFloat()
        listOf(0.14f, 0.26f, 0.38f, 0.50f, 0.62f, 0.74f, 0.86f).forEach { row ->
            stroke(wipe, 0.05f, row, 0.95f, row, steps = 24)
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertTrue("Expected the wipe to reveal the message", wipe.isRevealed)

        tap(wipe, wipe.width / 2f, height / 2f)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("Expected a tap after the reveal to reset the trick", wipe.isRevealed)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.performanceRoot).visibility)
    }

    @Test
    fun cardStoreRoundTrip() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val screen = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        assertTrue(CardStore.save(context, CardStore.SCREEN, screen))
        val loaded = CardStore.load(context, CardStore.SCREEN)
        assertNotNull(loaded)
        assertEquals(64, loaded!!.width)
        assertEquals(48, loaded.height)

        val message = Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565).apply {
            eraseColor(Color.BLUE)
        }
        assertTrue(CardStore.save(context, CardStore.MESSAGE, message))
        assertNotNull(CardStore.load(context, CardStore.MESSAGE))

        CardStore.clear(context)
        assertNull(CardStore.load(context, CardStore.SCREEN))
        assertNull(CardStore.load(context, CardStore.MESSAGE))
    }

    private fun createActivity(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        layout(activity.findViewById(android.R.id.content))
        return activity
    }

    private fun layout(view: View) {
        view.measure(
            MeasureSpec.makeMeasureSpec(1233, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(2673, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1233, 2673)
    }

    private fun stroke(view: View, fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int) {
        val startX = view.width * fromX
        val startY = view.height * fromY
        val endX = view.width * toX
        val endY = view.height * toY
        val downTime = SystemClock.uptimeMillis()

        dispatch(view, downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY)
        for (step in 1 until steps) {
            val t = step / steps.toFloat()
            dispatch(
                view,
                downTime,
                downTime + step,
                MotionEvent.ACTION_MOVE,
                startX + (endX - startX) * t,
                startY + (endY - startY) * t
            )
        }
        dispatch(view, downTime, downTime + steps, MotionEvent.ACTION_UP, endX, endY)
    }

    private fun tap(view: View, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        dispatch(view, downTime, downTime, MotionEvent.ACTION_DOWN, x, y)
        dispatch(view, downTime, downTime + 40, MotionEvent.ACTION_UP, x, y)
    }

    private fun dispatch(
        view: View,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
