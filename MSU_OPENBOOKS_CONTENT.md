# MSU OpenBooks course content

This repository includes reproducible draft-course imports for two openly licensed
Vietnamese textbooks by Tung Hoang, published by Michigan State University Libraries:

- **Basic Vietnamese** (2023): 15 courses, 87 lessons.
- **Advanced Vietnamese** (2024): 7 courses, 37 lessons. Chapter 7 includes the two
  transcript appendices as lessons.

Both books are licensed under the Creative Commons Attribution-NonCommercial 4.0
International license, except where the source identifies third-party material.

Sources:

- <https://openbooks.lib.msu.edu/vietnamese/>
- <https://openbooks.lib.msu.edu/advancedvietnamese/>
- <https://creativecommons.org/licenses/by-nc/4.0/>

The extracted source text and extraction audit reports live in
`src/main/resources/demo/msu/`. The official PDFs are intentionally omitted to keep
the repository small. Each generated course and activity includes source attribution.

## Import the content

Start the backend and make sure the `contentmanager` account exists. Set the password
only in the current shell, then run either importer from the repository root.

PowerShell:

```powershell
$env:MSU_CM_PASSWORD = 'your-content-manager-password'

python scripts/import-msu-basic-vietnamese.py `
  --text src/main/resources/demo/msu/basic-vietnamese-msu.txt `
  --output build/msu-basic-vietnamese-import.json `
  --import

python scripts/import-msu-advanced-vietnamese.py `
  --text src/main/resources/demo/msu/advanced-vietnamese-msu.txt `
  --output build/msu-advanced-vietnamese-import.json `
  --import
```

The importers call the normal authoring API. They create draft courses owned by the
authenticated Content Manager, one module per course, ordered lessons, prerequisite
links, and one source-based text activity per lesson. Covers, uploaded media, quizzes,
publication status, and review decisions remain editorial steps in Course Studio.

Run without `--import` to validate the extraction and write a report without changing
the database.

The importers are intended for a clean target database. Check for existing course codes
`MSU-BV-CH01` through `MSU-BV-CH15` and `MSU-ADV-VI-CH01` through
`MSU-ADV-VI-CH07` before importing again.
