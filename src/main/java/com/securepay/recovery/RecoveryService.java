package main.java.com.securepay.recovery;

import main.java.com.securepay.audit.AuditLogger;

import java.util.List;

public class RecoveryService {

    private final TransactionJournal journal;
    private final AuditLogger auditLogger;

    public RecoveryService(TransactionJournal journal, AuditLogger auditLogger) {
        this.journal = journal;
        this.auditLogger = auditLogger;
    }

    public void recover() {
        List<String> incomplete = journal.findIncompleteTransactions();

        if (incomplete.isEmpty()) {
            System.out.println("[RECOVERY] No incomplete transactions found.");
            return;
        }

        System.out.println("[RECOVERY] Found " + incomplete.size() + " incomplete transaction(s):");
        for (String txnId : incomplete) {
            System.out.println("  - " + txnId);
            auditLogger.log("RECOVERY_NEEDED", null, null, txnId,
                    "Incomplete transaction detected on startup");
        }
    }
}