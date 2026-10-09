# SonarQube Quality Gate — order-executor-app

This document contains the exact steps to run SonarQube analysis for the `order-executor-app` project and check the quality gate.

IMPORTANT
- Do NOT store or commit Sonar tokens in source control.
- These commands assume Sonar is reachable at `http://10.8.69.250:9000`.

## 1. (On the Linux VM) Stop, remove, and run SonarQube bound to host IP
Run these on the Linux host where the Sonar container currently runs:

```bash
docker stop sonarqube && docker rm sonarqube
docker run -d --name sonarqube-yourteam -p 10.8.69.250:9000:9000 sonarqube:community
```

If a firewall is enabled on the Linux host, open port 9000 (example with UFW):

```bash
sudo ufw allow 9000/tcp
```

## 2. (From Windows) Open the Sonar UI and create a token
- Open: http://10.8.69.250:9000
- Login with `admin` / `admin` (change the password when prompted)
- In UI: My Account → Security (or Tokens) → Generate Token. Copy it immediately.

## 3. Ensure plugin is present in `pom.xml`
The project should include the Sonar Maven plugin (pinned version):

```xml
<plugin>
  <groupId>org.sonarsource.scanner.maven</groupId>
  <artifactId>sonar-maven-plugin</artifactId>
  <version>3.9.1.2184</version>
</plugin>
```

## 4. Run the analysis from `order-executor-app`

PowerShell (recommended on Windows):

```powershell
$env:SONAR_TOKEN="YOUR_GENERATED_TOKEN"
& mvn clean verify sonar:sonar "-Dsonar.host.url=http://10.8.69.250:9000" "-Dsonar.login=$env:SONAR_TOKEN" "-Dsonar.projectKey=order-executor-app" "-Dsonar.qualitygate.wait=true"
```

Bash (if running from Linux/Git Bash):

```bash
export SONAR_TOKEN=YOUR_GENERATED_TOKEN
mvn clean verify sonar:sonar -Dsonar.host.url=http://10.8.69.250:9000 -Dsonar.login=$SONAR_TOKEN -Dsonar.projectKey=order-executor-app -Dsonar.qualitygate.wait=true
```

When the build completes the Sonar scanner prints a link to the analysis and the Quality Gate result.

## 5. Troubleshooting
- "Not authorized": token invalid or missing permissions — regenerate token or use an admin token to test. Make sure project key matches.
- PowerShell parsing issues: use `& mvn ... "-Dprop=val"` or `mvn --% ...` to pass parameters literally.
- Plugin resolution errors: plugin version is pinned in `pom.xml`; ensure Maven can access central or your internal repo.
- Network unreachable: either re-run the container with host or use an SSH tunnel from Windows:

```powershell
ssh -L 9000:localhost:9000 youruser@10.8.69.250
# then point Maven at http://localhost:9000
```

## 6. Where to look in the Sonar UI
- After analysis, open the project in the Sonar UI and check: Project → Overview → Quality Gate.

--
Saved in repository root: `order-executor-app/SONAR_QUALITY_GATE.md`

---

# SonarQube Quality Gate — Frontend (trading-ui) and auth-service

These steps run SonarQube analysis for the Angular frontend and the NestJS auth-service and check their quality gates.

Both are Node/TypeScript projects (Angular + Vitest, NestJS + Jest), so the Sonar Maven plugin does not apply.
They use the npm scanner `@sonar/scan`, run through `npx`, so there is nothing to install or add to `package.json`.

IMPORTANT
- Do NOT store or commit Sonar tokens in source control (not in `.env`, not in `sonar-project.properties`).
- These commands assume Sonar is reachable at `http://10.8.69.250:9000`.
- Sonar does not run the tests. Generate the coverage report first, then run the scanner. Otherwise coverage shows 0%.

## 1. Configuration already in the repository

Each project has a `sonar-project.properties` file in its folder. The scanner picks it up automatically.

| Project | Config file | Sonar project key | Coverage report |
|---|---|---|---|
| Frontend | `Frontend/trading-ui/sonar-project.properties` | `trading-ui` | `coverage/sprint-09-trading-ui/lcov.info` |
| auth-service | `Services/auth-service/sonar-project.properties` | `auth-service` | `coverage/lcov.info` |

What they exclude:
- Frontend: the generated OpenAPI clients (`src/app/api/generated/**`) are excluded from analysis, coverage and duplication; `main.ts` and `environment*.ts` from coverage.
- auth-service: `dist/` is excluded from analysis; `main.ts` and `*.module.ts` from coverage.
- Both: `*.spec.ts` files are treated as tests, not source.

## 2. Create a token (once per person)

