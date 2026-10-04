# JWT_SECRET Rotation Decision

## Decision Summary

**Status**: Keep published test secret in development environment  
**Date**: 2026-09-23  
**Affected Component**: Auth Service, Trade REST API integration  

---

## Choice

The team chose to **retain the published test secret** (`test-shared-secret-32-bytes-minimum-key`) in the development environment's root `.env` file.

---

## Rationale

### 1. Training Platform Context
This is a capstone training platform, not a production system. The published `.env.example` is intentionally distributed to all team members for consistency.

### 2. Developer Convenience
Sharing a known secret key facilitates:
- Quick local setup without key management infrastructure
- Consistent debugging across all developers
- Reproducible test environments
- Easier troubleshooting of integration issues

### 3. Security Acceptable for Training
The published secret has **bounded exposure**:
- Limited to a closed training cohort
- Not used in any production or public environments
- Intended to be rotated immediately if deployed beyond training

### 4. Production Deployment Pattern
When deploying beyond the training environment:
- A new, cryptographically secure secret will be generated
- The secret will be injected via secure configuration management (e.g., AWS Secrets Manager, HashiCorp Vault, environment injection in container orchestration)
- No secret will be committed to any repository
- Root `.env` or `.env.example` will never contain the production secret

---

## Risk Assessment

### Identified Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Secret leaked via `.env.example` | High (intentional) | Medium (training cohort only) | Repository access control; ephemeral training environment |
| Secret found in git history | Low | Medium (training cohort only) | Never rotate secret in production via git commits |
| Copied to unintended environments | Low | High (if copied to production) | Manual peer review before deployment; separate secret for production |

### Accepting the Risk
The team acknowledges this risk is **appropriate for training** because:
1. The platform is not publicly accessible
2. The secret applies only to a single closed environment
3. Production deployment follows secure key injection patterns
4. Training value of a shared secret outweighs the bounded risk

---

## Alternative Considered

### Rotate to a New Secret
**Not chosen**, because:
- Adds operational complexity without corresponding training value
- Requires key rotation, storage, and retrieval procedures
- The same training-only risk remains (the new secret must still be communicated to team members)
- Delays integration testing to Sprint 8 acceptance

---

## Implementation Details

### Current Configuration
- **JWT_SECRET**: `test-shared-secret-32-bytes-minimum-key` (32 bytes, minimum required)
- **JWT_ISSUER**: `auth-service` (constant across environments)
- **Location**: Root `.env` file, also documented in `.env.example`
- **Scope**: Development and training environments only

### Services Using This Secret
1. **Auth Service** (`sprint-08-auth-service`): Reads from `JWT_SECRET` env var to sign tokens
2. **Trade REST API** (`sprint-07-event-backbone`): Reads from `JWT_SECRET` env var to verify tokens

Both services use the same secret via environment variables, passed through root `.env` file.

---

## If Secret Were Compromised

An attacker with knowledge of this secret could:
1. **Mint unauthorized tokens** with any user ID, account ID, and roles
2. **Impersonate users** in the trading platform
3. **Access and modify trading data** for any account

### Response Steps (if detection occurs during training)
1. Immediately rotate the secret in root `.env`
2. Restart both Auth Service and Trade API with new secret
3. Invalidate all tokens issued with old secret (Trade API has no token revocation store in this sprint)
4. Audit all orders and trades placed with suspect tokens
5. Document the incident and recovery in security review

### Prevention During Training
- Repository access restricted to authorized team members
- `.env` file excluded from public repositories (`.gitignore` enforced)
- Regular security reminders about secret handling
- Code review checks for accidental secret commits

---

## Future Action: Production Deployment

When this Auth Service moves beyond training:

### Pre-Deployment
1. **Generate new secret**:
   ```bash
   openssl rand -base64 32  # Creates 32-byte secret
   ```

2. **Inject into production**:
   - Use environment-specific configuration management
   - Never commit to git or store in version control
   - Use separate secret for each deployment environment (dev, staging, prod)

3. **Verify issuer configuration**:
   - Auth Service must sign with production JWT_ISSUER
   - All consuming services must be configured to expect that issuer

### Post-Deployment
1. Destroy all copies of the training secret
2. Audit all tokens issued with the training secret
3. Document the secret rotation date
4. Implement automated secret rotation policies (e.g., quarterly)

---

## Sign-Off

| Role | Name | Signature | Date |
|---|---|---|---|
| Team Lead | Team 1 | Implementation Complete | 2026-09-23 |
| Sprint 8 Owner | Auth Service | Configuration Added | 2026-09-23 |

---

## References

- Sprint 8 README: `README_s8.md` — "The development JWT_SECRET in .env.example is published in this repository..."
- Integration Story: "Adopt the Real Auth Service with a Configuration Change Only"
- OWASP: [A02:2021 – Cryptographic Failures](https://owasp.org/Top10/A02_2021-Cryptographic_Failures/)
- Spring Security: [OAuth2 Resource Server with HS256](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

---

## Approval

**Final Decision**: The team accepts the risk of keeping the published test secret in the training `.env` file, with the understanding that:
- This is appropriate for a bounded training environment
- A separate, secure secret will be used for production
- Secret rotation follows post-deployment procedures
- No production or real customer data is exposed
