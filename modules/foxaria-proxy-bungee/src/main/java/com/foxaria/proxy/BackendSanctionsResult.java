package com.foxaria.proxy;

import java.util.List;

/**
 * Результат запроса к игровой БД: отличить «нет строк» от «ошибка соединения».
 */
public final class BackendSanctionsResult {

    private final List<ProxyPunishmentRepository.PunishmentRecord> records;
    private final boolean queryFailed;
    private final String errorMessage;

    private BackendSanctionsResult(List<ProxyPunishmentRepository.PunishmentRecord> records, boolean queryFailed, String errorMessage) {
        this.records = records;
        this.queryFailed = queryFailed;
        this.errorMessage = errorMessage;
    }

    public static BackendSanctionsResult ok(List<ProxyPunishmentRepository.PunishmentRecord> records) {
        return new BackendSanctionsResult(records, false, null);
    }

    public static BackendSanctionsResult failed(String message) {
        return new BackendSanctionsResult(List.of(), true, message == null ? "unknown" : message);
    }

    public List<ProxyPunishmentRepository.PunishmentRecord> records() {
        return records;
    }

    public boolean queryFailed() {
        return queryFailed;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
