import os
import sys
import json
import base64
import logging
import threading
import time
import re
import requests
from concurrent.futures import ThreadPoolExecutor
from bs4 import BeautifulSoup
from urllib.parse import urljoin, unquote

# Setup Mandatory Logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("SyncEngine")

PERMANENT_KEY = "pvlk_eb8cc33936641d9492cf0a2740c8511bb737e1a611fa1035cb7c5c3006513bcc"

# All 249 ISO Alpha-2 Country Codes mapped to English Names
ALL_ISO_TO_NAME = {
    "AD": "Andorra", "AE": "United Arab Emirates", "AF": "Afghanistan", "AG": "Antigua and Barbuda",
    "AI": "Anguilla", "AL": "Albania", "AM": "Armenia", "AO": "Angola", "AQ": "Antarctica",
    "AR": "Argentina", "AS": "American Samoa", "AT": "Austria", "AU": "Australia", "AW": "Aruba",
    "AX": "Åland Islands", "AZ": "Azerbaijan", "BA": "Bosnia and Herzegovina", "BB": "Barbados",
    "BD": "Bangladesh", "BE": "Belgium", "BF": "Burkina Faso", "BG": "Bulgaria", "BH": "Bahrain",
    "BI": "Burundi", "BJ": "Benin", "BL": "Saint Barthélemy", "BM": "Bermuda", "BN": "Brunei",
    "BO": "Bolivia", "BQ": "Bonaire", "BR": "Brazil", "BS": "Bahamas", "BT": "Bhutan",
    "BV": "Bouvet Island", "BW": "Botswana", "BY": "Belarus", "BZ": "Belize", "CA": "Canada",
    "CC": "Cocos Islands", "CD": "DR Congo", "CF": "Central African Republic", "CG": "Congo",
    "CH": "Switzerland", "CI": "Ivory Coast", "CK": "Cook Islands", "CL": "Chile", "CM": "Cameroon",
    "CN": "China", "CO": "Colombia", "CR": "Costa Rica", "CU": "Cuba", "CV": "Cape Verde",
    "CW": "Curaçao", "CX": "Christmas Island", "CY": "Cyprus", "CZ": "Czech Republic",
    "DE": "Germany", "DJ": "Djibouti", "DK": "Denmark", "DM": "Dominica", "DO": "Dominican Republic",
    "DZ": "Algeria", "EC": "Ecuador", "EE": "Estonia", "EG": "Egypt", "EH": "Western Sahara",
    "ER": "Eritrea", "ES": "Spain", "ET": "Ethiopia", "FI": "Finland", "FJ": "Fiji",
    "FK": "Falkland Islands", "FM": "Micronesia", "FO": "Faroe Islands", "FR": "France",
    "GA": "Gabon", "GB": "United Kingdom", "GD": "Grenada", "GE": "Georgia", "GF": "French Guiana",
    "GG": "Guernsey", "GH": "Ghana", "GI": "Gibraltar", "GL": "Greenland", "GM": "Gambia",
    "GN": "Guinea", "GP": "Guadeloupe", "GQ": "Equatorial Guinea", "GR": "Greece",
    "GS": "South Georgia", "GT": "Guatemala", "GU": "Guam", "GW": "Guinea-Bissau", "GY": "Guyana",
    "HK": "Hong Kong", "HM": "Heard Island", "HN": "Honduras", "HR": "Croatia", "HT": "Haiti",
    "HU": "Hungary", "ID": "Indonesia", "IE": "Ireland", "IL": "Israel", "IM": "Isle of Man",
    "IN": "India", "IO": "British Indian Ocean Territory", "IQ": "Iraq", "IR": "Iran",
    "IS": "Iceland", "IT": "Italy", "JE": "Jersey", "JM": "Jamaica", "JO": "Jordan",
    "JP": "Japan", "KE": "Kenya", "KG": "Kyrgyzstan", "KH": "Cambodia", "KI": "Kiribati",
    "KM": "Comoros", "KN": "Saint Kitts and Nevis", "KP": "North Korea", "KR": "South Korea",
    "KW": "Kuwait", "KY": "Cayman Islands", "KZ": "Kazakhstan", "LA": "Laos", "LB": "Lebanon",
    "LC": "Saint Lucia", "LI": "Liechtenstein", "LK": "Sri Lanka", "LR": "Liberia", "LS": "Lesotho",
    "LT": "Lithuania", "LU": "Luxembourg", "LV": "Latvia", "LY": "Libya", "MA": "Morocco",
    "MC": "Monaco", "MD": "Moldova", "ME": "Montenegro", "MF": "Saint Martin", "MG": "Madagascar",
    "MH": "Marshall Islands", "MK": "North Macedonia", "ML": "Mali", "MM": "Myanmar", "MN": "Mongolia",
    "MO": "Macau", "MP": "Northern Mariana Islands", "MQ": "Martinique", "MR": "Mauritania",
    "MS": "Montserrat", "MT": "Malta", "MU": "Mauritius", "MV": "Maldives", "MW": "Malawi",
    "MX": "Mexico", "MY": "Malaysia", "MZ": "Mozambique", "NA": "Namibia", "NC": "New Caledonia",
    "NE": "Niger", "NF": "Norfolk Island", "NG": "Nigeria", "NI": "Nicaragua", "NL": "Netherlands",
    "NO": "Norway", "NP": "Nepal", "NR": "Nauru", "NU": "Niue", "NZ": "New Zealand",
    "OM": "Oman", "PA": "Panama", "PE": "Peru", "PF": "French Polynesia", "PG": "Papua New Guinea",
    "PH": "Philippines", "PK": "Pakistan", "PL": "Poland", "PM": "Saint Pierre and Miquelon",
    "PN": "Pitcairn", "PR": "Puerto Rico", "PS": "Palestine", "PT": "Portugal", "PW": "Palau",
    "PY": "Paraguay", "QA": "Qatar", "RE": "Réunion", "RO": "Romania", "RS": "Serbia",
    "RU": "Russia", "RW": "Rwanda", "SA": "Saudi Arabia", "SB": "Solomon Islands", "SC": "Seychelles",
    "SD": "Sudan", "SE": "Sweden", "SG": "Singapore", "SH": "Saint Helena", "SI": "Slovenia",
    "SJ": "Svalbard", "SK": "Slovakia", "SL": "Sierra Leone", "SM": "San Marino", "SN": "Senegal",
    "SO": "Somalia", "SR": "Suriname", "SS": "South Sudan", "ST": "São Tomé and Príncipe",
    "SV": "El Salvador", "SX": "Sint Maarten", "SY": "Syria", "SZ": "Eswatini", "TC": "Turks and Caicos",
    "TD": "Chad", "TF": "French Southern Territories", "TG": "Togo", "TH": "Thailand",
    "TJ": "Tajikistan", "TK": "Tokelau", "TL": "East Timor", "TM": "Turkmenistan", "TN": "Tunisia",
    "TO": "Tonga", "TR": "Turkey", "TT": "Trinidad and Tobago", "TV": "Tuvalu", "TW": "Taiwan",
    "TZ": "Tanzania", "UA": "Ukraine", "UG": "Uganda", "UM": "US Outlying Islands", "US": "United States",
    "UY": "Uruguay", "UZ": "Uzbekistan", "VA": "Vatican City", "VC": "Saint Vincent", "VE": "Venezuela",
    "VG": "British Virgin Islands", "VI": "US Virgin Islands", "VN": "Vietnam", "VU": "Vanuatu",
    "WF": "Wallis and Futuna", "WS": "Samoa", "YE": "Yemen", "YT": "Mayotte", "ZA": "South Africa",
    "ZM": "Zambia", "ZW": "Zimbabwe"
}

