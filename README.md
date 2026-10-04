<p align="center">
  <img src="docs/assets/icon.png" width="104" alt="Z VPN">
</p>

<h1 align="center">Z VPN</h1>

<p align="center">Every way through, in one app.</p>

<p align="center">
  <a href="https://github.com/zvpn/zvpn/releases/latest"><img src="https://img.shields.io/github/v/release/zvpn/zvpn?style=flat-square&color=C7F24E&labelColor=15170B&label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android%20%C2%B7%20Linux%20%C2%B7%20Windows%20%C2%B7%20macOS-C7F24E?style=flat-square&labelColor=15170B" alt="Android, Linux, Windows, macOS">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-AGPL--3.0-C7F24E?style=flat-square&labelColor=15170B" alt="AGPL-3.0"></a>
</p>

<p align="center"><b>English</b> · <a href="README.fa.md">فارسی</a></p>

## What is Z VPN?

Z VPN is a VPN and proxy client for Android, Linux, Windows and macOS that ships with more than one
engine. When one route is blocked, the next one is already installed: Xray and sing-box for the
usual protocols, Psiphon and Tor for when nothing else connects, DNS tunnels for networks that let
little more than DNS through, and WireGuard, AmneziaWG, OpenConnect and IKEv2 for servers you
already run. Engines can be chained through each other, and one tap tests every server and moves
the connection to the fastest one.

<p align="center">
  <img src="docs/assets/showcase.png" width="100%" alt="Z VPN: home, servers and settings">
</p>

## Engines

| Engine | Carries |
|---|---|
| Xray | VLESS with Reality, Vision and XHTTP, VMess, Trojan, Shadowsocks, Hysteria2, WireGuard, AmneziaWG |
| sing-box | TUIC, Naive, AnyTLS, ShadowTLS, OpenVPN `.ovpn` files, and plain sing-box JSON |
| Psiphon | Psiphon's own network; no server of yours needed |
| Tor | obfs4, Snowflake and Conjure bridges |
| DNS tunnels | DNSTT, VayDNS and MasterDNS, over UDP, TCP, DoT or DoH |
| OpenConnect | Cisco AnyConnect and compatible gateways |
| IKEv2 | the IPsec client built into Android |
| SSH | on its own, or through any of the above |

Android has all of them. Linux, Windows and macOS carry Xray, sing-box, the DNS tunnels and SSH.

## Features

- Imports share links, subscriptions, Xray and sing-box JSON, `.ovpn`, Amnezia `vpn://` and QR codes
- Auto-select keeps the tunnel on the fastest server and fails over on its own
- Chains in both directions, for example Xray over Tor or Tor over Xray
- Per-app routing, a geoip/geosite rule editor, and SNI spoofing on rooted devices
- Vault: share a config as a `.zsx` file that connects without showing it, with an optional password and expiry date
- Speed test, MTU finder, DNS resolver scanner and a live log
- Material 3 Expressive, light and dark, in English, Persian, Russian and Chinese

## Download

**Android:** APK for your device from [Releases](https://github.com/zvpn/zvpn/releases/latest)
(`arm64-v8a` for almost every phone, `armeabi-v7a` for older ones).

**Linux:** `.deb`, `.rpm`, `.AppImage` or `.tar.gz` from Releases.

**Windows:** the `.msi` installer, or the portable `.zip`.

**macOS:** the `.dmg` for Apple Silicon or for Intel. The app is not notarized, so open it the
first time with right-click, then Open.

## Building

```sh
./tools/fetch-cores.sh           # every engine at its pinned commit
./tools/build-zedcore.sh         # the Android core; needs Go 1.26.3 and NDK 28 or newer
./gradlew :app:assembleRelease   # three APKs, one per ABI
./gradlew :desktop:packageDeb    # or packageRpm, packageMsi, packageDmg
```

## Credits

The DNSTT and VayDNS modes of the DNS tunnel are built with the
[VayDNS](https://github.com/net2share/vaydns) library, a fork of [dnstt](https://www.bamsoftware.com/software/dnstt/). The MasterDNS mode is a separate engine, credited below.

Z VPN also stands on [Xray-core](https://github.com/XTLS/Xray-core), [sing-box](https://github.com/SagerNet/sing-box), [Psiphon](https://github.com/Psiphon-Labs/psiphon-tunnel-core), [Tor](https://www.torproject.org/), [MasterDnsVPN](https://github.com/masterking32/MasterDnsVPN), [AmneziaWG](https://github.com/amnezia-vpn/amneziawg-go), [OpenConnect](https://www.infradead.org/openconnect/), and [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel). Their licences are in [NOTICE](NOTICE).

## License

[AGPL-3.0](LICENSE). The core also links sing-box, Psiphon and sing-openvpn, which are GPL-3.0;
section 13 of GPL-3.0 allows combining them with AGPL-3.0 code, and each keeps its own licence.
