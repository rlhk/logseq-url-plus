const { transparent } = require('daisyui/src/theming');

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./dist/**/*.{html,js}"],
  theme: {
    extend: {},
    container: {
      padding: '2rem'
    }
  },
  plugins: [
    require("@tailwindcss/typography"),
    require("daisyui")
  ],
  daisyui: {
    themes: ["retro", "light", "dark", "cupcake"]
  }
}
