# iosApp scaffold

This folder contains a minimal SwiftUI entrypoint prepared for `shared` framework usage.

## How to connect in Xcode

1. Create an iOS App project in Xcode in this `iosApp` directory (for example, project name `iosApp`).
2. In Xcode target Build Phases add a Run Script phase before Compile Sources:
   ```sh
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```
3. Use `ComposeView` as the app root view.

After this, Xcode will consume the framework built from `shared`.
