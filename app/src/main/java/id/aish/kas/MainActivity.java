package id.aish.kas;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Aish Kas — native WebView wrapper (struktur sama dengan Aish Financial Tracker).
 *
 * Memuat aplikasi POS HTML offline dari folder assets dan mengaktifkan
 * penyimpanan lokal (localStorage) agar seluruh data toko tersimpan
 * permanen di perangkat. Ditambah jembatan Bluetooth untuk mencetak
 * struk ke printer termal ESC/POS (58mm / 80mm).
 */
public class MainActivity extends Activity {

    private static final int REQ_BLUETOOTH = 71;
    /** UUID Serial Port Profile — dipakai semua printer termal Bluetooth. */
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private WebView web;
    private BluetoothSocket socket;          // koneksi printer yang sedang dipakai
    private String connectedMac = "";
    private final Object printLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);      // localStorage — data tersimpan permanen
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient());

        // WebChromeClient wajib ada agar dialog JavaScript (alert/confirm) berfungsi.
        // Tanpa ini, confirm() selalu mengembalikan "false" di WebView.
        web.setWebChromeClient(new WebChromeClient());

        // Jembatan JavaScript: backup file + printer Bluetooth.
        web.addJavascriptInterface(new AppBridge(), "AishApp");

        web.loadUrl("file:///android_asset/index.html");

        setContentView(web);
    }

    /** Dipanggil dari JavaScript lewat objek global window.AishApp */
    public class AppBridge {

        /* ================= Penyimpanan file (sama seperti Financial Tracker) ================= */

        /** window.AishApp.saveBackup(json, namaFile) → lokasi tersimpan, "" bila gagal. */
        @JavascriptInterface
        public String saveBackup(String json, String filename) {
            return saveFile(json, filename, "application/json");
        }

        /** window.AishApp.saveFile(teks, namaFile, mime) — dipakai ekspor CSV laporan. */
        @JavascriptInterface
        public String saveFile(String text, String filename, String mime) {
            try {
                byte[] data = text.getBytes(StandardCharsets.UTF_8);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ : simpan ke folder Download tanpa perlu izin apa pun.
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                    Uri uri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) return "";

                    OutputStream out = getContentResolver().openOutputStream(uri);
                    if (out == null) return "";
                    out.write(data);
                    out.flush();
                    out.close();
                    return "folder Download";
                } else {
                    // Android 7–9 : simpan ke folder khusus aplikasi (tanpa izin).
                    File dir = getExternalFilesDir(null);
                    if (dir == null) dir = getFilesDir();
                    File file = new File(dir, filename);
                    FileOutputStream out = new FileOutputStream(file);
                    out.write(data);
                    out.flush();
                    out.close();
                    return file.getAbsolutePath();
                }
            } catch (Exception e) {
                return "";
            }
        }

        /* ============================ Printer Bluetooth ESC/POS ============================ */

        /** Apakah izin Bluetooth sudah diberikan? (Sebelum Android 12 selalu true.) */
        @JavascriptInterface
        public boolean hasBluetoothPermission() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }

        /** Minta izin Bluetooth (Android 12+). Hasilnya dikirim ke onAishBtPermission(granted). */
        @JavascriptInterface
        public void requestBluetoothPermission() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                notifyPermission(true);
                return;
            }
            if (hasBluetoothPermission()) {
                notifyPermission(true);
                return;
            }
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, REQ_BLUETOOTH);
        }

        /** Apakah Bluetooth perangkat sedang menyala? */
        @JavascriptInterface
        public boolean isBluetoothOn() {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            return adapter != null && adapter.isEnabled();
        }

        /**
         * Daftar printer/perangkat Bluetooth yang SUDAH dipasangkan lewat
         * Pengaturan Bluetooth sistem. Hasil: JSON [{"name":..,"mac":..}, ...]
         */
        @JavascriptInterface
        public String getPairedPrinters() {
            try {
                if (!hasBluetoothPermission()) return "[]";
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) return "[]";
                Set<BluetoothDevice> devices = adapter.getBondedDevices();
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (BluetoothDevice d : devices) {
                    if (!first) sb.append(',');
                    first = false;
                    String name = d.getName() == null ? d.getAddress() : d.getName();
                    sb.append("{\"name\":\"").append(jsonEscape(name))
                      .append("\",\"mac\":\"").append(d.getAddress()).append("\"}");
                }
                return sb.append(']').toString();
            } catch (SecurityException e) {
                return "[]";
            }
        }

        /**
         * Cetak byte ESC/POS (base64) ke printer [mac].
         * Berjalan sinkron di thread jembatan JS (bukan UI thread) — aman.
         * Mengembalikan "ok" atau pesan kesalahan dalam Bahasa Indonesia.
         */
        @JavascriptInterface
        public String printEscPos(String base64Data, String mac) {
            if (!hasBluetoothPermission()) return "Izin Bluetooth belum diberikan";
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return "Perangkat tidak punya Bluetooth";
            if (!adapter.isEnabled()) return "Bluetooth mati — nyalakan dulu";
            if (mac == null || mac.isEmpty()) return "Printer belum dipilih";

            byte[] data;
            try {
                data = Base64.decode(base64Data, Base64.DEFAULT);
            } catch (Exception e) {
                return "Data cetak tidak valid";
            }

            synchronized (printLock) {
                try {
                    writeToPrinter(adapter, mac, data);
                    return "ok";
                } catch (Exception first) {
                    // Socket lama basi (printer sempat idle) → coba sambung ulang sekali.
                    closeSocket();
                    try {
                        writeToPrinter(adapter, mac, data);
                        return "ok";
                    } catch (Exception second) {
                        closeSocket();
                        return "Gagal terhubung ke printer. Pastikan printer menyala & dekat.";
                    }
                }
            }
        }

        /** Putuskan koneksi printer (opsional, dipanggil saat ganti printer). */
        @JavascriptInterface
        public void disconnectPrinter() {
            synchronized (printLock) {
                closeSocket();
            }
        }
    }

    /** Sambungkan (bila perlu) lalu tulis data per-512-byte + jeda kecil. */
    private void writeToPrinter(BluetoothAdapter adapter, String mac, byte[] data)
            throws Exception {
        if (socket == null || !socket.isConnected() || !mac.equals(connectedMac)) {
            closeSocket();
            BluetoothDevice device = adapter.getRemoteDevice(mac);
            adapter.cancelDiscovery(); // discovery memperlambat koneksi RFCOMM
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            connectedMac = mac;
        }
        OutputStream out = socket.getOutputStream();
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(512, data.length - offset);
            out.write(data, offset, len);
            out.flush();
            offset += len;
            Thread.sleep(25); // printer murah punya buffer kecil — beri waktu
        }
    }

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) { }
        socket = null;
        connectedMac = "";
    }

    /** Kabari JavaScript hasil permintaan izin: window.onAishBtPermission(granted). */
    private void notifyPermission(final boolean granted) {
        if (web == null) return;
        web.post(() -> web.evaluateJavascript(
                "window.onAishBtPermission && window.onAishBtPermission(" + granted + ")", null));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_BLUETOOTH) {
            boolean granted = results.length > 0
                    && results[0] == PackageManager.PERMISSION_GRANTED;
            notifyPermission(granted);
        }
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    protected void onDestroy() {
        closeSocket();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