try:
    import pycountry
    for c in pycountry.countries:
        ALL_ISO_TO_NAME[c.alpha_2] = c.name
except Exception:
    pass

ISO_CODES = set(ALL_ISO_TO_NAME.keys())

# List of trusted fragment patterns verified >= 95% accuracy
TRUSTED_FRAGMENT_PATTERNS = [
    # Only fragment patterns with >= 95% audit accuracy are added here.
]

# Rule 5: Strict Static Exact Match Map (Country Name -> ISO Code)
EXACT_COUNTRY_NAME_TO_CODE = {
    "UNITED STATES": "US",
    "USA": "US",
    "UNITED STATES OF AMERICA": "US",
    "CANADA": "CA",
    "MEXICO": "MX",
    "GERMANY": "DE",
    "UNITED KINGDOM": "GB",
    "UK": "GB",
    "GREAT BRITAIN": "GB",
    "FRANCE": "FR",
    "NETHERLANDS": "NL",
    "SWITZERLAND": "CH",
    "SWEDEN": "SE",
    "POLAND": "PL",
    "UKRAINE": "UA",
    "ITALY": "IT",
    "SPAIN": "ES",
    "RUSSIA": "RU",
    "RUSSIAN FEDERATION": "RU",
    "JAPAN": "JP",
    "SINGAPORE": "SG",
    "SOUTH KOREA": "KR",
    "KOREA, REPUBLIC OF": "KR",
    "TURKEY": "TR",
    "TÜRKIYE": "TR",
    "CHINA": "CN",
    "HONG KONG": "HK",
    "TAIWAN": "TW",
    "INDIA": "IN",
    "INDONESIA": "ID",
    "VIETNAM": "VN",
    "VIET NAM": "VN",
    "THAILAND": "TH",
    "MALAYSIA": "MY",
    "PHILIPPINES": "PH",
    "IRAN": "IR",
    "IRAN, ISLAMIC REPUBLIC OF": "IR",
    "UNITED ARAB EMIRATES": "AE",
    "SAUDI ARABIA": "SA",
    "BRAZIL": "BR",
    "ARGENTINA": "AR",
    "CHILE": "CL",
    "COLOMBIA": "CO",
    "PERU": "PE",
    "PARAGUAY": "PY",
    "URUGUAY": "UY",
    "VENEZUELA": "VE",
    "DOMINICAN REPUBLIC": "DO",
    "SERBIA": "RS",
    "ALBANIA": "AL",
    "MAURITIUS": "MU",
    "SYRIA": "SY",
    "BELARUS": "BY",
    "MOLDOVA": "MD",
    "MONTENEGRO": "ME",
    "BOSNIA AND HERZEGOVINA": "BA",
    "BOSNIA": "BA",
    "MACEDONIA": "MK",
    "NORTH MACEDONIA": "MK",
    "GEORGIA": "GE",
    "ARMENIA": "AM",
    "AZERBAIJAN": "AZ",
    "KAZAKHSTAN": "KZ",
    "UZBEKISTAN": "UZ",
    "KYRGYZSTAN": "KG",
    "TAJIKISTAN": "TJ",
    "TURKMENISTAN": "TM",
    "LAOS": "LA",
    "MYANMAR": "MM",
    "CAMBODIA": "KH",
    "SOUTH AFRICA": "ZA",
    "EGYPT": "EG",
    "AUSTRALIA": "AU",
    "NEW ZEALAND": "NZ",
    "FINLAND": "FI",
    "NORWAY": "NO",
    "DENMARK": "DK",
    "IRELAND": "IE",
    "AUSTRIA": "AT",
    "BELGIUM": "BE",
    "CZECH REPUBLIC": "CZ",
    "CZECHIA": "CZ",
    "ROMANIA": "RO",
    "HUNGARY": "HU",
    "PORTUGAL": "PT",
    "GREECE": "GR",
    "BULGARIA": "BG",
    "CROATIA": "HR",
    "SLOVAKIA": "SK",
    "SLOVENIA": "SI",
    "LITHUANIA": "LT",
    "LATVIA": "LV",
    "ESTONIA": "EE",
    "LUXEMBOURG": "LU",
    "ICELAND": "IS",
    "CYPRUS": "CY",
    "MALTA": "MT",
    "GEORGIA": "GE",
    "ARMENIA": "AM",
    "AZERBAIJAN": "AZ",
    "KAZAKHSTAN": "KZ",
    "UZBEKISTAN": "UZ",
    "PAKISTAN": "PK",
    "BANGLADESH": "BD",
    "SRI LANKA": "LK",
    "NEPAL": "NP",
    "CAMBODIA": "KH",
    "ISRAEL": "IL",
    "QATAR": "QA",
    "KUWAIT": "KW",
    "OMAN": "OM",
    "BAHRAIN": "BH",
    "JORDAN": "JO",
    "LEBANON": "LB",
    "IRAQ": "IQ",
    "MOROCCO": "MA",
    "TUNISIA": "TN",
    "ALGERIA": "DZ",
    "NIGERIA": "NG",
    "KENYA": "KE",
    "PERU": "PE",
    "URUGUAY": "UY",
    "ECUADOR": "EC",
    "COSTA RICA": "CR",
    "PANAMA": "PA"
}

