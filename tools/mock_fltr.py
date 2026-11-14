#!/usr/bin/env python3
"""A mock of the FLTR app API v1 for trying the app without a FLTR box.

Serves HTTPS on port 8443 with a self-signed certificate, keeps sample
people, devices and blocks in memory, and prints a pairing link. The
Android emulator reaches this machine at 10.0.2.2.

    python3 tools/mock_fltr.py                  # then, in another terminal:
    adb shell am start -a android.intent.action.VIEW -d "'<printed link>'"

Needs only Python 3 and the openssl command. Data resets on restart.
"""
import json, re, ssl, sys
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

import base64, hashlib, os, secrets, subprocess, tempfile
from urllib.parse import urlencode

HOST = os.environ.get("MOCK_FLTR_HOST", "10.0.2.2")  # as the emulator sees this machine
TOKEN = secrets.token_hex(32)
_dir = tempfile.mkdtemp(prefix="mock-fltr-")
CERT, KEY = os.path.join(_dir, "cert.pem"), os.path.join(_dir, "key.pem")
subprocess.run(["openssl", "req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256", "-nodes",
                "-keyout", KEY, "-out", CERT, "-days", "30", "-subj", "/CN=fltr"], check=True, capture_output=True)
_pub = subprocess.run(["openssl", "x509", "-in", CERT, "-pubkey", "-noout"], check=True, capture_output=True).stdout
_spki = subprocess.run(["openssl", "pkey", "-pubin", "-outform", "der"], input=_pub, check=True, capture_output=True).stdout
PIN = "sha256/" + base64.b64encode(hashlib.sha256(_spki).digest()).decode()
now = lambda: datetime.now(timezone.utc)
iso = lambda d: d.strftime("%Y-%m-%dT%H:%M:%SZ")
STARTED = now() - timedelta(hours=7, minutes=12)

# name -> state; "settings" holds what the app manages, "rules" devices come from rules.txt
users = {
    "alice": dict(allow=7200, used=2580, extra=1800, paused=False, managed=True,
                  devices=[("mac", "aa:bb:cc:dd:ee:01", "settings")]),
    "bob": dict(allow=3600, used=3600, extra=0, paused=False, managed=True,
                devices=[("mac", "aa:bb:cc:dd:ee:02", "settings"), ("ip", "192.168.1.40/32", "settings")]),
    "sam": dict(allow=5400, used=600, extra=0, paused=True, managed=False,
                devices=[("ip", "192.168.1.23/32", "rules")]),
}
seen = [
    dict(ip="192.168.1.10", mac="aa:bb:cc:dd:ee:01", client_label="Chrome (QUIC)", host="www.youtube.com", m=1),
    dict(ip="192.168.1.11", mac="aa:bb:cc:dd:ee:02", client_label="YouTube app (Android)", host="i.ytimg.com", m=4),
    dict(ip="192.168.1.23", client_label="Safari (TCP)", host="www.google.com", m=9),
    dict(ip="192.168.1.31", mac="aa:bb:cc:dd:ee:07", client_label="Smart TV YouTube", host="www.youtube.com", m=26),
    dict(ip="192.168.1.52", mac="aa:bb:cc:dd:ee:09", host="connectivitycheck.gstatic.com", m=95),
]
blocks = []
for i, (u, ip, reason, h) in enumerate([
    ("bob", "192.168.1.11", "allowance_exhausted", 0.2), ("bob", "192.168.1.11", "allowance_exhausted", 0.5),
    ("sam", "192.168.1.23", "paused", 1.1), (None, "192.168.1.31", "rule", 2.5),
    ("alice", "192.168.1.10", "rule", 20), ("bob", "192.168.1.11", "allowance_exhausted", 27)]):
    e = dict(time=iso(now() - timedelta(hours=h)), proto="udp" if i % 2 else "tcp", client=ip, client_port=50000 + i,
             server="142.250.1.1", server_port=443, **{"class": "QUIC.YouTube" if i % 2 else "TLS.YouTube"},
             host="www.youtube.com", rule=dict(type="ip", value=ip), reason=reason, mode="queue")
    if u: e["user"] = u
    if ip == "192.168.1.31": e["client_label"] = "Smart TV YouTube"
    blocks.append(e)
blocks.sort(key=lambda e: e["time"])

def user_obj(name):
    u = users[name]
    rem = 0 if u["paused"] else max(0, u["allow"] + u["extra"] - u["used"])
    return dict(name=name, allowance_seconds=u["allow"], used_seconds=u["used"], extra_seconds=u["extra"],
                remaining_seconds=rem, paused=u["paused"], managed=u["managed"],
                devices=[dict(type=t, value=v, source=s) for t, v, s in u["devices"]])

