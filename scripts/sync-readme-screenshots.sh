#!/usr/bin/env sh
#
# Regenerate docs/screenshots/ (the README gallery) from the committed Compose
# screenshot-test reference renders.
#
# The reference renders live under
#   app/src/screenshotTestDebug/reference/com/msmobile/gsearch/<pkg>/<Test>/
# with generated filenames of the form
#   <Method>$app_Phone_<themeHash>_<contentHash>_<index>.png
# where <themeHash> encodes the device/theme config (see LIGHT/DARK below) and
# <index> is the position of the preview in its PreviewParameterProvider.
#
# A handful of those are curated into readable filenames so the README is not
# coupled to the generated paths. Regenerate the references first with:
#   ./gradlew :app:updateDebugScreenshotTest
#
# The (dir, theme, index) triples below map to the providers in:
#   config/ConfigPreviewConfigProvider.kt / config/GSearchBarPreviewConfigProvider.kt
# Reordering either provider changes the indices here — the script fails loudly
# rather than copying the wrong image, but it cannot tell that the picture now
# shows something else, so check the gallery after a reorder.

set -eu

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

REF_ROOT="app/src/screenshotTestDebug/reference/com/msmobile/gsearch"
DEST="docs/screenshots"

# Device-config hashes in the reference filenames. Both uiMode variants of
# @PreviewPhone render every preview, so the gallery can show either without
# costing an extra reference image.
LIGHT="266ff7ee"
DARK="3e7ebd25"

if [ ! -d "$REF_ROOT" ]; then
	echo "error: $REF_ROOT not found — run './gradlew :app:updateDebugScreenshotTest' first" >&2
	exit 1
fi

mkdir -p "$DEST"

# dest filename | reference test dir (under REF_ROOT) | theme | preview index
status=0
while IFS='|' read -r name dir theme index; do
	[ -z "$name" ] && continue
	case "$name" in \#*) continue ;; esac

	case "$theme" in
		light) hash="$LIGHT" ;;
		dark) hash="$DARK" ;;
		*)
			echo "  x $name: unknown theme '$theme'" >&2
			status=1
			continue
			;;
	esac

	# shellcheck disable=SC2086
	set -- "$REF_ROOT/$dir"/*_"${hash}"_*_"${index}".png
	if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
		echo "  x $name: expected exactly one reference for '$dir' $theme index $index, found $#" >&2
		status=1
		continue
	fi

	cp "$1" "$DEST/$name"
	echo "  + $name"
done <<'MANIFEST'
settings-light.png|config/ConfigScreenshotTest|light|0
settings-dark.png|config/ConfigScreenshotTest|dark|0
settings-all-actions.png|config/ConfigScreenshotTest|light|1
bar-default.png|config/GSearchBarScreenshotTest|light|0
bar-all-actions-dark.png|config/GSearchBarScreenshotTest|dark|1
MANIFEST

if [ "$status" -ne 0 ]; then
	echo "sync-readme-screenshots: one or more references could not be resolved" >&2
	exit "$status"
fi

echo "docs/screenshots is in sync."
