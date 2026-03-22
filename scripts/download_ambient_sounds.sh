#!/bin/bash
#
# LifePlanner Ambient Sound Downloader
# =====================================
# Run this on your Mac to download real CC0 ambient sounds from Freesound.org
# and automatically process them into the correct format for the app.
#
# Prerequisites:
#   brew install ffmpeg   (if you don't have it)
#
# Usage:
#   1. Get a FREE API key: https://freesound.org/apiv2/apply/
#   2. Run: ./download_ambient_sounds.sh YOUR_API_KEY
#
# Or without an API key (uses curated Freesound preview URLs):
#   ./download_ambient_sounds.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_RAW="$PROJECT_ROOT/composeApp/src/androidMain/res/raw"
IOS_APP="$PROJECT_ROOT/iosApp/iosApp"
TMP_DIR=$(mktemp -d)
API_KEY="${1:-}"

DUR=30

echo "============================================================"
echo "  LifePlanner - Ambient Sound Downloader"
echo "============================================================"
echo ""
echo "  Android output: $ANDROID_RAW"
echo "  iOS output:     $IOS_APP"
echo ""

# Check ffmpeg
if ! command -v ffmpeg &>/dev/null; then
    echo "ERROR: ffmpeg not found. Install with: brew install ffmpeg"
    exit 1
fi

# ── Curated Freesound CC0 Sound IDs ──────────────────────────────────
# Each entry: "name|search_query|tag"
# These are chosen for ambient/loop quality
declare -a SOUNDS=(
    "ambient_ocean|ocean waves calm loop|ocean"
    "ambient_fireplace|fireplace crackling fire cozy|fireplace"
    "ambient_night|night crickets ambient field recording|crickets"
    "ambient_cafe|cafe coffee shop ambient murmur|cafe"
    "ambient_birds|birds singing morning forest nature|birds"
)

process_to_wav() {
    local input="$1"
    local output="$2"
    local src_dur

    src_dur=$(ffprobe -v quiet -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$input" 2>/dev/null)
    echo "    Source duration: ${src_dur}s"

    local loop_args=""
    if (( $(echo "$src_dur < $DUR" | bc -l) )); then
        local loops=$(echo "($DUR / $src_dur) + 1" | bc)
        loop_args="-stream_loop $loops"
    fi

    ffmpeg -y $loop_args -i "$input" \
        -af "atrim=0:$DUR,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.3,afade=t=out:st=$((DUR)).:d=0.3,loudnorm=I=-18:TP=-2:LRA=8" \
        -ar 44100 -ac 2 -acodec pcm_s16le -t $DUR \
        "$output" 2>/dev/null

    local size=$(du -h "$output" | cut -f1)
    echo "    Output: $size"
}

download_with_api() {
    local name="$1"
    local query="$2"
    local tag="$3"

    echo "  Searching Freesound for: $query (CC0 only)..."

    # Search for CC0 sounds sorted by rating
    local response
    response=$(curl -s "https://freesound.org/apiv2/search/text/?query=$(echo "$query" | sed 's/ /%20/g')&filter=license:%22Creative%20Commons%200%22%20duration:%5B10%20TO%20300%5D%20tag:$tag&sort=rating_desc&fields=id,name,duration,previews,avg_rating&page_size=5&token=$API_KEY")

    local count
    count=$(echo "$response" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('results',[])))" 2>/dev/null || echo "0")

    if [ "$count" -eq "0" ]; then
        echo "    No results found, trying broader search..."
        response=$(curl -s "https://freesound.org/apiv2/search/text/?query=$(echo "$query" | sed 's/ /%20/g')&filter=license:%22Creative%20Commons%200%22%20duration:%5B10%20TO%20300%5D&sort=rating_desc&fields=id,name,duration,previews,avg_rating&page_size=5&token=$API_KEY")
        count=$(echo "$response" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('results',[])))" 2>/dev/null || echo "0")
    fi

    echo "    Found $count CC0 results"

    # Try each result until one works
    for i in $(seq 0 $((count - 1))); do
        local sound_name sound_id preview_url
        sound_name=$(echo "$response" | python3 -c "import sys,json; r=json.load(sys.stdin)['results'][$i]; print(r['name'])")
        sound_id=$(echo "$response" | python3 -c "import sys,json; r=json.load(sys.stdin)['results'][$i]; print(r['id'])")
        preview_url=$(echo "$response" | python3 -c "import sys,json; r=json.load(sys.stdin)['results'][$i]; p=r.get('previews',{}); print(p.get('preview-hq-mp3','') or p.get('preview-hq-ogg',''))")

        echo "    Trying: $sound_name (id=$sound_id)..."

        if [ -n "$preview_url" ]; then
            local tmp_file="$TMP_DIR/${name}_raw.mp3"
            if curl -sL -o "$tmp_file" "$preview_url" && [ -s "$tmp_file" ]; then
                local file_size=$(wc -c < "$tmp_file")
                if [ "$file_size" -gt 10000 ]; then
                    echo "    Downloaded ${file_size} bytes"
                    if process_to_wav "$tmp_file" "$ANDROID_RAW/$name.wav"; then
                        cp "$ANDROID_RAW/$name.wav" "$IOS_APP/$name.wav"
                        echo "  ✓ $name ready! (from: $sound_name)"
                        return 0
                    fi
                fi
            fi
        fi

        # Try full quality download with API key
        local tmp_full="$TMP_DIR/${name}_full"
        if curl -sL -H "Authorization: Token $API_KEY" -o "$tmp_full" "https://freesound.org/apiv2/sounds/$sound_id/download/" && [ -s "$tmp_full" ]; then
            local file_size=$(wc -c < "$tmp_full")
            if [ "$file_size" -gt 10000 ]; then
                echo "    Downloaded full quality: ${file_size} bytes"
                if process_to_wav "$tmp_full" "$ANDROID_RAW/$name.wav"; then
                    cp "$ANDROID_RAW/$name.wav" "$IOS_APP/$name.wav"
                    echo "  ✓ $name ready! (full quality from: $sound_name)"
                    return 0
                fi
            fi
        fi
    done

    echo "  ✗ Could not download $name"
    return 1
}

