package com.milestonepay.auth;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/** One way of working out who sent a request. Throws {@code UnauthorizedException} if it can't. */
interface CallerAuthenticator {

    UUID authenticate(HttpServletRequest request);
}
