# Play Store assets

Assets for the Play Console listing. Nothing here is packaged into the app or uploaded by
CI — `.github/workflows/deploy.yml` ships the App Bundle and release notes only, so these
are attached to the listing by hand.

| File | Play slot | Size |
| --- | --- | --- |
| `feature-graphic.png` | Feature graphic | 1024×500 |

`icon.svg` is not a listing asset — it is the launcher icon's geometry, drawn by the graphic.

## Regenerating the feature graphic

`feature-graphic.html` is the source. It is laid out at exactly 1024×500 and pulls Space
Grotesk from Google Fonts, so it needs network access to render:

```sh
chrome --headless=new --disable-gpu --hide-scrollbars \
       --force-device-scale-factor=1 --virtual-time-budget=8000 \
       --window-size=1024,500 --screenshot=feature-graphic.png \
       feature-graphic.html
```

The two GSearch tiles in the graphic are `icon.svg`, which carries the same glyph outlines
and the same group transform as the shipping icon
(`app/src/main/res/drawable/ic_launcher_{background,foreground}.xml`) — so resizing or
recolouring the icon cannot silently leave the listing behind. Both are regenerated together;
colours come from `brand_*` in `app/src/main/res/values/colors.xml`. After any icon change,
re-render the PNG with the command above.

The phone in the right-hand panel is a mockup — the app grid around the GSearch tile is
abstract, not a real screenshot.
