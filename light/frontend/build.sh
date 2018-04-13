#!/bin/bash
[[ -d node_modules ]] || npm -S install
npm run build
