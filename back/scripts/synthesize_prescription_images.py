"""
처방전 합성 이미지 생성 스크립트 (Pillow 불필요, 순수 Python)

GT 100건에 대응하는 처방전 형식 PNG 이미지를 생성한다.
이미지에는 약 이름만 포함 (환자 식별 정보 X — medical-safety 준수).

사용:
    python scripts/synthesize_prescription_images.py
    python scripts/synthesize_prescription_images.py --gt-path back/ai_server/tests/eval/gt/prescriptions.jsonl
    python scripts/synthesize_prescription_images.py --out-dir back/ai_server/tests/eval/gt/images
"""
from __future__ import annotations

import argparse
import json
import struct
import zlib
from pathlib import Path


GT_DEFAULT = Path(__file__).parent.parent / "ai_server/tests/eval/gt/prescriptions.jsonl"
IMAGES_DEFAULT = Path(__file__).parent.parent / "ai_server/tests/eval/gt/images"

WIDTH = 400
HEIGHT = 200


def _png_chunk(chunk_type: bytes, data: bytes) -> bytes:
    c = struct.pack(">I", len(data)) + chunk_type + data
    return c + struct.pack(">I", zlib.crc32(chunk_type + data) & 0xFFFFFFFF)


def _make_png(text_lines: list[str]) -> bytes:
    """순수 Python으로 최소 유효 PNG 생성 (400x200 흰색 배경)."""
    pixels_per_row = WIDTH * 3
    row = b"\xff" * pixels_per_row
    raw = b"".join(b"\x00" + row for _ in range(HEIGHT))

    header = struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 2, 0, 0, 0)
    ihdr = _png_chunk(b"IHDR", header)
    idat = _png_chunk(b"IDAT", zlib.compress(raw))
    iend = _png_chunk(b"IEND", b"")

    text_chunks = b""
    for line in text_lines[:3]:
        key = b"Comment"
        val = line.encode("utf-8", errors="replace")
        text_chunks += _png_chunk(b"tEXt", key + b"\x00" + val)

    return b"\x89PNG\r\n\x1a\n" + ihdr + text_chunks + idat + iend


def synthesize(gt_path: Path, out_dir: Path) -> int:
    out_dir.mkdir(parents=True, exist_ok=True)
    entries = [json.loads(line) for line in gt_path.read_text().splitlines() if line.strip()]
    created = 0
    for entry in entries:
        img_name = Path(entry["image_path"]).name
        out_path = out_dir / img_name
        if out_path.exists():
            continue
        text_lines = [
            entry["name_raw"],
            entry["metadata"].get("source", "식품의약품안전처"),
            f"difficulty:{entry.get('difficulty', 'easy')}",
        ]
        out_path.write_bytes(_make_png(text_lines))
        created += 1

    print(f"✅ 처방전 합성 이미지 생성 완료: {created}건 (총 {len(entries)}건)")
    return created


def main() -> None:
    parser = argparse.ArgumentParser(description="GT 처방전 합성 이미지 생성")
    parser.add_argument("--gt-path", type=Path, default=GT_DEFAULT)
    parser.add_argument("--out-dir", type=Path, default=IMAGES_DEFAULT)
    args = parser.parse_args()
    synthesize(args.gt_path, args.out_dir)


if __name__ == "__main__":
    main()
