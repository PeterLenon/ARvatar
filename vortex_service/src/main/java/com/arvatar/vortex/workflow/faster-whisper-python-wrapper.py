#!/usr/bin/env python3
from faster_whisper import WhisperModel
import argparse
import json, pathlib

def transcribe():
    parser = argparse.ArgumentParser(description="Parse the argument for the filepath of the audio file to transcribe")
    parser.add_argument("--file", "-f", help="The path of the audio file to transcribe")
    args = parser.parse_args()
    audio = args.file
    model = WhisperModel("base", device="cpu", compute_type="int8")
    segments, info = model.transcribe(audio, language="en", word_timestamps=True)
    out = {"language": info.language, "segments": []}
    for s in segments:
        out["segments"].append({
            "start": s.start, "end": s.end, "text": s.text,
            "words": [{"word": w.word, "start": w.start, "end": w.end} for w in (s.words or [])]
        })
    print(json.dumps(out, indent=2, ensure_ascii=False, sort_keys=True, separators=(",", ": ")))

if __name__ == "__main__":
    transcribe()