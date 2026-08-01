package com.dbmanager.exception;

public class ExportCancelledException extends BusinessException {

    public ExportCancelledException() {
        super("EXPORT_CANCELLED", "Export was cancelled by user");
    }
}
