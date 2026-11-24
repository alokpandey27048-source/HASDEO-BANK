HASDEO BANK — RENDER DEPLOYMENT CHECKLIST
========================================

This is an educational demo only. It is not a real bank and does not process real money.
The original navy/gold design is retained. Do not add real customer or banking data.

BEFORE DEPLOYING
----------------
1. Create/keep a Neon PostgreSQL database.
2. Back up any existing database before deploying schema changes.
3. Keep all passwords and OAuth credentials private. Do not commit them to GitHub or send them in chat.
4. Push the contents of this project folder (pom.xml, Dockerfile, src/, README.txt, etc.) to your GitHub repository.

RENDER SERVICE SETTINGS
-----------------------
For the existing Render web service:
- Repository: your HASDEO BANK GitHub repository
- Branch: main (or the branch you actually pushed)
- Runtime/environment: Docker
- Dockerfile path: ./Dockerfile
- Build context: project root
- Health check path (if you configure one): /api/public/status
- Do not set a separate static publish directory; this is a Spring Boot app serving its own static files.

REQUIRED RENDER ENVIRONMENT VARIABLES
-------------------------------------
DB_URL=jdbc:postgresql://YOUR_NEON_HOST/neondb?sslmode=require
DB_USERNAME=YOUR_NEON_DATABASE_USERNAME
DB_PASSWORD=YOUR_NEON_DATABASE_PASSWORD
ADMIN_USERNAME=YOUR_DEMO_ADMIN_USERNAME
ADMIN_PASSWORD=YOUR_DEMO_ADMIN_PASSWORD
COOKIE_SECURE=true

Use the actual Neon hostname and credentials from your Neon dashboard. Do not include angle-bracket placeholders literally.

OPTIONAL GMAIL NOTIFICATIONS
----------------------------
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=YOUR_SENDING_GMAIL_ADDRESS
MAIL_APP_PASSWORD=YOUR_GOOGLE_APP_PASSWORD
ADMIN_ALERT_EMAIL=YOUR_ADMIN_RECEIVER_ADDRESS

The App Password belongs in Render Environment only. Do not use your normal Google password. The app's Owner Panel has a mail status and test-email action. Gmail cannot send until valid SMTP credentials are configured.

OPTIONAL GOOGLE DRIVE BACKUP — CHOOSE ONE AUTH METHOD
----------------------------------------------------
For personal Google Drive OAuth:
GOOGLE_OAUTH_CLIENT_ID=YOUR_OAUTH_CLIENT_ID
GOOGLE_OAUTH_CLIENT_SECRET=YOUR_OAUTH_CLIENT_SECRET
GOOGLE_OAUTH_REFRESH_TOKEN=YOUR_OAUTH_REFRESH_TOKEN

Or for a Google Workspace Shared Drive service account:
GOOGLE_SERVICE_ACCOUNT_JSON_BASE64=YOUR_BASE64_SERVICE_ACCOUNT_JSON

For either method:
GOOGLE_DRIVE_FOLDER_ID=YOUR_TARGET_FOLDER_ID
BACKUP_DB_URL=jdbc:postgresql://YOUR_DIRECT_NEON_HOST/neondb?sslmode=require
BACKUP_DB_USERNAME=YOUR_DATABASE_USERNAME
BACKUP_DB_PASSWORD=YOUR_DATABASE_PASSWORD
BACKUP_RETENTION_COUNT=14
BACKUP_MAX_SIZE_MB=250

The backup implementation requires the supplied Docker image because it uses PostgreSQL 18 command-line tools and a temporary PostgreSQL 18 restore test. A daily 02:00 IST scheduler can run only while the Render service process is awake. If the service sleeps/spins down, a scheduled run can be missed; use an always-on instance or an external scheduler for dependable timing.

DEPLOY STEPS
------------
1. Extract this ZIP into a new folder.
2. Open PowerShell in that folder.
3. Check the project root contains Dockerfile, pom.xml and src.
4. Commit and push the complete project to the intended GitHub repository/branch.
5. In Render, confirm the existing service is connected to that repository and branch and is set to Docker.
6. Add the required environment variables above. Add optional Gmail/Drive variables only after you configure those Google services.
7. Start a deploy from the latest commit and open the Render build/runtime logs.
8. Wait for Spring Boot to report that the application has started and the service is live.
9. Test on both laptop and mobile: public homepage, customer signup/sign-in, admin sign-in, account details, support form, and logout.
10. In the demo only, create two test customers and verify a transfer between them. Check both balances and histories, then refresh/re-login to confirm persistence.
11. Configure Gmail and use the Owner Panel test-email action. Configure Drive and run a manual backup; confirm a SUCCESS result and inspect the target Drive folder.
12. Confirm transaction timestamps display in Asia/Kolkata on both devices.

LOCAL WINDOWS TEST
------------------
Install JDK 17+ and Maven 3.9+. Set DB_URL, DB_USERNAME, DB_PASSWORD, ADMIN_USERNAME and ADMIN_PASSWORD in the same PowerShell session, then run:
  mvn spring-boot:run
Open http://localhost:8080. If port 8080 is already occupied, stop the old app process or use another PORT.

IMPORTANT VERIFICATION LIMIT
----------------------------
The ZIP archive structure is checked, but this packaging environment does not have Maven or Docker installed. Therefore a Maven package build, Render image build, Neon runtime connection, Gmail delivery, Google Drive upload/restore, and real-device browser tests have NOT been executed here. Render's Docker build will compile the Java project; inspect its logs and complete the checklist above before treating the deployment as working.