def owner(ip, mac):
    for n, u in users.items():
        for t, v, _ in u["devices"]:
            if (t == "mac" and mac and v == mac) or (t == "ip" and v.split("/")[0] == ip):
                return n, f"{t} {v}"
    return None, None

def norm(t, v):
    v = v.strip().lower()
    return v if t == "mac" or "/" in v else v + "/32"

class H(BaseHTTPRequestHandler):
    def reply(self, code, obj=None):
        body = b"" if obj is None else json.dumps(obj).encode()
        self.send_response(code)
        if obj is not None: self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def handle_any(self):
        if self.headers.get("Authorization") != f"Bearer {TOKEN}":
            return self.reply(401, {"error": "unauthorized"})
        u = urlparse(self.path); p = u.path; m = self.command
        n = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(n) or b"null") if n else None
        if p == "/api/v1/status" and m == "GET":
            reset = (now() + timedelta(days=1)).replace(hour=6, minute=0, second=0)
            return self.reply(200, dict(mode="queue", fail_closed=True, protocols=["YouTube", "YouTubeUpload", "YouTubeKids"],
                started=iso(STARTED), time=iso(now()), uptime_seconds=int((now() - STARTED).total_seconds()),
                next_reset=reset.astimezone(timezone(timedelta(hours=-7))).isoformat(), rules={"ip": 1, "mac": 3, "ja4": 0},
                users=len(users)))
        if p == "/api/v1/users" and m == "GET":
            return self.reply(200, [user_obj(k) for k in sorted(users)])
        if p == "/api/v1/devices" and m == "GET":
            out = []
            for d in seen:
                o = {k: v for k, v in d.items() if k != "m"}
                o["last_seen"] = iso(now() - timedelta(minutes=d["m"]))
                who, rule = owner(d["ip"], d.get("mac"))
                if who: o["user"], o["rule"] = who, rule
                out.append(o)
            return self.reply(200, out)
        if p == "/api/v1/blocks" and m == "GET":
            q = parse_qs(u.query); lim = int(q.get("limit", ["100"])[0])
            r = [b for b in blocks if "user" not in q or b.get("user") == q["user"][0]]
            return self.reply(200, r[-lim:])
        mm = re.fullmatch(r"/api/v1/users/([A-Za-z0-9_.-]+)(/extra|/pause)?", p)
        if not mm: return self.reply(404, {"error": "not found"})
        name, sub = mm.groups()
        if sub is None and m == "PUT":
            if body.get("allowance_seconds") is None and not body.get("devices"):
                return self.reply(400, {"error": "a user needs allowance_seconds or at least one device"})
            new = [(d["type"], norm(d["type"], d["value"]), "settings") for d in body.get("devices") or []]
            cur = users.get(name, dict(allow=0, used=0, extra=0, paused=False, managed=True, devices=[]))
            cur["devices"] = [d for d in cur["devices"] if d[2] == "rules"] + new
            if body.get("allowance_seconds") is not None: cur["allow"] = body["allowance_seconds"]
            cur["managed"] = True; users[name] = cur
            return self.reply(200, user_obj(name))
        if name not in users: return self.reply(404, {"error": f'no user "{name}"'})
        if sub is None and m == "GET": return self.reply(200, user_obj(name))
        if sub is None and m == "DELETE":
            if not users[name]["managed"]: return self.reply(404, {"error": "no settings"})
            rules = [d for d in users[name]["devices"] if d[2] == "rules"]
            if rules: users[name].update(devices=rules, managed=False)
            else: del users[name]
            return self.reply(204)
        if sub == "/extra" and m == "POST":
            users[name]["extra"] = max(0, users[name]["extra"] + body["seconds"]); return self.reply(200, user_obj(name))
        if sub == "/pause":
            users[name]["paused"] = m == "PUT"; return self.reply(200, user_obj(name))
        return self.reply(405, {"error": "method"})

    do_GET = do_PUT = do_POST = do_DELETE = handle_any
    def log_message(self, fmt, *a): sys.stderr.write("%s %s\n" % (self.command, self.path))

print("Pairing link:\n  fltr://pair?" + urlencode({"url": f"https://{HOST}:8443", "token": TOKEN, "pin": PIN}), flush=True)
srv = ThreadingHTTPServer(("0.0.0.0", 8443), H)
ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); ctx.load_cert_chain(CERT, KEY)
srv.socket = ctx.wrap_socket(srv.socket, server_side=True)
srv.serve_forever()