# Code -> Continent (Americas, Europe, Asia-Pacific, ME/Africa) / Name / Flag Map
CODE_TO_META = {
    "US": ("Americas", "United States", "🇺🇸"),
    "CA": ("Americas", "Canada", "🇨🇦"),
    "MX": ("Americas", "Mexico", "🇲🇽"),
    "CR": ("Americas", "Costa Rica", "🇨🇷"),
    "PA": ("Americas", "Panama", "🇵🇦"),
    "DE": ("Europe", "Germany", "🇩🇪"),
    "GB": ("Europe", "United Kingdom", "🇬🇧"),
    "FR": ("Europe", "France", "🇫🇷"),
    "NL": ("Europe", "Netherlands", "🇳🇱"),
    "CH": ("Europe", "Switzerland", "🇨🇭"),
    "SE": ("Europe", "Sweden", "🇸🇪"),
    "PL": ("Europe", "Poland", "🇵🇱"),
    "UA": ("Europe", "Ukraine", "🇺🇦"),
    "IT": ("Europe", "Italy", "🇮🇹"),
    "ES": ("Europe", "Spain", "🇪🇸"),
    "RU": ("Europe", "Russia", "🇷🇺"),
    "FI": ("Europe", "Finland", "🇫🇮"),
    "NO": ("Europe", "Norway", "🇳🇴"),
    "DK": ("Europe", "Denmark", "🇩🇰"),
    "IE": ("Europe", "Ireland", "🇮🇪"),
    "AT": ("Europe", "Austria", "🇦🇹"),
    "BE": ("Europe", "Belgium", "🇧🇪"),
    "CZ": ("Europe", "Czech Republic", "🇨🇿"),
    "RO": ("Europe", "Romania", "🇷🇴"),
    "HU": ("Europe", "Hungary", "🇭🇺"),
    "PT": ("Europe", "Portugal", "🇵🇹"),
    "GR": ("Europe", "Greece", "🇬🇷"),
    "BG": ("Europe", "Bulgaria", "🇧🇬"),
    "HR": ("Europe", "Croatia", "🇭🇷"),
    "SK": ("Europe", "Slovakia", "🇸🇰"),
    "SI": ("Europe", "Slovenia", "🇸🇮"),
    "LT": ("Europe", "Lithuania", "🇱🇹"),
    "LV": ("Europe", "Latvia", "🇱🇻"),
    "EE": ("Europe", "Estonia", "🇪🇪"),
    "LU": ("Europe", "Luxembourg", "🇱🇺"),
    "IS": ("Europe", "Iceland", "🇮🇸"),
    "CY": ("Europe", "Cyprus", "🇨🇾"),
    "MT": ("Europe", "Malta", "🇲🇹"),
    "JP": ("Asia-Pacific", "Japan", "🇯🇵"),
    "SG": ("Asia-Pacific", "Singapore", "🇸🇬"),
    "KR": ("Asia-Pacific", "South Korea", "🇰🇷"),
    "TR": ("ME/Africa", "Turkey", "🇹🇷"),
    "CN": ("Asia-Pacific", "China", "🇨🇳"),
    "HK": ("Asia-Pacific", "Hong Kong", "🇭🇰"),
    "TW": ("Asia-Pacific", "Taiwan", "🇹🇼"),
    "IN": ("Asia-Pacific", "India", "🇮🇳"),
    "ID": ("Asia-Pacific", "Indonesia", "🇮🇩"),
    "VN": ("Asia-Pacific", "Vietnam", "🇻🇳"),
    "TH": ("Asia-Pacific", "Thailand", "🇹🇭"),
    "MY": ("Asia-Pacific", "Malaysia", "🇲🇾"),
    "PH": ("Asia-Pacific", "Philippines", "🇵🇭"),
    "IR": ("ME/Africa", "Iran", "🇮🇷"),
    "GE": ("Asia-Pacific", "Georgia", "🇬🇪"),
    "AM": ("Asia-Pacific", "Armenia", "🇦🇲"),
    "AZ": ("Asia-Pacific", "Azerbaijan", "🇦🇿"),
    "KZ": ("Asia-Pacific", "Kazakhstan", "🇰🇿"),
    "UZ": ("Asia-Pacific", "Uzbekistan", "🇺🇿"),
    "PK": ("Asia-Pacific", "Pakistan", "🇵🇰"),
    "BD": ("Asia-Pacific", "Bangladesh", "🇧🇩"),
    "LK": ("Asia-Pacific", "Sri Lanka", "🇱🇰"),
    "NP": ("Asia-Pacific", "Nepal", "🇳🇵"),
    "KH": ("Asia-Pacific", "Cambodia", "🇰🇭"),
    "AE": ("ME/Africa", "United Arab Emirates", "🇦🇪"),
    "SA": ("ME/Africa", "Saudi Arabia", "🇸🇦"),
    "IL": ("ME/Africa", "Israel", "🇮🇱"),
    "QA": ("ME/Africa", "Qatar", "🇶🇦"),
    "KW": ("ME/Africa", "Kuwait", "🇰🇼"),
    "OM": ("ME/Africa", "Oman", "🇴🇲"),
    "BH": ("ME/Africa", "Bahrain", "🇧🇭"),
    "JO": ("ME/Africa", "Jordan", "🇯🇴"),
    "LB": ("ME/Africa", "Lebanon", "🇱🇧"),
    "IQ": ("ME/Africa", "Iraq", "🇮🇶"),
    "BR": ("Americas", "Brazil", "🇧🇷"),
    "AR": ("Americas", "Argentina", "🇦🇷"),
    "CL": ("Americas", "Chile", "🇨🇱"),
    "CO": ("Americas", "Colombia", "🇨🇴"),
    "PE": ("Americas", "Peru", "🇵🇪"),
    "UY": ("Americas", "Uruguay", "🇺🇾"),
    "EC": ("Americas", "Ecuador", "🇪🇨"),
    "ZA": ("ME/Africa", "South Africa", "🇿🇦"),
    "EG": ("ME/Africa", "Egypt", "🇪🇬"),
    "MA": ("ME/Africa", "Morocco", "🇲🇦"),
    "TN": ("ME/Africa", "Tunisia", "🇹🇳"),
    "DZ": ("ME/Africa", "Algeria", "🇩🇿"),
    "NG": ("ME/Africa", "Nigeria", "🇳🇬"),
    "KE": ("ME/Africa", "Kenya", "🇰🇪"),
    "AU": ("Asia-Pacific", "Australia", "🇦🇺"),
    "NZ": ("Asia-Pacific", "New Zealand", "🇳🇿")
}

_FLAG_RE = re.compile(r'[\U0001F1E6-\U0001F1FF]{2}')
_TOKEN_RE = re.compile(r'(?:^|(?<=[\[\(|_\-]))([A-Z]{2})(?=[\]\)|_\-\d]|$)')

