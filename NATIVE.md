# Native components

The app compiles without these. The GitHub Actions workflow (`.github/workflows/build.yml`) builds/downloads
all of them automatically, so building through CI gives a complete APK.

| Component | Needed for | Where it goes | CI |
|---|---|---|---|
| `libv2ray.aar` (Xray) | VLESS, VMess, Trojan, Shadowsocks, SOCKS, HTTP, WS/WSS, gRPC, TLS, mKCP (UDP) | `app/libs/` | downloaded from 2dust/AndroidLibXrayLite |
| `libhev-socks5-tunnel.so` | every connection (TUN -> SOCKS bridge) | `app/src/main/jniLibs/<abi>/` | built with NDK |
| `libdnstt.so` (dnstt-client binary) | SSH over DNSTT (DNS tunnel) | `app/src/main/jniLibs/<abi>/` | built with Go |

`hev/htproxy/TProxyService.kt` must keep its package and class name (JNI symbols).
SSH (Direct / Payload / HTTP proxy / SSL-TLS / WS-WSS / DNSTT) is pure Kotlin on top of JSch, no native lib except dnstt.

## DNSTT notes
dnstt is a Go program with no system resolver on Android: give the resolver as an IP
(`8.8.8.8:53`, `https://1.1.1.1/dns-query`, `1.1.1.1:853`). The SSH server is the one reachable
behind the dnstt server (usually `127.0.0.1:22` on the tunnel server).

## Not implemented
* OpenVPN (UDP/TCP): `.ovpn` files are detected and stored, but connecting shows a clear error. OpenVPN needs its own
  native engine (e.g. ics-openvpn, GPLv2); it is not bundled.
* WireGuard, per-app routing, split-tunnel and custom rules.
