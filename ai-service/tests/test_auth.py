import os

os.environ["AI_SERVICE_SECRET"] = "test-secret"

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_is_open():
    assert client.get("/health").status_code == 200


def test_protected_rejects_missing_and_wrong_token():
    assert client.get("/ping").status_code == 401
    response = client.get("/ping", headers={"Authorization": "Bearer nope"})
    assert response.status_code == 401


def test_protected_accepts_right_token():
    response = client.get(
        "/ping", headers={"Authorization": "Bearer test-secret"}
    )
    assert response.status_code == 200