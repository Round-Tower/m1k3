## What & why

<!-- One or two sentences: what changes, and what it fixes or enables. -->

## Checklist

- [ ] Tests cover the new behaviour (`swift test --parallel` green locally)
- [ ] If `macos/M1K3App/` changed: app shell builds (`xcodegen generate && xcodebuild -scheme M1K3 build`)
- [ ] No new network calls without explicit opt-in (privacy is the product)
- [ ] No real user data in fixtures or eval cases
- [ ] Signed files: existing MurphySig blocks read and respected (see CONTRIBUTING.md)
