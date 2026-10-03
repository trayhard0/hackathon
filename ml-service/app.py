from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class ClassifyRequest(BaseModel):
    subject: str
    snippet: str
    sender: str = ""

class ClassifyResponse(BaseModel):
    label: str
    confidence: float

@app.post("/classify", response_model=ClassifyResponse)
def classify(req: ClassifyRequest):
    # Stub: keyword rules until the trained model lands.
    # Labels: applied, assessment, interview, rejection, recruiter, other
    text = (req.subject + " " + req.snippet).lower()
    if any(k in text for k in ["interview", "onsite", "phone screen"]):
        return ClassifyResponse(label="interview", confidence=0.5)
    if any(k in text for k in ["assessment", "codesignal", "hackerrank", "online assessment"]):
        return ClassifyResponse(label="assessment", confidence=0.5)
    if any(k in text for k in ["rejected", "not moving forward", "unfortunately"]):
        return ClassifyResponse(label="rejection", confidence=0.5)
    if any(k in text for k in ["recruiter", "reaching out", "talent acquisition"]):
        return ClassifyResponse(label="recruiter", confidence=0.5)
    if any(k in text for k in ["application received", "thank you for applying", "applied"]):
        return ClassifyResponse(label="applied", confidence=0.5)
    return ClassifyResponse(label="other", confidence=0.5)
