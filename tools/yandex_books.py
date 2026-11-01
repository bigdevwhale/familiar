"""Exports a reader's books from Yandex Books to a Goodreads-style CSV that Familiar imports.

python tools/yandex_books.py <login | profile link> [out.csv]

The login is the part after @ in the profile link: https://books.yandex.ru/@b4262508732/books/all.
Only what the open profile shows comes across: finished books go to Read, the ones opened lately and not
finished to Reading, the rest to Want to read. Text books, audiobooks and comics all come, with their covers
and the site's topics as genres; Yandex Books shows nobody else the dates, ratings or page counts.
"""
import csv
import http.cookiejar
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

SITE = "https://books.yandex.ru"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
PAUSE = 1.0  # seconds between requests, to be gentle with the site
STEP = 50
COLUMNS = ["Title", "Author", "ISBN", "My Rating", "Number of Pages", "Date Read", "Date Added",
           "Bookshelves", "Exclusive Shelf", "Cover", "Genres", "Audio"]
# The library filters on the profile page, the Goodreads shelf each becomes, in the order a book is kept
# when it turns up under several: a finished book is also among the recently opened.
STATES = [("FINISHED", "read"), ("OPENED", "currently-reading"), ("ADDED", "to-read")]

opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def login_of(who: str) -> str:
    who = who.strip()
    if m := re.search(r"@([^/?#]+)", who):
        return m.group(1)
    return who.rstrip("/").rsplit("/", 1)[-1]


def get(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ru,en;q=0.8"})
    with opener.open(req, timeout=30) as r:
        return r.read().decode("utf-8")


def library(login: str, token: str, state: str, cursor: str) -> dict:
    """One page of the reader's library, asked the way the site asks: its proxy only runs the queries it knows."""
    body = json.dumps({
        "operationName": "GetUserLibrary",
        "query": QUERY,
        "variables": {"userLogin": login, "params": {"cursor": cursor, "step": STEP, "contentFilter": None, "state": state}},
    }).encode()
    req = urllib.request.Request(f"{SITE}/graphql-proxy", data=body, headers={
        "User-Agent": UA, "Content-Type": "application/json", "Accept": "application/json",
        "x-csrf-token": token, "Origin": SITE, "Referer": SITE + "/",
    })
    with opener.open(req, timeout=30) as r:
        answer = json.loads(r.read().decode("utf-8"))
    if "data" not in answer:
        raise SystemExit(f"Yandex Books said: {answer.get('errors') or answer}")
    user = answer["data"].get("user")
    if not user:
        raise SystemExit("No such reader, or the profile is closed.")
    return user["library"]["books"]


def row(item: dict, shelf: str) -> dict:
    book = item.get("book") or {}
    authors = [a["name"] for a in book.get("authors") or [] if not a.get("hidden") and "AUTHOR" in (a.get("roles") or ["AUTHOR"])]
    kind = item.get("__typename")
    return {
        "Title": (book.get("name") or "").strip(),
        "Author": ", ".join(authors),
        "Exclusive Shelf": shelf,
        "Bookshelves": "комиксы" if kind == "ComicBook" else "",
        "Cover": (book.get("cover") or {}).get("url") or "",
        # The site lists the broad topics ("Художественная литература") among the telling ones; Familiar picks.
        "Genres": "; ".join(t["name"] for t in book.get("topics") or []),
        "Audio": "yes" if kind == "AudioBook" else "",
    }


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    login = login_of(sys.argv[1])
    out = Path(sys.argv[2] if len(sys.argv) > 2 else "yandex-books.csv")
    page = get(f"{SITE}/@{login}/books/all")
    token = re.search(r'"csrfToken":"([^"]+)"', page)
    if not token:
        raise SystemExit("The profile page changed: no token on it.")
    seen, rows = set(), []
    for state, shelf in STATES:
        cursor = ""
        while True:
            books = library(login, token.group(1), state, cursor)
            for item in books.get("page") or []:
                uuid = (item.get("book") or {}).get("uuid")
                if uuid and uuid not in seen:
                    seen.add(uuid)
                    rows.append(row(item, shelf))
            print(f"\r{len(rows)} books", end="", flush=True)
            cursor = books.get("cursor") or ""
            if not cursor or not books.get("page"):
                break
            time.sleep(PAUSE)
    print()
    with out.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, COLUMNS, restval="")
        w.writeheader()
        w.writerows(r for r in rows if r["Title"])
    print(f"{len(rows)} books written to {out}")


# The site's own query, word for word: the proxy refuses any other.
QUERY = """
    query GetUserLibrary($userLogin: String!, $params: UserLibraryBooksParamsInput!) {
  user(login: $userLogin) {
    library {
      books(params: $params) {
        cursor
        page {
          __typename
          ... on AudioBook {
            availability {
              ...FAvailability
            }
            progress {
              ...FProgress
            }
            book {
              ...FBook
            }
            listenersCount
          }
          ... on TextBook {
            availability {
              ...FAvailability
            }
            progress {
              ...FProgress
            }
            book {
              ...FBook
            }
            readersCount
          }
          ... on TextSerial {
            episodes {
              total
            }
            availability {
              ...FAvailability
            }
            progress {
              ...FProgress
            }
            book {
              ...FBook
            }
            readersCount
          }
          ... on ComicBook {
            availability {
              ...FAvailability
            }
            progress {
              ...FProgress
            }
            book {
              ...FBook
            }
            readersCount
          }
        }
      }
    }
  }
}
    
    fragment FAvailability on Availability {
  __typename
  ageRestriction {
    __typename
    name
    value
  }
  state {
    ... on Unavailable {
      __typename
      reason
    }
    ... on Available {
      __typename
      full
    }
  }
}
    

    fragment FProgress on Progress {
  inLibrary
  finished
  progress
  __typename
}
    

    fragment FBook on Book {
  uuid
  initUuid
  name
  annotation
  ageRestriction
  editorAnnotation
  cover {
    ...FCover
  }
  topics {
    name
    totalBook
    uuid
  }
  publisher {
    uuid
    name
    avatar {
      ...FCover
    }
  }
  translators {
    uuid
    name
    narrator {
      totalBook
    }
    author {
      totalBook
    }
    author {
      totalBook
    }
    translator {
      totalBook
    }
    avatar {
      ...FCover
    }
  }
  authors {
    ...FPerson
  }
}
    

    fragment FCover on Cover {
  url
  ratio
  backgroundColorHex
  fromShedevrum
  __typename
}
    

    fragment FPerson on Person {
  uuid
  hidden
  avatar {
    ...FCover
    __typename
  }
  author {
    totalBook
    __typename
  }
  name
  roles
  narrator {
    totalBook
    __typename
  }
  translator {
    totalBook
    __typename
  }
  __typename
}
    """


if __name__ == "__main__":
    main()
