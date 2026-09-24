/** @type {import('tailwindcss').Config} */
export default {
  content: ['./app/**/*.{js,jsx,ts,tsx}', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        orange: {
          400: '#FFB36B',
          500: '#FF7A1A',
          600: '#E35A05',
        },
        violet: {
          400: '#FFBF80',
          500: '#FF9848',
          600: '#D46A1F',
          700: '#A84F14',
          800: '#7A390D',
        },
        dark: {
          950: '#050607',
          900: '#090B0F',
          800: '#11141A',
          700: '#171C23',
          600: '#202733',
          500: '#2C3645',
        },
      },
      backgroundImage: {
        'gradient-main': 'linear-gradient(135deg, #FF7A1A 0%, #FFAA4D 55%, #FFD087 100%)',
        'gradient-card': 'linear-gradient(145deg, rgba(255,122,26,0.12) 0%, rgba(255,174,90,0.06) 100%)',
        'gradient-border': 'linear-gradient(135deg, rgba(255,122,26,0.45), rgba(255,208,135,0.25))',
      },
      animation: {
        'glow-pulse': 'glow-pulse 2s ease-in-out infinite',
        'float': 'float 6s ease-in-out infinite',
        'slide-up': 'slide-up 0.3s ease-out',
        'fade-in': 'fade-in 0.3s ease-out',
      },
      keyframes: {
        'glow-pulse': {
          '0%, 100%': { boxShadow: '0 0 24px rgba(255,122,26,0.25)' },
          '50%': { boxShadow: '0 0 44px rgba(255,176,95,0.3)' },
        },
        'float': {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-10px)' },
        },
        'slide-up': {
          from: { opacity: 0, transform: 'translateY(20px)' },
          to: { opacity: 1, transform: 'translateY(0)' },
        },
        'fade-in': {
          from: { opacity: 0 },
          to: { opacity: 1 },
        },
      },
      fontFamily: {
        sans: ['Trebuchet MS', 'Segoe UI Variable', 'Segoe UI', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
    },
  },
  plugins: [],
};
