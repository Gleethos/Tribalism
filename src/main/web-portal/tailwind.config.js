/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      backgroundImage: {
        'survival-1':
          "url('../public/survivor-standing-in-postapocalyptic-city.png')",
        'survival-4':
          "url('../public/survivor-looking-out-of-window-at-burning-plane.png')",
      },
    },
  },
  plugins: [],
};
