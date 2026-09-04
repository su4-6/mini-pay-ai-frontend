# Food H5 Bridge v1

This document reserves the only supported messages between the UniApp-generated food H5 and the MiniPay Android container. It is a security contract, not an enabled business API.

## Preconditions

- The WebView loads one configured HTTPS origin only.
- The food H5 has completed its own account authorization flow.
- MiniPay access tokens, refresh tokens, payment passwords, payment authorization tokens and authoritative payment amounts never enter the H5 or bridge payload.

## Messages from H5 to Android

| Type | Purpose | Allowed data |
|---|---|---|
| `REQUEST_AUTHORIZATION` | Request the native account-binding flow | `requestId` only |
| `CLOSE` | Request WebView dismissal | `requestId` only |
| `REQUEST_NATIVE_PAYMENT` | Request navigation to the native payment route | `requestId`, `externalOrderNo` only |

## Messages from Android to H5

| Type | Purpose | Allowed data |
|---|---|---|
| `AUTHORIZATION_CHANGED` | Report binding state | `requestId`, `bindingStatus` |
| `PAYMENT_RESULT` | Report a completed native payment attempt | `requestId`, `externalOrderNo`, `payOrderNo`, `status` |

The native app must fetch its own authoritative payment intent before presenting a payment screen. Unknown types, non-HTTPS origins and any payload containing sensitive credentials or monetary values are rejected.
