package main.java.com.securepay.recovery;

import main.java.com.securepay.model.Transaction;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionJournal {

    private final Path journalFile;
    private final ConcurrentHashMap<String, String> inMemory = new ConcurrentHashMap<>();

    public TransactionJournal(String dataDirectory) {
        this.journalFile = Paths.get(dataDirectory, "transaction_journal.log");
        try {
            Files.createDirectories(journalFile.getParent());
            if (!Files.exists(journalFile)) {
                Files.createFile(journalFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot initialize transaction journal", e);
        }
    }

    public synchronized void record(String transactionId, String step, Transaction txn) {
        String line = System.currentTimeMillis() + "|" + transactionId + "|" + step +
                "|" + txn.getFromAccountId() + "|" + txn.getToAccountId() +
                "|" + txn.getAmount() + "|" + txn.getStatus() + "|" + txn.getIdempotencyKey();

        inMemory.put(transactionId, step);

        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(journalFile.toFile(), true))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Journal write failed: " + e.getMessage());
        }
    }


    public List<String> findIncompleteTransactions() {
        Map<String, String> lastStep = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(journalFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    lastStep.put(parts[1], parts[2]);
                }
            }
        } catch (IOException e) {
            System.err.println("Journal read failed: " + e.getMessage());
        }

        List<String> incomplete = new ArrayList<>();
        for (Map.Entry<String, String> e : lastStep.entrySet()) {
            String step = e.getValue();
            if (!step.equals("SUCCESS") && !step.equals("FAILED") && !step.equals("ROLLED_BACK")) {
                incomplete.add(e.getKey());
            }
        }
        return incomplete;
    }

    public Map<String, String> getInMemoryState() {
        return Map.copyOf(inMemory);
    }
}
