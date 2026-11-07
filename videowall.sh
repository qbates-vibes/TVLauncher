#!/bin/bash
# Turns a video into a seamless wallpaper loop, encoded the way the TV plays best.
#
#   ./videowall.sh INPUT [--length SECONDS] [--start SECONDS] [--push]
#
#   --length SECONDS   how long the loop is (default 120)
#   --start SECONDS    where in INPUT the loop begins (default 0)
#   --push             copy the result to the TV afterwards
#
# The result goes in media/ (which git ignores) as NAME-loop.mp4.
#
# What it does, and why (see README, "Getting the best video quality"):
#   - Keeps the video's own resolution, up to 4K. It never upscales: that only adds file size.
#   - HEVC (H.265): about half the size of H.264 for the same picture, decoded in hardware by the TV.
#     8-bit, which plays on every TV and streaming stick; downloaded videos are almost always 8-bit anyway.
#   - Embeds a thumbnail picture: the TV can't make its own thumbnails of 4K videos.
#   - Quality-based encoding (CRF 20) with a 40 Mbps ceiling, which the TV's 4K decoder handles easily.
#   - Crossfades the last 2 seconds into the first 2, so the loop has no visible jump.
#   - AAC stereo sound at 160 kbps.
set -euo pipefail
cd "$(dirname "$0")"

LENGTH=120
START=0
PUSH=
INPUT=
while [ $# -gt 0 ]; do
  case "$1" in
    --length) LENGTH="$2"; shift ;;
    --start) START="$2"; shift ;;
    --push) PUSH=1 ;;
    -*) echo "Unknown option $1"; exit 1 ;;
    *) INPUT="$1" ;;
  esac
  shift
done
[ -n "$INPUT" ] && [ -f "$INPUT" ] || { echo "Usage: ./videowall.sh INPUT [--length SECONDS] [--start SECONDS] [--push]"; exit 1; }
command -v ffmpeg >/dev/null || { echo "ffmpeg is needed: brew install ffmpeg"; exit 1; }

FADE=2
DURATION=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$INPUT")
HEIGHT=$(ffprobe -v error -select_streams v:0 -show_entries stream=height -of csv=p=0 "$INPUT")
HAS_AUDIO=$(ffprobe -v error -select_streams a -show_entries stream=index -of csv=p=0 "$INPUT" | head -1)

# The loop needs LENGTH seconds plus FADE more to blend into its start.
NEEDED=$(echo "$START + $LENGTH + $FADE" | bc)
if [ "$(echo "$NEEDED > $DURATION" | bc)" = 1 ]; then
  LENGTH=$(echo "$DURATION - $START - $FADE" | bc | cut -d. -f1)
  echo "The video is too short for that; making a ${LENGTH}-second loop instead."
fi
TAKE=$(echo "$LENGTH + $FADE" | bc)

if [ "$HEIGHT" -ge 2160 ]; then TARGET=2160
elif [ "$HEIGHT" -ge 1440 ]; then TARGET=1440
elif [ "$HEIGHT" -ge 1080 ]; then TARGET=1080
else
  TARGET=$HEIGHT
  echo "Note: this video is only ${HEIGHT}p. It will loop smoothly, but it can't look sharper than it is;"
  echo "a 1080p or 4K original of the same video will look much better."
fi

NAME=$(basename "${INPUT%.*}")
mkdir -p media
OUTPUT="media/${NAME}-loop.mp4"

# head = the loop itself; tail = the FADE seconds just after it, faded out over the head's start.
# At the end of the loop the picture is exactly where the start of the loop began fading in from.
VIDEO="[0:v]scale=-2:${TARGET}:flags=lanczos,setsar=1,split[a][b];
[a]trim=0:${LENGTH},setpts=PTS-STARTPTS[head];
[b]trim=${LENGTH}:${TAKE},setpts=PTS-STARTPTS[tail];
[tail][head]xfade=transition=fade:duration=${FADE}:offset=0,format=yuv420p[v]"
MAPS=(-map "[v]")
if [ -n "$HAS_AUDIO" ]; then
  AUDIO="[0:a]asplit[c][d];
[c]atrim=0:${LENGTH},asetpts=PTS-STARTPTS[ahead];
[d]atrim=${LENGTH}:${TAKE},asetpts=PTS-STARTPTS[atail];
[atail][ahead]acrossfade=d=${FADE}[aout]"
  FILTER="$VIDEO;$AUDIO"
  MAPS+=(-map "[aout]" -c:a aac -b:a 160k -ac 2)
else
  FILTER="$VIDEO"
fi

echo "Making a ${LENGTH}-second ${TARGET}p loop from $INPUT. 4K takes a while (often 10-30 minutes)..."
ffmpeg -hide_banner -loglevel error -stats -y \
  -ss "$START" -t "$TAKE" -i "$INPUT" \
  -filter_complex "$FILTER" "${MAPS[@]}" \
  -c:v libx265 -preset medium -crf 20 -profile:v main -tag:v hvc1 \
  -x265-params "log-level=error:vbv-maxrate=40000:vbv-bufsize=80000" \
  -movflags +faststart \
  "$OUTPUT.encoding.mp4"

# Embed a frame from a second in as the thumbnail Launcher settings shows.
ffmpeg -hide_banner -loglevel error -y -ss 1 -i "$OUTPUT.encoding.mp4" -frames:v 1 -vf scale=640:-2 "$OUTPUT.thumb.jpg"
ffmpeg -hide_banner -loglevel error -y -i "$OUTPUT.encoding.mp4" -i "$OUTPUT.thumb.jpg" \
  -map 0 -map 1 -c copy -disposition:v:1 attached_pic -movflags +faststart "$OUTPUT"
rm -f "$OUTPUT.encoding.mp4" "$OUTPUT.thumb.jpg"

SIZE=$(du -m "$OUTPUT" | cut -f1)
RATE=$(ffprobe -v error -show_entries format=bit_rate -of csv=p=0 "$OUTPUT")
echo "Saved $OUTPUT: ${SIZE} MB, $((RATE / 1000000)) Mbps on average."

if [ -n "$PUSH" ]; then
  adb push "$OUTPUT" /sdcard/Android/data/com.simple.tvlauncher/files/
  echo "Copied to the TV. Choose it under Launcher settings > Wallpaper picture or video."
fi
