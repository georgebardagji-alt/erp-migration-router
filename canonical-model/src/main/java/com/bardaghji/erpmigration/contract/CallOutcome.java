package com.bardaghji.erpmigration.contract;

public enum CallOutcome {
    SUCCESS,
    NOT_FOUND,
    UNMAPPABLE,
    ERP_UNAVAILABLE,
    REJECTED;

    CallOutcome() {
    }
}
