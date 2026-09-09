# LABUDA

LABUDA — Android VLESS subscription client.

## Goal

Build a fast, clean and reliable client that imports a complete VLESS/Xray subscription from a QR code or clipboard, keeps the full server list, lets the user select a server, and connects through Android VPNService using a real Xray-compatible core.

## Architecture

- Kotlin + Jetpack Compose
- Subscription import and refresh layer
- VLESS profile parser
- Local profile storage
- QR/clipboard import
- Server list and selection
- Xray core integration layer
- Android VPNService
- Traffic statistics
- Per-app routing
- GitHub Actions APK builds

The project deliberately does **not** treat a subscription QR as a single `vless://` server. A subscription URL is fetched and parsed as a collection of profiles.

## Status

Initial Android application skeleton is being built. Core/VPN integration will be added after the subscription pipeline is in place.