- Open http://10.8.69.250:9000 and log in.
- Go to My Account → Security → Generate Token.
- Choose **Type: Global Analysis Token**. One global token works for all three projects (`order-executor-app`, `trading-ui`, `auth-service`).
  A *Project Analysis Token* only works for the single project it was created for and gives a **401** on the others.
- Copy the token immediately; it is shown only once.

The first scan of `trading-ui` and `auth-service` creates the project on the server, so it needs an account with
"Create Projects" permission (e.g. admin). Otherwise create the projects first in the UI: Projects → Create Project →
Local project, using exactly the project keys above.

## 3. Set the token in your terminal

The variable only lives for the current terminal window. Set it again after opening a new terminal or restarting VS Code.

PowerShell:

```powershell
$env:SONAR_TOKEN="YOUR_GENERATED_TOKEN"
```

Bash / Git Bash:

```bash
export SONAR_TOKEN=YOUR_GENERATED_TOKEN
```

Check that the server accepts it (should print `{"valid":true}`):

```powershell
curl.exe -s -u "$($env:SONAR_TOKEN):" http://10.8.69.250:9000/api/authentication/validate
```

## 4. Run the analysis — Frontend

From `Frontend/trading-ui`:

```powershell
npm ci
npm run test:coverage
npx @sonar/scan "-Dsonar.host.url=http://10.8.69.250:9000" "-Dsonar.token=$env:SONAR_TOKEN" "-Dsonar.qualitygate.wait=true"
```

Bash:

```bash
npm ci
npm run test:coverage
npx @sonar/scan -Dsonar.host.url=http://10.8.69.250:9000 -Dsonar.token=$SONAR_TOKEN -Dsonar.qualitygate.wait=true
```

`npm run test:coverage` runs the Vitest unit tests once and writes `coverage/sprint-09-trading-ui/lcov.info`.

## 5. Run the analysis — auth-service

From `Services/auth-service`:

```powershell
npm ci
npm run test:cov
npx @sonar/scan "-Dsonar.host.url=http://10.8.69.250:9000" "-Dsonar.token=$env:SONAR_TOKEN" "-Dsonar.qualitygate.wait=true"
```

Bash:

```bash
npm ci
npm run test:cov
npx @sonar/scan -Dsonar.host.url=http://10.8.69.250:9000 -Dsonar.token=$SONAR_TOKEN -Dsonar.qualitygate.wait=true
```

`npm run test:cov` runs the Jest tests and writes `coverage/lcov.info`.

Note: `src/auth/auth.integration.spec.ts` calls the Trade API, so those 9 tests fail unless the stack is running.
The coverage report is still written and the scan still works, but start the stack first for a clean run.

## 6. Check the result

- The scanner ends with `QUALITY GATE STATUS: PASSED` (exit code 0) or `QUALITY GATE STATUS: FAILED` (non-zero exit code)
  and prints a link to the dashboard.
- In the Sonar UI: Projects → `trading-ui` / `auth-service` → Overview → Quality Gate.

## 7. If the quality gate fails

The default "Sonar way" gate checks only **new code** (code changed since the previous version), with these conditions:

| Condition | Threshold |
|---|---|
| Coverage on new code | at least 80% |
| Duplicated lines on new code | at most 3% |
| New issues | 0 |

See which condition failed (replace the project key as needed):

```powershell
curl.exe -s -u "$($env:SONAR_TOKEN):" "http://10.8.69.250:9000/api/qualitygates/project_status?projectKey=auth-service"
```

Look for entries with `"status":"ERROR"`. For example, `"metricKey":"new_violations","actualValue":"52"` means 52 new issues.

Save the list of new issues to a file to work through them (writes into the git-ignored `coverage` folder):

```powershell
curl.exe -s -u "$($env:SONAR_TOKEN):" "http://10.8.69.250:9000/api/issues/search?componentKeys=auth-service&inNewCodePeriod=true&resolved=false&ps=500" -o coverage/sonar-issues.json
```

Or in the UI: Issues tab → filter on **New Code**.

Then either:
- fix the issue in the code and re-run steps 4 / 5, or
- for a genuine false positive, mark it **Accept** / **False Positive** in the UI (the gate updates without a rescan).

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `AxiosError: Request failed with status code 401` | Token empty in this terminal, wrong type, or revoked | Check `$env:SONAR_TOKEN.Length` is not 0, run the validate call in step 3, create a Global Analysis Token |
| 401 / 403 even with a valid token | No permission to create the project | Run the first scan with an admin token, or create the project in the UI first |
| Coverage shows 0% | Tests not run, or `lcov.info` path does not match | Run the coverage command first and check the file exists at the path in section 1 |
| Generated API code shows up as issues | Exclusion missing | Add the folder to `sonar.exclusions` in `sonar-project.properties` |
| All tokens/projects gone | Sonar container was recreated without a volume | Create a new token and rescan |