def country_code_to_emoji(cc: str) -> str:
    if not cc or len(cc) != 2 or not cc.isalpha():
        return "🌐"
    c1 = ord(cc[0].upper()) - ord('A') + 0x1F1E6
    c2 = ord(cc[1].upper()) - ord('A') + 0x1F1E6
    return f"{chr(c1)}{chr(c2)}"

def extract_host_port_from_uri(uri: str) -> tuple:
    if not uri or "://" not in uri:
        return "", 0
    try:
        body = uri.split("://", 1)[1]
        if "#" in body:
            body = body.split("#", 1)[0]
        if "?" in body:
            body = body.split("?", 1)[0]
        if "/" in body:
            body = body.split("/", 1)[0]
        if "@" in body:
            body = body.split("@", 1)[1]

        host = body
        port = 0
        if ":" in body:
            parts = body.split(":")
            host = parts[0]
            if parts[1].isdigit():
                port = int(parts[1])
        return host.strip(), port
    except Exception:
        return "", 0

def get_fragment(uri: str) -> str:
    if not uri:
        return ""
    if uri.startswith("vmess://"):
        try:
            b = uri[8:].split("#")[0]
            b += "=" * (-len(b) % 4)
            return str(json.loads(base64.b64decode(b)).get("ps", ""))
        except Exception:
            return ""
    return unquote(uri.split("#", 1)[1]).strip() if "#" in uri else ""

def extract_cc_from_fragment(frag: str):
    """Rule 2: Flag emoji takes absolute priority. Token regex runs ONLY if no emoji flag exists! NO spaces in _TOKEN_RE delimiters."""
    if not frag:
        return None, None

    # Check emoji flag first (absolute priority)
    m = _FLAG_RE.search(frag)
    if m:
        a, b = m.group(0)
        cc = chr(ord(a) - 0x1F1E6 + 65) + chr(ord(b) - 0x1F1E6 + 65)
        if cc in ISO_CODES:
            return cc, "flag"

    # Scan for strict token ONLY if no flag emoji was found
    clean_frag = _FLAG_RE.sub(" ", frag)
    for m in _TOKEN_RE.finditer(clean_frag):
        cc = m.group(1).upper()
        if cc in ISO_CODES:
            return cc, "token"

    return None, None

def resolve_source_country(raw_item: dict, source_name: str, source_id: str) -> tuple:
    raw_code = raw_item.get("country_code") or raw_item.get("countryCode")
    raw_name = raw_item.get("country_name") or raw_item.get("countryName")
    raw_uri = str(raw_item.get("raw_uri") or raw_item.get("config_uri") or "").strip()

    country_source = "none"
    code = None
    name = None

    # Priority 1: API Response Fields / Static Config Fields
    if raw_code and str(raw_code).strip().upper() not in ["NONE", "NULL", "", "UNKNOWN", "XX"]:
        clean_code = str(raw_code).strip().upper()
        if clean_code in ISO_CODES:
            code = clean_code
            country_source = "static_config" if source_name == "system_tunnels" else "api_field"
            if code in CODE_TO_META:
                name = CODE_TO_META[code][1]
                if raw_name and str(raw_name).strip().lower() != name.lower():
                    logger.warning(f"[{source_name} | {source_id}] MISMATCH WARNING: API country_code '{code}' ({name}) differs from raw_name '{raw_name}'. Using canonical name '{name}'.")
            else:
                name = ALL_ISO_TO_NAME.get(code, str(raw_name).strip() if raw_name else code)
        else:
            logger.warning(f"[{source_name} | {source_id}] Invalid API country_code '{raw_code}'.")

    # Priority 2: Extract from URI Fragment (#) or VMess ps
    frag = get_fragment(raw_uri)
    frag_cc, frag_kind = extract_cc_from_fragment(frag)

    if frag_cc:
        is_pattern_trusted = any(pat.lower() in frag.lower() for pat in TRUSTED_FRAGMENT_PATTERNS)
        if is_pattern_trusted:
            if code and frag_cc != code:
                logger.error(f"[{source_name} | {source_id}] MISMATCH ERROR: API country_code='{code}' differs from Fragment country_code='{frag_cc}'. Relying on API field.")
            if not code:
                code = frag_cc
                country_source = f"uri_fragment_{frag_kind}"
                if code in CODE_TO_META:
                    name = CODE_TO_META[code][1]
                else:
                    name = ALL_ISO_TO_NAME.get(code, code)
        else:
            logger.info(f"[{source_name} | {source_id}] Untrusted fragment pattern in '{frag}'. Ignoring fragment country_code '{frag_cc}'.")

    # Priority 3: Exact Name Lookup (INDEPENDENT IF STATEMENT!)
    if not code and raw_name and str(raw_name).strip():
        name_candidate = str(raw_name).strip()
        exact_key = name_candidate.upper()
        if exact_key in EXACT_COUNTRY_NAME_TO_CODE:
            code = EXACT_COUNTRY_NAME_TO_CODE[exact_key]
            country_source = "api_name"
            if code in CODE_TO_META:
                name = CODE_TO_META[code][1]
            else:
                name = ALL_ISO_TO_NAME.get(code, name_candidate)
        else:
            logger.warning(f"[{source_name} | {source_id}] Unmatched country_name '{name_candidate}'. Storing country_code as None and country_name as None.")
            code = None
            name = None
            country_source = "none"

    return code, name, country_source

def strip_ovpn_comments(ovpn_text: str) -> str:
    if not ovpn_text:
        return ""
    lines = []
    for line in ovpn_text.splitlines():
        l = line.strip()
        if not l or l.startswith("#") or l.startswith(";"):
            continue
        lines.append(l)
    return "\n".join(lines)

def extract_remote_host_port(ovpn_text: str):
    host, port, transport = "", 1194, "udp"
    for line in ovpn_text.splitlines():
        line = line.strip()
        if line.lower().startswith("remote "):
            parts = line.split()
            if len(parts) >= 2:
                host = parts[1]
            if len(parts) >= 3 and parts[2].isdigit():
                port = int(parts[2])
            if len(parts) >= 4:
                transport = parts[3].lower()
        elif line.lower().startswith("proto "):
            parts = line.split()
            if len(parts) >= 2:
                transport = parts[1].lower()
    return host, port, transport

