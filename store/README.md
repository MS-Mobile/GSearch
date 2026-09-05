# Play Store assets

Assets for the Play Console listing. Nothing here is packaged into the app or uploaded by
CI — `.github/workflows/deploy.yml` ships the App Bundle and release notes only, so these
are attached to the listing by hand.

| File | Play slot | Size |
| --- | --- | --- |
| `feature-graphic.png` | Feature graphic | 1024×500 |

## Regenerating the feature graphic

`feature-graphic.html` is the source. It is laid out at exactly 1024×500 and pulls Space
Grotesk from Google Fonts, so it needs network access to render:

```sh
chrome --headless=new --disable-gpu --hide-scrollbars \
       --force-device-scale-factor=1 --virtual-time-budget=8000 \
       --window-size=1024,500 --screenshot=feature-graphic.png \
       feature-graphic.html
```

The launcher icon inside the graphic is a CSS reproduction of the real one; the shipping
icon is `app/src/main/res/drawable/ic_launcher_{background,foreground}.xml`, and both draw
their colours from `brand_*` in `app/src/main/res/values/colors.xml`. Change a brand colour
in one place and re-render here so the listing and the icon stay in step.

The phone in the right-hand panel is a mockup — the app grid around the GSearch tile is
abstract, not a real screenshot.
