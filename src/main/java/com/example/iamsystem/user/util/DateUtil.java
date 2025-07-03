package com.example.iamsystem.user.util;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@UtilityClass
public class DateUtil {
    public static Instant calculateExpiryDate(int expiryTimeInDays) {
        return Instant.now().plus(expiryTimeInDays, ChronoUnit.DAYS);
    }
}
