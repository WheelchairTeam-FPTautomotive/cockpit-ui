---
name: Cockpit UI AAOS Enhancement
overview: Enhance cockpit-ui voice copilot HUD with Design-for-Driving-compliant motion, voice-plate states, and car-control confirmation animations on a separate feature branch so main remains submission-safe.
todos:
  - id: branch-setup
    content: Create feature/aaos-ui-polish from main; never merge until demo gate
    status: pending
  - id: voice-state-machine
    content: Formalize idle|listening|thinking|speaking|control_*|rag_answer UI states
    status: pending
  - id: voice-plate-motion
    content: Bottom voice plate enter/exit + listening pulse per AAOS motion patterns
    status: pending
  - id: car-control-lottie
    content: Add Lottie (or Compose) one-shot confirms for door/lock/HVAC/music
    status: pending
  - id: rag-citation-polish
    content: Glanceable citation card motion; max 3 text lines while driving
    status: pending
  - id: contrast-distraction
    content: Contrast ≥4.5:1; no overlay on system chrome; ≤5 interaction steps
    status: pending
  - id: demo-checklist
    content: Emulator/device demo checklist + screen recording for reviewers
    status: pending
isProject: false
---

# Plan: Cockpit UI enhancement (AAOS-safe voice + car-control animation)

