## A very basic ETL (extract, transform, load) pipeline for covid stats in Austria

Functions in the `extract` namespace handle loading & parsing of datasources. Currently implemented are HTML scrapers (loaded into enlive) and a simple fetcher & parser for JSONp sources.

In `transform` you'll find functions that expect output from scrapers in `extract` and turn them into a common stats data format: A hashmap
with location identifiers (`:at` for Austria, `"W"` for Vienna, etc). A quick peek into how that map looks like:
looks like this:
```
  {:at {:cases xxx
        :tests xxx
        :tdouble xxx}
    "W" {:cases xxx
         :tdouble xxx}
     
     ...
  }
```

All calculated stats are combined and, along with an extracted timestamp, later be sent to targets. Currently only
Google Sheets support is implemented.

## Setup
_wip_
