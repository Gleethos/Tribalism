/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      backgroundImage: {
        'survival-1': "url('../public/survival-1.png')",
      },
    },
  },
  plugins: [],
};
