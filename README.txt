HASDEO BANK — EDUCATIONAL WEBSITE DEMO
======================================

IMPORTANT
This is a fictional school/learning demonstration, NOT a real bank.
Do not use real money, real banking credentials, or real customer personal data.
All customer and transaction records are demo records only.

FEATURES
- Original Navy + Gold public website design retained
- Admin sign-in and demo banking dashboard
- Demo deposit, withdrawal and transfer flows
- Transaction history stored in PostgreSQL
- Transaction timestamps stored as UTC instants and displayed in Asia/Kolkata (IST)
- Owner Panel using the same authenticated admin session
- Demo customer management
- Transaction monitoring, activity logs and admin session history
- Maintenance mode and basic website settings
- Google Drive PostgreSQL backups: daily at 2:00 AM IST while the Render service is running
- Manual backup action and backup history in Owner Panel
- Backup retention (default: latest 14 backups)
- Backup archive is restored into a temporary isolated PostgreSQL 18 instance before upload
- Google Drive upload is checked using file ID, size and MD5 checksum

REQUIREMENTS
- JDK 17 or newer
- Maven 3.9+ (or use your IDE's Maven integration)
- PostgreSQL database (Neon PostgreSQL is supported)
- Docker deployment for the built-in pg_dump / temporary PostgreSQL restore test

ENVIRONMENT VARIABLES
Set these in your local terminal and in your hosting provider's environment settings.
Never put passwords, service-account keys, or connection strings in source code or GitHub.

Required for the application:
DB_URL=jdbc:postgresql://YOUR-NEON-HOST/neondb?sslmode=require
DB_USERNAME=YOUR_DATABASE_USERNAME
DB_PASSWORD=YOUR_DATABASE_PASSWORD
ADMIN_USERNAME=YOUR_DEMO_ADMIN_USERNAME
ADMIN_PASSWORD=YOUR_DEMO_ADMIN_PASSWORD

Required to enable Google Drive backups:
# Choose ONE authentication method:
# Option A (Google Workspace Shared Drive):
GOOGLE_SERVICE_ACCOUNT_JSON_BASE64=BASE64_ENCODED_SERVICE_ACCOUNT_JSON
# Option B (personal Google Drive / OAuth user):
GOOGLE_OAUTH_CLIENT_ID=YOUR_OAUTH_CLIENT_ID
GOOGLE_OAUTH_CLIENT_SECRET=YOUR_OAUTH_CLIENT_SECRET
GOOGLE_OAUTH_REFRESH_TOKEN=YOUR_OAUTH_REFRESH_TOKEN
# Required for either method:
GOOGLE_DRIVE_FOLDER_ID=YOUR_DRIVE_FOLDER_ID

GMAIL ALERTS (optional, for admin notifications):
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=YOUR_GMAIL_ADDRESS
MAIL_APP_PASSWORD=YOUR_GOOGLE_16_CHARACTER_APP_PASSWORD
ADMIN_ALERT_EMAIL=YOUR_ADMIN_INBOX

For Gmail, enable 2-Step Verification on the sender Google account and create an App Password in Google Account security settings. Use the App Password, not the normal Google account password. Never paste it into ChatGPT or commit it to GitHub. The Owner Panel > Website Settings page shows whether SMTP is configured and provides a Send test email button.

Optional backup settings:
BACKUP_DB_URL=jdbc:postgresql://YOUR-DIRECT-NEON-HOST/neondb?sslmode=require
BACKUP_DB_USERNAME=YOUR_DATABASE_USERNAME
BACKUP_DB_PASSWORD=YOUR_DATABASE_PASSWORD
BACKUP_RETENTION_COUNT=14
BACKUP_MAX_SIZE_MB=250

NEON BACKUP CONNECTION
Use the direct (non-pooler) Neon endpoint for BACKUP_DB_URL if available. The normal app DB_URL can continue using Neon's pooled endpoint. If BACKUP_DB_URL / BACKUP_DB_USERNAME / BACKUP_DB_PASSWORD are omitted, the backup process falls back to DB_URL / DB_USERNAME / DB_PASSWORD.

GOOGLE DRIVE SETUP (DO THIS IN YOUR GOOGLE ACCOUNT, NOT IN CHAT)
1. Open Google Cloud Console and create/select a project.
2. Enable the Google Drive API for that project.
3. Create a dedicated backup folder in Google Drive and copy its folder ID from the URL.
4. Preferred for a personal Google Drive: configure an OAuth consent screen and create an OAuth client. Add https://developers.google.com/oauthplayground as an authorized redirect URI. In Google OAuth Playground settings, enable use of your own OAuth credentials, enter the client ID/secret, authorize the Drive scope https://www.googleapis.com/auth/drive, exchange the code, and copy the refresh token. Add GOOGLE_OAUTH_CLIENT_ID, GOOGLE_OAUTH_CLIENT_SECRET and GOOGLE_OAUTH_REFRESH_TOKEN to Render Environment.
5. Alternatively, for a Google Workspace Shared Drive, create a service account, generate a JSON key, add its client_email as a member of the Shared Drive with permission to create/manage files, Base64-encode the JSON locally, and set GOOGLE_SERVICE_ACCOUNT_JSON_BASE64. Service accounts do not have personal Drive storage; use a Shared Drive rather than relying on a personal My Drive folder.
6. Set GOOGLE_DRIVE_FOLDER_ID to the dedicated folder ID in Render Environment.
7. Keep OAuth secrets, refresh token, JSON key and Base64 value private. Never send them to ChatGPT or commit them to GitHub.
8. If an OAuth app is left in Testing mode, Google may expire its refresh token after seven days. For long-running automation, configure the consent screen appropriately and review Google's verification requirements.

WINDOWS POWERSHELL — LOCAL RUN
1. Open the extracted project folder in VS Code.
2. Open Terminal > New Terminal.
3. Set the required environment variables for this terminal session, using your own private values:
   $env:DB_URL="jdbc:postgresql://YOUR-NEON-HOST/neondb?sslmode=require"
   $env:DB_USERNAME="YOUR_DATABASE_USERNAME"
   $env:DB_PASSWORD="YOUR_DATABASE_PASSWORD"
   $env:ADMIN_USERNAME="YOUR_DEMO_ADMIN_USERNAME"
   $env:ADMIN_PASSWORD="YOUR_DEMO_ADMIN_PASSWORD"
4. Run:
   mvn spring-boot:run
5. Open http://localhost:8080

Note: the Google Drive backup process requires PostgreSQL 18 pg_dump, pg_restore, initdb, pg_ctl and createdb tools, plus a Linux postgres user for the isolated restore test. The supplied Dockerfile installs these tools. The supported route for verified Drive backups is Docker deployment; a plain Windows Maven run does not provide the Linux restore-test environment.

RENDER DEPLOYMENT
This project includes a Dockerfile. Configure the Render service to deploy using Docker so pg_dump, pg_restore and the temporary PostgreSQL restore-test server are present.
Add the five required application environment variables above in Render.
Also set COOKIE_SECURE=true on Render (keep false for local HTTP testing).
Add Google Drive and backup environment variables only after completing the Google Cloud / Drive setup.
Keep DB_PASSWORD, ADMIN_PASSWORD, GOOGLE_SERVICE_ACCOUNT_JSON_BASE64, GOOGLE_OAUTH_CLIENT_SECRET and GOOGLE_OAUTH_REFRESH_TOKEN private.

SCHEDULING LIMITATION
The Spring scheduler runs at 02:00 Asia/Kolkata only while the Render web service process is running. If the hosting plan spins the service down or it is sleeping at that time, the scheduled backup may be delayed or missed. Use an always-on service for reliable daily scheduling, or configure an external scheduler later.

DATABASE BEHAVIOUR
The application creates/updates its demo tables using Hibernate ddl-auto=update.
The initial demo balance is seeded once when the database is empty. Existing in-memory balances/history from an older deployment cannot be recovered automatically.
Back up the database before any schema changes.

TIMEZONE
Database timestamps are stored as UTC instants. API responses include an explicit IST offset,
and the browser formats transaction times using Asia/Kolkata. This avoids relying on the
Render server's or visitor's local timezone.

BACKUP VERIFICATION
Each run uses pg_dump custom format, restores that dump into a temporary isolated local PostgreSQL 18 instance, uploads it to the configured Drive folder, then checks Drive file ID, file size and MD5 checksum. Old matching backups beyond BACKUP_RETENTION_COUNT are removed only after a new backup is uploaded and verified. The temporary restore test does not modify the live Neon database.

TROUBLESHOOTING
- If the app cannot connect to PostgreSQL, check DB_URL, DB_USERNAME and DB_PASSWORD.
- If backup fails to connect, check BACKUP_DB_URL and credentials; use Neon's direct endpoint where possible.
- If Google Drive returns 403, confirm Drive API is enabled and the OAuth account has access to the folder, or the service account is a member of the target Shared Drive.
- If admin sign-in fails, check ADMIN_USERNAME and ADMIN_PASSWORD in the environment.
- If Render backup schedule is missed, confirm the service was awake at 02:00 IST.
- If port 8080 is busy locally, close the other process or set PORT to another port.
- If Maven dependencies fail, check internet connectivity and retry.

PROJECT STRUCTURE
src/main/java/com/hasdeo/bank/        Java backend, entities, repositories and backup service
src/main/resources/static/            Website HTML, CSS and JavaScript
src/main/resources/application.properties
pom.xml                               Maven project
Dockerfile                            Docker build with PostgreSQL 18 backup tools


CUSTOMER ACCOUNTS AND SUPPORT (EDUCATIONAL DEMO)
------------------------------------------------
- Customers can create an account with full name, username, optional email and a password (minimum 8 characters). Passwords are stored using PBKDF2-HMAC-SHA256 with a random salt.
- A 16-digit fictional demo card identifier and unique 4-digit transfer code are generated automatically. These are NOT payment cards, bank account numbers, PINs or payment credentials.
- Customer demo balances and transaction history are stored in the configured database. Customer-to-customer transfers use the 4-digit transfer code and update both customer balances in one database transaction.
- The Support form stores tickets in the database and makes them visible in Owner Panel > Support.
- Transaction times are stored as UTC instants and returned with the Asia/Kolkata (+05:30) offset; the browser also formats them explicitly in Asia/Kolkata to avoid device timezone shifts.

OPTIONAL GMAIL ADMIN NOTIFICATIONS ON RENDER
--------------------------------------------
Set these Environment Variables in the Render service (never put the app password in source code):
  ADMIN_ALERT_EMAIL=alokpandey27048@gmail.com
  MAIL_HOST=smtp.gmail.com
  MAIL_PORT=587
  MAIL_USERNAME=your-sending-gmail-address
  MAIL_APP_PASSWORD=your-Gmail-App-Password

Use a Gmail App Password from the Google Account that sends the alerts (2-Step Verification must be enabled if Google offers App Passwords for that account). Do not share the App Password in chat or commit it to GitHub. If mail variables are not configured, the website and database functions continue to work, but email alerts will not be delivered. Admin alerts are sent for new signups, admin login, deposits, withdrawals, transfers and support requests.

IMPORTANT
---------
This is a school/educational simulation only. It is not a real bank and does not connect to payment networks. Do not enter real card details, CVV, PIN, OTP, Aadhaar or other sensitive banking information.


AI SUPPORT CONFIGURATION (OPTIONAL)
-----------------------------------
The public Support Centre includes a website-specific AI assistant. To activate AI replies, add OPENAI_API_KEY in the Render service Environment settings. The default model is gpt-6.2-sol; optionally set OPENAI_MODEL to a model available to your API project. Never put the API key in app.js, index.html, or any public file. API usage may incur charges.

Human escalation and the Contact Support form save tickets in the database and email the owner only when Gmail SMTP is configured using MAIL_USERNAME and MAIL_APP_PASSWORD. ADMIN_ALERT_EMAIL controls the destination. If email is not configured, tickets remain visible in Owner Panel > Support.

ADMIN DEMO CARD
---------------
The admin demo card shown only after admin sign-in uses fictional card number 0000000000002704 and demo code 2704. It is not a real payment card and does not connect to real payment networks.


RENDER SESSION COOKIE
---------------------
The deployed Render site uses HTTPS, so the session cookie is marked Secure by default. For local HTTP-only testing, set COOKIE_SECURE=false in the local environment; do not disable Secure on the public HTTPS deployment.
