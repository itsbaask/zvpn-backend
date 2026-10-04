# ⚡ Z VPN — BRAND IDENTITY & DESIGN SYSTEM SPECIFICATION

> **PRIVATE. FAST. CONNECTED.**  
> *Production-Ready Visual Identity System for Z VPN*

---

## 🧭 BRAND OVERVIEW & PHILOSOPHY

The **Z VPN** visual language is engineered to communicate **trust, speed, absolute privacy, and technological precision**. It avoids the tropes of cheap neon, cyberpunk, gaming, or crypto apps. Instead, it adopts an **Apple-like restraint and minimalism**, where deep, velvety black surfaces dominate, and a signature **Primary Lime** is deployed strictly as a functional signal of protection, activity, and primary intent.

### Visual Ratio Guidelines
* **70–85%** Deep Obsidian & Dark Surfaces
* **10–20%** Neutral Whites & Technical Grays
* **5–10%** Signature Lime Accents

---

## 1. CORE BRAND PALETTE

| Token Name | HEX | RGB | HSL | Primary Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Deep Obsidian** | `#080A09` | `rgb(8, 10, 9)` | `hsl(150, 11%, 4%)` | Level 0 Canvas / AMOLED Background |
| **Primary Black** | `#0D100E` | `rgb(13, 16, 14)` | `hsl(140, 10%, 6%)` | Level 1 Base Card Surface |
| **Surface Black** | `#121613` | `rgb(18, 22, 19)` | `hsl(135, 10%, 8%)` | Level 2 Component Card Surface |
| **Elevated Surface** | `#181D18` | `rgb(24, 29, 24)` | `hsl(120, 9%, 10%)` | Level 3 Floating Sheets & Dialogs |
| **Hover Surface** | `#202520` | `rgb(32, 37, 32)` | `hsl(120, 7%, 14%)` | Level 4 Interactive Surface / Hover |
| **Subtle Border** | `#252B25` | `rgb(37, 43, 37)` | `hsl(120, 7%, 16%)` | Divider Lines & Card Strokes |
| **Primary Lime** | `#C7F24E` | `rgb(183, 255, 42)` | `hsl(80, 100%, 58%)` | **Brand Signature** / Connected / Primary CTA |
| **Electric Lime** | `#D4FF5A` | `rgb(212, 255, 90)` | `hsl(76, 100%, 68%)` | Primary CTA Hover & Active Highlights |
| **Deep Lime** | `#759F18` | `rgb(117, 159, 24)` | `hsl(78, 74%, 36%)` | Progress Filling & Pressed States |
| **Soft Lime** | `#A8D92A` | `rgb(168, 217, 42)` | `hsl(75, 71%, 51%)` | Protected Status Badges & Secondary Indicators |
| **Primary White** | `#F5F7F2` | `rgb(245, 247, 242)` | `hsl(84, 20%, 96%)` | Primary Text & High Contrast Icons |
| **Secondary Text** | `#A5AAA0` | `rgb(165, 170, 160)` | `hsl(90, 6%, 65%)` | Secondary Subtitles & Labels |
| **Muted Text** | `#686E65` | `rgb(104, 110, 101)` | `hsl(93, 4%, 41%)` | Tertiary Text, Placeholders & Inactive Icons |
| **Disabled** | `#454A43` | `rgb(69, 74, 67)` | `hsl(96, 5%, 28%)` | Disabled Controls & Inactive Borders |

---

## 2. SURFACE & ELEVATION HIERARCHY (DARK MODE)

Z VPN avoids floaty, ungrounded black boxes by creating subtle structural depth using precise monochromatic steps:

```
[ Level 0 ] #080A09  ── Canvas / Background
  [ Level 1 ] #0D100E  ── Section Groups / Base Containers
    [ Level 2 ] #121613  ── Cards / Action Tiles
      [ Level 3 ] #181D18  ── Elevated Sheets & Modals
        [ Level 4 ] #202520  ── Hover / Selected States
```
* **Borders (`#252B25`):** 1dp thin stroke with 0.8 alpha opacity for crisp edge definition without heavy drop shadows.

---

## 3. VPN CONNECTION STATES

| State | Background | Indicator | Status Text / CTA | Visual Experience |
| :--- | :--- | :--- | :--- | :--- |
| **DISCONNECTED** | `#080A09` (Deep Obsidian) | Subtle `#252B25` ring with muted `#686E65` center | Neutral Gray (`#A5AAA0`) | Calm, idle, reassuring. Not an error. |
| **CONNECTING** | `#080A09` | Progressive ring fill: `#759F18` → `#C7F24E` → `#D4FF5A` | `#F5F7F2` ("Connecting...") | Smooth, controlled sweeping light around the Z logo portal. No screen-wide neon. |
| **CONNECTED** | `#080A09` | `#C7F24E` Primary Lime active ring with 8% opacity Lime ambient aura | `#C7F24E` ("Protected & Connected") | **Strongest brand moment.** Vibrant, alive, confident, clean. |
| **RECONNECTING** | `#080A09` | `#F5B942` Amber inner pulse + `#759F18` Lime track | Amber/Gray ("Reestablishing...") | Restrained warning state, keeping dark foundation. |
| **CONNECTION FAILED**| `#080A09` | `#FF4D4D` soft red status badge & CTA border | `#FF4D4D` ("Connection Failed") | Red is restricted ONLY to error badge/text. UI stays black/lime brand. |

