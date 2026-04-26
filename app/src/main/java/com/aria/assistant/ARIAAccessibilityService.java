package com.aria.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class ARIAAccessibilityService extends AccessibilityService {

    // Singleton so MainActivity can call it
    public static ARIAAccessibilityService instance;

    // Callback interface so results get sent back to JS
    public interface ActionCallback {
        void onResult(String result);
    }

    @Override
    public void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We don't need to react to events — we're acting, not observing
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    // ── TAP ─────────────────────────────────────────────────────────────────
    public void tap(float x, float y, ActionCallback cb) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 50);

        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(stroke)
            .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                if (cb != null) cb.onResult("tapped " + (int)x + "," + (int)y);
            }
            @Override public void onCancelled(GestureDescription g) {
                if (cb != null) cb.onResult("tap cancelled");
            }
        }, null);
    }

    // ── LONG PRESS ───────────────────────────────────────────────────────────
    public void longPress(float x, float y, ActionCallback cb) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 800);

        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(stroke)
            .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                if (cb != null) cb.onResult("long pressed " + (int)x + "," + (int)y);
            }
            @Override public void onCancelled(GestureDescription g) {
                if (cb != null) cb.onResult("long press cancelled");
            }
        }, null);
    }

    // ── SWIPE ────────────────────────────────────────────────────────────────
    public void swipe(float x1, float y1, float x2, float y2, int durationMs, ActionCallback cb) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, durationMs);

        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(stroke)
            .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                if (cb != null) cb.onResult("swiped");
            }
            @Override public void onCancelled(GestureDescription g) {
                if (cb != null) cb.onResult("swipe cancelled");
            }
        }, null);
    }

    // ── SCROLL ───────────────────────────────────────────────────────────────
    public void scrollDown(ActionCallback cb) {
        // Swipe up = scroll down (finger moves up)
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float cx = dm.widthPixels / 2f;
        float midY = dm.heightPixels / 2f;
        swipe(cx, midY + 300, cx, midY - 300, 300, cb);
    }

    public void scrollUp(ActionCallback cb) {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float cx = dm.widthPixels / 2f;
        float midY = dm.heightPixels / 2f;
        swipe(cx, midY - 300, cx, midY + 300, 300, cb);
    }

    // ── GLOBAL ACTIONS ───────────────────────────────────────────────────────
    public void pressBack(ActionCallback cb) {
        boolean ok = performGlobalAction(GLOBAL_ACTION_BACK);
        if (cb != null) cb.onResult(ok ? "back pressed" : "back failed");
    }

    public void pressHome(ActionCallback cb) {
        boolean ok = performGlobalAction(GLOBAL_ACTION_HOME);
        if (cb != null) cb.onResult(ok ? "home pressed" : "home failed");
    }

    public void pressRecents(ActionCallback cb) {
        boolean ok = performGlobalAction(GLOBAL_ACTION_RECENTS);
        if (cb != null) cb.onResult(ok ? "recents opened" : "recents failed");
    }

    public void openNotifications(ActionCallback cb) {
        boolean ok = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        if (cb != null) cb.onResult(ok ? "notifications opened" : "notifications failed");
    }

    public void openQuickSettings(ActionCallback cb) {
        boolean ok = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
        if (cb != null) cb.onResult(ok ? "quick settings opened" : "quick settings failed");
    }

    // ── TYPE TEXT ────────────────────────────────────────────────────────────
    public void typeText(String text, ActionCallback cb) {
        // Find the focused text field and type into it
        AccessibilityNodeInfo focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            // Try to find any editable field on screen
            focused = findEditableNode(getRootInActiveWindow());
        }
        if (focused != null) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            if (cb != null) cb.onResult(ok ? "typed: " + text : "type failed — no text field focused");
        } else {
            if (cb != null) cb.onResult("no text field found on screen");
        }
    }

    private AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        if (root.isEditable()) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo result = findEditableNode(root.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    // ── CLICK BY TEXT ────────────────────────────────────────────────────────
    // Finds a node with matching text/content description and taps it
    public void tapByText(String text, ActionCallback cb) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (cb != null) cb.onResult("no active window");
            return;
        }
        AccessibilityNodeInfo node = findNodeByText(root, text.toLowerCase());
        if (node != null) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            float cx = bounds.centerX();
            float cy = bounds.centerY();
            tap(cx, cy, cb);
        } else {
            if (cb != null) cb.onResult("element not found: " + text);
        }
    }

    private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        CharSequence nodeText = node.getText();
        CharSequence nodeDesc = node.getContentDescription();
        if ((nodeText != null && nodeText.toString().toLowerCase().contains(text)) ||
            (nodeDesc != null && nodeDesc.toString().toLowerCase().contains(text))) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findNodeByText(node.getChild(i), text);
            if (result != null) return result;
        }
        return null;
    }

    // ── READ SCREEN ──────────────────────────────────────────────────────────
    // Returns a JSON summary of visible text on screen
    public String readScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "{\"error\":\"no active window\"}";

        JSONArray items = new JSONArray();
        collectText(root, items, 0);

        try {
            JSONObject result = new JSONObject();
            result.put("items", items);
            result.put("count", items.length());

            // Get window info
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null && !windows.isEmpty()) {
                AccessibilityWindowInfo w = windows.get(0);
                CharSequence title = w.getTitle();
                if (title != null) result.put("window", title.toString());
            }

            return result.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private void collectText(AccessibilityNodeInfo node, JSONArray out, int depth) {
        if (node == null || depth > 12) return;
        try {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            CharSequence hint = node.getHintText();
            String label = text != null ? text.toString().trim()
                         : desc != null ? desc.toString().trim()
                         : hint != null ? hint.toString().trim() : "";
            if (!label.isEmpty()) {
                JSONObject item = new JSONObject();
                item.put("text", label);
                item.put("clickable", node.isClickable());
                item.put("editable", node.isEditable());
                item.put("checkable", node.isCheckable());
                item.put("checked", node.isChecked());
                item.put("className", node.getClassName() != null ? node.getClassName().toString() : "");
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                item.put("x", r.centerX());
                item.put("y", r.centerY());
                item.put("width", r.width());
                item.put("height", r.height());
                out.put(item);
            }
        } catch (Exception ignored) {}
        for (int i = 0; i < node.getChildCount(); i++) {
            collectText(node.getChild(i), out, depth + 1);
        }
    }

    // ── SCAN UI ──────────────────────────────────────────────────────────────
    // Returns ALL interactive elements: buttons, inputs, checkboxes, etc.
    public String scanUI() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "{\"error\":\"no active window\"}";

        JSONArray buttons = new JSONArray();
        JSONArray inputs = new JSONArray();
        JSONArray all = new JSONArray();
        int[] index = {0};

        scanNode(root, buttons, inputs, all, index, 0);

        try {
            JSONObject result = new JSONObject();
            result.put("buttons", buttons);
            result.put("inputs", inputs);
            result.put("all", all);
            result.put("buttonCount", buttons.length());
            result.put("inputCount", inputs.length());
            return result.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private void scanNode(AccessibilityNodeInfo node, JSONArray buttons, JSONArray inputs,
                          JSONArray all, int[] index, int depth) {
        if (node == null || depth > 15) return;
        try {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            CharSequence hint = node.getHintText();
            String className = node.getClassName() != null ? node.getClassName().toString() : "";

            String label = text != null ? text.toString().trim()
                         : desc != null ? desc.toString().trim()
                         : hint != null ? "[hint: " + hint.toString().trim() + "]" : "";

            boolean isEditable = node.isEditable();
            boolean isClickable = node.isClickable();
            boolean isCheckable = node.isCheckable();

            if (isEditable || isClickable || isCheckable) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) {
                    JSONObject item = new JSONObject();
                    item.put("index", index[0]);
                    item.put("text", label.isEmpty() ? "(no label)" : label);
                    item.put("x", r.centerX());
                    item.put("y", r.centerY());
                    item.put("width", r.width());
                    item.put("height", r.height());
                    item.put("editable", isEditable);
                    item.put("clickable", isClickable);
                    item.put("checkable", isCheckable);
                    item.put("checked", node.isChecked());
                    item.put("class", className);

                    all.put(item);
                    if (isEditable) inputs.put(item);
                    else if (isClickable || isCheckable) buttons.put(item);
                    index[0]++;
                }
            }
        } catch (Exception ignored) {}
        for (int i = 0; i < node.getChildCount(); i++) {
            scanNode(node.getChild(i), buttons, inputs, all, index, depth + 1);
        }
    }

    // ── FIND BUTTON / INPUT BY LABEL ─────────────────────────────────────────
    // Returns JSON with x,y of the best matching element
    public String findElement(String query) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "{\"error\":\"no active window\"}";

        String q = query.toLowerCase().trim();
        AccessibilityNodeInfo best = findBestMatch(root, q, 0);

        if (best != null) {
            try {
                Rect r = new Rect();
                best.getBoundsInScreen(r);
                JSONObject result = new JSONObject();
                result.put("found", true);
                result.put("x", r.centerX());
                result.put("y", r.centerY());
                CharSequence t = best.getText();
                CharSequence d = best.getContentDescription();
                result.put("text", t != null ? t.toString() : d != null ? d.toString() : "");
                result.put("editable", best.isEditable());
                result.put("clickable", best.isClickable());
                return result.toString();
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
        return "{\"found\":false,\"query\":\"" + query + "\"}";
    }

    private AccessibilityNodeInfo findBestMatch(AccessibilityNodeInfo node, String query, int depth) {
        if (node == null || depth > 15) return null;
        try {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            CharSequence hint = node.getHintText();
            String t = text != null ? text.toString().toLowerCase() : "";
            String d = desc != null ? desc.toString().toLowerCase() : "";
            String h = hint != null ? hint.toString().toLowerCase() : "";

            if ((t.contains(query) || d.contains(query) || h.contains(query))
                    && (node.isClickable() || node.isEditable())) {
                return node;
            }
        } catch (Exception ignored) {}
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findBestMatch(node.getChild(i), query, depth + 1);
            if (result != null) return result;
        }
        return null;
    }

    // ── FILL FORM FIELD ──────────────────────────────────────────────────────
    // Tap a field by label, then type text into it
    public void fillField(String fieldLabel, String value, ActionCallback cb) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (cb != null) cb.onResult("no active window");
            return;
        }

        String q = fieldLabel.toLowerCase().trim();
        AccessibilityNodeInfo field = findBestMatch(root, q, 0);

        if (field == null) {
            if (cb != null) cb.onResult("field not found: " + fieldLabel);
            return;
        }

        // First tap the field to focus it
        Rect r = new Rect();
        field.getBoundsInScreen(r);
        float cx = r.centerX();
        float cy = r.centerY();

        tap(cx, cy, result -> {
            // Short delay then type
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // Clear existing text first, then type
                AccessibilityNodeInfo focused = getRootInActiveWindow() != null
                        ? findEditableNode(getRootInActiveWindow()) : null;
                if (focused != null) {
                    // Clear field
                    Bundle clearArgs = new Bundle();
                    clearArgs.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                    focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs);
                    // Set value
                    Bundle args = new Bundle();
                    args.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
                    boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    if (cb != null) cb.onResult(ok ? "filled \"" + fieldLabel + "\" with \"" + value + "\"" : "fill failed");
                } else {
                    if (cb != null) cb.onResult("could not focus field: " + fieldLabel);
                }
            }, 300);
        });
    }

    // ── TAP BY INDEX ─────────────────────────────────────────────────────────
    // After scanUI(), tap element by its index number
    public void tapByIndex(int targetIndex, ActionCallback cb) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (cb != null) cb.onResult("no active window");
            return;
        }
        int[] index = {0};
        AccessibilityNodeInfo found = findByIndex(root, targetIndex, index, 0);
        if (found != null) {
            Rect r = new Rect();
            found.getBoundsInScreen(r);
            tap(r.centerX(), r.centerY(), cb);
        } else {
            if (cb != null) cb.onResult("index not found: " + targetIndex);
        }
    }

    private AccessibilityNodeInfo findByIndex(AccessibilityNodeInfo node, int target,
                                               int[] current, int depth) {
        if (node == null || depth > 15) return null;
        try {
            boolean isInteractive = node.isClickable() || node.isEditable() || node.isCheckable();
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            if (isInteractive && r.width() > 0) {
                if (current[0] == target) return node;
                current[0]++;
            }
        } catch (Exception ignored) {}
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findByIndex(node.getChild(i), target, current, depth + 1);
            if (result != null) return result;
        }
        return null;
    }
}
