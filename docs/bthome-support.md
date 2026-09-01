# BTHome support

Android service data starts with Device Information, not UUID bytes. Only unencrypted BTHome v2 is accepted for measurements.

The scanner filters full service UUID `0xFCD2`. Device Information exposes version `(byte >> 5) & 0x07`, encryption bit 0, and trigger bit 2. Version 2 and unencrypted payloads are required for measurement decoding.

| Object | ID | Encoding/result |
| --- | --- | --- |
| Packet ID | `0x00` | uint8 |
| Battery | `0x01` | uint8 percent |
| Temperature | `0x02` | signed LE int16, 0.01 °C |
| Humidity | `0x03` | LE uint16, 0.01 % |
| Pressure | `0x04` | LE uint24, 0.01 hPa |
| CO2 | `0x12` | LE uint16 ppm |
| Timestamp | `0x50` | LE uint32 Unix seconds |
| Device type | `0xF0` | LE uint16 |
| Firmware | `0xF1`/`0xF2` | component bytes reversed for display |

For example, `F1 00 01 02 04` displays `4.2.1.0` and `F2 00 01 06` displays `6.1.0`. Object IDs must be ordered. Duplicates retain the first supported value with a diagnostic; truncation is malformed. An unknown object preserves prior values, stops because length is unknown, and adds an unsupported-object diagnostic. Raw packet bytes remain transient and are not logged or persisted. V1, encryption, GATT, and unlisted objects are out of scope.

Android scanning uses exact first-byte Device Information service-data prefixes for v1/v2 regular, trigger, encrypted, and unencrypted advertisements. This accommodates Pixel offload behavior that did not match empty or masked Service Data filters; the callback still extracts and verifies FCD2 service data before parsing.
