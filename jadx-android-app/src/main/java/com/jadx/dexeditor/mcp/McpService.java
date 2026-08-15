package com.jadx.dexeditor.mcp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.jadx.dexeditor.DexLoader;
import com.jadx.dexeditor.MainActivity;
import com.jadx.dexeditor.R;

import java.io.IOException;

/**
 * MCP 前台服务：承载 {@link McpServer}，保证服务在应用退到后台后仍可运行。
 * <p>
 * 通过 {@link #start(Context, int)} / {@link #stop(Context)} 控制。
 */
public class McpService extends Service {

    public static final String ACTION_START = "com.jadx.dexeditor.mcp.START";
    public static final String ACTION_STOP = "com.jadx.dexeditor.mcp.STOP";
    public static final String EXTRA_PORT = "port";

    private static final String CHANNEL_ID = "mcp_service";
    private static final int NOTIFICATION_ID = 0xD5C;

    /** 运行中的服务器实例（静态，供 UI 查询） */
    private static volatile McpServer runningServer;

    public static void start(Context context, int port) {
        Intent intent = new Intent(context, McpService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_PORT, port);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, McpService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static boolean isRunning() {
        McpServer s = runningServer;
        return s != null && s.wasStarted();
    }

    public static McpServer getRunningServer() {
        return runningServer;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // 确保 DexLoader 有 Context（服务可能先于界面启动）
        DexLoader.getInstance().init(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null && intent.getAction() != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stopServer();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        int port = intent != null ? intent.getIntExtra(EXTRA_PORT, McpServer.DEFAULT_PORT)
                : McpServer.DEFAULT_PORT;

        if (isRunning()) {
            // 已在运行：仅刷新通知
            startForegroundCompat(port);
            return START_STICKY;
        }

        McpServer server = new McpServer(port);
        try {
            // timeout=15000: SSE 长连接保持；daemon=false 由服务生命周期管理
            server.start(15000, false);
            runningServer = server;
            startForegroundCompat(port);
        } catch (IOException e) {
            runningServer = null;
            stopSelf();
        }
        return START_STICKY;
    }

    private void startForegroundCompat(int port) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.mcp_notif_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.mcp_notif_channel_desc));
            nm.createNotificationChannel(channel);
        }

        String url = "http://" + McpServer.getLanIp() + ":" + port + "/mcp";
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mcp)
                .setContentTitle(getString(R.string.mcp_notif_title))
                .setContentText(url)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void stopServer() {
        McpServer s = runningServer;
        runningServer = null;
        if (s != null) {
            s.closeAllSessions();
            s.stop();
        }
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }
}
