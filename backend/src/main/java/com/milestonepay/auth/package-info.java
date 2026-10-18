/**
 * Who is calling. The auth method (magic link, JWT, ...) is still undecided, so for now the only way in is
 * the dev-only {@code X-User-Id} header ({@link com.milestonepay.auth.DevHeaderAuthenticator}). Controllers
 * take a {@link com.milestonepay.auth.CurrentUser} parameter and never read headers themselves, so swapping
 * the method later touches only this package.
 */
package com.milestonepay.auth;
