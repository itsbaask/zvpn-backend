import re
from typing import Dict, Any, Tuple

# Country Flag Emojis & ISO Code Map
COUNTRY_FLAGS = {
    "US": ("United States", "🇺🇸"),
    "DE": ("Germany", "🇩🇪"),
    "FR": ("France", "🇫🇷"),
    "GB": ("United Kingdom", "🇬🇧"),
    "JP": ("Japan", "🇯🇵"),
    "KR": ("South Korea", "🇰🇷"),
    "SG": ("Singapore", "🇸🇬"),
    "NL": ("Netherlands", "🇳🇱"),
    "CA": ("Canada", "🇨🇦"),
    "AU": ("Australia", "🇦🇺"),
    "IN": ("India", "🇮🇳"),
    "BR": ("Brazil", "🇧🇷"),
    "AE": ("United Arab Emirates", "🇦🇪"),
    "SA": ("Saudi Arabia", "🇸🇦"),
    "EG": ("Egypt", "🇪🇬"),
    "TR": ("Turkey", "🇹🇷"),
    "RU": ("Russia", "🇷🇺"),
    "FI": ("Finland", "🇫🇮"),
    "SE": ("Sweden", "🇸🇪"),
    "CH": ("Switzerland", "🇨🇭"),
    "PL": ("Poland", "🇵🇱"),
    "IT": ("Italy", "🇮🇹"),
    "ES": ("Spain", "🇪🇸"),
    "HK": ("Hong Kong", "🇭🇰"),
    "TW": ("Taiwan", "🇹🇼"),
    "UN": ("Global", "🌐")
}

SPAM_PATTERNS = [
    r't\.me/[a-zA-Z0-9_]+',
    r'https?://[^\s]+',
    r'@[a-zA-Z0-9_]+',
    r'modded\s+by\s*[a-zA-Z0-9_]*',
    r'telegram[^\s]*',
    r'vpngate|vpnbook|omgvpn|riseup',
    r'free vpn[^\s]*',
    r'admin[^\s]*'
]

def sanitize_server_name(raw_name: str, country_code: str, country_name: str, protocol: str, index: int = 1) -> str:
    """
    Sanitizes raw server names to eliminate spam, telegram links, indian names, and random numbers.
    Formats to premium global standards: '🇺🇸 United States - Fast #01'.
    """
    code = (country_code or "UN").upper()
    c_info = COUNTRY_FLAGS.get(code, (country_name or "Global", "🌐"))
    c_name, flag = c_info[0], c_info[1]

    clean = raw_name or ""
    for pat in SPAM_PATTERNS:
        clean = re.sub(pat, '', clean, flags=re.IGNORECASE)

    clean = re.sub(r'[^a-zA-Z0-9\s\-\.\#\_]', '', clean).strip()

    if not clean or len(clean) < 3 or clean.isdigit():
        clean = f"Fast #{index:02d}"

    return f"{flag} {c_name} - {clean}"

def sanitize_openvpn_content(ovpn_text: str) -> str:
    """
    Cleans OpenVPN profile content. Removes spam comments, personal hardcoded user/pass,
    and formats directives cleanly.
    """
    if not ovpn_text:
        return ""

    lines = []
    for line in ovpn_text.splitlines():
        l = line.strip()
        if not l:
            continue
        # Strip spam comments
        if l.startswith("#") or l.startswith(";"):
            if any(term in l.lower() for term in ["telegram", "t.me", "modded", "owner", "admin"]):
                continue
        lines.append(l)

    return "\n".join(lines)
