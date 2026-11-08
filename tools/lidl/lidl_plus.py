#!/usr/bin/env python3
"""
Lidl Plus mobile-API client (stdlib only).

Mirrors the OAuth2 + PKCE flow the real Lidl Plus app uses:

  1. authurl   -> prints an accounts.lidl.com authorize URL, stores the PKCE verifier
  2. exchange  -> swaps the ?code= from the com.lidlplus.app://callback redirect for tokens
  3. tickets   -> lists receipts (structured JSON, no HTML parsing)
  4. ticket    -> single receipt with its line items
  5. dump      -> writes list + details to a directory

Tokens live in a local state file (gitignored). Never print the refresh token
into shared output.
"""
import argparse
import base64
import hashlib
import json
import os
import secrets
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

CLIENT_ID = "LidlPlusNativeClient"
CLIENT_SECRET = "secret"          # public client, documented default
AUTH_HOST = "https://accounts.lidl.com"
TICKET_API = "https://tickets.lidlplus.com/api/v2"
PROFILE_API = "https://profile.lidlplus.com/profile/api"
REDIRECT_URI = "com.lidlplus.app://callback"
SCOPE = "openid profile offline_access lpprofile lpapis"

DEFAULT_STATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".lidl_state.json")
UA = "okhttp/4.12.0"
TIMEOUT = 30

_ctx = ssl.create_default_context()


def _insecure():
    global _ctx
    _ctx = ssl._create_unverified_context()


# ---------------------------------------------------------------- http helpers

def _request(url, method="GET", data=None, headers=None):
    body = None
    hdrs = {"User-Agent": UA, "Accept": "application/json"}
    if headers:
        hdrs.update(headers)
    if data is not None:
        body = urllib.parse.urlencode(data).encode()
        hdrs["Content-Type"] = "application/x-www-form-urlencoded"
    req = urllib.request.Request(url, data=body, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT, context=_ctx) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return resp.status, dict(resp.headers), raw
    except urllib.error.HTTPError as exc:
        return exc.code, dict(exc.headers), exc.read().decode("utf-8", "replace")


def _json(url, headers=None):
    status, _, raw = _request(url, headers=headers)
    if status != 200:
        sys.exit("HTTP %s for %s\n%s" % (status, url, raw[:800]))
    return json.loads(raw)


# --------------------------------------------------------------- state on disk

def load_state(path):
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    return {}


def save_state(path, state):
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(state, fh, indent=2)
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass


# ------------------------------------------------------------------- oauth2

def discovery():
    return _json(AUTH_HOST + "/.well-known/openid-configuration")


def cmd_authurl(args):
    conf = discovery()
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(64)).decode().rstrip("=")
    challenge = base64.urlsafe_b64encode(
        hashlib.sha256(verifier.encode()).digest()
    ).decode().rstrip("=")
    state_param = secrets.token_hex(16)
    nonce = secrets.token_hex(16)

    params = {
        "client_id": CLIENT_ID,
        "response_type": "code",
        "scope": SCOPE,
        "redirect_uri": REDIRECT_URI,
        "code_challenge": challenge,
        "code_challenge_method": "S256",
        "state": state_param,
        "nonce": nonce,
        "Country": args.country.upper(),
        "language": args.language,
    }
    url = conf["authorization_endpoint"] + "?" + urllib.parse.urlencode(params)

    st = load_state(args.state)
    st.update({
        "code_verifier": verifier,
        "oauth_state": state_param,
        "country": args.country.upper(),
        "language": args.language,
        "token_endpoint": conf["token_endpoint"],
    })
    save_state(args.state, st)

    print("issuer           :", conf.get("issuer"))
    print("token endpoint   :", conf["token_endpoint"])
    print("grant types      :", ",".join(conf.get("grant_types_supported", [])))
    print("pkce methods     :", ",".join(conf.get("code_challenge_methods_supported", [])))
    print("")
    print("Open this URL, log in, then copy the com.lidlplus.app://callback?... URL:")
    print("")
    print(url)


def _token_request(args, payload):
    st = load_state(args.state)
    endpoint = st.get("token_endpoint") or (AUTH_HOST + "/connect/token")
    basic = base64.b64encode((CLIENT_ID + ":" + CLIENT_SECRET).encode()).decode()
    status, _, raw = _request(
        endpoint, method="POST", data=payload,
        headers={"Authorization": "Basic " + basic},
    )
    if status != 200:
        sys.exit("token request failed: HTTP %s\n%s" % (status, raw[:1200]))
    tok = json.loads(raw)
    st.update({
        "access_token": tok["access_token"],
        "refresh_token": tok.get("refresh_token", st.get("refresh_token")),
        "expires_at": int(time.time()) + int(tok.get("expires_in", 3600)) - 60,
    })
    save_state(args.state, st)
    return tok


def cmd_exchange(args):
    raw = args.code.strip().strip('"').strip("'")
    if "code=" in raw:
        qs = urllib.parse.urlparse(raw).query or raw.split("?", 1)[-1]
        code = urllib.parse.parse_qs(qs).get("code", [""])[0]
    else:
        code = raw
    if not code:
        sys.exit("could not find a code in that input")

    st = load_state(args.state)
    if not st.get("code_verifier"):
        sys.exit("no code_verifier stored - run authurl first")

    tok = _token_request(args, {
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": REDIRECT_URI,
        "code_verifier": st["code_verifier"],
    })
    print("OK - tokens stored in", args.state)
    print("token type :", tok.get("token_type"))
    print("expires in :", tok.get("expires_in"), "s")
    print("scope      :", tok.get("scope"))
    print("refresh token present:", bool(tok.get("refresh_token")))


