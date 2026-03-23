package com.wuhu.vpnhotspot;

import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpProxyServer implements Runnable {

    private final int port;
    private volatile boolean isRunning = false;
    private ServerSocket serverSocket;

    private final ExecutorService threadPool = Executors.newFixedThreadPool(50);

    public HttpProxyServer(int port) {
        this.port = port;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            Log.e("ProxyServer", "关闭服务器错误", e);
        }
        threadPool.shutdownNow();
    }

    @Override
    public void run() {
        isRunning = true;
        try {
            // 显式绑定 0.0.0.0，确保监听所有网卡（包括热点和 WiFi）
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            Log.d("ProxyServer", "代理服务器启动在端口: " + port);

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(15000);
                threadPool.execute(new ProxyTask(clientSocket));
            }
        } catch (Exception e) {
            if (isRunning) {
                Log.e("ProxyServer", "服务器异常或已停止", e);
            }
        }
    }

    private static class ProxyTask implements Runnable {
        private final Socket clientSocket;

        public ProxyTask(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            Socket targetSocket = null;
            try {
                InputStream clientIn = clientSocket.getInputStream();
                OutputStream clientOut = clientSocket.getOutputStream();

                // 读取第一行请求
                String requestLine = readLine(clientIn);
                if (requestLine == null || requestLine.isEmpty()) return;

                String[] parts = requestLine.split(" ");
                if (parts.length < 3) return;

                String method = parts[0];
                String url = parts[1];
                String host;
                int targetPort = 80;

                if (method.equalsIgnoreCase("CONNECT")) {
                    String[] hostPort = url.split(":");
                    host = hostPort[0];
                    if (hostPort.length > 1) {
                        try { targetPort = Integer.parseInt(hostPort[1]); } catch (Exception ignored) { targetPort = 443; }
                    } else {
                        targetPort = 443;
                    }

                    String header;
                    while ((header = readLine(clientIn)) != null && !header.isEmpty()) {
                        // 循环读取，直到读到空行（\r\n），直接丢弃
                    }
                } else {
                    int hostStart = url.indexOf("://");
                    if (hostStart != -1) {
                        url = url.substring(hostStart + 3);
                    }
                    int pathStart = url.indexOf('/');
                    host = pathStart != -1 ? url.substring(0, pathStart) : url;
                    if (host.contains(":")) {
                        String[] hostPort = host.split(":");
                        host = hostPort[0];
                        try { targetPort = Integer.parseInt(hostPort[1]); } catch (Exception ignored) { targetPort = 80; }
                    }
                }

                // 连接到目标服务器
                targetSocket = new Socket(host, targetPort);
                targetSocket.setSoTimeout(15000);

                InputStream serverIn = targetSocket.getInputStream();
                OutputStream serverOut = targetSocket.getOutputStream();

                if (method.equalsIgnoreCase("CONNECT")) {
                    clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                    clientOut.flush();
                } else {
                    serverOut.write((requestLine + "\r\n").getBytes());
                }

                Thread clientToServerThread = new Thread(() -> forwardData(clientIn, serverOut));
                clientToServerThread.start();

                forwardData(serverIn, clientOut);
                clientToServerThread.join(15000);

            } catch (Exception e) {
                // 忽略超时或被服务器主动重置
            } finally {
                try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
                try { if (targetSocket != null) targetSocket.close(); } catch (Exception ignored) {}
            }
        }

        private String readLine(InputStream input) {
            StringBuilder sb = new StringBuilder();
            int c;
            int prev = -1;
            try {
                while ((c = input.read()) != -1) {
                    if (prev == '\r' && c == '\n') {
                        break;
                    }
                    if (c != '\r' && c != '\n') {
                        sb.append((char) c);
                    }
                    prev = c;
                }
                return sb.toString();
            } catch (Exception e) {
                return null;
            }
        }

        private void forwardData(InputStream input, OutputStream output) {
            byte[] buffer = new byte[8192];
            int read;
            try {
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } catch (Exception e) {
                // 正常结束
            }
        }
    }
}