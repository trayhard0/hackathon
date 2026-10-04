# GroveMail — Frontend

Job application journal: a tree-themed dashboard for tracking applications,
online assessments, interviews, and rejections. Built with React + Vite +
TypeScript + Tailwind CSS v4.

## Run it

```bash
npm install   # or: pnpm install
npm run dev   # → http://localhost:5173
```

## Build

```bash
npm run build   # → dist/
npm run preview # serve the production build
```

## Structure

```
src/
  App.tsx      # the whole journal UI (columns + little forest)
  main.tsx     # React entrypoint
  index.css    # Tailwind + global styles
index.html     # Vite HTML shell
```
