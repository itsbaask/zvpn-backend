# Z VPN Rebrand Token Replacement Map (RENAME_MAP.md)

This map documents all token transformations applied during the rebrand from **ZedSecure** to **Z VPN** (`com.zvpn`).

## 1. High-Level Tokens & Identifiers

| Old Token | New Token | Context / Scope |
| :--- | :--- | :--- |
| `ZedSecure` | `Z VPN` | App title, user-visible UI strings, documentation |
| `ZedSecure` | `ZVPN` | Brand token without spaces, desktop `packageName`, Gradle `rootProject.name` |
| `dev.cluvex.zedsecure` | `com.zvpn` | Application ID, main Android namespace, Java/Kotlin package prefix |
| `dev.cluvex.zedsecure.shared` | `com.zvpn.shared` | Shared module namespace, KMP package prefix |
| `dev.cluvex.zedsecure.shared.resources` | `com.zvpn.shared.resources` | Compose resources package (`packageOfResClass`) |
| `zedsecure` | `zvpn` | Lowercase identifiers, directory names, file prefixes |
| `zedsecure://` | `zvpn://` | Deep link URL scheme |
| `dev.cluvex.zedsecure.widget.TOGGLE` | `com.zvpn.widget.TOGGLE` | Android Widget toggle intent action |

## 2. PascalCase Source Code Identifiers

| Old Class / Token | New Class / Token | Context / Scope |
| :--- | :--- | :--- |
| `ZedSecureApp` | `ZVpnApp` | Application class |
| `ZedSecureTheme` | `ZVpnTheme` | Compose theme entry point |
| `ZedVpnService` | `ZVpnService` | Main VPN Service (never ZVpnVpnService) |
| `ZedWidgetProvider` | `ZWidgetProvider` | App widget provider |
| `ZedTileService` | `ZTileService` | Quick settings tile service |
| `ZedLink` | `ZLink` | Deep link parser and builder |
| `ZedLinkTest` | `ZLinkTest` | Unit test for ZLink |
| `ZedLinkFieldCoverageTest` | `ZLinkFieldCoverageTest` | Unit test for ZLink |
| `ZedLime` | `ZLime` | Theme color token |
| `ZedViolet` | `ZViolet` | Theme color token |
| `ZedHotPink` | `ZHotPink` | Theme color token |
| `ZedCyan` | `ZCyan` | Theme color token |
| `ZedDeepViolet` | `ZDeepViolet` | Theme color token |
| `ZedOnLime` | `ZOnLime` | Theme color token |
| `ZedGradients` | `ZGradients` | Theme gradient definitions |
| `ZedShapes` | `ZShapes` | Theme shape definitions |
| `ZedNavBar` | `ZNavBar` | Navigation bar component |
| `ZsxCrypto` | `ZvxCrypto` | Config encryption/decryption helper |
| `ZsxModels` | `ZvxModels` | Config file models |
| `ZsxSharing` | `ZvxSharing` | File sharing helper |
| `ZsxCryptoTest` | `ZvxCryptoTest` | Encryption test |
| `ZsxFileNameTest` | `ZvxFileNameTest` | File name test |
| `ZsxSealRequest` | `ZvxSealRequest` | Config seal payload |
| `ZsxHeader` | `ZvxHeader` | File header parser |
| `ZsxLegacyException` | `ZvxLegacyException` | Legacy config format exception |

## 3. Snake_case Resources & File Names

