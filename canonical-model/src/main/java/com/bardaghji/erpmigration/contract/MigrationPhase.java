package com.bardaghji.erpmigration.contract;

public enum MigrationPhase {
    LEGACY,
    SHADOW,
    CUTOVER;

    MigrationPhase() {
    }
}