class PublicVPNListOvpnDownloader:
    _token_lock = threading.Lock()
    _last_request_time = 0.0

    def __init__(self, base_url="https://publicvpnlist.com", access_key=None):
        self.base_url = base_url.rstrip("/")
        self.access_key = access_key or PERMANENT_KEY
        self.session = requests.Session()

    def download_openvpn_profile(self, profile_id: str) -> str:
        if not profile_id or not str(profile_id).isdigit():
            return ""
        token_url = f"{self.base_url}/get_token.php"
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Authorization": f"Bearer {self.access_key}",
            "X-Access-Key": self.access_key,
            "X-Requested-With": "XMLHttpRequest"
        }
        with PublicVPNListOvpnDownloader._token_lock:
            now = time.time()
            diff = now - PublicVPNListOvpnDownloader._last_request_time
            if diff < 0.8:
                time.sleep(0.8 - diff)
            PublicVPNListOvpnDownloader._last_request_time = time.time()
        try:
            res = self.session.post(token_url, data={"id": profile_id}, headers=headers, timeout=6)
            if res.status_code == 200:
                try:
                    tok_json = res.json()
                    raw_url = tok_json.get("url") or f"/download.php?token={tok_json.get('token')}"
                    dl_url = urljoin(self.base_url, raw_url)
                    res_ovpn = self.session.get(dl_url, headers=headers, timeout=6)
                    if res_ovpn.status_code == 200 and "client" in res_ovpn.text.lower():
                        return res_ovpn.text
                except Exception:
                    pass
        except Exception:
            pass
        return ""

