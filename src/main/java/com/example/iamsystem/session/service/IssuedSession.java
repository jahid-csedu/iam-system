package com.example.iamsystem.session.service;

import com.example.iamsystem.session.entity.UserSession;

/**
 * A session and the refresh token just issued for it ({@code null} when its client doesn't issue refresh tokens).
 * The refresh token exists in plain form only here, on its way to the user.
 */
public record IssuedSession(UserSession session, String refreshToken) {
}
