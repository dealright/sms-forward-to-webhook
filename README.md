# sms-forward-to-webhook

Android app that intercepts incoming SMS messages and forwards them to any webhook URL as an HTTP POST with a fully customizable JSON body.

Works with Google Chat, Slack, Discord, Microsoft Teams, or any custom webhook endpoint.

## Download

Grab the latest APK from [Releases](https://github.com/dylanjamessnow/sms-forward-to-webhook/releases).

## Features

- **Customizable JSON template** — write your own request body using `{{variable}}` placeholders
- **14 SMS fields** — sender, body, timestamp, SIM slot, carrier, country, service center, format, and more
- **Configurable headers** — Content-Type selector (JSON, text, form-urlencoded) + custom HTTP headers
- **Full debug log** — every forwarded message shows request headers, request body, response headers, and response body
- **Test button** — verify your webhook works without waiting for a real SMS
- **adb debug receiver** — simulate inbound SMS from your computer for testing
- **No tracking, no analytics, no ads** — the only network request is the webhook POST you configure

## Available Template Variables

| Variable | Description |
|---|---|
| `{{sender}}` | Sender phone number |
| `{{body}}` | Message text |
| `{{timestamp}}` | Unix timestamp (ms) |
| `{{service_center}}` | SMSC address |
| `{{display_address}}` | Formatted sender |
| `{{display_body}}` | Formatted message |
| `{{is_email}}` | true if email gateway |
| `{{protocol_id}}` | Protocol identifier |
| `{{status}}` | Message status code |
| `{{sim_slot}}` | SIM slot index (0, 1, ...) |
| `{{sim_name}}` | SIM display name |
| `{{carrier}}` | Carrier name |
| `{{country}}` | Country ISO code |
| `{{format}}` | 3gpp or 3gpp2 |

### Example templates

**Google Chat:**
```json
{"text": "SMS from {{sender}}: {{body}}"}
```

**Slack:**
```json
{"text": "*{{sender}}* ({{carrier}}, SIM {{sim_slot}})\n{{body}}"}
```

**Custom API:**
```json
{"from": "{{sender}}", "message": "{{body}}", "sim": "{{sim_name}}", "carrier": "{{carrier}}", "ts": "{{timestamp}}"}
```

## Testing via adb

You can simulate an inbound SMS from your computer without needing a real text:

```bash
adb shell am broadcast \
  -a com.dealright.smsforwarder.DEBUG_SMS \
  --es sender "+15559876543" \
  --es body "Hello from adb" \
  -n com.dealright.smsforwarder/.DebugSmsReceiver
```

## Building from source

```bash
# Clone
git clone https://github.com/dylanjamessnow/sms-forward-to-webhook.git
cd sms-forward-to-webhook

# Set up local.properties with your Android SDK path
echo "sdk.dir=/path/to/android/sdk" > local.properties

# Build debug APK
./gradlew assembleDebug

# APK will be at app/build/outputs/apk/debug/app-debug.apk
```

For signed release builds, create a `keystore.properties` file:
```properties
storeFile=../keystore.jks
storePassword=yourpassword
keyAlias=release
keyPassword=yourpassword
```

Then run `./gradlew assembleRelease`.

## Requirements

- Android 8.0+ (API 26)
- SMS permissions (prompted on first launch)

## License

Apache-2.0 — see [LICENSE](LICENSE).