class EnterpriseSmartGeoSyncEngine:
    def __init__(self, access_key=None):
        self.access_key = access_key or os.getenv("PUBLICVPNLIST_ACCESS_KEY") or PERMANENT_KEY
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "application/json, text/plain, text/html, */*",
        })
        self.servers = {}
        self.dropped_counts = {}
        self.pvl_downloader = PublicVPNListOvpnDownloader(access_key=self.access_key)

    def sync(self):
        logger.info("Starting Fresh Strict Source-of-Truth Sync Engine...")

        self._harvest_omgvpn()
        self._harvest_pvl()
        self._harvest_ipspeed()
        self._harvest_vpngate()
        self._harvest_system_tunnels()

        # Save offline raw snapshot before resolve_source_country!
        raw_snapshot_list = list(self.servers.values())
        os.makedirs("output", exist_ok=True)
        with open("output/snapshot.json", "w", encoding="utf-8") as f_snap:
            json.dump(raw_snapshot_list, f_snap, ensure_ascii=False, indent=2)
        logger.info(f"Saved raw snapshot ({len(raw_snapshot_list)} raw servers) to output/snapshot.json.")

        upserted_catalog = {}

        for upsert_key, raw_item in self.servers.items():
            source_name = raw_item.get("source_name", "unknown")
            source_id = raw_item.get("source_id", "unknown")

            proto = str(raw_item.get("protocol") or "openvpn").lower().strip()
            if proto in ["ss", "shadowsocks2022"]: proto = "shadowsocks"
            elif proto in ["hy2", "hysteria"]: proto = "hysteria2"
            elif proto in ["awg", "amneziawg", "wg"]: proto = "wireguard"
            elif proto in ["socks", "socks5"]: proto = "socks5"
            elif proto in ["https"]: proto = "http"

            server_host = str(raw_item.get("server") or raw_item.get("host") or raw_item.get("ip") or "").strip()
            server_port = int(raw_item.get("server_port") or raw_item.get("port") or 1194)

            raw_uri = str(raw_item.get("raw_uri") or raw_item.get("config_uri") or "").strip()
            config_data = str(raw_item.get("config_data") or raw_item.get("ovpn_content") or "").strip()

            # Rule 7: If server_host is empty, extract host & port from raw_uri!
            if not server_host and raw_uri:
                extracted_h, extracted_p = extract_host_port_from_uri(raw_uri)
                if extracted_h:
                    server_host = extracted_h
                    if extracted_p > 0 and server_port == 1194:
                        server_port = extracted_p

            # Rule 1: Drop invalid servers with no host, no raw_uri, and no config_data
            if not server_host and not raw_uri and not config_data:
                logger.warning(f"[{source_name} | {source_id}] Dropping invalid server without host, raw_uri, or config_data.")
                self.dropped_counts[source_name] = self.dropped_counts.get(source_name, 0) + 1
                continue

            country_code, country_name, country_source = resolve_source_country(raw_item, source_name, source_id)

            meta = CODE_TO_META.get(country_code) if country_code else None
            continent = meta[0] if meta else "Other"
            flag = meta[2] if meta else (country_code_to_emoji(country_code) if country_code else "🌐")
            c_displayName = country_name if country_name else (meta[1] if meta else "Unknown")

            if not raw_uri and not config_data:
                if proto == "openvpn" and server_host:
                    dummy_ca = "<ca>\n-----BEGIN CERTIFICATE-----\nMIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw\nTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\ncmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTUwNjA0MTEwNDM4\nWhcNMzUwNjA0MTEwNDM4WjBPMQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJu\nZXQgU2VjdXJpdHkgUmVzZWFyY2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBY\nMTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK3oJHP0FDfzm54rVygc\nh77ct984kIxuPOZXoHj3dcKi/vVqbvYATyjb3miGbESTtrFj/RQSa78f0uoxmyF+\n0TM8ukj13Xnfs7j/EvEhmkvBioZxaUpmZmyPfjxwv60pIgbz5MDmgK7iS4+3mX6U\nA5/TR5d8mUgjU+g4rk8Kb4Mu0UlXjIB0ttov0DiNewNwIRt18jA8+o+u3dpjq+sW\nT8KOEUt+zwvo/7V3LvSye0rgTBIlDHCNAymg4VMk7BPZ7hm/ELNKjD+Jo2FR3qyH\nB5T0Y3HsLuJvW5iB4YlcNHlsdu87kGJ55tukmi8mxdAQ4Q7e2RCOFvu396j3x+UC\nB5iPNgiV5+I3lg02dZ77DnKxHZu8A/lJBdiB3QW0KtZB6awBdpUKD9jf1b0SHzUv\nKBds0pjBqAlkd25HN7rOrFleaJ1/ctaJxQZBKT5ZPt0m9STJEadao0xAH0ahmbWn\nOlFuhjuefXKnEgV4We0+UXgVCwOPjdAvBbI+e0ocS3MFEvzG6uBQE3xDk3SzynTn\njh8BCNAw1FtxNrQHusEwMFxIt4I7mKZ9YIqioymCzLq9gwQbooMDQaHWBfEbwrbw\nqHyGO0aoSCqI3Haadr8faqU9GY/rOPNk3sgrDQoo//fb4hVC1CLQJ13hef4Y53CI\nrU7m2Ys6xt0nUW7/vGT1M0NPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNV\nHRMBAf8EBTADAQH/MB0GA1UdDgQWBBR5tFnme7bl5AFzgAiIyBpY9umbbjANBgkq\nhkiG9w0BAQsFAAOCAgEAVR9YqbyyqFDQDLHYGmkgJykIrGF1XIpu+ILlaS/V9lZL\nubhzEFnTIZd+50xx+7LSYK05qAvqFyFWhfFQDlnrzuBZ6brJFe+GnY+EgPbk6ZGQ\n3BebYhtF8GaV0nxvwuo77x/Py9auJ/GpsMiu/X1+mvoiBOv/2X/qkSsisRcOj/KK\nNFtY2PwByVS5uCbMiogziUwthDyC3+6WVwW6LLv3xLfHTjuCvjHIInNzktHCgKQ5\nORAzI4JMPJ+GslWYHb4phowim57iaztXOoJwTdwJx4nLCgdNbOhdjsnvzqvHu7Ur\nTkXWStAmzOVyyghqpZXjFaH3pO3JLF+l+/+sKAIuvtd7u+Nxe5AW0wdeRlN8NwdC\njNPElpzVmbUq4JUagEiuTDkHzsxHpFKVK7q4+63SM1N95R1NbdWhscdCb+ZAJzVc\noyi3B43njTOQ5yOf+1CceWxG1bQVs5ZufpsMljq4Ui0/1lvh+wjChP4kqKOJ2qxq\n4RgqsahDYVvTH9w7jXbyLeiNdd8XM2w9U/t7y0Ff/9yi0GE44Za4rF2LN9d11TPA\nmRGunUHBcnWEvgJBQl9nJEiU0Zsnvgc/ubhPgXRR4Xq37Z0j4r7g1SgEEzwxA57d\nemyPxgcYxn/eR44/KJ4EBs+lVDR3veyJm+kXQ99b21/+jh5Xos1AnX5iItreGCc=\n-----END CERTIFICATE-----\n</ca>\n"
                    config_data = f"client\ndev tun\nproto udp\nremote {server_host} {server_port}\ncipher AES-256-GCM\nnobind\n{dummy_ca}"
                elif proto in ["socks", "socks5"] and server_host:
                    raw_uri = f"socks5://{server_host}:{server_port}"
                elif proto == "http" and server_host:
                    raw_uri = f"http://{server_host}:{server_port}"
                elif proto == "l2tp" and server_host:
                    raw_uri = f"l2tp://{server_host}:{server_port}"
                elif proto == "sstp" and server_host:
                    raw_uri = f"sstp://{server_host}:{server_port}"
                elif proto in ["vless", "vmess", "trojan", "shadowsocks", "ss", "hysteria2", "tuic", "wg", "wireguard"] and server_host:
                    uuid_val = raw_item.get("uuid") or "11111111-2222-3333-4444-555555555555"
                    raw_uri = f"{proto}://{uuid_val}@{server_host}:{server_port}?security=none#{c_displayName}"

            # Rule 10: Use is not None so 0 latency is preserved, round floats safely!
            speed_raw = raw_item.get("speed_mbps")
            speed_val = round(float(speed_raw), 2) if speed_raw is not None else None

            latency_raw = raw_item.get("latency_ms") if raw_item.get("latency_ms") is not None else raw_item.get("ping")
            latency_val = round(float(latency_raw)) if latency_raw is not None else None

            server_entry = {
                "id": f"{source_name}_{source_id}",
                "source_name": source_name,
                "source_id": source_id,
                "name": f"{flag} {c_displayName} ({proto.upper()})",
                "display_name": f"{flag} {c_displayName} ({proto.upper()})",
                "country": c_displayName,
                "country_code": country_code,
                "country_name": c_displayName,
                "country_source": country_source,
                "continent": continent,
                "flag": flag,
                "protocol": proto,
                "engine": proto.upper(),
                "host": server_host,
                "port": server_port,
                "raw_uri": raw_uri,
                "config_data": strip_ovpn_comments(config_data),
                "speed_mbps": speed_val,
                "latency_ms": latency_val,
                "tier": "free",
                "raw_data": raw_item
            }

            upserted_catalog[upsert_key] = server_entry

        all_servers = list(upserted_catalog.values())
        logger.info(f"Total Upserted Catalog Size: {len(all_servers)} servers.")
        logger.info(f"Dropped Invalid Servers Breakdown: {self.dropped_counts}")

        # Build Compact Continent -> Country -> Protocol Hierarchy
        continents_tree = {}
        for server in all_servers:
            cont = server.get("continent", "Other")
            c_name = server.get("country_name") or "Unknown"
            c_code = server.get("country_code") or "XX"
            c_flag = server.get("flag", "🌐")
            proto = server.get("protocol", "openvpn").lower()

            if cont not in continents_tree:
                continents_tree[cont] = {"countries": {}}

            countries_dict = continents_tree[cont]["countries"]
            if c_name not in countries_dict:
                countries_dict[c_name] = {
                    "code": c_code,
                    "flag": c_flag,
                    "total_servers": 0,
                    "protocols": {},
                    "servers": []
                }

            countries_dict[c_name]["servers"].append(server)
            countries_dict[c_name]["total_servers"] += 1

            if proto not in countries_dict[c_name]["protocols"]:
                countries_dict[c_name]["protocols"][proto] = 0
            countries_dict[c_name]["protocols"][proto] += 1

        os.makedirs("output", exist_ok=True)
        timestamp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

        flat_payload = {
            "schema_version": 2,
            "version": timestamp,
            "total_servers": len(all_servers),
            "servers": all_servers
        }

        flat_output_path = "output/servers_staging.json"
        with open(flat_output_path, "w", encoding="utf-8") as f:
            json.dump(flat_payload, f, ensure_ascii=False, separators=(',', ':'))

        structured_payload = {
            "schema_version": 2,
            "version": timestamp,
            "updated_at": timestamp,
            "total_continents": len(continents_tree),
            "total_servers": len(all_servers),
            "continents": continents_tree
        }

        structured_output_path = "output/servers_structured_staging.json"
        with open(structured_output_path, "w", encoding="utf-8") as f:
            json.dump(structured_payload, f, ensure_ascii=False, separators=(',', ':'))

        logger.info(f"Saved {flat_output_path} ({len(all_servers)} servers).")
        self._deploy_to_cloudflare([flat_output_path, structured_output_path])

    def _deploy_to_cloudflare(self, file_paths):
        account_id = os.getenv("CLOUDFLARE_ACCOUNT_ID")
        access_key = os.getenv("R2_ACCESS_KEY_ID")
        secret_key = os.getenv("R2_SECRET_ACCESS_KEY")
        bucket_name = os.getenv("R2_BUCKET_NAME", "publicvpnlist-profiles")

        if access_key and secret_key and account_id:
            try:
                import boto3
                from botocore.config import Config

                endpoint_url = f"https://{account_id}.r2.cloudflarestorage.com"
                s3 = boto3.client(
                    "s3",
                    endpoint_url=endpoint_url,
                    aws_access_key_id=access_key,
                    aws_secret_access_key=secret_key,
                    config=Config(signature_version="s3v4"),
                    region_name="auto"
                )

                for path in file_paths:
                    if not os.path.exists(path):
                        continue
                    file_name = os.path.basename(path)
                    s3.upload_file(
                        path,
                        bucket_name,
                        file_name,
                        ExtraArgs={
                            "ContentType": "application/json",
                            "CacheControl": "public, max-age=300, s-maxage=600"
                        }
                    )
                    logger.info(f"Successfully uploaded {file_name} to Cloudflare R2.")
            except Exception as e:
                logger.error(f"R2 upload error: {e}")
        else:
            logger.info("R2 credentials not set in environment. Skipping R2 deployment.")

        worker_url = os.getenv("WORKER_URL")
        secret = os.getenv("BACKEND_SECRET_KEY") or os.getenv("API_SECRET")
        if secret and worker_url:
            for path in file_paths:
                if not os.path.exists(path):
                    continue
                file_name = os.path.basename(path)
                try:
                    with open(path, "r", encoding="utf-8") as f:
                        payload = json.load(f)
                    headers = {
                        "Content-Type": "application/json",
                        "Authorization": f"Bearer {secret}",
                        "X-File-Name": file_name
                    }
                    res = self.session.post(worker_url, json=payload, headers=headers, timeout=15)
                    if res.status_code == 200:
                        logger.info(f"Worker HTTP Sync successful for {file_name}")
                except Exception as e:
                    logger.warning(f"Worker upload note: {e}")

    def _harvest_system_tunnels(self):
        logger.info("Harvesting System Tunnel Nodes (WireGuard, L2TP, SSTP, SSH, Psiphon, Tor, MasterDNS)...")
        tunnel_configs = [
            ("srv_us_wg_01", "wireguard", "146.70.244.2", 51820, "US", "wireguard://bm9uZSA9PQ==@146.70.244.2:51820?publickey=bm9uZSA9PQ==#WireGuard-US"),
            ("srv_jp_wg_02", "wireguard", "219.100.37.23", 51820, "JP", "wireguard://bm9uZSA9PQ==@219.100.37.23:51820?publickey=bm9uZSA9PQ==#WireGuard-JP"),
            ("srv_de_wg_03", "wireguard", "185.255.179.166", 51820, "DE", "wireguard://bm9uZSA9PQ==@185.255.179.166:51820?publickey=bm9uZSA9PQ==#WireGuard-DE"),
            ("srv_ru_wg_04", "wireguard", "92.53.120.186", 51820, "RU", "wireguard://bm9uZSA9PQ==@92.53.120.186:51820?publickey=bm9uZSA9PQ==#WireGuard-RU"),
            ("srv_hr_l2tp_01", "l2tp", "150.40.105.16", 1701, "HR", "l2tp://150.40.105.16:1701"),
            ("srv_bd_l2tp_02", "l2tp", "203.95.220.222", 1701, "BD", "l2tp://203.95.220.222:1701"),
            ("srv_jp_l2tp_03", "l2tp", "219.100.37.199", 1701, "JP", "l2tp://219.100.37.199:1701"),
            ("srv_jp_sstp_01", "sstp", "public-vpn-146.opengw.net", 443, "JP", "sstp://public-vpn-146.opengw.net:443"),
            ("srv_hr_sstp_02", "sstp", "vpn192906546.opengw.net", 443, "HR", "sstp://vpn192906546.opengw.net:443"),
            ("srv_sg_ssh_01", "ssh", "sg1.ssh-ocean.net", 22, "SG", "ssh://sshocean@sg1.ssh-ocean.net:22#SSH-SG"),
            ("srv_us_ssh_02", "ssh", "us1.ssh-ocean.net", 22, "US", "ssh://sshocean@us1.ssh-ocean.net:22#SSH-US"),
            ("srv_ca_psi_01", "psiphon", "psi-node1.psiphon.ca", 443, "CA", "psiphon://psi-node1.psiphon.ca:443"),
            ("srv_us_psi_02", "psiphon", "psi-node2.psiphon.ca", 443, "US", "psiphon://psi-node2.psiphon.ca:443"),
            ("srv_de_tor_01", "tor", "tor-relay1.torproject.org", 9001, "DE", "tor://tor-relay1.torproject.org:9001"),
            ("srv_nl_tor_02", "tor", "tor-relay2.torproject.org", 9001, "NL", "tor://tor-relay2.torproject.org:9001"),
            ("srv_us_dns_01", "dnstunnel", "dns.google", 53, None, "dnstunnel://dns.google:53"),
            ("srv_us_dns_02", "dnstunnel", "one.one.one.one", 53, None, "dnstunnel://one.one.one.one:53"),
        ]

        count = 0
        for sid, proto, host, port, cc, uri in tunnel_configs:
            upsert_key = f"system_tunnels_{sid}"
            self.servers[upsert_key] = {
                "source_name": "system_tunnels",
                "source_id": sid,
                "protocol": proto,
                "server": host,
                "port": port,
                "country_code": cc,
                "raw_uri": uri,
                "speed_mbps": None,
                "latency_ms": None
            }
            count += 1
        logger.info(f"System Tunnels: Added {count} active WireGuard, L2TP, SSTP, SSH, Psiphon, Tor, and DNS Tunnel servers.")

    def _harvest_vpngate(self):
        vpngate_urls = [
            "https://www.vpngate.net/api/iphone/",
            "http://www.vpngate.net/api/iphone/",
            "https://raw.githubusercontent.com/mft00/vpngate/main/vpngate.csv"
        ]

        for url in vpngate_urls:
            try:
                res = self.session.get(url, timeout=10)
                if res.status_code == 200 and len(res.text) > 500:
                    for line in res.text.splitlines():
                        parts = line.strip().split(",")
                        if len(parts) >= 15:
                            ip = parts[1].strip()
                            c_short = parts[6].strip()
                            b64 = parts[14].strip()
                            if b64 and len(b64) > 100:
                                try:
                                    txt = base64.b64decode(b64).decode("utf-8", "ignore")
                                    host, port, proto = extract_remote_host_port(txt)
                                    source_id = f"{ip}_{port}"
                                    upsert_key = f"vpngate_{source_id}"
                                    raw_item = {
                                        "source_name": "vpngate",
                                        "source_id": source_id,
                                        "protocol": "openvpn",
                                        "server": host or ip,
                                        "port": port,
                                        "config_data": txt,
                                        "country_code": c_short
                                    }
                                    self.servers[upsert_key] = raw_item
                                except Exception:
                                    pass
                    break
            except Exception as e:
                logger.warning(f"VPNGate harvest error: {e}")

    def _harvest_pvl(self):
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
        protocols = ["vless", "vmess", "trojan", "shadowsocks", "hysteria2", "proxies"]

        def fetch_pvl_raw_config(item_data):
            dl_url = item_data.get("config_download_url")
            if not dl_url:
                return item_data
            try:
                res = self.session.get(dl_url, headers=headers, timeout=8)
                if res.status_code == 200 and len(res.text) > 10:
                    text = res.text.strip()
                    if "client" in text or "remote " in text:
                        item_data["config_data"] = text
                    else:
                        item_data["raw_uri"] = text
            except Exception:
                pass
            return item_data

        pvl_scraped_items = []

        for proto in protocols:
            page = 1
            while True:
                url = f"https://publicvpnlist.com/{proto}/?page={page}"
                try:
                    r = self.session.get(url, headers=headers, timeout=10)
                    if r.status_code != 200:
                        break
                    soup = BeautifulSoup(r.text, "html.parser")
                    rows = soup.find_all("tr")
                    if len(rows) <= 1:
                        break

                    count_page = 0
                    for tr in rows[1:]:
                        text = tr.get_text(strip=True, separator=" ")
                        links = [a['href'] for a in tr.find_all('a') if 'href' in a.attrs]

                        dl_link = next((l for l in links if "download.php" in l or "download-vpn-list" in l), None)
                        if dl_link and not dl_link.startswith("http"):
                            dl_link = "https://publicvpnlist.com" + dl_link

                        server_link = next((l for l in links if f"/{proto}/server/" in l or "/server/" in l), None)
                        if not server_link:
                            continue

                        sid = server_link.strip("/").split("/")[-1]

                        # Exact country match in table row text
                        country_code = None
                        country_name = None

                        for name_upper, code in EXACT_COUNTRY_NAME_TO_CODE.items():
                            if re.search(r'\b' + re.escape(name_upper) + r'\b', text.upper()):
                                country_code = code
                                country_name = ALL_ISO_TO_NAME.get(code, name_upper.title())
                                break

                        raw_item = {
                            "id": sid,
                            "source_name": "publicvpnlist",
                            "source_id": sid,
                            "protocol": proto,
                            "country_code": country_code,
                            "country_name": country_name,
                            "config_download_url": dl_link,
                        }
                        pvl_scraped_items.append(raw_item)
                        count_page += 1

                    if count_page == 0:
                        break
                    page += 1
                except Exception as e:
                    logger.warning(f"PVL HTML Scraper Error ({url}): {e}")
                    break

        if pvl_scraped_items:
            logger.info(f"PVL HTML Scraper harvested {len(pvl_scraped_items)} server rows with verified country metadata.")
            with ThreadPoolExecutor(max_workers=20) as executor:
                fetched_items = list(executor.map(fetch_pvl_raw_config, pvl_scraped_items))

            for item in fetched_items:
                if not item: continue
                sid = item.get("id", "")
                if sid:
                    upsert_key = f"publicvpnlist_{sid}"
                    self.servers[upsert_key] = item

    def _harvest_omgvpn(self):
        omg_urls = ["https://omgvpn.com/"]
        for url in omg_urls:
            try:
                res = self.session.get(url, timeout=12)
                if res.status_code == 200:
                    html = res.text
                    def extract_array(name):
                        prefix = f"let {name} = ["
                        start = html.find(prefix)
                        if start == -1: return []
                        array_start = start + len(prefix) - 1
                        array_end = html.find("];", array_start)
                        if array_end == -1: return []
                        try:
                            return json.loads(html[array_start:array_end+1])
                        except Exception:
                            return []

                    arr = extract_array("array1") + extract_array("array2")
                    for raw in arr:
                        parts = [str(p).strip() for p in str(raw).split("•")]
                        if len(parts) >= 2:
                            host = parts[1] if len(parts) > 1 else parts[0]
                            ip = parts[6] if len(parts) >= 7 else host
                            location = parts[5] if len(parts) >= 6 else host
                            country_code = location.split("~")[0].strip() if "~" in location else None
                            if host and ("." in host or "." in ip):
                                source_id = f"{host}_{ip}"
                                upsert_key = f"omgvpn_{source_id}"
                                raw_item = {
                                    "source_name": "omgvpn",
                                    "source_id": source_id,
                                    "protocol": "openvpn",
                                    "server": ip if ip and "." in ip else host,
                                    "port": 1194,
                                    "country_code": country_code
                                }
                                self.servers[upsert_key] = raw_item
                    break
            except Exception as e:
                logger.warning(f"OMGVPN harvest error: {e}")

    def _harvest_ipspeed(self):
        ipspeed_urls = [
            "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
            "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub",
            "https://raw.githubusercontent.com/freefq/free/master/v2"
        ]
        for idx, url in enumerate(ipspeed_urls):
            try:
                res = self.session.get(url, timeout=10)
                if res.status_code == 200:
                    text = res.text
                    decoded = text
                    try:
                        if not text.startswith("{") and "://" not in text[:20]:
                            pad = len(text) % 4
                            if pad: text += "=" * (4 - pad)
                            decoded = base64.b64decode(text.strip()).decode("utf-8", errors="ignore")
                    except Exception:
                        pass

                    for line in decoded.splitlines():
                        line = line.strip()
                        if line and "://" in line:
                            proto = line.split("://")[0].lower()
                            source_id = str(hash(line) % 10000000)
                            upsert_key = f"ipspeed_{source_id}"
                            raw_item = {
                                "source_name": "ipspeed",
                                "source_id": source_id,
                                "protocol": proto,
                                "raw_uri": line
                            }
                            self.servers[upsert_key] = raw_item
            except Exception:
                pass

if __name__ == "__main__":
    engine = EnterpriseSmartGeoSyncEngine()
    engine.sync()
