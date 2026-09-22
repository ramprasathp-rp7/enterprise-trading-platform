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
