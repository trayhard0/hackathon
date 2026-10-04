from fastapi import FastAPI
from pydantic import BaseModel
import os
import json
import urllib.request


app = FastAPI()

try:
    import joblib
    HAVE_JOBLIB = True
except ImportError:
    HAVE_JOBLIB = False

pipe = None
if HAVE_JOBLIB and os.path.exists("model/pipeline.joblib"):
    pipe = joblib.load("model/pipeline.joblib")

class ClassifyRequest(BaseModel):
    subject: str
    snippet: str
    sender: str = ""

class ClassifyResponse(BaseModel):
    label: str
    confidence: float
    source: str

def rule_label(text: str) -> str:
    t = text.lower()
    if any(k in t for k in ["rejected", "not selected", "not move forward",
                            "not moving forward", "n't move forward",
                            "not be moving forward", "moving forward with other",
                            "pursue other", "unfortunately"]):
        return "rejection"
    if any(k in t for k in ["interview", "onsite", "phone screen"]):
        return "interview"
    if any(k in t for k in ["assessment", "codesignal", "hackerrank", "online assessment"]):
        return "assessment"
    if any(k in t for k in ["recruiter", "reaching out"]):
        return "recruiter"
    if any(k in t for k in ["application received", "thank you for applying",
                            "successfully received your application",
                            "thank you for your interest", "thanks for applying"]):
        return "applied"
    return "other"

@app.post("/classify", response_model=ClassifyResponse)
def classify(req: ClassifyRequest):
    text = f"{req.sender} {req.subject} {req.snippet}"
    if pipe is not None:
        pred = pipe.predict([text])[0]
        conf = float(max(pipe.predict_proba([text])[0]))
        if pred == "other":
            return ClassifyResponse(label="other", confidence=conf, source="model")
        sub = rule_label(text)
        return ClassifyResponse(label=sub if sub != "other" else "applied",
                                confidence=conf, source="model+rules")
    return ClassifyResponse(label=rule_label(text), confidence=0.5, source="rules")

@app.get("/health")
def health():
    return {"status": "ok", "modelLoaded": pipe is not None}

OLLAMA_URL = "http://localhost:11434/api/generate"
OLLAMA_MODEL = "qwen2.5:3b"
VALID_PHASES = {"applied", "assessment", "interview", "rejection", "recruiter", "other"}

UNDERSTAND_SYSTEM = """You analyze job-application emails. Read the sender, subject, and snippet, then decide:
- phase: exactly one of "applied", "assessment", "interview", "rejection", "recruiter", "other"
- company: the employer company name (not the job board or sender domain), or "Unknown"
- role: the job title as a clean title (e.g. "Software Engineer Intern"), or "Unknown"

Phase meanings:
- applied: confirms an application was received or submitted
- assessment: online assessment / coding challenge invite, reminder, or expiry (CodeSignal, HackerRank, etc.)
- interview: interview invitation or scheduling
- rejection: the candidate will not move forward
- recruiter: a recruiter reaching out about a role (not an application confirmation)
- other: not a job-application email

Examples — follow these exactly:

Sender: Duolingo Careers <careers@duolingo.com>
Subject: An update on your application
Snippet: Thank you for your interest in Duolingo. After careful consideration, we have decided not to move forward with your candidacy at this time.
{"phase": "rejection", "company": "Duolingo", "role": "Software Engineer Intern"}

Sender: Company Careers <noreply@ashbyhq.com>
Subject: Your application to Superhuman
Snippet: Unfortunately, we won't be able to move forward. We appreciate your interest in the Software Engineering Intern role and wish you luck.
{"phase": "rejection", "company": "Superhuman", "role": "Software Engineering Intern"}

Sender: Gilead Sciences Talent <talent@gilead.com>
Subject: Your application has been received
Snippet: Thank you for applying to the Intern - Development position. Your application is under review. Next steps may include an online assessment or interview.
{"phase": "applied", "company": "Gilead Sciences", "role": "Intern - Development"}

Sender: CodeSignal <notifications@codesignal.com>
Subject: Action required: Complete your Ramp assessment
Snippet: Ramp has invited you to take the Frontend Challenge on CodeSignal. You have 7 days to complete it. Click here to start.
{"phase": "assessment", "company": "Ramp", "role": "Unknown"}

Respond with ONLY a JSON object like {"phase": "applied", "company": "Amgen", "role": "Software Engineer Intern"}. No other text.
If the email confirms your application was received, it is "applied" — even if it mentions assessments or interviews as possible future steps. Only use "assessment" if the email explicitly asks you to complete a test now.
"""

def ollama_understand(sender: str, subject: str, snippet: str) -> dict:
    prompt = f"{UNDERSTAND_SYSTEM}\n\nSender: {sender}\nSubject: {subject}\nSnippet: {snippet}"
    body = json.dumps({
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "format": "json",          # Ollama guarantees valid JSON back
        "stream": False,
        "options": {"temperature": 0, "num_predict": 100, "num_ctx": 2048},
    }).encode()
    req = urllib.request.Request(OLLAMA_URL, data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        outer = json.loads(resp.read().decode())
    data = json.loads(outer["response"])
    phase = str(data.get("phase", "other")).strip().lower()
    if phase not in VALID_PHASES:
        phase = "other"
    company = str(data.get("company", "Unknown")).strip() or "Unknown"
    role = str(data.get("role", "Unknown")).strip() or "Unknown"
    return {"phase": phase, "company": company, "role": role, "source": "ollama"}

@app.post("/understand")
def understand(req: ClassifyRequest):
    try:
        return ollama_understand(req.sender, req.subject, req.snippet)
    except Exception:
        text = f"{req.sender} {req.subject} {req.snippet}"
        return {"phase": rule_label(text),
                "company": "Unknown", "role": "Unknown", "source": "rules-fallback"}