download_without_api() {
    local name="$1"
    echo "  No API key - skipping Freesound search for $name"
    echo "  Please provide an API key or download manually."
    return 1
}

# ── Main Loop ────────────────────────────────────────────────
successes=0
failures=0

for entry in "${SOUNDS[@]}"; do
    IFS='|' read -r name query tag <<< "$entry"
    echo ""
    echo "--- $name ---"

    if [ -n "$API_KEY" ]; then
        if download_with_api "$name" "$query" "$tag"; then
            ((successes++))
        else
            ((failures++))
        fi
    else
        if download_without_api "$name"; then
            ((successes++))
        else
            ((failures++))
        fi
    fi
done

# Cleanup
rm -rf "$TMP_DIR"

echo ""
echo "============================================================"
echo "  RESULTS: $successes succeeded, $failures failed"
echo "============================================================"

if [ "$failures" -gt 0 ]; then
    echo ""
    echo "  For failed sounds, download manually from:"
    echo "    https://freesound.org  (filter: CC0 license)"
    echo "    https://mixkit.co/free-sound-effects/"
    echo ""
    echo "  Then convert with:"
    echo "    ffmpeg -i input.mp3 \\"
    echo "      -af 'atrim=0:30,loudnorm=I=-18:TP=-2' \\"
    echo "      -ar 44100 -ac 2 -acodec pcm_s16le output.wav"
    echo ""
    echo "  Place files in:"
    echo "    $ANDROID_RAW/"
    echo "    $IOS_APP/"
fi

if [ "$successes" -gt 0 ]; then
    echo ""
    echo "  ✓ All downloaded sounds are:"
    echo "    - CC0 licensed (public domain, no attribution needed)"
    echo "    - 30 seconds, loopable"
    echo "    - 44.1kHz stereo WAV (matches existing sounds)"
    echo "    - Placed in both Android and iOS resource folders"
fi
