Contributing

Thanks for your interest in contributing to Ver-ID UI for Android!

Before you open a pull request:

- Ensure `./gradlew :veridui:lint :veridui:test` passes
- Build `:veridui` and `:sample` locally (or let CI validate)
- Follow the PR template and keep changes focused and scoped

Development tips

- Use Android Studio Giraffe or newer with JDK 17
- Run the sample app on a physical device for camera features

Project scripts

- Translation completeness check: `python3 test_translation.py veridui/src/main/assets/<locale>.xml`

