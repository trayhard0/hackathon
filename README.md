# Grove

Your job applications, cultivated. Grove reads your Gmail, finds every job application email, and grows them into a living garden.

Only 85% of internship applications send a confirmation email. 1 in 7 vanishes into the void with no trail. Grove makes sure nothing slips through.

## What it does

- **Syncs your Gmail** and finds every job-related email using a trained classifier
- **Understands each email** with a local LLM: company, role, and stage (applied, assessment, interview, rejection, recruiter)
- **Builds your dashboard**: one row per application with a full email timeline
- **Grows your garden**: applications are seeds, assessments sprout, interviews branch, rejections become compost
- **Organizes your inbox**: one click applies `JobSearch/` labels to every classified email in Gmail
- **Learns from you**: correct any label and the dashboard updates instantly

## The ML

This is not an API wrapper. The classification pipeline is two stages, both local:

1. **Binary gate** (scikit-learn): TF-IDF + logistic regression trained on 400 hand-labeled emails. Five-fold cross-validation macro F1 of **0.973**, plus **37/37** on the hardest cases where baseline rules failed.
2. **Structured extraction** (Ollama `qwen2.5:3b`): reads the full email body and returns company, role, and stage as JSON. Full bodies matter: Gmail snippets truncate before the rejection sentence, so we fetch the complete message.

Everything runs on your laptop. Your emails never leave your machine.

## Architecture

```
Gmail API → Spring Boot → FastAPI (classifier) → Ollama (extraction)
                ↓
           H2 (persistent) → React dashboard
```

- **backend/**: Spring Boot. Gmail OAuth, sync orchestration, REST API, H2 persistence.
- **ml-service/**: FastAPI. Serves the scikit-learn classifier (`/classify`) and the Ollama extraction endpoint (`/understand`).
- **frontend/**: React + Vite. Dashboard, garden visualization, label controls.

## Running it

**1. ML service** (classifier + Ollama bridge):
```bash
cd ml-service
uvicorn app:app --port 8000
```
Requires Ollama running locally with `qwen2.5:3b` pulled.

**2. Backend**:
```bash
cd backend
./mvnw spring-boot:run
```
On first run, authenticate with Gmail when prompted. Then:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/applications/sync?max=200" -Method Post
```

**3. Frontend**:
```bash
cd frontend
pnpm install
pnpm dev
```

Open `http://localhost:5173`, hit **Sync Gmail**, then **Organize Gmail** to label your inbox.

## Privacy

No email content is committed to this repo. `labeled_emails.csv`, OAuth tokens, `.env` files, and the H2 database are all gitignored. The classifier model file contains no personal data.

## Built at

GirlHacks 2026, NJIT. 24 hours, one all-nighter, zero cloud calls.
