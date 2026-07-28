# Voice Recognition Fix & UI Redesign Plan

This plan addresses the voice recognition issues and the outdated UI of the Copilot Cockpit app.

## User Review Required

> [!IMPORTANT]
> **Voice Recognition:** I've identified a missing `<queries>` declaration in the `AndroidManifest.xml`, which is required for apps targeting Android 11+ to interact with speech services. This is likely the cause of the recognition not starting.
> **UI Redesign:** I will shift the current dark/neon theme to a cleaner "Blue and White" palette, removing all emojis and replacing them with modern icons.

## Open Questions

1. **Voice Recognition:** Are you testing on an emulator or a physical device? Some emulators lack the Google Speech Service (Google App) required for `SpeechRecognizer`.
2. **UI Preferences:** Do you prefer a "Light Mode" (White background) or a "Professional Dark Mode" (Dark Blue background with White text)? The prompt mentions "Blue/White palette", which often implies a clean, light interface, but high-end car cockpits often use dark themes for legibility.

## Proposed Changes

### [Component Name] Voice Recognition Logic

#### [MODIFY] [AndroidManifest.xml](file:///D:/Hackathon/cockpit-ui/app/src/main/AndroidManifest.xml)
- Add `<queries>` tag to allow the app to see the `RecognitionService`. This is mandatory for API 30+.

#### [MODIFY] [MainActivity.kt](file:///D:/Hackathon/cockpit-ui/app/src/main/java/com/wheelchair/cockpit/MainActivity.kt)
- Add more robust error handling for `SpeechRecognizer` errors (e.g., `ERROR_NETWORK`, `ERROR_CLIENT`).
- Ensure the mic is released correctly.

---

### [Component Name] UI Redesign (Modern Blue & White)

#### [MODIFY] [build.gradle.kts](file:///D:/Hackathon/cockpit-ui/app/build.gradle.kts)
- Add `androidx.compose.material:material-icons-extended` dependency to provide a wider range of icons (Mic, Warning, History, etc.).

#### [MODIFY] [MainActivity.kt](file:///D:/Hackathon/cockpit-ui/app/src/main/java/com/wheelchair/cockpit/MainActivity.kt)
- **Colors:** Redefine the color palette using primary blues and white/light-grey backgrounds.
- **Icons:** Replace all emoji characters (🎙️, ✅, 🔊, ⏳, ⚠️, 🤖, 📚) with `Icon` components using `Icons.Default` or `Icons.Rounded`.
- **Layout:**
    - Use a more modern "Card-based" layout.
    - Improve the "Voice Status Banner" to look like a sleek dashboard component.
    - Enhance the chat/answer area with better spacing and typography.
    - Ensure the "Speed Alert" is visually striking but consistent with the new palette (e.g., using a high-contrast red-on-white or similar).

## Verification Plan

### Automated Tests
- I'll perform a Gradle build to ensure no syntax errors were introduced.
- (Optional) I can run `analyze_file` on modified files.

### Manual Verification
- **Voice:** The user should check if the "Speech Service not available" message disappears (if it was present) and if the mic starts listening.
- **UI:** I will use `render_compose_preview` for the `CockpitUI` if possible, although it has many parameters that might need mock data.
