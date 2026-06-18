from fastapi import FastAPI
from pydantic import BaseModel
from fastapi.middleware.cors import CORSMiddleware
from groq import Groq
import os
from dotenv import load_dotenv

load_dotenv()

client = Groq(api_key=os.getenv("GROQ_API_KEY"))

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class ChatRequest(BaseModel):
    message: str


# Emergency detection
def is_emergency(text: str) -> bool:
    emergency_words = [
        "severe chest pain",
        "can't breathe",
        "fainting",
        "suicidal",
        "heart attack"
    ]
    text = text.lower()
    return any(word in text for word in emergency_words)


# Medical restriction filter
def is_medical_query(text: str) -> bool:
    medical_keywords = [
        "stress", "heart", "chest", "sleep", "anxiety",
        "ecg", "cholesterol", "temperature", "pain",
        "mental", "health", "exercise", "blood"
    ]
    text = text.lower()
    return any(word in text for word in medical_keywords)


SYSTEM_PROMPT = """
You are a medical stress monitoring assistant.

Rules:
- Only answer health and stress related questions.
- If question is unrelated, say:
  "I can only assist with medical or stress-related concerns."
- Do not diagnose diseases.
- Do not prescribe medication.
- Give general wellness advice only.
- Suggest consulting a healthcare professional for serious symptoms.
- Keep answers safe and responsible.
"""


@app.post("/chat")
async def chat(req: ChatRequest):

    if is_emergency(req.message):
        return {
            "reply": "This may require immediate medical attention. Please contact emergency services or consult a doctor immediately."
        }

    if not is_medical_query(req.message):
        return {
            "reply": "I can only assist with medical or stress-related concerns."
        }

    response = client.chat.completions.create(
        model="llama-3.3-70b-versatile",
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": req.message}
        ],
        temperature=0.4,
    )

    reply = response.choices[0].message.content

    reply += "\n\nNote: This information is for general guidance and not a medical diagnosis."

    return {"reply": reply}
