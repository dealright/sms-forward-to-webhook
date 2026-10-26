<p align="center">
  <img src="docs/icon.png" alt="sms-forward-to-webhook" width="160" />
</p>

# sms-forward-to-webhook

Android app that intercepts incoming SMS messages and forwards them to any webhook URL as an HTTP POST with a fully customizable JSON body.

Works with Google Chat, Slack, Discord, Microsoft Teams, or any custom webhook endpoint.

## Install

**Direct APK** — download the latest signed APK from [Releases](https://github.com/dealright/sms-forward-to-webhook/releases) and install it. Android will warn about installing from an unknown source; that's expected.

**[Obtainium](https://github.com/ImranR98/Obtainium)** — automatic updates straight from this repo's GitHub Releases. After installing Obtainium, add this app:

```
https://github.com/dealright/sms-forward-to-webhook
```

**F-Droid** — submission in progress. Once accepted, the app will be available at `https://f-droid.org/packages/com.dealright.smsforwarder/`.

**IzzyOnDroid** — submission in progress. Once accepted, add the IzzyOnDroid repo to your F-Droid client and search for "SMS Forward to Webhook".

## Features

- **Customizable JSON template** — write your own request body using `{{variable}}` placeholders
- **14 SMS fields** — sender, body, timestamp, SIM slot, carrier, country, service center, format, and more
- **Works with the phone locked** — delivery survives screen lock, Doze, the app being closed or killed, and reboots, and retries until the webhook accepts the message
- **Dual-SIM routing** — send each SIM's incoming SMS to its own webhook, with a shared default fallback
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

## Dual SIM

The app receives SMS from every active SIM and records which SIM each message
arrived on (`{{sim_slot}}`, `{{sim_name}}`, `{{carrier}}`). Under **Per-SIM
webhooks (dual SIM)** you can give each SIM its own webhook URL: a message
received on that SIM is routed to its URL, and any SIM left blank falls back to
the default **Webhook URL** at the top.

Granting the phone permission (prompted on first launch) lets the app auto-detect
and label each SIM by carrier and number. Routing by slot still works if you
decline — you just won't get the friendly labels.

## Reliability

Each incoming SMS is handed to an Android system job (JobScheduler) instead of
being sent from the SMS receiver directly. The job:

- has network access in Doze, even when the app is battery-optimized (an
  expedited job on Android 12+)
- runs with the screen locked and the app closed; Android starts the app for it
- is saved to disk, so it survives the app being killed and a reboot
- waits for a network connection, then retries quick failures after 5, 10, 20,
  40 and 60 seconds, then with exponential backoff for up to 24 hours. `429`
  and `5xx` responses are retried; other `4xx` responses are not

On first launch the app asks to be exempt from battery optimization. Delivery
works without the exemption; with it, backoff retries are not held until a
Doze maintenance window.

Limits Android imposes on every app:

- **Force stop** (Settings > Apps > Force stop) blocks all broadcasts until the
  app is opened again.
- **RCS and iMessage** chats are not SMS. Android does not announce them to
  apps, so they are not forwarded. To test, send a plain SMS (turn off RCS
  chats on the sending phone) or use a verification-code SMS.

## Testing via adb

You can simulate an inbound SMS from your computer without needing a real text:

```bash
adb shell am broadcast \
  -a com.dealright.smsforwarder.DEBUG_SMS \
  --es sender "+15559876543" \
  --es body "Hello from adb" \
  --ei slot 0 \
  -n com.dealright.smsforwarder/.DebugSmsReceiver
```

The optional `--ei slot <0|1>` extra selects which SIM's webhook to route to.

## Building from source

```bash
# Clone
git clone https://github.com/dealright/sms-forward-to-webhook.git
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
- Phone permission (`READ_PHONE_STATE`) — optional, enables SIM detection and per-SIM routing labels
- Battery optimization exemption — optional, prompted on first launch, makes retries faster

## License

Apache-2.0 — see [LICENSE](LICENSE).
