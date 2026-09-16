#!/bin/sh
# Verify every daisyUI component class used in the UI actually exists in the
# installed daisyUI release.
#
# The inspector UI cannot be eyeballed without running Logseq, so this is the
# mechanical guard against class rot: daisyUI renames classes between majors,
# and a dropped class fails silently - the markup still renders, just unstyled.
#
# Checks against daisyUI's full.css rather than the built dist/styles.css,
# because the built file is tree-shaken: a class missing from it may simply be
# unused, which would make this check report false positives.

DAISY=${1:-node_modules/daisyui/dist/full.css}
SRC=${2:-src/main/ui.cljs}

[ -f "$DAISY" ] || { echo "No $DAISY - run 'yarn install' first."; exit 1; }

# daisyUI component families this UI draws on. Plain Tailwind utilities
# (flex, w-full, text-xs, ...) are not daisyUI's to rename, so they are
# excluded rather than checked.
FAMILIES='(card|btn|input|radio|checkbox|table|badge|tabs?|form-control|label)'

CLASSES=$(grep -oE '\.[a-z][a-z0-9-]*' "$SRC" \
  | sed 's/^\.//' \
  | grep -xE "${FAMILIES}(-[a-z0-9]+)*" \
  | sort -u)

missing=0
for c in $CLASSES; do
  if grep -qE "(^|[^a-zA-Z0-9_-])\.$c([^a-zA-Z0-9_-]|$)" "$DAISY"; then
    printf '  ok      %s\n' "$c"
  else
    printf '  MISSING %s\n' "$c"
    missing=$((missing + 1))
  fi
done

echo
if [ "$missing" -gt 0 ]; then
  echo "$missing class(es) used by the UI do not exist in $(basename "$(dirname "$(dirname "$DAISY")")")"
  exit 1
fi
echo "All daisyUI classes used by the UI exist in the installed release."
