# video-clipping-tool

Clip vids and stitch them back together.

## Usage

* Requires Java 8.
* Requires `lein`

Run `lein install`. Optionally, run `lein uberjar` to generate a standlone executable `.jar` file. Otherwise:

```
lein run --video VIDEO_FILE --clips CLIP_FILE --output OUTPUT_FILE
```

## License

Copyright © 2018 IvantheTricourne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
