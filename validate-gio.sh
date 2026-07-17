#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Gebruik: $0 <gml-bestand1> [bestand2 ...]"
  exit 2
fi

declare -A GEOM_BY_ID
declare -A ID_BY_NAME

FAIL=0

echo "=== GIO VALIDATIE START ==="
echo "Bestanden: $*"
echo "---------------------------"

if [[ -t 1 ]]; then
  RED='\033[0;31m'
  GREEN='\033[0;32m'
  YELLOW='\033[0;33m'
  RESET='\033[0m'
else
  RED=''
  GREEN=''
  YELLOW=''
  RESET=''
fi

log_ok() {
  printf "${GREEN}[OK]${RESET} "
  printf "$@"
  printf "\n"
}

log_warn() {
  printf "${YELLOW}[WARN]${RESET} " >&2
  printf "$@" >&2
  printf "\n" >&2
}

log_err() {
  printf "${RED}[FAIL]${RESET} " >&2
  printf "$@" >&2
  printf "\n" >&2
}

validate_file() {
  local file="$1"

  if [[ ! -f "$file" ]]; then
    log_err "Bestand niet gevonden: $file"
    FAIL=1
    return
  fi

  if ! xmllint --noout "$file" 2>/dev/null; then
    log_err "XML niet well-formed: $file"
    FAIL=1
    return
  fi

  log_ok "XML well-formed: $file"

  # --------------------------------
  # Regel 1: FRBRWork moet valide zijn
  # --------------------------------
  local frbr_work
  frbr_work=$(xmllint --xpath "//*[local-name()='FRBRWork']/text()" "$file" 2>/dev/null || true)

  local frbr_work_regex
  frbr_work_regex="^/join/id/regdata/mnre1153/[0-9]{4}(-[0-9]{2}-[0-9]{2})?/N2000_.+_(N2000|HR|VR)$"

  if [[ -z "$frbr_work" ]]; then
    log_err "FRBRWork ontbreekt"
    FAIL=1
    return
  elif [[ "$frbr_work" =~ $frbr_work_regex ]]; then
    log_ok "FRBRWork voldoet aan het voorgeschreven patroon"
  else
    log_err "FRBRWork ongeldig: $frbr_work"
    echo "       Verwacht patroon: /join/id/regdata/mnre1153/<jaar-of-datum>/N2000_<gebiedsnaam>_(N2000|HR|VR)" >&2
    FAIL=1
  fi

  # --------------------------------
  # Regel 2: FRBRExpression moet beginnen met FRBRWork
  # --------------------------------
  local frbr_expr
  frbr_expr=$(xmllint --xpath "//*[local-name()='FRBRExpression']/text()" "$file" 2>/dev/null || true)

  local frbr_expr_regex
  frbr_expr_regex="^${frbr_work}/nld@[0-9]{4}-[0-9]{2}-[0-9]{2}$"

  if [[ -z "$frbr_expr" ]]; then
    log_err "FRBRExpression ontbreekt"
    FAIL=1
  elif [[ "$frbr_expr" =~ $frbr_expr_regex ]]; then
    log_ok "FRBRExpression voldoet aan het voorgeschreven patroon"
  else
    log_err "FRBRExpression ongeldig: $frbr_expr"
    echo "       Verwacht patroon: $frbr_work/nld@<datum>" >&2
    FAIL=1
  fi

  # Aantal locaties in dit bestand
  location_count=$(xmllint --xpath "count(//*[local-name()='Locatie'])" "$file" 2>/dev/null || true)
  location_count=${location_count%.*}

  for ((i=1; i<=location_count; i++)); do
    # geo:naam
    name=$(xmllint --xpath "(//*[local-name()='Locatie'])[$i]/*[local-name()='naam']/text()" "$file" 2>/dev/null || true)

    # basisgeo:id
    id=$(xmllint --xpath "(//*[local-name()='Locatie'])[$i]//*[local-name()='Geometrie']/*[local-name()='id']/text()" "$file" 2>/dev/null || true)

    # basisgeo:geometrie
    geometry=$(xmllint --xpath "(//*[local-name()='Locatie'])[$i]//*[local-name()='Geometrie']/*[local-name()='geometrie']" "$file" 2>/dev/null \
        | xmllint --c14n - 2>/dev/null || true)

    geom_hash=$(printf "%s" "$geometry" | md5sum | awk '{print $1}')

    # --------------------------------
    # Regel 3: zelfde naam => zelfde id
    # --------------------------------
    if [[ -v ID_BY_NAME["$name"] ]]; then
      if [[ "${ID_BY_NAME[$name]}" != "$id" ]]; then
        log_err "geo:naam '$name' heeft meerdere id's"
        FAIL=1
      else
        log_ok "\tgeo:naam <=> basisgeo:id (%s)" "$name"
      fi
    else
      ID_BY_NAME["$name"]="$id"
      log_ok "\tgeo:naam => %s" "$name"
    fi

    # --------------------------------
    # Regel 4: zelfde id => zelfde geometrie
    # --------------------------------
    if [[ -v GEOM_BY_ID["$id"] ]]; then
      if [[ "${GEOM_BY_ID[$id]}" != "$geom_hash" ]]; then
        log_err "basisgeo:id '$id' heeft verschillende geometrie tussen bestanden"
        FAIL=1
      else
        log_ok "basisgeo:id <=> basisgeo:geometrie ($id)"
      fi
    else
      GEOM_BY_ID["$id"]="$geom_hash"
      log_ok "basisgeo:id => $id"
    fi
  done
}

# --------------------------------
# Run over all files
# --------------------------------
for file in "$@"; do
  echo ""
  echo "== Valideer: $file =="
  validate_file "$file"
done

echo ""
echo "---------------------------"

if [[ "$FAIL" -eq 0 ]]; then
  echo "VALIDATIE SUCCESVOL"
  exit 0
else
  echo "VALIDATIE MISLUKT"
  exit 1
fi