**Owner:** Bảo (`S0meDudeIdk`)  
**Repo:** [WheelchairTeam-FPTautomotive/cockpit-ui](https://github.com/WheelchairTeam-FPTautomotive/cockpit-ui)  
**Priority:** Fast-track polish for demo — **ship on a feature branch only**  
**Fallback:** If time runs out, **submit `main` as-is**. Do **not** merge this work into `main` until explicitly approved.

---

## 0. Branching rule (non-negotiable)

```bash
git fetch origin
git checkout main
git pull --ff-only origin main
git checkout -b feature/aaos-ui-polish
```

| Do | Don't |
|----|--------|
| All UI work on `feature/aaos-ui-polish` | Merge to `main` without team OK |
| Open PR when ready; keep PR draft until demo gate | Break wake-word / STT / VHAL paths |
| Prefer additive Compose/Lottie | Rewrite `MainActivity` orchestration |

**Why:** Demo deadline risk. `main` must stay a known-good submission tree.

---

## 1. Goals

Elevate the Carsky / Traceable Voice Copilot HUD so it feels like a production AAOS assistant:

1. **Voice-forward plate** with clear listening / thinking / speaking states  
2. **Car-control confirmation animation** (door, lock, HVAC, music) — informative, short, one-shot  
3. **RAG answer + citations** glanceable under distraction constraints  
4. Align with **Design for Driving** motion + assistant UX (not decorative “AI dashboard” chrome)

### Non-goals
- Backend/RAG accuracy work (other tracks)  
- Full OEM skinning of system UI  
- Replacing Vosk wake or Retrofit gateway contracts  

---

## 2. Current codebase anchors

| Concern | Path |
|---------|------|
| HUD shell | `app/src/main/java/com/wheelchair/cockpit/MainActivity.kt` → `CockpitAppScreen` |
| Copilot panel | `ui/components/CopilotResponsePanel.kt` |
| Car feedback today | `ui/components/MockActuationOverlay.kt` (Compose `AnimatedVisibility` only) |
| Waveform | `ui/components/StitchVoiceWaveform.kt` |
| Citations | `ui/components/CitationCard.kt` |
| Wake | `voice/WakeWordForegroundService.kt`, `WakeWordEngine.kt` |
| VHAL | `vhal/CarPropertyHelper.kt` |
| Theme | `ui/theme/CockpitTheme.kt` |

**Gap:** No Lottie; control feedback is toast/overlay-level; voice states are partially implicit.

---

## 3. Design sources (use as Constraints)

| Source | Use for |
|--------|---------|
| [Design for Driving – Motion](https://developers.google.com/cars/design/automotive-os/design-system/motion) | Motion only when informative; overlay/scrim patterns |
| [AAOS system UI reference](https://developers.google.com/cars/design/automotive-os/product-experience/system-ui/overview) | Layout language; don’t fight system chrome |
| [Preloaded assistants UX (PDF)](https://source.android.google.cn/static/docs/automotive/voice/voice_interaction_guide/preloaded-assistants_UX-guidelines.pdf) | Voice-forward; listening indicator; privacy mute |
| [Assistant voice-plate motion](https://docs.partner.android.com/drivingux/gemini/voice-plate/motion-patterns) | Enter/exit from **bottom**; rectangular scrim; transcription fade ≤150ms/word |
| [Cars UX requirements](https://developer.android.com/design/ui/cars/guides/ux-requirements/overview) | ≤5 steps; contrast; animation while driving rules |
| [Alexa Auto Design](https://developer.amazon.com/en-US/docs/alexa/alexa-auto/about-this-guide.html) | Multimodal confirm patterns |
| [TC-EBC prompting](https://www.figma.com/blog/designer-framework-for-better-ai-prompts/) | Structured design prompts if generating mockups |
| [Lottie Compose](https://github.com/airbnb/lottie/blob/master/android-compose.md) | State-driven car-control clips |

---

## 4. Target UX state machine

Introduce an explicit `CopilotUiState` (name flexible) consumed by Compose:

```text
idle → listening → thinking → speaking
                 ↘ control_success (600–900ms) → idle
                 ↘ control_fail → idle
                 ↘ rag_answer (citations visible) → idle
```

| State | Visual | Motion |
|-------|--------|--------|
| `idle` | Collapsed plate / dock mic affordance | None |
| `listening` | Bottom voice plate + waveform | Soft pulse; plate slides up from bottom |
| `thinking` | Plate + subtle indeterminate | No looping distraction on map-critical areas |
| `speaking` / TTS | Plate + transcript | Word fade ≤150ms if showing STT text |
| `control_success` | Car control Lottie/iconography | **One-shot** 600–900ms then auto-dismiss |
| `control_fail` | Error chip | Short shake/fade; no loop |
| `rag_answer` | Answer + ≤2 citation cards | Slide/fade up; max ~3 glanceable lines |

Wire from existing intent results in `MainActivity` / repository callbacks (`CAR_CONTROL`, RAG success, errors) — **do not** invent new backend APIs.

---

## 5. Implementation work packages (fast path)

### WP1 — Branch + dependency (~30–45 min)
- Branch `feature/aaos-ui-polish`
- Add `com.airbnb.android:lottie-compose` (current stable) in `app/build.gradle.kts`
- Place short Lottie JSON under `app/src/main/res/raw/` (door, lock, hvac, music) — prefer CC0/LottieFiles; trim duration

### WP2 — Voice plate shell (~2–3 h)
- Extract / refine bottom **voice plate** composable (new file e.g. `ui/components/VoicePlate.kt`)
- Enter/exit from bottom + optional partial scrim (rectangular plate pattern)
- Bind `StitchVoiceWaveform` to `listening` only
- Ensure plate does **not** permanently cover climate/system affordances in `AutomotiveBottomDock`

### WP3 — Car-control animation (~2–3 h)
- Evolve `MockActuationOverlay` → `CarControlFeedbackOverlay`:
  - Map `command_id` / existing mock actuation keys → Lottie `RawRes`
  - `animateLottieCompositionAsState` / `LottieClipSpec` for success segment; `iterations = 1`
  - Keep Compose fallback if asset missing (current slide/fade) so demo never hard-crashes
- Trigger only on **successful** CAR_CONTROL path already used by overlay today

### WP4 — RAG / citation glanceability (~1–2 h)
- `CopilotResponsePanel` + `CitationCard`: tighten typography, contrast, max lines
- Motion: content enters after `thinking`; no continuous shimmer while driving
- Preserve citation fields (document_name basename, page) — traceability KPI

### WP5 — Theme / distraction pass (~1 h)
- Tokens in `CockpitTheme`: high-contrast surfaces; avoid purple-glow “AI” cliché
- Status pulse in `MainActivity` — reduce amplitude if it competes with voice plate
- Driving restriction: keep existing VHAL lock behavior; animations must not unlock unsafe input

### WP6 — Demo proof (~45 min)
- Checklist in PR:
  - [ ] Hey Car → listening plate
  - [ ] Door / HVAC / music → one-shot confirm
  - [ ] RAG ask → answer + citation without covering dock
  - [ ] Fail path → non-blocking error
- Optional 20s screen recording attached to PR

---

## 6. TC-EBC prompt (for Figma / design iteration if needed)

```text
Task: Polish AAOS landscape voice-copilot HUD with car-control confirmation animations.
Context: Carsky Traceable Voice Copilot; intents RAG / FREE_TALK / CAR_CONTROL; submit-safe feature branch.
Elements: Bottom voice plate, waveform, transcript, citation cards, control Lottie slot, dock.
Behavior: listening pulse; control_success 600–900ms one-shot; rag_answer slide-up; dismiss on settle.
Constraints: Design for Driving Motion; plate from bottom; contrast ≥4.5:1; no system-chrome cover;
  ≤3 glanceable text lines; ~1920×720; Compose+Lottie; brand Carsky; no decorative loops while driving.
```

---

## 7. Acceptance criteria

1. All commits on `feature/aaos-ui-polish` (or PR from that branch); **`main` untouched** by this task.  
2. Explicit UI states for listening / thinking / control_success at minimum.  
3. CAR_CONTROL success shows ≤900ms confirmation animation (Lottie or Compose equivalent).  
4. Voice plate enters/exits from bottom; listening is visually obvious.  
5. RAG citations remain visible and basename-safe.  
6. No regression: wake word foreground path, STT→gateway, mock actuation still fire.  
7. PR description links this plan and notes “do not merge until demo gate.”

---

## 8. Time box

| Budget | Outcome |
|--------|---------|
| **≤1 day** | WP1–WP3 + minimal WP4 (demo-critical) |
| **+0.5 day** | WP5–WP6 polish |
| **Timeout** | Stop; leave PR draft; team submits **`main`** |

Ask: **make it quick** — prioritize control animation + listening plate over perfect theming.

---

## 9. Pros / cons

| | Pros | Cons |
|--|------|------|
| Feature branch | `main` always submittable | Extra merge step later |
| Lottie confirms | Clear demo “wow” for car control | Asset licensing + APK size |
| AAOS motion rules | Credible / safer HMI | Less flashy than consumer-app animation |

---

## 10. References checklist (paste into PR)

- https://developers.google.com/cars/design/automotive-os/design-system/motion  
- https://docs.partner.android.com/drivingux/gemini/voice-plate/motion-patterns  
- https://github.com/airbnb/lottie/blob/master/android-compose.md  
- This plan file: `docs/COCKPIT_UI_AAOS_ENHANCEMENT_PLAN.md`
