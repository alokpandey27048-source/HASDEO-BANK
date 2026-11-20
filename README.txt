HASDEO BANK — EDUCATIONAL WEBSITE DEMO
======================================

WHAT IS INCLUDED
- Premium navy-blue and gold home page
- Admin demo login
- Dashboard with demo balance
- Deposit, withdraw and transfer demo actions
- Transaction history
- Java Spring Boot backend + HTML/CSS/JavaScript frontend
- No MySQL or SQL database required

IMPORTANT
This is a school/learning demonstration, NOT a real bank.
Do not enter real banking details or use it for real money.
Data is stored temporarily in server memory and resets when the app restarts.
The sample login is hard-coded for demonstration only.

DEMO LOGIN
Username: alok2701
Password: hps2701

REQUIREMENTS
- JDK 17 or newer
- Internet connection on first run (Maven downloads dependencies)
- VS Code or another Java IDE

RUN IN VS CODE
1. Extract the ZIP.
2. Open the extracted HASDEO-BANK folder in VS Code.
3. Open Terminal > New Terminal.
4. On Windows, run:
   .\mvnw.cmd spring-boot:run
5. Wait until the terminal says the application has started.
6. Open this address in your browser:
   http://localhost:8080

If the Maven wrapper file is missing, install Maven and run:
   mvn spring-boot:run

TROUBLESHOOTING
- If Java is not found, install a JDK and reopen VS Code.
- If dependencies fail to download, check internet and try the run command again.
- If port 8080 is busy, close the other app using it or change server.port in
  src/main/resources/application.properties.

PROJECT STRUCTURE
src/main/java/com/hasdeo/bank/       Java backend
src/main/resources/static/           Website HTML, CSS and JavaScript
pom.xml                              Maven dependencies
