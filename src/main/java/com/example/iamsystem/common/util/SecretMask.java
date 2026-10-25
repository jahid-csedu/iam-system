package com.example.iamsystem.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Stands in for passwords, OTPs and tokens in the {@code toString()} of the records that carry them, so logging a request
 * (Spring MVC logs request bodies at DEBUG) never writes a credential.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecretMask {

    public static final String MASK = "[PROTECTED]";
}