| Old Resource / File | New Resource / File | Context / Scope |
| :--- | :--- | :--- |
| `ic_zed_mark` / `ic_zed_mark.xml` | `ic_z_mark` / `ic_z_mark.xml` | Brand mark drawable |
| `ic_tile_zed` / `ic_tile_zed.xml` | `ic_tile_z` / `ic_tile_z.xml` | Quick settings tile icon |
| `widget_zed.xml` | `widget_z.xml` | Main widget layout |
| `widget_zed_compact.xml` | `widget_z_compact.xml` | Compact widget layout |
| `widget_zed_preview.xml` | `widget_z_preview.xml` | Widget preview layout |
| `appwidget_zed.xml` | `appwidget_z.xml` | Widget provider info XML |
| `zed_window_bg` | `z_window_bg` | Window background resource |
| `zed_settings` | `z_settings` | Settings file key |
| `zed_configs` | `z_configs` | Config storage directory / file key |
| `zed_vpn` | `z_vpn` | VPN database / preferences key |
| `zed_geo_seed` | `z_geo_seed` | Geo seed key |
| `subs_ua_zedsecure` | `subs_ua_zvpn` | Subscription User-Agent key (Value: `"ZVPN"`) |
| `.zsx` | `.zvx` | Encrypted configuration file extension |
| `zsx_*` | `zvx_*` | Resource string keys for config files |

## 4. Native Helpers, Environment Variables & Scripts

| Old Name | New Name | Purpose |
| :--- | :--- | :--- |
| `zedcore` / `zedcore.aar` | `zvpncore` / `zvpncore.aar` | Native binary / library archive |
| `tools/build-zedcore.sh` | `tools/build-zvpncore.sh` | Build script for native core |
| `zeddns` / `zeddns.exe` | `zvpndns` / `zvpndns.exe` | Native DNS binary |
| `ZEDCORE_TMPDIR` | `ZVPNCORE_TMPDIR` | Native temp directory env var |
| `ZEDSECURE_TUN_READY` | `ZVPN_TUN_READY` | TUN ready status signal env var |
| `ZEDSECURE_SOFTWARE_RENDERING` | `ZVPN_SOFTWARE_RENDERING` | Desktop rendering flag env var |
| `ZED_DUMP_DIR` | `ZVPN_DUMP_DIR` | Core crash dump directory env var |

## 5. Gradle & Desktop Packaging Tokens

| Location | Old Setting | New Setting |
| :--- | :--- | :--- |
| `settings.gradle.kts` | `rootProject.name = "ZedSecure"` | `rootProject.name = "ZVPN"` |
| `gradle.properties` | `zedsecure.desktopVersionName` | `zvpn.desktopVersionName` |
| `app/build.gradle.kts` | `namespace = "dev.cluvex.zedsecure"` | `namespace = "com.zvpn"` |
| `app/build.gradle.kts` | `applicationId = "com.zedsecure.vpn"` | `applicationId = "com.zvpn"` |
| `shared/build.gradle.kts` | `namespace = "dev.cluvex.zedsecure.shared"` | `namespace = "com.zvpn.shared"` |
| `shared/build.gradle.kts` | `packageOfResClass = "dev.cluvex..."` | `packageOfResClass = "com.zvpn.shared.resources"` |
| `desktop/build.gradle.kts` | `packageName = "ZedSecure"` | `packageName = "ZVPN"` |
| `desktop/build.gradle.kts` | `bundleID = "dev.cluvex.zedsecure"` | `bundleID = "com.zvpn"` |
| `desktop/build.gradle.kts` | `vendor = "Cluvex"` | `vendor = "Z VPN"` |
| `desktop/icons/` | `zedsecure.{png,ico,icns,svg}` | `zvpn.{png,ico,icns,svg}` |

## 6. Allowlisted Exceptions (Unchanged)

- AGPL-3.0 copyright and license texts in `LICENSE`, `COPYRIGHT`, and `NOTICE`.
- Attribution line added to `NOTICE`: `"Z VPN is based on ZedSecure by CluvexStudio (AGPL-3.0)."`.
- Third-party repository URLs and dependency coordinates (`github.com/CluvexStudio/Xray-core`, `AndroidLibXrayLite`, `sing-openvpn`, `qpack`, `psiphon-tunnel-core`, `tools/core-sources.txt`, branch names in `NOTICE`).
- English dictionary words containing `"zed"` (e.g. `synchronized`, `randomized`, `initialized`, `serialized`, `optimized`, `customized`, `normalized`, `HelloRandomizedALPN`).
