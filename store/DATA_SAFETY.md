# Play Data Safety form — NeoCalc

The answers below match what the code actually does, checked against the source
rather than against intent. If a future change makes any of these wrong, the
form has to change in the same release, and so does `about/Legal.kt`.

---

## Data collection and sharing

**Does your app collect or share any of the required user data types?**
→ **No.**

That single answer covers the whole form, but the reasoning for each category is
recorded here so the next person does not have to re-derive it.

| Category | Collected? | Why not |
| --- | --- | --- |
| Location | No | No location API is used, and no location permission is declared. |
| Personal info | No | No accounts, no name, email, phone or ID is ever asked for. |
| Financial info | No | Amounts typed into the calculator or converter never leave the device. They are not payment details and are not transmitted. |
| Health and fitness | No | Not applicable. |
| Messages | No | Not applicable. |
| Photos and videos | No | A scanned photo is passed to on-device text recognition and discarded. It is not stored by the app and not uploaded. |
| Audio files | No | Not applicable. |
| Files and docs | No | Export and import use the system document picker; the app only reads or writes the one file the user chooses, at the moment they choose it. |
| Calendar | No | Not applicable. |
| Contacts | No | Not applicable. |
| App activity | No | No analytics SDK, no event logging, no crash reporting. |
| Web browsing | No | Not applicable. |
| App info and performance | No | No crash or diagnostic reporting library is present. |
| Device or other IDs | No | No advertising ID, no device ID, no fingerprinting. |

---

## Security practices

- **Is data encrypted in transit?** Yes. Every network request is HTTPS.
- **Can users request data deletion?** There is nothing held to delete. All data
  is local; uninstalling removes it, and the app also offers an explicit export
  so the user can take it with them.
- **Has the app been independently reviewed against a security standard?** No.

---

## Permissions declared, and why

| Permission | Why | User-visible |
| --- | --- | --- |
| `INTERNET` | Fetching exchange rates | Yes, rates only load online |
| `ACCESS_NETWORK_STATE` | Deciding whether a fetch is worth attempting | No |
| `POST_NOTIFICATIONS` | Delivering a rate alert the user created | Asked only after the user creates their first alert |
| `CAMERA` | Scanning a price | Asked only when the user taps the camera button |

`CAMERA` is declared alongside `<uses-feature android:required="false">`, so the
app installs normally on devices without one.

---

## Third-party services contacted

| Host | What is sent | What comes back |
| --- | --- | --- |
| `open.er-api.com` | A base currency code | That day's rates |
| `api.frankfurter.app` | A base currency code, or a date range and a pair | Rates, or a 30-day history |
| `api.coingecko.com` | Asset ids and a base currency | Crypto and gold prices |

None of these requests carry an identifier, an account, or anything the user
typed. As with any web request, the operators of those servers will see the
device's IP address. This is stated in the in-app privacy policy.

ML Kit text recognition is bundled, not Play-delivered, so scanning makes no
network call at all.
