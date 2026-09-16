/** @type {import('tailwindcss').Config} */

// Scan the ClojureScript sources rather than the compiled bundle.
//
// The previous config scanned ./dist/**/*.{html,js} - the advanced-optimized
// output. That made Tailwind depend on the CLJS build having already run, so
// `bb dev`, which starts both watchers in parallel, always began with
// "No utility classes were detected in your source files".
//
// Hiccup writes classes as part of a keyword (:table.table.table-xs.w-full),
// which Tailwind's default extractor does not split, so cljs files need their
// own extractor.
const cljsExtractor = (content) =>
  (content.match(/[^\s"'`;()\[\]{}]+/g) || [])
    .flatMap((token) => token.split('.'))
    .map((token) => token.replace(/^:+/, ''))
    .filter(Boolean);

module.exports = {
  content: {
    files: ['./src/**/*.cljs', './resources/*.html'],
    extract: { cljs: cljsExtractor },
  },
  theme: { extend: {}, container: { padding: '2rem' } },
  plugins: [require('@tailwindcss/typography'), require('daisyui')],
  daisyui: { themes: ['retro', 'light', 'dark', 'cupcake'] },
};
