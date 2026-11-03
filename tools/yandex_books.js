// Exports your Yandex Books shelves to a CSV that Familiar imports (Settings → Books from another app).
//
// Open your profile on books.yandex.ru (or anyone's open profile: books.yandex.ru/@login/books/all),
// press F12, go to Console, paste all of this and press Enter. The browser asks to type "allow pasting"
// the first time. A file yandex-books-<login>.csv is downloaded when it's done.
//
// Finished books go to Read, the ones opened lately and not finished to Reading, the rest to Want to read.
// Text books, audiobooks and comics all come, with their covers and the site's topics as genres.
// In a block of its own, so pasting it again doesn't clash with the last run.
{
// The site's own query, word for word: its proxy refuses any other.
const QUERY = `
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
    `;

(async () => {
    const login = (location.pathname.match(/@([^/]+)/) || [])[1]
    if (location.host !== 'books.yandex.ru' || !login) {
        alert('Откройте свой профиль на books.yandex.ru (адрес вида books.yandex.ru/@логин/books/all) и запустите скрипт ещё раз.')
        return
    }
    const token = (document.documentElement.innerHTML.match(/"csrfToken":"([^"]+)"/) || [])[1]
    if (!token) {
        alert('Familiar: обновите страницу и запустите скрипт ещё раз.')
        return
    }
    // The order a book is kept in when it turns up under several: a finished one is also among the opened.
    const states = [['FINISHED', 'read'], ['OPENED', 'currently-reading'], ['ADDED', 'to-read']]
    const seen = new Set()
    const rows = []
    for (const [state, shelf] of states) {
        let cursor = ''
        while (true) {
            const r = await fetch('/graphql-proxy', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'x-csrf-token': token },
                body: JSON.stringify({
                    operationName: 'GetUserLibrary',
                    query: QUERY,
                    variables: { userLogin: login, params: { cursor, step: 50, contentFilter: null, state } },
                }),
            }).then(r => r.json())
            if (!r.data || !r.data.user) {
                alert('Familiar: Яндекс Книги не отдали полку: ' + JSON.stringify(r.errors || r))
                return
            }
            const books = r.data.user.library.books
            for (const item of books.page || []) {
                const book = item.book || {}
                if (!book.uuid || seen.has(book.uuid)) continue
                seen.add(book.uuid)
                rows.push({
                    Title: (book.name || '').trim(),
                    Author: (book.authors || []).filter(a => !a.hidden && (a.roles || ['AUTHOR']).includes('AUTHOR')).map(a => a.name).join(', '),
                    'Exclusive Shelf': shelf,
                    Bookshelves: item.__typename === 'ComicBook' ? 'комиксы' : '',
                    Cover: (book.cover && book.cover.url) || '',
                    Genres: (book.topics || []).map(t => t.name).join('; '),
                    Audio: item.__typename === 'AudioBook' ? 'yes' : '',
                    // Everything else on Yandex Books is read on a screen.
                    Ebook: item.__typename === 'AudioBook' ? '' : 'yes',
                })
            }
            console.log(`Familiar: ${rows.length} книг…`)
            cursor = books.cursor || ''
            if (!cursor || !(books.page || []).length) break
            await new Promise(ok => setTimeout(ok, 500))
        }
    }
    const columns = ['Title', 'Author', 'ISBN', 'My Rating', 'Number of Pages', 'Date Read', 'Date Added',
        'Bookshelves', 'Exclusive Shelf', 'Cover', 'Genres', 'Audio', 'Ebook']
    const cell = v => /[",\r\n]/.test(v || '') ? '"' + v.replaceAll('"', '""') + '"' : (v || '')
    const csv = [columns.join(','), ...rows.filter(r => r.Title).map(r => columns.map(c => cell(r[c])).join(','))].join('\r\n')
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }))
    a.download = `yandex-books-${login}.csv`
    a.click()
    setTimeout(() => URL.revokeObjectURL(a.href), 10000)
    alert(`Familiar: готово, ${rows.length} книг в файле ${a.download}.`)
})()
}
