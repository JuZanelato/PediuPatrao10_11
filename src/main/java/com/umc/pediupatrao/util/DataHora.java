package com.umc.pediupatrao.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class DataHora {
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private DataHora() {
    }

    public static LocalDateTime agora() {
        return LocalDateTime.now(FUSO).truncatedTo(ChronoUnit.MILLIS);
    }
}
