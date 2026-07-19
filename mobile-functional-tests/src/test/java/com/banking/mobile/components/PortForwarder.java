package com.banking.mobile.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PortForwarder {

    private static final Logger log = LoggerFactory.getLogger(PortForwarder.class);

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int SO_TIMEOUT_MS = 30_000;
    private static final int BUFFER_SIZE = 8192;
    private static final long THREAD_POOL_KEEPALIVE_SECS = 60;
    private static final int SHUTDOWN_WAIT_SECS = 5;

    private final List<Forwarder> forwarders = new ArrayList<>();
    private final ExecutorService threadPool;

    public PortForwarder() {
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "port-forwarder");
            t.setDaemon(true);
            return t;
        });
    }

    public void startForward(String label, int listenPort, String targetHost, int targetPort) {
        Forwarder f = new Forwarder(label, listenPort, targetHost, targetPort, threadPool);
        f.start();
        forwarders.add(f);
        log.info("Port forwarder [{}] :{} → {}:{}", label, listenPort, targetHost, targetPort);
    }

    public void stopAll() {
        for (Forwarder f : forwarders) {
            f.stop();
        }
        forwarders.clear();
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(SHUTDOWN_WAIT_SECS, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class Forwarder {
        private final String label;
        private final int listenPort;
        private final String targetHost;
        private final int targetPort;
        private final ExecutorService threadPool;
        private volatile ServerSocket serverSocket;

        Forwarder(String label, int listenPort, String targetHost, int targetPort,
                ExecutorService threadPool) {
            this.label = label;
            this.listenPort = listenPort;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
            this.threadPool = threadPool;
        }

        void start() {
            threadPool.submit(() -> {
                try {
                    serverSocket = new ServerSocket(listenPort);
                    log.debug("[{}] Listening on port {}", label, listenPort);
                    while (!serverSocket.isClosed()) {
                        Socket client = serverSocket.accept();
                        client.setSoTimeout(SO_TIMEOUT_MS);
                        threadPool.submit(() -> handle(client));
                    }
                } catch (IOException e) {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        log.error("[{}] Forwarder error: {}", label, e.getMessage());
                    }
                }
            });
        }

        void stop() {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                    log.debug("[{}] Server socket closed", label);
                }
            } catch (IOException e) {
                log.warn("[{}] Error closing server socket: {}", label, e.getMessage());
            }
        }

        private void handle(Socket client) {
            String peer = client.getRemoteSocketAddress().toString();
            log.trace("[{}] New connection from {}", label, peer);
            long start = System.currentTimeMillis();

            try (Socket clientSock = client;
                    Socket target = new Socket()) {

                target.connect(
                        new java.net.InetSocketAddress(targetHost, targetPort),
                        CONNECT_TIMEOUT_MS);
                target.setSoTimeout(SO_TIMEOUT_MS);

                InputStream ci = clientSock.getInputStream();
                OutputStream co = clientSock.getOutputStream();
                InputStream ti = target.getInputStream();
                OutputStream to = target.getOutputStream();

                Thread toTarget = new Thread(() -> {
                    try { transfer(ci, to); } catch (IOException ignored) {}
                }, "pfwd-" + label + "-tgt");
                Thread toClient = new Thread(() -> {
                    try { transfer(ti, co); } catch (IOException ignored) {}
                }, "pfwd-" + label + "-cli");

                toTarget.start();
                toClient.start();

                toTarget.join();
                toClient.join();

            } catch (ConnectException e) {
                log.warn("[{}] Connection refused to {}:{} — {}", label, targetHost, targetPort, e.getMessage());
            } catch (SocketTimeoutException e) {
                log.warn("[{}] Socket timeout ({}s) on connection from {}", label, SO_TIMEOUT_MS / 1000, peer);
            } catch (Exception e) {
                log.debug("[{}] Connection closed: {} — {}", label, peer, e.getMessage());
            }

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 1000) {
                log.debug("[{}] Connection from {} closed after {}ms", label, peer, elapsed);
            }
        }

        private void transfer(InputStream in, OutputStream out) throws IOException {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        }
    }
}
