"""Exports the books a reader has read on LiveLib to a Goodreads-style CSV that Familiar imports.

python tools/livelib.py <login | user id | profile link> [out.csv]

Only the public "Read" shelf is exported, so the profile must be open. LiveLib gives no page counts,
genres or ISBNs; its ratings out of ten become stars out of five (9 and 10 are five stars), and a date
known only to the month is written as the first of that month.
"""
import csv
import http.cookiejar
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

SITE = "https://www.livelib.ru"
API = "https://beta.api.livelib.ru/trisolaris"  # where the site's own pages get their data
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
PAUSE = 1.5  # seconds between requests, to be gentle with the site
COLUMNS = ["Title", "Author", "ISBN", "My Rating", "Number of Pages", "Year Published",
           "Date Read", "Date Added", "Bookshelves", "Exclusive Shelf", "Cover"]

opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def get(url: str, accept: str = "text/html") -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": accept, "Accept-Language": "ru,en;q=0.8"})
    with opener.open(req, timeout=30) as r:
        return r.read().decode("utf-8")


def shelf_url(who: str) -> str:
    who = who.strip().rstrip("/")
    if m := re.search(r"/users/(\d+)", who):
        return f"{SITE}/users/{m.group(1)}/books/read"
    if m := re.search(r"/reader/([^/?#]+)", who):
        who = m.group(1)
    return f"{SITE}/users/{who}/books/read" if who.isdigit() else f"{SITE}/reader/{who}/read"


def first_page(html: str) -> dict:
    """The reads the page was rendered with: Next.js streams its data as JSON pieces in script tags."""
    text = "".join(json.loads(c) for c in re.findall(r'self\.__next_f\.push\(\[1,(".*?")\]\)</script>', html, re.S))
    dec = json.JSONDecoder()
    for m in re.finditer(r'"queryKey":\["users","detail",\d+,"profile","reads"', text):
        start = text.rfind('"state":{"data":', 0, m.start())
        data, _ = dec.raw_decode(text, start + len('"state":{"data":'))
        return data.get("payload", data)
    raise SystemExit("No Read shelf on that page: is the profile open, and is the name right?")


def page(data: dict) -> dict:
    return data.get("payload", data)


def row(item: dict) -> dict:
    book = item.get("art_edition") or {}
    when = item.get("read_date") or {}
    read = ""
    if when.get("year"):
        read = f"{when['year']:04d}/{when.get('month') or 1:02d}/{when.get('day') or 1:02d}"
    added = (item.get("status_set_at") or "")[:10].replace("-", "/")
    rating = item.get("rating") or 0
    return {
        "Title": book.get("title", "").strip(),
        "Author": ", ".join(a.get("full_name", "") for a in book.get("authors") or []),
        "ISBN": "",
        "My Rating": (rating + 1) // 2 if rating else 0,
        "Number of Pages": "",
        "Year Published": book.get("publication_year") or "",
        "Date Read": read,
        "Date Added": added,
        "Bookshelves": "read",
        "Exclusive Shelf": "read",
        "Cover": book.get("cover_url") or "",
    }


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    out = Path(sys.argv[2] if len(sys.argv) > 2 else "livelib.csv")
    data = first_page(get(shelf_url(sys.argv[1])))
    total = (data.get("counters") or {}).get("all", "?")
    rows = [row(i) for i in data.get("data") or []]
    print(f"{len(rows)} of {total}", end="", flush=True)
    while nxt := (data.get("pagination") or {}).get("next_page"):
        time.sleep(PAUSE)
        data = page(json.loads(get(API + nxt, "application/json")))
        rows += [row(i) for i in data.get("data") or []]
        print(f"\r{len(rows)} of {total}", end="", flush=True)
    print()
    with out.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, COLUMNS)
        w.writeheader()
        w.writerows(rows)
    print(f"{len(rows)} books written to {out}")


if __name__ == "__main__":
    main()
