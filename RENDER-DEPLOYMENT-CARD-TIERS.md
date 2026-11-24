# Deploy Card Tiers and QR Profile Update

1. Extract `HASDEO-BANK-CARD-TIERS-QR-UPDATE.zip` in Downloads.
2. Copy the contents of the `signup_login_work` folder into your existing `HASDEO-BANK-RENDER-DEPLOY-PACKAGE` repository folder, excluding `.git` and `target`.
3. Run `mvn clean package`. Only continue if it succeeds.
4. Commit and push to `main`; wait for Render to finish deployment.
5. Hard-refresh the website with Ctrl+Shift+R.

PowerShell:

```powershell
cd "$env:USERPROFILE\Downloads"
Expand-Archive ".\HASDEO-BANK-CARD-TIERS-QR-UPDATE.zip" ".\HASDEO-BANK-CARD-TIERS-QR-UPDATE" -Force
robocopy ".\HASDEO-BANK-CARD-TIERS-QR-UPDATE\signup_login_work" ".\HASDEO-BANK-RENDER-DEPLOY-PACKAGE" /E /XD .git target
if ($LASTEXITCODE -ge 8) { throw "Copy failed. Stop here." }
cd ".\HASDEO-BANK-RENDER-DEPLOY-PACKAGE"
mvn clean package
if ($LASTEXITCODE -ne 0) { throw "Build failed. Do not push." }
git add .
git commit -m "Add five demo card tiers and secure QR profiles"
git push origin main
```

This update uses the existing database. Do not reset or delete Neon data. QR profiles intentionally reveal only limited public demo details; they do not reveal private account information.
