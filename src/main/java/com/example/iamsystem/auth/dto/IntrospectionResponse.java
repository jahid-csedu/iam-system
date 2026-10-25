package com.example.iamsystem.auth.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.Map;

/**
 * An RFC 7662 introspection answer: {@code active}, and for an active token its claims next to it
 * ({@code iss}, {@code sub}, {@code aud}, {@code client_id}, {@code sid}, {@code exp}, ...). An inactive token gets
 * nothing but {@code "active": false}, never a reason.
 */
public final class IntrospectionResponse {

    private static final IntrospectionResponse INACTIVE = new IntrospectionResponse(false, Map.of());

    private final boolean active;
    private final Map<String, Object> claims;

    private IntrospectionResponse(boolean active, Map<String, Object> claims) {
        this.active = active;
        this.claims = claims;
    }

    public static IntrospectionResponse active(Map<String, Object> claims) {
        return new IntrospectionResponse(true, Map.copyOf(claims));
    }

    public static IntrospectionResponse inactive() {
        return INACTIVE;
    }

    public boolean isActive() {
        return active;
    }

    @JsonAnyGetter
    public Map<String, Object> getClaims() {
        return claims;
    }
}
