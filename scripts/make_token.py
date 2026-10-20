#!/usr/bin/env python3
"""Mint a local HS256 JWT for calling the Insights API. Standard library only.

Examples:
    python3 scripts/make_token.py --tenant tesco --role RETAILER
    python3 scripts/make_token.py --tenant tesco --role BRAND --brand nestle

The secret must match JWT_HMAC_SECRET in docker-compose.yml. For local use only:
production tokens come from the identity provider.
"""
import argparse
import base64
import hashlib
import hmac
import json
import time

DEFAULT_SECRET = "local-dev-secret-change-me-0123456789abcdef"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def make_token(claims: dict, secret: str) -> str:
    header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    payload = b64url(json.dumps(claims, separators=(",", ":")).encode())
    signature = hmac.new(secret.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
    return f"{header}.{payload}.{b64url(signature)}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tenant", required=True, help="tenant_id claim, e.g. tesco")
    parser.add_argument("--role", choices=["RETAILER", "BRAND"], default="RETAILER")
    parser.add_argument("--brand", help="brand_id claim, required for --role BRAND")
    parser.add_argument("--subject", default="local-user")
    parser.add_argument("--ttl", type=int, default=3600, help="seconds until expiry")
    parser.add_argument("--secret", default=DEFAULT_SECRET)
    args = parser.parse_args()

    if args.role == "BRAND" and not args.brand:
        parser.error("--brand is required for BRAND tokens")

    now = int(time.time())
    claims = {"sub": args.subject, "tenant_id": args.tenant, "role": args.role, "iat": now, "exp": now + args.ttl}
    if args.brand:
        claims["brand_id"] = args.brand
    print(make_token(claims, args.secret))


if __name__ == "__main__":
    main()
