# Launcher icon: Ai06

Owner selected Ai06 variant 1 on 2026-11-10. Merge still requires normal exact-head CI and independent review.

Selected concept: a hand-drawn gold A, white lowercase i replacing the digit 1, a gold four-point spark as its dot, followed by white 0 and 6 on the existing navy background. At small sizes the mark reads Ai06, not literally 106; the owner chose this after viewing 32px and 48px previews. No font, municipal logo, coat of arms or external asset is used. Geometry was drawn for this project and is covered by this repository's license. This is not an official municipal app or an official municipal mark.

- Navy: #2C537E. Gold: #D8A038. White: #FFFFFF.
- Adaptive icon: foreground and solid navy background in a 108dp viewport.
- Geometry remains in the central safe region; checked with circle and rounded-square masks, including 48px and 32px previews.
- Android 13 themed icon: one-color version through the monochrome layer. Launcher chooses its tint.
- minSdk is 26, so the app supports adaptive icons on every supported device. No raster fallback is required.
- Application icon and roundIcon are wired in the manifest. The existing Quick Settings tile icon is unchanged.

The repository checker verifies both manifest references and all adaptive layers. The screenshot preview was rendered from the same path geometry. Android build and launcher inspection are still CI/device checks, not covered by the static checker.
