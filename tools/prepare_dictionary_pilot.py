"""Build a reproducible, attributed pilot from the Vietnamese Wiktionary raw dump.

Standard library only. Streams gzip/JSONL; never connects to an application database.
Redistributed lexical text is CC BY-SA 4.0; application source licensing is unchanged.
"""
import argparse
import collections
import datetime
import gzip
import hashlib
import json
import pathlib
import re
import unicodedata
import urllib.request

URL = "https://kaikki.org/viwiktionary/raw-wiktextract-data.jsonl.gz"
INDEX = "https://kaikki.org/viwiktionary/rawdata.html"
ANCHORS = {"bàn", "ăn", "đi", "nước", "đường", "nhà", "học", "đẹp", "làm", "một", "tiếng Việt", "vân vân"}
POS = {"noun", "verb", "adj", "adv", "pron", "num", "particle", "conj", "prep", "intj", "phrase", "proverb", "name", "abbrev", "det", "prefix", "suffix", "affix", "classifier", "counter", "character", "romanization"}


def normalize(text):
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", text)).strip()


def sanitized(entry, counts):
    keep = {key: value for key, value in entry.items() if key in {
        "word", "lang", "lang_code", "pos", "pos_title", "senses", "translations",
        "synonyms", "antonyms", "related", "derived", "notes", "etymology_texts", "etymology_number"
    }}
    keep["senses"] = json.loads(json.dumps(keep.get("senses", [])))
    for sense in keep["senses"]:
        examples = []
        for example in sense.get("examples", []):
            if example.get("ref") or example.get("reference") or example.get("source"):
                counts["quotedExamplesOmitted"] += 1
                continue
            examples.append({key: value for key, value in example.items() if key in {"text", "translation", "bold_text_offsets"}})
        if "examples" in sense:
            sense["examples"] = examples
    keep["sounds"] = [{key: sound[key] for key in ("ipa", "tags", "raw_tags") if key in sound}
                      for sound in entry.get("sounds", []) if sound.get("ipa")]
    counts["mediaOmitted"] += sum(bool(s.get("audio") or s.get("mp3_url") or s.get("ogg_url")) for s in entry.get("sounds", []))
    keep["_original_record_sha256"] = hashlib.sha256(json.dumps(entry, ensure_ascii=False, sort_keys=True).encode()).hexdigest()
    return keep


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dump", type=pathlib.Path, default=pathlib.Path(".verification/dictionary-raw.jsonl.gz"))
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("src/main/resources/dictionary"))
    parser.add_argument("--groups", type=int, default=200)
    args = parser.parse_args()
    if not 100 <= args.groups <= 300:
        parser.error("pilot must contain 100–300 lemma/POS groups")
    with urllib.request.urlopen(INDEX, timeout=60) as response:
        metadata = response.read().decode()
    dump_date = re.search(r"dump.*?dated\s+(\d{4}-\d{2}-\d{2})", metadata, re.S).group(1)
    extracted = re.search(r"extracted on\s+(\d{4}-\d{2}-\d{2})", metadata).group(1)
    args.dump.parent.mkdir(parents=True, exist_ok=True)
    cache_metadata = args.dump.with_suffix(".metadata.json")
    cached = json.loads(cache_metadata.read_text()) if cache_metadata.exists() else {}
    if not args.dump.exists() or cached.get("dumpDate") != dump_date or cached.get("extractedAt") != extracted:
        request = urllib.request.Request(URL, headers={"User-Agent": "HolaVietnamese-dictionary-pilot/1.0 (educational dataset preparation)"})
        with urllib.request.urlopen(request, timeout=120) as response, args.dump.open("wb") as target:
            while chunk := response.read(1024 * 1024):
                target.write(chunk)
        cache_metadata.write_text(json.dumps({"dumpDate": dump_date, "extractedAt": extracted}), encoding="utf-8")
    counts = collections.Counter()
    selected = collections.OrderedDict()
    anchor_entries = collections.OrderedDict()
    seen_records = set()
    first = []
    with gzip.open(args.dump, "rt", encoding="utf-8") as stream:
        for line in stream:
            counts["recordsRead"] += 1
            try:
                entry = json.loads(line)
            except (ValueError, TypeError):
                counts["invalidJson"] += 1
                continue
            if entry.get("lang_code") != "vi":
                counts["otherLanguages"] += 1
                continue
            counts["vietnameseRecords"] += 1
            word = normalize(entry.get("word", ""))
            pos = entry.get("pos", "")
            if not word or len(word) > 200 or ":" in word or any(unicodedata.category(c) == "Cc" for c in word):
                counts["invalidHeadwords"] += 1
                continue
            if pos not in POS:
                counts["missingOrUnsupportedPos"] += 1
                continue
            if not entry.get("senses") or any(not any(g.strip() for g in s.get("glosses", [])) for s in entry["senses"]):
                counts["missingDefinitions"] += 1
                continue
            counts["validRecords"] += 1
            key = (word.casefold(), pos.casefold())
            fingerprint = hashlib.sha256(line.encode()).hexdigest()
            if fingerprint in seen_records:
                counts["duplicateRecords"] += 1
                continue
            seen_records.add(fingerprint)
            target = anchor_entries if word in ANCHORS else selected
            if key not in target and (word in ANCHORS or len(selected) < args.groups):
                target[key] = []
            if key in target:
                target[key].append(entry)
            if len(first) < 3:
                first.append({"word": word, "pos": pos, "senses": len(entry["senses"])})
    groups = list(anchor_entries.items()) + [(key, value) for key, value in selected.items() if key not in anchor_entries]
    groups = groups[:args.groups]
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / f"viwiktionary-{dump_date.replace('-', '')}.pilot.jsonl"
    with path.open("w", encoding="utf-8", newline="\n") as output:
        for _, records in groups:
            for entry in records:
                output.write(json.dumps(sanitized(entry, counts), ensure_ascii=False, separators=(",", ":")) + "\n")
    sha = hashlib.sha256(path.read_bytes()).hexdigest()
    with args.dump.open("rb") as archive:
        archive_sha = hashlib.file_digest(archive, "sha256").hexdigest()
    manifest = {
        "id": f"viwiktionary-{dump_date}", "name": "Wiktionary tiếng Việt · Kaikki/Wiktextract",
        "edition": "viwiktionary", "languageCode": "vi", "glossLanguage": "vi",
        "downloadUrl": URL, "catalogUrl": INDEX, "dumpDate": dump_date, "extractedAt": extracted,
        "retrievedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "rawArchiveSha256": archive_sha,
        "acceptedFiles": [{"filename": path.name, "sha256": sha}],
        "license": "CC-BY-SA-4.0", "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
        "copyrightUrl": "https://vi.wiktionary.org/wiki/Wiktionary:Quy%E1%BB%81n_t%C3%A1c_gi%E1%BA%A3",
        "attribution": "Wiktionary tiếng Việt contributors; extracted by Wiktextract and distributed by Kaikki.org. Each entry links to its original page and contributor history.",
        "modifications": "Vietnamese pilot selection; non-lexical metadata omitted; cited/externally attributed examples and audio/images excluded. Import normalizes Unicode NFC/whitespace and groups lemma/POS while retaining all supplied senses. No machine-generated definitions, translations or CEFR labels.",
        "reviewStatus": "PENDING_REVIEW", "pilotGroups": len(groups),
        "pilotRecords": sum(len(records) for _, records in groups), "counts": dict(counts),
        "qualityReview": "Structural validation only. Definitions, sense alignment, register and suitability still require human editorial review before publication."
    }
    (args.output / "source-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output / "LICENSE.txt").write_text(
        "Dictionary pilot lexical text: CC BY-SA 4.0, https://creativecommons.org/licenses/by-sa/4.0/\n"
        "Copyright: Vietnamese Wiktionary contributors. Each entry is attributed through its original page and history.\n"
        "Extraction: Wiktextract / Kaikki.org. Snapshot and hashes: source-manifest.json.\n"
        "Selection/normalization and omitted media/quoted examples: see modifications in the manifest.\n"
        "Redistributed adaptations of this lexical content must retain attribution, change notices and CC BY-SA 4.0.\n"
        "The pilot has NOT received human editorial approval. Application source licensing is unchanged.\n", encoding="utf-8")
    print(json.dumps({"file": str(path), "sha256": sha, "groups": len(groups), "records": manifest["pilotRecords"], "counts": dict(counts), "sample": first}, ensure_ascii=False))


if __name__ == "__main__":
    main()
