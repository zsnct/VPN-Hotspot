package com.wuhu.vpnhotspot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    private Button btnToggle;
    private TextView tvStatus;
    private TextView tvIpAddress;
    private EditText etPort;
    private boolean isRunning = false;

    private static final String PREFS_NAME = "ProxyPrefs";
    private static final String KEY_PORT = "saved_port";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = findViewById(R.id.btnToggle);
        tvStatus = findViewById(R.id.tvStatus);
        tvIpAddress = findViewById(R.id.tvIpAddress);
        etPort = findViewById(R.id.etPort);

        // 读取保存的端口，如果没有保存过，默认设置为7897
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedPort = prefs.getInt(KEY_PORT, 7897);
        etPort.setText(String.valueOf(savedPort));

        updateIpAddressDisplay();

        btnToggle.setOnClickListener(v -> {
            if (!isRunning) {
                startProxy();
            } else {
                stopProxy();
            }
        });
    }

    private void startProxy() {
        String portStr = etPort.getText().toString().trim();

        // 输入端口检查
        if (portStr.isEmpty()) {
            Toast.makeText(this, "端口不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "端口格式错误或数值过大", Toast.LENGTH_SHORT).show();
            return;
        }

        if (port <= 0 || port > 65535) {
            Toast.makeText(this, "请输入有效的端口号 (1-65535)", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(KEY_PORT, port);
        editor.apply();

        updateIpAddressDisplay();

        Intent serviceIntent = new Intent(this, ProxyService.class);
        serviceIntent.putExtra("PROXY_PORT", port);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        tvStatus.setText("代理运行中... 监听端口: " + port);
        btnToggle.setText("停止代理服务");
        etPort.setEnabled(false); // 运行时禁止修改端口
        isRunning = true;
    }

    private void stopProxy() {
        Intent serviceIntent = new Intent(this, ProxyService.class);
        stopService(serviceIntent);

        tvStatus.setText("当前状态：未运行");
        btnToggle.setText("启动代理服务");
        etPort.setEnabled(true);
        isRunning = false;
    }

    private void updateIpAddressDisplay() {
        StringBuilder ipList = new StringBuilder("请在其他设备填入以下代理 IP:\n");
        boolean foundIp = false;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        String ip = address.getHostAddress();
                        String name = networkInterface.getName();

                        if (name.contains("wlan") || name.contains("ap") || name.contains("swlan") || name.contains("rndis")) {
                            ipList.append("[热点/WiFi] ").append(ip).append("\n");
                            foundIp = true;
                        } else if (!name.contains("rmnet")) {
                            ipList.append("[其他网络] ").append(ip).append("\n");
                            foundIp = true;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        if (foundIp) {
            tvIpAddress.setText(ipList.toString().trim());
        } else {
            tvIpAddress.setText("未检测到局域网 IP\n请确保已开启 WiFi 或 个人热点");
        }
    }
}