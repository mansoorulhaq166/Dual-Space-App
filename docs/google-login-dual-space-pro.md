# Dual Space Pro Google login vs Dual Space (findings)

Read-only reference: decompiled Dual Space Pro 3.1.1 at
`/Users/apple/AndroidStudioProjects/Projects/dual space/MA App/Dual+Space+Pro+-Multi+Accounts_3.1.1`
(DEX, not Gradle source). Compared briefly with Multiple Accounts 5.5 (microG) and Parallel Space 4.0.

## What Dual Space Pro does

Pro does **not** ship or install microG (`org.microg` is absent from the APK). Google login is
**user-driven**: the guest’s own Continue with Google / Add account starts host Play services UI.
It does not auto-open a login screen on Reddit/Instagram cold start.

| Topic | Pro behavior | Classes / strings relied on |
| --- | --- | --- |
| Trigger | Guest Sign-In SDK or Add account. Host GMS `SignInHubActivity` / account picker. | `com.lody.virtual.client.stub.GoogleLoginAccountActivity` (`classes3.dex`) — `addAccount("com.google")`, then launches `KEY_INTENT`. Logs: `GoogleLoginAccountActivity.onActivityResult`. |
| Settings Add account | `android.settings.ADD_ACCOUNT_SETTINGS` rewritten in the ActivityManager hook. | `com.lody.virtual.client.hook.proxies.am.d` (and inner classes `d$a` …). |
| Credential Manager | Fail-closed; no auto login UI. | `CredentialManagerStub` / `CredentialManagerStub.java` in `classes3.dex`. |
| In-VA GMS | Install-host-GMS-into-VA is **off**. Tools ON → clones see **host** `com.google.android.gms` / `gsf`. | `GmsSupport.java`, `VirtualCore.getGoogleToolsState` / `setGoogleToolsState`. |
| Purge leftover VA GMS | App-side uninstall so cloned GMS cannot shadow host Play services. | `com.ludashi.dualspaceprox.pkgmgr.k` (and `k$a`…`k$i`) in `classes5.dex`. |
| What runs where | Clone: guest app + Sign-In SDK. Host: Play services UI and accounts. | No in-clone GmsCore. |

## What we copy vs what we must not copy

**Copy (behavior):** host GMS passthrough; no microG install/warm; no synthesized `LoginActivity`;
Credential Manager does not pop login; one `KEY_INTENT` / one `startActivity` (not both); uninstall
leftover clone GMS/GSF/FakeStore (same idea as `pkgmgr.k`).

**Do not copy:** Pro ads (`com.ludashi.dualspaceprox.ads`), obfuscated `am.d` internals, installing
host GMS *into* the VA (`GmsSupport` / Features flag stays false in Pro — we also do not clone
device Play services). Do not delete our microG assets or `GmsProvisioner`.

## Switch in this product

- `BuildConfig.USE_MICROG_GOOGLE_LOGIN` (default **false**) → `ClientConfiguration.isUseMicrogGoogleLogin()`.
- **false:** Dual Space Pro path above. microG APKs remain in `DualCore/src/main/assets/microg`.
- **true:** previous in-clone microG flow unchanged (`GmsProvisioner`, DummyService rewrite, YouTube
  Credential Manager → `LoginActivity`, AccountManager `KEY_INTENT` only — no extra `startActivity`).
