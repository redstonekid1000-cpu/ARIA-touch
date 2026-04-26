package com.aria.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ARIA";
    private static final int PERM_REQUEST = 100;
    private static final String NOTIF_CHANNEL = "aria_channel";

    private WebView webView;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean ttsReady = false;
    private boolean torchOn = false;
    private CameraManager cameraManager;
    private String cameraId;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen immersive
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        setupNotificationChannel();
        setupTTS();
        setupCamera();
        setupWebView();
        requestPermissions();
    }

    // ─── SETUP ───────────────────────────────────────────────────────────────

    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                NOTIF_CHANNEL, "ARIA Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.0f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        runOnUiThread(() -> webView.evaluateJavascript("onTTSStart()", null));
                    }
                    @Override public void onDone(String id) {
                        runOnUiThread(() -> webView.evaluateJavascript("onTTSDone()", null));
                    }
                    @Override public void onError(String id) {}
                });
                ttsReady = true;
            }
        });
    }

    private void setupCamera() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                cameraId = id;
                break;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera setup failed", e);
        }
    }

    private void setupWebView() {
        webView = findViewById(R.id.webview);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setAllowFileAccess(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith("http") && !url.contains("file://")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }
        });

        // Inject Android bridge
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        // Load the ARIA HTML from assets
        webView.loadUrl("file:///android_asset/aria.html");
    }

    private void requestPermissions() {
        String[] perms = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms = new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.POST_NOTIFICATIONS
            };
        }
        ActivityCompat.requestPermissions(this, perms, PERM_REQUEST);
    }

    // ─── ANDROID BRIDGE ──────────────────────────────────────────────────────

    public class AndroidBridge {

        // ── TTS ──
        @JavascriptInterface
        public void speak(String text) {
            if (!ttsReady) return;
            tts.stop();
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "aria");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "aria");
        }

        @JavascriptInterface
        public void stopSpeaking() {
            if (ttsReady) tts.stop();
        }

        // ── STT ──
        @JavascriptInterface
        public void startListening() {
            mainHandler.post(() -> {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    callJS("onSTTError('Permission denied')");
                    return;
                }
                if (speechRecognizer != null) {
                    speechRecognizer.destroy();
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle p) { callJS("onSTTReady()"); }
                    @Override public void onBeginningOfSpeech() { callJS("onSTTBegin()"); }
                    @Override public void onRmsChanged(float rms) {
                        callJS("onSTTVolume(" + rms + ")");
                    }
                    @Override public void onBufferReceived(byte[] b) {}
                    @Override public void onEndOfSpeech() { callJS("onSTTEnd()"); }
                    @Override public void onError(int err) {
                        callJS("onSTTError('" + sttErrorMsg(err) + "')");
                    }
                    @Override public void onResults(Bundle results) {
                        ArrayList<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        );
                        if (matches != null && !matches.isEmpty()) {
                            String text = matches.get(0).replace("'", "\\'");
                            callJS("onSTTResult('" + text + "')");
                        } else {
                            callJS("onSTTError('No speech detected')");
                        }
                    }
                    @Override public void onPartialResults(Bundle partial) {
                        ArrayList<String> p = partial.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        );
                        if (p != null && !p.isEmpty()) {
                            String text = p.get(0).replace("'", "\\'");
                            callJS("onSTTPartial('" + text + "')");
                        }
                    }
                    @Override public void onEvent(int t, Bundle b) {}
                });

                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                speechRecognizer.startListening(intent);
            });
        }

        @JavascriptInterface
        public void stopListening() {
            mainHandler.post(() -> {
                if (speechRecognizer != null) speechRecognizer.stopListening();
            });
        }

        // ── TORCH ──
        @JavascriptInterface
        public void setTorch(boolean on) {
            if (cameraManager == null || cameraId == null) return;
            try {
                cameraManager.setTorchMode(cameraId, on);
                torchOn = on;
            } catch (CameraAccessException e) {
                Log.e(TAG, "Torch failed", e);
            }
        }

        @JavascriptInterface
        public boolean getTorchState() { return torchOn; }

        // ── BATTERY ──
        @JavascriptInterface
        public String getBatteryInfo() {
            try {
                IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent bi = registerReceiver(null, iFilter);
                if (bi == null) return "{\"level\":0,\"charging\":false}";
                int level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int pct = (scale > 0) ? (int)((level / (float) scale) * 100) : 0;
                boolean charging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL);
                JSONObject j = new JSONObject();
                j.put("level", pct);
                j.put("charging", charging);
                return j.toString();
            } catch (Exception e) {
                return "{\"level\":0,\"charging\":false}";
            }
        }

        // ── NOTIFICATION ──
        @JavascriptInterface
        public void sendNotification(String title, String content) {
            mainHandler.post(() -> {
                NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm == null) return;
                NotificationCompat.Builder b = new NotificationCompat.Builder(
                    MainActivity.this, NOTIF_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);
                nm.notify((int) System.currentTimeMillis(), b.build());
            });
        }

        // ── INTENTS / OPEN APP ──
        @JavascriptInterface
        public void openUrl(String url) {
            mainHandler.post(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    toast("Could not open URL");
                }
            });
        }

        @JavascriptInterface
        public void dialNumber(String number) {
            mainHandler.post(() -> {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
            });
        }

        @JavascriptInterface
        public void sendSMS(String number) {
            mainHandler.post(() -> {
                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("sms:" + number)));
            });
        }

        @JavascriptInterface
        public void openSettings() {
            mainHandler.post(() -> startActivity(new Intent(
                android.provider.Settings.ACTION_SETTINGS)));
        }

        @JavascriptInterface
        public void shareText(String text) {
            mainHandler.post(() -> {
                Intent s = new Intent(Intent.ACTION_SEND);
                s.setType("text/plain");
                s.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(s, "Share via"));
            });
        }

        // ── DEVICE INFO ──
        @JavascriptInterface
        public String getDeviceInfo() {
            try {
                JSONObject j = new JSONObject();
                j.put("model", Build.MODEL);
                j.put("manufacturer", Build.MANUFACTURER);
                j.put("android", Build.VERSION.RELEASE);
                return j.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        }

        @JavascriptInterface
        public void toast(String msg) {
            mainHandler.post(() -> Toast.makeText(MainActivity.this, msg,
                Toast.LENGTH_SHORT).show());
        }

        // ── TOUCH / ACCESSIBILITY ──
        @JavascriptInterface
        public boolean isAccessibilityEnabled() {
            return ARIAAccessibilityService.instance != null;
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            mainHandler.post(() -> startActivity(
                new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }

        @JavascriptInterface
        public void tap(float x, float y) {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.tap(x, y,
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void longPress(float x, float y) {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.longPress(x, y,
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void swipe(float x1, float y1, float x2, float y2, int duration) {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.swipe(x1, y1, x2, y2, duration,
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void scrollDown() {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.scrollDown(
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void scrollUp() {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.scrollUp(
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void pressBack() {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.pressBack(
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void pressHome() {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.pressHome(
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void pressRecents() {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.pressRecents(
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void openNotifications() {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.openNotifications(
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void openQuickSettings() {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.openQuickSettings(
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void tapByText(String text) {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.tapByText(text,
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public void typeText(String text) {
            if (ARIAAccessibilityService.instance == null) {
                callJS("onTouchResult('Accessibility service not enabled')");
                return;
            }
            ARIAAccessibilityService.instance.typeText(text,
                result -> callJS("onTouchResult('" + result + "')"));
        }

        @JavascriptInterface
        public String readScreen() {
            if (ARIAAccessibilityService.instance == null) {
                return "{\"error\":\"Accessibility service not enabled\"}";
            }
            return ARIAAccessibilityService.instance.readScreen();
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private void callJS(String fn) {
        mainHandler.post(() -> webView.evaluateJavascript(fn, null));
    }

    private String sttErrorMsg(int err) {
        switch (err) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "No mic permission";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER: return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Timeout";
            default: return "Unknown error";
        }
    }

    // ─── LIFECYCLE ───────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        if (ttsReady) tts.stop();
        if (speechRecognizer != null) speechRecognizer.stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) tts.shutdown();
        if (speechRecognizer != null) speechRecognizer.destroy();
        try {
            if (cameraManager != null && cameraId != null)
                cameraManager.setTorchMode(cameraId, false);
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
