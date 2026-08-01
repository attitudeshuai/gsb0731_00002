package com.dbtool.backend.dto;

import com.dbtool.backend.service.export.ExportJob;

/** Snapshot of an export job's progress, returned by the status endpoint. */
public class ExportProgress {
    public String jobId;
    public String status;        // RUNNING | COMPLETED | FAILED | CANCELLED
    public long rowsWritten;
    public Long totalRows;       // best-effort estimate; may be null
    public Integer percent;      // 0-100 when totalRows is known
    public String filename;
    public String error;

    public static ExportProgress from(ExportJob job) {
        ExportProgress p = new ExportProgress();
        p.jobId = job.getId();
        p.status = job.getStatus().name();
        p.rowsWritten = job.getRowsWritten();
        p.totalRows = job.getTotalRows();
        p.filename = job.getFilename();
        p.error = job.getError();
        if (job.getTotalRows() != null && job.getTotalRows() > 0) {
            long pct = Math.min(100, job.getRowsWritten() * 100 / job.getTotalRows());
            p.percent = (int) pct;
        }
        return p;
    }
}
