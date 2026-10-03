from fastapi import FastAPI
from pydantic import BaseModel
import os

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
    if any(k in t for k in ["interview", "onsite", "phone screen"]):
        return "interview"
    if any(k in t for k in ["assessment", "codesignal", "hackerrank", "online assessment"]):
        return "assessment"
    if any(k in t for k in ["rejected", "not moving forward", "not move forward", "unfortunately"]):
        return "rejection"
    if any(k in t for k in ["recruiter", "reaching out", "talent acquisition"]):
        return "recruiter"
    if any(k in t for k in ["application received", "thank you for applying",
                            "successfully received your application", "thank you for your interest"]):
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
