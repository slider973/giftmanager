# /// script
# requires-python = ">=3.10"
# dependencies = ["pyjwt[crypto]>=2.8", "requests>=2.31"]
# ///
"""Client minimal pour l'API App Store Connect.

Lit ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY dans l'environnement
(fournis par `op run --env-file=.env.op`).

Usage :
  op run --env-file=.env.op -- uv run scripts/apple/asc.py GET  /v1/apps
  op run --env-file=.env.op -- uv run scripts/apple/asc.py POST /v1/bundleIds '{"data": {...}}'
"""
import json
import os
import sys
import time

import jwt
import requests

BASE = "https://api.appstoreconnect.apple.com"


def token() -> str:
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 1200, "aud": "appstoreconnect-v1"},
        os.environ["ASC_PRIVATE_KEY"],
        algorithm="ES256",
        headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"},
    )


def call(method: str, path: str, body: dict | None = None) -> dict:
    url = path if path.startswith("http") else BASE + path
    r = requests.request(method, url, json=body, headers={"Authorization": f"Bearer {token()}"}, timeout=60)
    data = r.json() if r.content else {}
    if r.status_code >= 400:
        print(json.dumps(data, indent=2), file=sys.stderr)
        sys.exit(f"HTTP {r.status_code}")
    return data


if __name__ == "__main__":
    method, path = sys.argv[1].upper(), sys.argv[2]
    body = json.loads(sys.argv[3]) if len(sys.argv) > 3 else None
    print(json.dumps(call(method, path, body), indent=2))
