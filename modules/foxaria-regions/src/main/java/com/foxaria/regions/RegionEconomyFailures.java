package com.foxaria.regions;

import java.math.BigDecimal;
import java.util.concurrent.CompletionException;

/**
 * Понятные сообщения об ошибках экономики вместо сырого stack trace.
 */
public final class RegionEconomyFailures {

    private RegionEconomyFailures() {
    }

    public static String describe(Throwable throwable) {
        Throwable t = unwrap(throwable);
        if (t == null) {
            return "Неизвестная ошибка.";
        }
        String msg = t.getMessage();
        if (t instanceof IllegalStateException) {
            if (msg != null && msg.toLowerCase().contains("insufficient")) {
                return "Недостаточно монет на счёте.";
            }
            return msg != null ? msg : "Операция с балансом отклонена.";
        }
        if (t instanceof java.sql.SQLException) {
            return "Ошибка базы данных. Попробуйте позже или сообщите администратору.";
        }
        if (msg != null && !msg.isBlank() && !msg.startsWith("java.")) {
            return msg;
        }
        return "Оплата не удалась. Попробуйте позже.";
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        for (int i = 0; i < 8 && cur != null; i++) {
            if (cur instanceof CompletionException && cur.getCause() != null) {
                cur = cur.getCause();
                continue;
            }
            break;
        }
        return cur;
    }

    public static String insufficientCoins(BigDecimal need, BigDecimal have) {
        return "Нужно &f" + need.toPlainString() + " &cмонет, на счёте &f" + have.toPlainString() + "&c.";
    }
}
