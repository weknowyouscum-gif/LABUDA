# LABUDA protected build model

LABUDA release builds use defense in depth. No single repository file, workflow step, or local credential should be sufficient to replace the official release APK.

## Protection layers

1. **Source control** — `main` should be protected; production changes should arrive through reviewed pull requests and required CI checks.
2. **CI integrity** — release builds run only in GitHub Actions with least-privilege workflow permissions. Secrets are supplied at runtime and are never committed.
3. **Release signing** — the Android signing keystore and passwords must live only in GitHub Actions secrets/environment protection. They must never be stored in Git.
4. **Dependency/build verification** — dependency and secret scanning must pass before a release artifact is trusted. The Xray engine version is pinned.
5. **Artifact verification** — the final APK should be checked after assembly and only then exposed as a release artifact.

## Important

GitHub repository protection settings and Actions secrets are account-level controls. They cannot be safely represented as source files. Configure them in GitHub repository settings with the appropriate administrator permissions.
