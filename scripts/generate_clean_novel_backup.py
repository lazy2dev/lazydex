import zipfile
import json
import os

src_path = r"C:\Users\lazyman\.gemini\antigravity\brain\1575a553-7933-429e-ae17-1d4184aea0d2\.user_uploaded\media_1795374574119_b9830bfa.lazydex"
out_dir = r"C:\Users\lazyman\Downloads"
out_path = os.path.join(out_dir, "lazydex_novels_clean.lazydex")

print(f"Reading from {src_path}...")
with zipfile.ZipFile(src_path, "r") as src_zip:
    raw_json = src_zip.read("backup.json").decode("utf-8-sig")
    data = json.loads(raw_json)
    
    orig_items = data.get("items", [])
    print(f"Total items in source backup: {len(orig_items)}")
    
    # Status normalization map
    status_map = {
        "READING": "CURRENT",
        "IN_PROGRESS": "CURRENT",
        "CURRENT": "CURRENT",
        "PLAN_TO": "PLANNING",
        "PLANNING": "PLANNING",
        "ON_HOLD": "PAUSED",
        "PAUSED": "PAUSED",
        "COMPLETED": "COMPLETED",
        "DROPPED": "DROPPED",
        "REPEATING": "REPEATING"
    }

    novels = []
    novel_ids = set()
    
    for item in orig_items:
        item_type = (item.get("type") or "").upper()
        item_category = (item.get("category") or "").upper()
        
        # Keep only NOVEL items
        if item_type == "NOVEL" or item_category == "NOVEL":
            novel = dict(item)
            
            # Ensure proper type and format
            novel["type"] = "NOVEL"
            if not novel.get("format"):
                novel["format"] = "WEB_NOVEL"
            if not novel.get("countryOfOrigin"):
                novel["countryOfOrigin"] = "CN"
            
            # Normalize status to standard enum name
            raw_status = (novel.get("userStatus") or "CURRENT").upper()
            novel["userStatus"] = status_map.get(raw_status, "CURRENT")
            
            # Ensure category & categoryId for backward compatibility
            novel["category"] = "NOVEL"
            if not novel.get("categoryId"):
                novel["categoryId"] = "typ_novel_cn"
            
            # Ensure double progress values match current progress
            cur_prog = novel.get("currentProgress", 0)
            if "progress" not in novel:
                novel["progress"] = float(cur_prog)
            
            tot = novel.get("totalItems")
            if tot is not None and "progressTotal" not in novel:
                novel["progressTotal"] = float(tot)
            
            if "secondaryProgress" not in novel:
                novel["secondaryProgress"] = 0.0
            
            if "repeat" not in novel:
                novel["repeat"] = 0
            
            if "notes" not in novel:
                novel["notes"] = ""
            
            if "extraData" not in novel:
                novel["extraData"] = "{}"
            
            if "isDeleted" not in novel:
                novel["isDeleted"] = False
            
            if "isAdult" not in novel:
                novel["isAdult"] = False
            
            # Rating to scoreRaw if needed
            rating = novel.get("rating")
            if rating is not None and "scoreRaw" not in novel:
                novel["scoreRaw"] = int(round(rating * 20.0))
            
            novels.append(novel)
            novel_ids.add(novel["id"])
    
    print(f"Total novels to export: {len(novels)}")
    
    # Build clean envelope
    clean_envelope = {
        "schemaVersion": 3,
        "items": novels
    }
    
    # Write to destination zip
    os.makedirs(out_dir, exist_ok=True)
    with zipfile.ZipFile(out_path, "w", compression=zipfile.ZIP_DEFLATED) as out_zip:
        # Write backup.json with pretty indentation
        out_zip.writestr("backup.json", json.dumps(clean_envelope, ensure_ascii=False, indent=2))
        
        # Copy matching covers
        cover_count = 0
        for entry in src_zip.namelist():
            if entry.startswith("covers/"):
                cid = entry.replace("covers/", "")
                if cid in novel_ids:
                    cover_bytes = src_zip.read(entry)
                    out_zip.writestr(entry, cover_bytes)
                    cover_count += 1
    
    print(f"Wrote clean backup to: {out_path}")
    print(f"Novels: {len(novels)}, Covers copied: {cover_count}")

    # Also make a copy in brain directory for artifacts
    brain_copy = r"C:\Users\lazyman\.gemini\antigravity\brain\1575a553-7933-429e-ae17-1d4184aea0d2\lazydex_novels_clean.lazydex"
    with open(out_path, "rb") as sf, open(brain_copy, "wb") as df:
        df.write(sf.read())
    print(f"Copied to brain: {brain_copy}")
