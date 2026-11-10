# MAL API agreement checklist

Checked against the [MAL API agreement](https://myanimelist.net/static/apiagreement.html) as read on 2026-10-20 (ADR 0007). Re-read the agreement before a public release: it is not re-verified here.

| Rule | How the project meets it | Where |
|---|---|---|
| No stored user credentials | The app signs in with OAuth 2 and PKCE, so the user's MAL password never reaches the app or the server. The server holds no MAL token or profile. | ADR 0007, ADR 0008 |
| Cache only as long as needed | Anime details and search results are cached on the device with an expiry (24 hours for details, 1 hour for search) and dropped on request. The MAL user profile is never cached. | ADR 0008 (Caching) |
| Removal within 24 hours | The server stores no MAL content, so there is nothing of MAL's to remove there. Account deletion removes everything the server holds. The privacy policy promises a response to removal requests within 24 hours. | ADR 0013, privacy policy |
| No scraping | The app uses only the official API v2 endpoints. | ADR 0008 |
| No revenue | Open source, no ads, no payments, no paid tier. | Project scope |
| No personal data on our server | Only the Google `sub`, library entries with MAL anime IDs and the linked platform name. | `docs/data-safety.md`, ADR 0007 |

## Not verified in this checklist
- That the 24-hour cache expiry from ADR 0008 is what the code does today.
- That the agreement text has not changed since 2026-10-20.
