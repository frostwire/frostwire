# IceBridge white paper

- `icebridge-whitepaper.tex`: the source of truth. Edit only this file.
- `icebridge-whitepaper.pdf`: built from the `.tex` with pdflatex.
- `icebridge-whitepaper.md`: generated from the `.tex` with pandoc and `tex2md.py`, for reading on GitHub.
- `tex2md.py`: the generator for the Markdown version.

## Rebuild the PDF

Requires a TeX Live installation with `tikz`, `booktabs`, `longtable`, `tabularx`, `listings`,
`enumitem`, `microtype`, `lmodern`, `amsmath` and `hyperref` (all in TeX Live basic plus those
packages). Run pdflatex twice so that the table of contents and cross-references resolve:

```sh
cd docs/whitepaper
pdflatex -interaction=nonstopmode icebridge-whitepaper.tex
pdflatex -interaction=nonstopmode icebridge-whitepaper.tex
```

On macOS with MacTeX, pdflatex is at `/Library/TeX/texbin/pdflatex`. Check
`icebridge-whitepaper.log` for `Overfull` boxes and warnings; the committed version has none.

## Rebuild the Markdown

Build the PDF first: `tex2md.py` reads `icebridge-whitepaper.aux` for section, table and figure
numbers. Then:

```sh
cd docs/whitepaper
python3 tex2md.py                                   # uses `pandoc` from PATH
PANDOC=/opt/homebrew/bin/pandoc python3 tex2md.py   # or name pandoc explicitly
```

The script simplifies a few LaTeX constructs that pandoc does not handle (`tabularx`, the custom
`\code` macro, a side-by-side table), runs pandoc to GitHub-flavored Markdown, then numbers the
headings, resolves references, and replaces the two TikZ figures with text diagrams. When you
change a figure in the `.tex`, update its text version in `tex2md.py`.

The build by-products (`.aux`, `.log`, `.toc`, `.out`) are not committed.
