#!/usr/bin/env python3
"""
setup_secrets.py — يسأل عن قيم الـ secrets ويضعها في:
  1. Cloudflare Worker (via wrangler secret put)
  2. GitHub Actions (via gh secret set)
تشغيل: python setup_secrets.py
"""
import subprocess, sys, getpass, os

CYAN  = "\033[96m"
GREEN = "\033[92m"
YELLOW= "\033[93m"
RED   = "\033[91m"
RESET = "\033[0m"
BOLD  = "\033[1m"

def run(cmd, input_text=None):
    result = subprocess.run(
        cmd, shell=True, capture_output=True, text=True,
        input=input_text
    )
    return result.returncode, result.stdout.strip(), result.stderr.strip()

def section(title):
    print(f"\n{BOLD}{CYAN}{'─'*55}{RESET}")
    print(f"{BOLD}{CYAN}  {title}{RESET}")
    print(f"{BOLD}{CYAN}{'─'*55}{RESET}\n")

def ask(prompt, secret=True, default=None):
    hint = f" [{YELLOW}Enter to skip{RESET}]" if default is None else f" [{YELLOW}default: {default[:4]}...{RESET}]"
    print(f"  {prompt}{hint}")
    if secret:
        val = getpass.getpass("  → ")
    else:
        val = input("  → ")
    val = val.strip()
    if not val and default:
        return default
    return val or None

def wrangler_secret(name, value):
    if not value:
        print(f"  {YELLOW}⚠  Skipped {name} (no value){RESET}")
        return
    code, out, err = run(f'npx wrangler secret put {name}', input_text=value + "\n")
    if code == 0:
        print(f"  {GREEN}✅ Cloudflare secret set: {name}{RESET}")
    else:
        print(f"  {RED}❌ Failed {name}: {err or out}{RESET}")

def gh_secret(name, value, repo):
    if not value:
        print(f"  {YELLOW}⚠  Skipped {name} (no value){RESET}")
        return
    code, out, err = run(f'gh secret set {name} --repo {repo}', input_text=value)
    if code == 0:
        print(f"  {GREEN}✅ GitHub secret set: {name}{RESET}")
    else:
        print(f"  {RED}❌ Failed {name}: {err or out}{RESET}")

# ──────────────────────────────────────────────────────────
print(f"\n{BOLD}🔐 Z VPN — Secrets Setup Script{RESET}")

# Detect GitHub repo from git remote
code, repo_url, _ = run("gh repo view --json nameWithOwner -q .nameWithOwner")
gh_repo = repo_url if code == 0 and repo_url else "itsbaask/vpn-core-build"
print(f"  GitHub repo: {CYAN}{gh_repo}{RESET}")

# ──────────────────────────────────────────────────────────
section("1/4 — Cloudflare Worker Secrets")
print(f"  These go into the Worker at https://vpn-backend.xocqoc.workers.dev\n")

api_secret = ask(
    "API_SECRET  (used by Python sync to authenticate POST /upload)",
    secret=True
)
backend_secret = ask(
    "BACKEND_SECRET_KEY  (same as API_SECRET — used as fallback key)",
    secret=True
)
unity_secret = ask(
    "UNITY_S2S_SECRET  (Unity Ads S2S reward HMAC key)",
    secret=True
)

os.chdir(os.path.join(os.path.dirname(__file__), "backend"))
wrangler_secret("API_SECRET",        api_secret)
wrangler_secret("BACKEND_SECRET_KEY", backend_secret or api_secret)
wrangler_secret("UNITY_S2S_SECRET",  unity_secret)

# ──────────────────────────────────────────────────────────
section("2/4 — GitHub Actions Secrets (for sync workflow)")
print(f"  These go into repo: {CYAN}{gh_repo}{RESET}\n")

r2_key_id      = ask("R2_ACCESS_KEY_ID")
r2_secret_key  = ask("R2_SECRET_ACCESS_KEY")
r2_bucket      = ask("R2_BUCKET_NAME (default: publicvpnlist-profiles)",
                      secret=False, default="publicvpnlist-profiles")
cf_account_id  = ask("CLOUDFLARE_ACCOUNT_ID", secret=False,
                      default="42326c36b367947b8ff21f4f1bad14af")
cf_api_token   = ask("CLOUDFLARE_API_TOKEN")
cf_kv_ns_id    = ask("CLOUDFLARE_KV_NAMESPACE_ID", secret=False,
                      default="73219165768a41b6bb2f920ecb41ec06")
pvl_access_key = ask("PUBLICVPNLIST_ACCESS_KEY  ← was missing from workflow!")

os.chdir(os.path.join(os.path.dirname(__file__)))
gh_secret("R2_ACCESS_KEY_ID",           r2_key_id,      gh_repo)
gh_secret("R2_SECRET_ACCESS_KEY",       r2_secret_key,  gh_repo)
gh_secret("R2_BUCKET_NAME",             r2_bucket,      gh_repo)
gh_secret("CLOUDFLARE_ACCOUNT_ID",      cf_account_id,  gh_repo)
gh_secret("CLOUDFLARE_API_TOKEN",       cf_api_token,   gh_repo)
gh_secret("CLOUDFLARE_KV_NAMESPACE_ID", cf_kv_ns_id,    gh_repo)
gh_secret("BACKEND_SECRET_KEY",         backend_secret or api_secret, gh_repo)
gh_secret("PUBLICVPNLIST_ACCESS_KEY",   pvl_access_key, gh_repo)

# ──────────────────────────────────────────────────────────
section("3/4 — Verifying Cloudflare Secrets")
os.chdir(os.path.join(os.path.dirname(__file__), "backend"))
code, out, err = run("npx wrangler secret list")
if code == 0:
    import json
    try:
        names = [s["name"] for s in json.loads(out)]
        print(f"  Worker secrets: {GREEN}{', '.join(names)}{RESET}")
    except Exception:
        print(f"  {out}")

# ──────────────────────────────────────────────────────────
section("4/4 — Verifying GitHub Secrets")
os.chdir(os.path.join(os.path.dirname(__file__)))
code, out, err = run(f"gh secret list --repo {gh_repo}")
if code == 0:
    for line in out.splitlines():
        print(f"  {GREEN}✅{RESET} {line}")

print(f"\n{BOLD}{GREEN}🎉 Done! All secrets configured.{RESET}\n")
print(f"  Next step — trigger a manual sync:")
print(f"  {CYAN}gh workflow run sync.yml --repo {gh_repo} -f sync_mode=dry_run{RESET}\n")
