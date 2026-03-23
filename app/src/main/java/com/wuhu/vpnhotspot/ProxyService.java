package com.wuhu.vpnhotspot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class ProxyService extends Service {
    private static final String CHANNEL_ID = "ProxyServiceChannel";
    private HttpProxyServer proxyServer;

    // 设置默认端口为 7897
    private int currentPort = 7897;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // 从 Intent 读取端口，如果没有传，则默认 7897
            currentPort = intent.getIntExtra("PROXY_PORT", 7897);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("热点代理已启动")
                .setContentText("代理服务器正在端口 " + currentPort + " 运行")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        startForeground(1, notification);

        if (proxyServer == null || !proxyServer.isRunning()) {
            proxyServer = new HttpProxyServer(currentPort);
            new Thread(proxyServer).start();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (proxyServer != null) {
            proxyServer.stopServer();
            proxyServer = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Proxy Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}