## A very basic ETL (extract, transform, load) pipeline for covid stats in Austria

Functions in the `extract` namespace handle loading & parsing of datasources. Currently implemented are HTML scrapers (loaded into enlive), a simple fetcher & parser for JSONp sources, and a JSON fetcher & parser.

The `transform` ns contains functions that expect output from `extract`-scrapers and turn them into a common stats data format: A hashmap
with location identifiers (`:at` for Austria, `:wien` for Vienna, etc) for keys, and a hashmap of statistics for values. A quick peek into how that map looks like:
looks like this:
```
  {:at {:cases xxx
        :tests xxx
        :tdouble xxx}
   :wien {:cases xxx
          :tdouble xxx}
     
     ...
  }
```

All calculated stats are combined and, along with an extracted timestamp, later be sent to targets. Currently supported exports:
  - Google Sheets 
  - json file dump
  
## Setup
If you want use the Google sheets export, you'll need a Google Service Account. [Here are instructions on how to create one](https://support.google.com/a/answer/7378726?hl=en). To grant write access to your service account, you add its "email address" as a collaborator directly within the sheet.

This project uses `deps.edn`. Run the scraper with `clj -m core`, or build an uberjar `clj -Auberjar`.
