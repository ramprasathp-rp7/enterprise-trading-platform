# Notes on the Kubernetes CrashLoopBackOff fix

Written for: the team, as a record of what broke the first EKS deployment and how it was fixed.

## Symptom

After the first deploy, `auth-service`, `order-service` and `executor-service` were in `CrashLoopBackOff`
(25+ restarts). Only `frontend` and `kafka` were healthy.

## Root causes and fixes

| # | Service | Error in the logs | Cause | Fix |
|---|---------|-------------------|-------|-----|
| 1 | order, executor | `Could not resolve placeholder 'kafka.port'`; `password authentication failed for user "${database.user}"` | The Spring services import the shared config from `${user.dir}/../../Config/application.yaml`. In the container that path does not exist; the file is mounted at `/config/application.yml`. So `database.*`, `kafka.*`, `services.*` never loaded. | `config.env`: added `SPRING_CONFIG_IMPORT=optional:file:/config/application.yml` |
| 2 | auth | `frontend.url / frontend.port is not configured` | auth-service reads `PLATFORM_CONFIG_PATH`, which was `../../config/application.yaml` (wrong case, wrong extension, wrong place). | `config.env`: `PLATFORM_CONFIG_PATH=/config/application.yml` |
| 3 | auth | `ENOENT open /Config/rds-ca-bundle.pem` | `PGSSLMODE=no-verify #require`: the inline `#` comment became part of the value (kustomize env files do not support inline comments), so the mode was not `no-verify` and the code tried to verify against a CA bundle that was not in the image. | Removed inline comments (`DB_SSL`, `PGSSLMODE`, `JDBC_PARAMS`). Set `PGSSLMODE=verify-full` and `PGSSLROOTCERT=/config/rds-ca-bundle.pem`. Added `rds-ca-bundle.pem=../../Config/rds-ca-bundle.pem` to the `etp-shared-config` ConfigMap in `k8s/base/kustomization.yaml`. |
| 4 | all | (silent) | `k8s/overlays/four-lb/urls.env` had a trailing space after `ORDER_SERVICE_URL`, which ended up in the ConfigMap. | Trimmed the trailing whitespace. |

## How it is applied now

The CA bundle is copied into `k8s/base/generated/` (inside the kustomize root), so a plain apply works:

```bash
kubectl apply -k k8s/overlays/four-lb
```


## Verification

`kubectl get pods` shows auth, order, executor and frontend `1/1 Running`, `kafka-0` Running, and the
`kafka-topics` job `Completed`.

## Lessons

- In `config.env` and `urls.env`: no inline `#` comments, no trailing spaces.
- The Spring services and auth-service find the shared config by *path*; in a container, point them at
  the mounted file explicitly.
- Read the real exception (`kubectl logs <pod> --previous`) before changing anything; each service
  failed for a different reason.

## Known follow-ups

- `k8s/base/generated/application.yml` contains Spring-style `${...}` placeholders; Node (auth) does not
  resolve them, so auth's CORS origin is a literal string. Use literal frontend values or
  `CORS_ALLOWED_ORIGINS` if browser CORS errors appear.
- `MARKET_DATA_CACHE_FILE=../../data/market-data-cache.json` points outside `/app`; consider `/tmp/...`.
- Stale ConfigMaps from earlier applies (`etp-config-*`, `etp-shared-config-*`, `etp-urls-*`) can be deleted.

## How teammates run / redeploy after pulling this

Prerequisites: `aws` CLI logged in (account 906048429811, region ap-south-1), `kubectl`, and access to the
EKS cluster `etp-cluster-1` (created from `k8s/cluster.example.yaml` with `eksctl`).

```bash
# 1. Point kubectl at the cluster
aws eks update-kubeconfig --region ap-south-1 --name etp-cluster-1
kubectl get nodes                      # 2 nodes Ready

# 2. Create the git-ignored secrets file (NOT in the repo)
cp k8s/base/secrets.env.example k8s/base/secrets.env      # then fill in the real values
```

`k8s/base/generated/` is git-ignored. Also copy the CA bundle: `cp Config/rds-ca-bundle.pem k8s/base/generated/`.

`k8s/base/generated/application.yml` is git-ignored, so a fresh clone does not have it. It must be the
placeholder version (`host: ${DB_HOST}`, `port: ${DB_PORT:5432}`, `kafka.port: 29092`, ...), *not* a plain copy
of `Config/application.yaml` (which has `localhost` values and no `database.password`). Get it from the person
who deployed, or remove `generated/` from `k8s/base/.gitignore` and commit it so it is shared.
`k8s/base/secrets.env` must stay out of git. Get its values from a teammate; never commit it.

```bash
# 3. (Only if code changed) build and push images to ECR, tags are :latest
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 906048429811.dkr.ecr.ap-south-1.amazonaws.com
# build/push etp-auth-service, etp-order-service, etp-executor-service, etp-frontend

# 4. Deploy (needs k8s/base/generated/application.yml and rds-ca-bundle.pem, see above)
kubectl apply -k k8s/overlays/four-lb

# 5. If only images changed (same tag :latest), force a rollout
kubectl rollout restart deploy/auth-service deploy/order-service deploy/executor-service deploy/frontend

# 6. Verify
kubectl get pods                       # all 1/1 Running
kubectl get svc                        # load-balancer addresses
kubectl logs deploy/auth-service --tail=50
```

Changing `config.env` or `urls.env` and re-running step 4 is enough: the ConfigMap name carries a hash, so
the pods restart on their own.

If the load-balancer addresses change (new cluster), copy them from `kubectl get svc` into
`k8s/overlays/four-lb/urls.env` (no trailing spaces), then redeploy.
