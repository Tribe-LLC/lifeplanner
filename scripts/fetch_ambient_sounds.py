#!/usr/bin/env python3
"""
Ambient Sound Fetcher for LifePlanner App
==========================================
Fetches CC0-licensed ambient sounds from Freesound.org API,
processes them into 30-second loopable WAV files suitable for
bundling into the Compose Multiplatform app.

Sources:
  - Primary: Freesound.org (CC0 filter)
  - Fallback: Archive.org (Public Domain)

Usage:
  1. Get a free API key from https://freesound.org/apiv2/apply/
  2. Run: python fetch_ambient_sounds.py --api-key YOUR_KEY
  3. Or set environment variable: FREESOUND_API_KEY=YOUR_KEY
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    import requests
except ImportError:
    print("Install requests: pip install requests")
    sys.exit(1)

# ── Configuration ──────────────────────────────────────────────────────────
FREESOUND_BASE = "https://freesound.org/apiv2"
ARCHIVE_BASE = "https://archive.org"

# Target audio specs (matching existing ambient sounds)
TARGET_SAMPLE_RATE = 44100
TARGET_CHANNELS = 2  # stereo
TARGET_DURATION_SEC = 30
TARGET_FORMAT = "wav"

# Output directory
ANDROID_RAW = Path(__file__).parent.parent / "composeApp" / "src" / "androidMain" / "res" / "raw"

# Sound categories to fetch - mapped to new AmbientSound enum values
SOUND_QUERIES = {
    "ambient_ocean": {
        "freesound_query": "ocean waves ambient loop",
        "freesound_tags": "ocean,waves,water,ambient",
        "archive_query": "ocean waves ambient",
        "description": "Ocean waves crashing and receding",
        "min_duration": 15,  # minimum source duration in seconds
    },
    "ambient_fireplace": {
        "freesound_query": "fireplace crackling fire loop",
        "freesound_tags": "fireplace,fire,crackling,ambient",
        "archive_query": "fireplace crackling ambient",
        "description": "Cozy fireplace crackling sounds",
        "min_duration": 15,
    },
    "ambient_night": {
        "freesound_query": "night crickets ambient nature",
        "freesound_tags": "night,crickets,ambient,nature",
        "archive_query": "night crickets ambient nature",
        "description": "Nighttime crickets and gentle wind",
        "min_duration": 15,
    },
    "ambient_cafe": {
        "freesound_query": "cafe coffee shop ambient background chatter",
        "freesound_tags": "cafe,coffee,ambient,chatter",
        "archive_query": "cafe ambient background noise",
        "description": "Cozy cafe background ambience",
        "min_duration": 15,
    },
    "ambient_birds": {
        "freesound_query": "birds singing morning nature ambient",
        "freesound_tags": "birds,singing,nature,ambient,morning",
        "archive_query": "birds singing morning nature",
        "description": "Morning birdsong in nature",
        "min_duration": 15,
    },
}


def search_freesound(api_key: str, query: str, tags: str, min_duration: float = 15) -> list:
    """Search Freesound for CC0-licensed sounds."""
    params = {
        "query": query,
        "filter": f"license:\"Creative Commons 0\" duration:[{min_duration} TO 300]",
        "sort": "rating_desc",
        "fields": "id,name,duration,previews,download,license,avg_rating,num_ratings,tags,description",
        "page_size": 10,
        "token": api_key,
    }
    if tags:
        params["filter"] += f" tag:{tags.split(',')[0]}"

    try:
        resp = requests.get(f"{FREESOUND_BASE}/search/text/", params=params, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        results = data.get("results", [])
        print(f"  Found {len(results)} CC0 results for '{query}'")
        return results
    except Exception as e:
        print(f"  Freesound search error: {e}")
        return []


def download_freesound_preview(sound: dict, output_path: Path) -> bool:
    """Download the HQ preview from Freesound (no auth needed for previews)."""
    previews = sound.get("previews", {})
    # Prefer high-quality OGG, then MP3
    url = previews.get("preview-hq-ogg") or previews.get("preview-hq-mp3")
    if not url:
        print(f"  No preview URL for sound {sound['id']}")
        return False

    try:
        print(f"  Downloading preview: {sound['name']} (id={sound['id']}, {sound['duration']:.1f}s)")
        resp = requests.get(url, timeout=60)
        resp.raise_for_status()
        with open(output_path, "wb") as f:
            f.write(resp.content)
        return True
    except Exception as e:
        print(f"  Download error: {e}")
        return False


def download_freesound_full(api_key: str, sound_id: int, output_path: Path) -> bool:
    """Download the full quality sound from Freesound (requires auth)."""
    url = f"{FREESOUND_BASE}/sounds/{sound_id}/download/"
    headers = {"Authorization": f"Token {api_key}"}

    try:
        print(f"  Downloading full quality sound {sound_id}...")
        resp = requests.get(url, headers=headers, timeout=120)
        resp.raise_for_status()
        with open(output_path, "wb") as f:
            f.write(resp.content)
        return True
    except Exception as e:
        print(f"  Full download error (using preview instead): {e}")
        return False


def search_archive_org(query: str) -> list:
    """Search Archive.org for public domain audio."""
    params = {
        "q": f"{query} AND mediatype:audio AND licenseurl:*publicdomain*",
        "fl[]": ["identifier", "title", "description", "avg_rating"],
        "sort[]": "avg_rating desc",
        "rows": 5,
        "output": "json",
    }

    try:
        resp = requests.get(f"{ARCHIVE_BASE}/advancedsearch.php", params=params, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        docs = data.get("response", {}).get("docs", [])
        print(f"  Found {len(docs)} Archive.org results for '{query}'")
        return docs
    except Exception as e:
        print(f"  Archive.org search error: {e}")
        return []


def process_audio(input_path: Path, output_path: Path, duration_sec: int = TARGET_DURATION_SEC) -> bool:
    """
    Process audio file into target format:
    - Convert to WAV (PCM 16-bit)
    - Resample to 44100 Hz stereo
    - Trim/loop to exactly 30 seconds
    - Apply fade in/out for seamless looping
    """
    try:
        # Get input duration
        probe = subprocess.run(
            ["ffprobe", "-v", "quiet", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", str(input_path)],
            capture_output=True, text=True, timeout=30
        )
        src_duration = float(probe.stdout.strip())
        print(f"  Source duration: {src_duration:.1f}s")

        # Build ffmpeg command
        filters = []

        if src_duration < duration_sec:
            # Loop the audio to reach target duration
            loop_count = int(duration_sec / src_duration) + 1
            cmd = [
                "ffmpeg", "-y", "-stream_loop", str(loop_count),
                "-i", str(input_path),
            ]
        else:
            cmd = ["ffmpeg", "-y", "-i", str(input_path)]

        # Audio filters: trim, fade, normalize
        filters.extend([
            f"atrim=0:{duration_sec}",        # Trim to target duration
            f"asetpts=PTS-STARTPTS",           # Reset timestamps
            f"afade=t=in:st=0:d=0.5",         # 0.5s fade in
            f"afade=t=out:st={duration_sec-0.5}:d=0.5",  # 0.5s fade out
            f"loudnorm=I=-16:TP=-1.5:LRA=11", # Normalize loudness
        ])

        cmd.extend([
            "-af", ",".join(filters),
            "-ar", str(TARGET_SAMPLE_RATE),
            "-ac", str(TARGET_CHANNELS),
            "-acodec", "pcm_s16le",  # 16-bit PCM WAV
            "-t", str(duration_sec),
            str(output_path)
        ])

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        if result.returncode != 0:
            print(f"  ffmpeg error: {result.stderr[-500:]}")
            return False

        # Verify output
        file_size = output_path.stat().st_size
        print(f"  Output: {output_path.name} ({file_size / 1024:.0f} KB)")
        return True

    except Exception as e:
        print(f"  Processing error: {e}")
        return False


def fetch_and_process_sound(api_key: str, sound_name: str, config: dict, output_dir: Path) -> bool:
    """Fetch a sound from available sources and process it."""
    output_file = output_dir / f"{sound_name}.wav"

    if output_file.exists():
        print(f"\n✓ {sound_name}.wav already exists, skipping")
        return True

    print(f"\n{'='*60}")
    print(f"Fetching: {sound_name} - {config['description']}")
    print(f"{'='*60}")

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_path = Path(tmpdir)
        raw_file = tmp_path / f"{sound_name}_raw"

        # Try Freesound first
        if api_key:
            results = search_freesound(
                api_key,
                config["freesound_query"],
                config["freesound_tags"],
                config["min_duration"]
            )

            for sound in results[:3]:  # Try top 3 results
                raw_ext = ".ogg"
                raw_with_ext = raw_file.with_suffix(raw_ext)

                # Try full download first, then preview
                if download_freesound_full(api_key, sound["id"], raw_with_ext):
                    if process_audio(raw_with_ext, output_file):
                        return True
                elif download_freesound_preview(sound, raw_with_ext):
                    if process_audio(raw_with_ext, output_file):
                        return True

        # Fallback: Archive.org
        print(f"\n  Trying Archive.org fallback...")
        archive_results = search_archive_org(config["archive_query"])
        for item in archive_results[:2]:
            identifier = item.get("identifier")
            if identifier:
                # Try to get audio files from the item
                try:
                    meta_resp = requests.get(
                        f"{ARCHIVE_BASE}/metadata/{identifier}",
                        timeout=30
                    )
                    meta_resp.raise_for_status()
                    files = meta_resp.json().get("files", [])

                    for f in files:
                        if f.get("format") in ("VBR MP3", "Ogg Vorbis", "WAVE", "128Kbps MP3"):
                            audio_url = f"{ARCHIVE_BASE}/download/{identifier}/{f['name']}"
                            ext = Path(f["name"]).suffix or ".mp3"
                            raw_with_ext = raw_file.with_suffix(ext)

                            try:
                                print(f"  Downloading from Archive.org: {f['name']}")
                                resp = requests.get(audio_url, timeout=120)
                                resp.raise_for_status()
                                with open(raw_with_ext, "wb") as fout:
                                    fout.write(resp.content)

                                if process_audio(raw_with_ext, output_file):
                                    return True
                            except Exception as e:
                                print(f"  Archive download error: {e}")
                                continue
                except Exception:
                    continue

    print(f"\n✗ Could not fetch {sound_name}")
    return False


def generate_sound_with_ffmpeg(sound_name: str, output_dir: Path) -> bool:
    """
    Generate ambient sounds using ffmpeg's audio synthesis as ultimate fallback.
    Creates procedural ambient textures that sound decent for placeholder use.
    """
    output_file = output_dir / f"{sound_name}.wav"
    duration = TARGET_DURATION_SEC

    print(f"\n  Generating synthetic {sound_name} with ffmpeg...")

    generators = {
        "ambient_ocean": (
            # Layered brown noise with slow modulation = ocean-like
            f"anoisesrc=d={duration}:c=pink:r={TARGET_SAMPLE_RATE}:a=0.3,"
            f"aformat=sample_fmts=flt,"
            f"lowpass=f=400,"
            f"tremolo=f=0.1:d=0.7,"
            f"afade=t=in:st=0:d=1,afade=t=out:st={duration-1}:d=1"
        ),
        "ambient_fireplace": (
            # Crackle-like: filtered noise bursts
            f"anoisesrc=d={duration}:c=brown:r={TARGET_SAMPLE_RATE}:a=0.4,"
            f"aformat=sample_fmts=flt,"
            f"bandpass=f=2000:w=1500,"
            f"tremolo=f=3:d=0.9,"
            f"volume=0.5,"
            f"afade=t=in:st=0:d=1,afade=t=out:st={duration-1}:d=1"
        ),
        "ambient_night": (
            # Gentle high-frequency noise = crickets-ish
            f"anoisesrc=d={duration}:c=white:r={TARGET_SAMPLE_RATE}:a=0.15,"
            f"aformat=sample_fmts=flt,"
            f"highpass=f=3000,"
            f"lowpass=f=8000,"
            f"tremolo=f=8:d=0.3,"
            f"volume=0.4,"
            f"afade=t=in:st=0:d=1,afade=t=out:st={duration-1}:d=1"
        ),
        "ambient_cafe": (
            # Low rumble with mid-range chatter-like modulation
            f"anoisesrc=d={duration}:c=pink:r={TARGET_SAMPLE_RATE}:a=0.25,"
            f"aformat=sample_fmts=flt,"
            f"bandpass=f=800:w=600,"
            f"tremolo=f=1.5:d=0.4,"
            f"volume=0.5,"
            f"afade=t=in:st=0:d=1,afade=t=out:st={duration-1}:d=1"
        ),
        "ambient_birds": (
            # High-pitched chirp-like modulated noise
            f"anoisesrc=d={duration}:c=white:r={TARGET_SAMPLE_RATE}:a=0.2,"
            f"aformat=sample_fmts=flt,"
            f"highpass=f=2000,"
            f"lowpass=f=6000,"
            f"tremolo=f=5:d=0.6,"
            f"volume=0.4,"
            f"afade=t=in:st=0:d=1,afade=t=out:st={duration-1}:d=1"
        ),
    }

    filter_str = generators.get(sound_name)
    if not filter_str:
        print(f"  No generator for {sound_name}")
        return False

    cmd = [
        "ffmpeg", "-y",
        "-filter_complex", filter_str,
        "-ac", str(TARGET_CHANNELS),
        "-ar", str(TARGET_SAMPLE_RATE),
        "-acodec", "pcm_s16le",
        "-t", str(duration),
        str(output_file)
    ]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode == 0:
            size = output_file.stat().st_size
            print(f"  ✓ Generated: {output_file.name} ({size / 1024:.0f} KB)")
            return True
        else:
            print(f"  ffmpeg error: {result.stderr[-300:]}")
            return False
    except Exception as e:
        print(f"  Generation error: {e}")
        return False


def print_report(results: dict):
    """Print a summary report."""
    print(f"\n{'='*60}")
    print("AMBIENT SOUND FETCH REPORT")
    print(f"{'='*60}")
    for name, status in results.items():
        icon = "✓" if status else "✗"
        print(f"  {icon} {name}")

    success = sum(1 for v in results.values() if v)
    print(f"\n  {success}/{len(results)} sounds ready")

    if success < len(results):
        print("\n  Missing sounds will use synthetic placeholders.")
        print("  For best quality, download CC0 sounds manually from:")
        print("    - https://freesound.org (search with CC0 filter)")
        print("    - https://mixkit.co/free-sound-effects/")
        print("    - https://archive.org/details/audio")


def main():
    parser = argparse.ArgumentParser(description="Fetch ambient sounds for LifePlanner")
    parser.add_argument("--api-key", default=os.environ.get("FREESOUND_API_KEY", ""),
                        help="Freesound API key (or set FREESOUND_API_KEY env var)")
    parser.add_argument("--output", default=str(ANDROID_RAW),
                        help="Output directory for WAV files")
    parser.add_argument("--synth-only", action="store_true",
                        help="Skip API calls, generate synthetic sounds only")
    parser.add_argument("--sounds", nargs="+", choices=list(SOUND_QUERIES.keys()),
                        help="Only fetch specific sounds")
    args = parser.parse_args()

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    sounds_to_fetch = args.sounds or list(SOUND_QUERIES.keys())
    results = {}

    print(f"Output directory: {output_dir}")
    print(f"Sounds to fetch: {', '.join(sounds_to_fetch)}")

    if not args.api_key and not args.synth_only:
        print("\n⚠ No Freesound API key provided.")
        print("  Get one free at: https://freesound.org/apiv2/apply/")
        print("  Falling back to synthetic generation.\n")

    for sound_name in sounds_to_fetch:
        config = SOUND_QUERIES[sound_name]
        success = False

        if not args.synth_only and args.api_key:
            success = fetch_and_process_sound(args.api_key, sound_name, config, output_dir)

        if not success:
            # Generate synthetic placeholder
            success = generate_sound_with_ffmpeg(sound_name, output_dir)

        results[sound_name] = success

    print_report(results)

    # Generate a metadata file for attribution
    metadata = {}
    for name, config in SOUND_QUERIES.items():
        if name in sounds_to_fetch:
            metadata[name] = {
                "description": config["description"],
                "license": "CC0 / Public Domain / Synthetic",
                "loopable": True,
                "duration_sec": TARGET_DURATION_SEC,
                "sample_rate": TARGET_SAMPLE_RATE,
                "channels": TARGET_CHANNELS,
            }

    meta_path = output_dir / "ambient_sounds_metadata.json"
    with open(meta_path, "w") as f:
        json.dump(metadata, f, indent=2)
    print(f"\nMetadata written to: {meta_path}")


if __name__ == "__main__":
    main()