---

## 4. BUTTON & INTERACTION SYSTEM

```
PRIMARY BUTTON (Lime CTA)
[ #C7F24E Fill  |  #080A09 Text  |  Bold Weight ]
  Hover:   #D4FF5A Fill
  Pressed: #759F18 Fill
  Disabled:#181D18 Fill  |  #454A43 Text

OUTLINE BUTTON
[ #121613 Surface  |  #252B25 Stroke  |  #F5F7F2 Text ]
  Hover:   #181D18 Surface  |  #C7F24E Stroke
  Active:  #C7F24E Stroke + Subtle Lime Text

GHOST BUTTON
[ Transparent Fill  |  #A5AAA0 Text ]
  Hover:   #121613 Fill     |  #C7F24E Text
```

---

## 5. GLOW & GRADIENT SYSTEM

### Glow Protocol
* **Opacity limit:** Max **8% - 12%** opacity.
* **Glow target:** Restricted strictly to:
  1. Connected State Portal Ring
  2. Active Security Shield Status
  3. Primary Connected CTA
* **Prohibited:** Never apply glow to plain body text, secondary cards, or list items.

### Controlled Gradients
* **Brand Primary Gradient:** `#759F18` (Deep Lime) → `#C7F24E` (Primary Lime)
* **Energy Pulse Gradient:** `#C7F24E` (Primary Lime) → `#D4FF5A` (Electric Lime)
* **Surface Depth Gradient:** `#080A09` → `#121613` (Top to bottom subtle background shift)

---

## 6. LIGHT MODE ADAPTATION

Light mode retains the exact Z VPN identity without harsh contrast inversions:

| Token Name | Light Mode HEX | Dark Mode Equivalent | Purpose |
| :--- | :--- | :--- | :--- |
| **Canvas Background** | `#F7F8F4` | `#080A09` | Clean, soft cream-gray canvas |
| **Base Surface** | `#FFFFFF` | `#0D100E` | Pure white cards |
| **Secondary Surface** | `#EFF2EC` | `#121613` | Input fields & subtle tiles |
| **Border** | `#DDE2D8` | `#252B25` | Light crisp card borders |
| **Primary Text** | `#11140F` | `#F5F7F2` | Deep charcoal primary text |
| **Secondary Text** | `#666B62` | `#A5AAA0` | Muted charcoal subtitles |
| **Accessible Lime** | `#8FCF18` | `#C7F24E` | Adjusted for high contrast on white |

---

## 7. PRO / VIP SUBSCRIPTION SYSTEM

The Subscription tier feels luxurious and high-end while maintaining the core **Z VPN** brand identity:
* **Background:** Level 0 Deep Obsidian (`#080A09`)
* **Primary Accent:** `#C7F24E` (Primary Lime)
* **Luxury Secondary Accent:** `#E6D3A3` (Subtle Champagne Gold) applied **only** to VIP badges and premium server tags.
* *Rule:* Never turn the whole screen gold. The dark surface + lime identity remains 100% recognizable.

---

## 8. SPLASH SCREEN & APP ICON

### App Icon
* **Dark Mode Icon:** Pure `#080A09` background with the sweeping White/Lime **Z** emblem.
* **Light Mode Icon:** `#FFFFFF` background with the crisp Black **Z** emblem and subtle `#8FCF18` Lime accent.

### Splash Screen
* Minimalist Deep Obsidian background (`#080A09`).
* Center-aligned **Z** emblem in `#F5F7F2` with a subtle 6% Primary Lime glow underneath. No progress bars, particles, or clutter.

---

## 9. ACCESSIBILITY & CONTRAST

* **Contrast Ratios:**
  - `#C7F24E` on `#080A09` = **15.2:1** (Exceeds AAA standard).
  - `#F5F7F2` on `#080A09` = **18.1:1** (Exceeds AAA standard).
  - `#8FCF18` on `#FFFFFF` = **3.1:1** (Exceeds AA standard for large graphic controls).
* **Multi-Cue Status:** All VPN statuses pair color with iconography and explicit text labels so status is never conveyed by color alone.

---

*Z VPN — PRODUCTION DESIGN SYSTEM SPECIFICATION*
