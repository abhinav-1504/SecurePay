package main.java.com.securepay.audit;

import main.java.com.securepay.concurrency.ThreadPoolManager;
import main.java.com.securepay.model.AuditEvent;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuditLogger {

    private final BlockingQueue<AuditEvent> queue = new LinkedBlockingQueue<>(5000);
    private final ThreadPoolManager poolManager;
    private final Path logFile;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    public AuditLogger(ThreadPoolManager poolManager, String logDirectory) {
        this.poolManager = poolManager;
        this.logFile = Paths.get(logDirectory, "audit.log");
        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Cannot create audit log directory", e);
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            worker = new Thread(this::processLoop, "audit-dispatcher");
            worker.setDaemon(true);
            worker.start();
        }
    }

    public void stop() {
        running.set(false);
        if (worker != null) worker.interrupt();
        drainRemaining();
    }

    public void log(String eventType, String userId, String accountId,
                    String transactionId, String details) {
        AuditEvent event = new AuditEvent(eventType, userId, accountId, transactionId, details);
        if (!queue.offer(event)) {
            writeEvent(event);
        }
    }

    private void processLoop() {
        while (running.get()) {
            try {
                AuditEvent event = queue.poll(300, TimeUnit.MILLISECONDS);
                if (event != null) {
                    poolManager.getAuditWorkers().submit(() -> writeEvent(event));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void writeEvent(AuditEvent event) {
        synchronized (logFile) {
            try (BufferedWriter writer = new BufferedWriter(
                    new FileWriter(logFile.toFile(), true))) {
                writer.write(event.toString());
                writer.newLine();
            } catch (IOException e) {
                System.err.println("Failed to write audit log: " + e.getMessage());
            }
        }
        System.out.println("[AUDIT] " + event);
    }

    private void drainRemaining() {
        AuditEvent event;
        while ((event = queue.poll()) != null) {
            writeEvent(event);
        }
    }

}
