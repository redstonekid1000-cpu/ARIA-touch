package com.aria.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class ARIAAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ARIAAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ── SCAN UI ──────────────────────────────────────────────────────────────
    // Returns a JSON object with buttons, inputs, and all visible text.
    // This is what aria.html calls via AndroidBridge.scanUI()
    fun scanUI(): String {
        val root = rootInActiveWindow
            ?: return JSONObject().put("error", "no active window").toString()

        val buttons = JSONArray()
        val inputs  = JSONArray()
        val texts   = JSONArray()

        fun walk(node: AccessibilityNodeInfo?, index: Int): Int {
            if (node == null) return index
            var idx = index

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            val label = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()

            if (node.isClickable && label.isNotEmpty()) {
                buttons.put(
                    JSONObject()
                        .put("index", idx)
                        .put("text", label)
                        .put("x", cx)
                        .put("y", cy)
                )
            }

            if (node.isEditable) {
                inputs.put(
                    JSONObject()
                        .put("index", idx)
                        .put("text", label)
                        .put("x", cx)
                        .put("y", cy)
                )
            }

            if (label.isNotEmpty()) {
                texts.put(label)
            }

            idx++
            for (i in 0 until node.childCount) {
                idx = walk(node.getChild(i), idx)
            }
            node.recycle()
            return idx
        }

        walk(root, 0)

        return JSONObject()
            .put("buttons", buttons)
            .put("inputs",  inputs)
            .put("texts",   texts)
            .toString()
    }

    // ── TAP BY COORDINATES ───────────────────────────────────────────────────
    fun tap(x: Int, y: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path   = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    // ── TAP BY VISIBLE TEXT ──────────────────────────────────────────────────
    fun tapByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false

        fun find(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            val label = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            if (label.trim().equals(text.trim(), ignoreCase = true)) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                return clicked
            }
            for (i in 0 until node.childCount) {
                if (find(node.getChild(i))) return true
            }
            node.recycle()
            return false
        }

        return find(root)
    }

    // ── TAP BY INDEX (from scanUI) ───────────────────────────────────────────
    fun tapByIndex(targetIndex: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        var currentIndex = 0

        fun find(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (currentIndex == targetIndex) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                return clicked
            }
            currentIndex++
            for (i in 0 until node.childCount) {
                if (find(node.getChild(i))) return true
            }
            node.recycle()
            return false
        }

        return find(root)
    }

    // ── FILL A TEXT FIELD ────────────────────────────────────────────────────
    // Taps the field first, then sets text via ACTION_SET_TEXT
    fun fillField(fieldLabel: String, value: String): Boolean {
        val root = rootInActiveWindow ?: return false

        fun find(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            val label = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            if (node.isEditable &&
                (label.trim().equals(fieldLabel.trim(), ignoreCase = true) || fieldLabel.isBlank())) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val args = android.os.Bundle().apply {
                    putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }
                val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                node.recycle()
                return result
            }
            for (i in 0 until node.childCount) {
                if (find(node.getChild(i))) return true
            }
            node.recycle()
            return false
        }

        return find(root)
    }

    // ── GLOBAL ACTIONS ───────────────────────────────────────────────────────
    fun pressHome()     = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressBack()     = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressRecents()  = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun openNotifications() =
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun openQuickSettings() =
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    // ── SCROLL ───────────────────────────────────────────────────────────────
    fun scrollUp(): Boolean {
        val root = rootInActiveWindow ?: return false
        fun find(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.isScrollable) {
                val scrolled = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                node.recycle()
                return scrolled
            }
            for (i in 0 until node.childCount) {
                if (find(node.getChild(i))) return true
            }
            node.recycle()
            return false
        }
        return find(root)
    }

    fun scrollDown(): Boolean {
        val root = rootInActiveWindow ?: return false
        fun find(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.isScrollable) {
                val scrolled = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                node.recycle()
                return scrolled
            }
            for (i in 0 until node.childCount) {
                if (find(node.getChild(i))) return true
            }
            node.recycle()
            return false
        }
        return find(root)
    }

    // ── OPEN URL ─────────────────────────────────────────────────────────────
    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    // ── OPEN ACCESSIBILITY SETTINGS ──────────────────────────────────────────
    fun openAccessibilitySettings() {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }
}
