HASDEO BANK - COMPLETE COMBINED UPDATE

This package combines the Premium Features update and the 100 Crore + Paise update.

Included premium features:
- Multilingual AI support instructions (requires OPENAI_API_KEY in Render for live AI responses)
- Transaction search and credit/debit filters
- CSV statement export and browser print-to-PDF
- Savings goal tracker (browser-local)
- Demo account QR code
- Customer password change option
- Transaction notification centre
- Dark mode and language switch
- Customer feedback and ratings
- Admin analytics and monthly CSV report
- Existing admin/customer management, transfers, login history, announcements, maintenance mode, and backup features retained

Amount updates:
- Deposit, withdrawal, and transfer limit: up to INR 100 crore per transaction
- Supports amounts with up to 2 decimal places, e.g. INR 0.78
- Indian number grouping with Lakh/Crore compact display
- Database amount precision updated for larger values

IMPORTANT:
- This is an educational demo project, not a real banking/payment system.
- Build locally with Maven before pushing to GitHub and deploying on Render.
- Java/Maven build and live Render deployment have not been verified as part of packaging.


Latest additions in this final package:
- Lightweight CSS-only animations with reduced-motion support; no continuous heavy effects.
- Refined navy/gold visual treatment for the existing 16-digit fictional demo card.
- Admin Panel > AI Brain: set creator name (default Alok) and persistent custom knowledge / FAQ instructions.
- AI continues to answer normal questions independently, using admin guidance when relevant.
- AI website facts updated for the ₹100 crore transaction cap and paise support.
- AI Brain settings require OPENAI_API_KEY for live model responses.

v5 verification notes:
- Fullscreen dashboard CSS/JS cache versions bumped so browsers fetch the updated layout files.
- Live Presence now treats a customer as ONLINE when any of their sessions has a fresh heartbeat.
- Added persistent presence timeline events for session start, periodic heartbeat, explicit sign-out, and timeout expiration.
- Historical presence events begin from this deployment; older presence transitions cannot be reconstructed retroactively.