def cmd_refresh(args):
    st = load_state(args.state)
    if not st.get("refresh_token"):
        sys.exit("no refresh token stored")
    tok = _token_request(args, {
        "grant_type": "refresh_token",
        "refresh_token": st["refresh_token"],
    })
    print("refreshed OK, expires in", tok.get("expires_in"), "s")


def auth_headers(args):
    st = load_state(args.state)
    if not st.get("access_token"):
        sys.exit("not logged in - run authurl/exchange first")
    if st.get("expires_at", 0) <= time.time() and st.get("refresh_token"):
        _token_request(args, {
            "grant_type": "refresh_token",
            "refresh_token": st["refresh_token"],
        })
        st = load_state(args.state)
    hdrs = {
        "Authorization": "Bearer " + st["access_token"],
        "App-Version": "999.99.9",
        "Operating-System": "iOs",
        "App": "com.lidl.eci.lidl.plus",
        "Accept-Language": st.get("language", args.language),
    }
    return hdrs, st


# -------------------------------------------------------------------- tickets

def cmd_tickets(args):
    hdrs, st = auth_headers(args)
    country = st.get("country", args.country.upper())
    url = "%s/%s/tickets?pageNumber=%s&onlyFavorite=false" % (TICKET_API, country, args.page)
    data = _json(url, headers=hdrs)
    print("totalCount=%s size=%s page=%s" % (
        data.get("totalCount"), data.get("size"), args.page))
    for t in data.get("tickets", []):
        print("  %s  %s  %s  %s" % (
            t.get("id"), t.get("date"), t.get("totalAmount"), t.get("storeCode", "")))
    if args.raw:
        print(json.dumps(data, indent=2, ensure_ascii=False)[:4000])


def cmd_ticket(args):
    hdrs, st = auth_headers(args)
    country = st.get("country", args.country.upper())
    data = _json("%s/%s/tickets/%s" % (TICKET_API, country, args.id), headers=hdrs)
    print(json.dumps(data, indent=2, ensure_ascii=False))


def cmd_dump(args):
    hdrs, st = auth_headers(args)
    country = st.get("country", args.country.upper())
    os.makedirs(args.out, exist_ok=True)

    page = 1
    summaries = []
    while True:
        url = "%s/%s/tickets?pageNumber=%s&onlyFavorite=false" % (TICKET_API, country, page)
        data = _json(url, headers=hdrs)
        batch = data.get("tickets", [])
        summaries += batch
        total = int(data.get("totalCount", len(summaries)))
        if not batch or len(summaries) >= total:
            break
        if args.limit and len(summaries) >= args.limit:
            break
        page += 1
        time.sleep(0.4)

    if args.limit:
        summaries = summaries[: args.limit]
    with open(os.path.join(args.out, "tickets_list.json"), "w", encoding="utf-8") as fh:
        json.dump(summaries, fh, indent=2, ensure_ascii=False)
    print("%s summaries -> %s/tickets_list.json" % (len(summaries), args.out))

    det_dir = os.path.join(args.out, "tickets")
    os.makedirs(det_dir, exist_ok=True)
    for i, s in enumerate(summaries, 1):
        tid = s.get("id")
        dest = os.path.join(det_dir, "%s.json" % tid)
        if os.path.exists(dest):
            continue
        detail = _json("%s/%s/tickets/%s" % (TICKET_API, country, tid), headers=hdrs)
        with open(dest, "w", encoding="utf-8") as fh:
            json.dump(detail, fh, indent=2, ensure_ascii=False)
        print("  [%s/%s] %s" % (i, len(summaries), tid))
        time.sleep(0.4)


def cmd_profile(args):
    hdrs, st = auth_headers(args)
    country = st.get("country", args.country.upper())
    status, _, raw = _request("%s/v1/%s/loyalty" % (PROFILE_API, country), headers=hdrs)
    print("loyalty HTTP %s: %s" % (status, raw[:300]))


def main():
    p = argparse.ArgumentParser(description="Lidl Plus mobile API client")
    p.add_argument("--state", default=DEFAULT_STATE)
    p.add_argument("--country", default="GB")
    p.add_argument("--language", default="en-GB")
    p.add_argument("--insecure", action="store_true", help="skip TLS verification")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("authurl").set_defaults(func=cmd_authurl)
    e = sub.add_parser("exchange")
    e.add_argument("code")
    e.set_defaults(func=cmd_exchange)
    sub.add_parser("refresh").set_defaults(func=cmd_refresh)
    t = sub.add_parser("tickets")
    t.add_argument("--page", type=int, default=1)
    t.add_argument("--raw", action="store_true")
    t.set_defaults(func=cmd_tickets)
    d = sub.add_parser("ticket")
    d.add_argument("id")
    d.set_defaults(func=cmd_ticket)
    du = sub.add_parser("dump")
    du.add_argument("--out", default="lidl_data")
    du.add_argument("--limit", type=int, default=0)
    du.set_defaults(func=cmd_dump)
    sub.add_parser("profile").set_defaults(func=cmd_profile)

    args = p.parse_args()
    if args.insecure:
        _insecure()
    args.func(args)


if __name__ == "__main__":
    main()
