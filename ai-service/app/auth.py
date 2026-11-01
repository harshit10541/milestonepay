import hmac

from fastapi import Depends, Header, HTTPException, status

from .config import Settings, get_settings


def require_secret(
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
) -> None:
    prefix = "Bearer "
    if not authorization or not authorization.startswith(prefix):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing bearer token")
    token = authorization[len(prefix):]
    if not hmac.compare_digest(token, settings.ai_service_secret):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid token")