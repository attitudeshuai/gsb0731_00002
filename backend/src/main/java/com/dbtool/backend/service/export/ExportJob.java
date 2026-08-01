package com.dbtool.backend.service.export;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * State of a single asynchronous export. Progress ({@link #rowsWritten}) is updated
 * live as rows stream to disk so the client can poll it. The result is written to a
 * temp file that the client downloads once the job completes.
 */
public class ExportJob {

    public enum Status { RUNNING, COMPLETED, FAILED, CANCELLED }

    private final String id;
    private final Long connectionId;
    private final String queryToken;   // used to cancel the underlying streaming statement
    private final String format;
    private final String filename;
    private final Long totalRows;      // best-effort estimate for percentage; may be null

    private volatile Status status = Status.RUNNING;
    private final AtomicLong rowsWritten = new AtomicLong(0);
    private volatile String error;
    private volatile java.nio.file.Path filePath;
    private final Instant startedAt = Instant.now();
    private volatile Instant finishedAt;

    public ExportJob(String id, Long connectionId, String queryToken, String format,
                     String filename, Long totalRows) {
        this.id = id;
        this.connectionId = connectionId;
        this.queryToken = queryToken;
        this.format = format;
        this.filename = filename;
        this.totalRows = totalRows;
    }

    public String getId() { return id; }
    public Long getConnectionId() { return connectionId; }
    public String getQueryToken() { return queryToken; }
    public String getFormat() { return format; }
    public String getFilename() { return filename; }
    public Long getTotalRows() { return totalRows; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public long getRowsWritten() { return rowsWritten.get(); }
    public void setRowsWritten(long v) { rowsWritten.set(v); }
    public long incrementRows() { return rowsWritten.incrementAndGet(); }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public java.nio.file.Path getFilePath() { return filePath; }
    public void setFilePath(java.nio.file.Path filePath) { this.filePath = filePath; }

    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
