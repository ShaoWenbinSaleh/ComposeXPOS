# Open-Source Payment/Printing Integration Guide

This repository now runs **mock-only** payment and printing flows.

## Current behavior

- Payment actions show an in-app popup/toast and return mock success (or mock cancel).
- Print actions show an in-app popup/toast and return mock success.
- No private key, merchant credential, or vendor endpoint is bundled in source.

## How to implement real payment

1. Create a provider adapter in `shared/src/commonMain/kotlin/com/cofopt/shared/payment/wecr/` (or a new provider package).
2. Replace mock calls in `WecrHttpsClient`/`WecrTcpClient` with real SDK or HTTPS/TCP logic.
3. Load credentials from runtime secure storage (Android Keystore/remote config/backend token), not constants.
4. Add signing, retry, timeout, cancel, and idempotency handling.
5. Audit-log transaction lifecycle and error codes.

## How to implement real printing

1. Implement payload generation (ESC/POS or vendor command format) in app print modules.
2. Implement transport adapters (USB/IP/Vendor SDK) with proper connection and retry handling.
3. Validate printer capability (paper width/codepage/cut support) before dispatch.
4. Return success only after printer/job acknowledgment.
5. Keep mock fallback path for local development and CI.

## Security requirements

- Never commit private keys, certificates, merchant IDs, or production URLs.
- Keep secrets outside VCS and inject them per environment.
- Rotate credentials periodically and revoke leaked material immediately.
