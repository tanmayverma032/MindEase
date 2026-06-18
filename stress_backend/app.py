from fastapi import FastAPI, HTTPException, Depends, BackgroundTasks
from models import User, Login, StressInput, ChangePasswordRequest
from database import users_collection, history_collection, pending_users_collection
from passlib.context import CryptContext
from jose import jwt, JWTError
from datetime import datetime, timedelta
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import requests
import time
import os
import json
import smtplib
import random
from email.message import EmailMessage
from bson.json_util import dumps
from dotenv import load_dotenv

load_dotenv()

app = FastAPI()

# 📧 EMAIL CONFIGURATION
EMAIL_SENDER = os.getenv("EMAIL_SENDER")
EMAIL_PASSWORD = os.getenv("EMAIL_PASSWORD")
RESEND_API_KEY = os.getenv("RESEND_API_KEY")

def send_otp_email(receiver_email: str, otp_code: str):
    """Try Resend API first (works on Render), fallback to Gmail SMTP (works locally)."""
    
    # Method 1: Resend HTTP API (works on Render free tier)
    if RESEND_API_KEY:
        try:
            response = requests.post(
                "https://api.resend.com/emails",
                headers={
                    "Authorization": f"Bearer {RESEND_API_KEY}",
                    "Content-Type": "application/json"
                },
                json={
                    "from": "MindEase <onboarding@resend.dev>",
                    "to": [receiver_email],
                    "subject": "Verify Your MindEase Account",
                    "html": f"""
                    <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 32px; background: #f8f9fa; border-radius: 12px;">
                        <h2 style="color: #2d6b5e; text-align: center;">Welcome to MindEase!</h2>
                        <p style="color: #555; text-align: center;">Your verification code is:</p>
                        <div style="text-align: center; margin: 24px 0;">
                            <span style="font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #3e8b7a; background: #e8f5f1; padding: 12px 24px; border-radius: 8px;">{otp_code}</span>
                        </div>
                        <p style="color: #888; text-align: center; font-size: 13px;">This code expires in 10 minutes.<br>If you didn't request this, please ignore this email.</p>
                    </div>
                    """
                },
                timeout=10
            )
            if response.status_code == 200:
                print(f"OTP email sent via Resend to {receiver_email}")
                return True
            else:
                print(f"Resend API error: {response.status_code} - {response.text}")
                return False
        except Exception as e:
            print(f"Resend API failed: {e}")
            return False
    
    # Method 2: Gmail SMTP fallback (for local development)
    if EMAIL_SENDER and EMAIL_PASSWORD:
        msg = EmailMessage()
        msg["Subject"] = "Verify Your MindEase Account"
        msg["From"] = EMAIL_SENDER
        msg["To"] = receiver_email
        msg.set_content(f"""
        Welcome to MindEase!
        
        Your verification code is: {otp_code}
        
        This code will expire in 10 minutes. 
        If you did not request this, please ignore this email.
        """)
        
        try:
            with smtplib.SMTP_SSL("smtp.gmail.com", 465) as server:
                server.login(EMAIL_SENDER, EMAIL_PASSWORD)
                server.send_message(msg)
                print(f"OTP email sent via Gmail SMTP to {receiver_email}")
                return True
        except Exception as e:
            print(f"Gmail SMTP failed: {e}")
            return False
    
    print("Warning: No email service configured (set RESEND_API_KEY or EMAIL_SENDER+EMAIL_PASSWORD)")
    return False

# 🔐 PASSWORD HASHING
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# 🔐 JWT CONFIG
SECRET_KEY = "mysecretkey123"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 43200  # 30 days

# 🔐 SECURITY
security = HTTPBearer()

# 🔷 EXTERNAL APIs
BLINK_API = "https://blink-api-jxdv.onrender.com/blink"
STRESS_API = "https://stress-api-99xw.onrender.com/predict"
CHATBOT_API = "https://stress-chatbot-api.onrender.com/chat"


# ---------------- PASSWORD FUNCTIONS ---------------- #

def hash_password(password):
    return pwd_context.hash(password[:72])


def verify_password(password, hashed):
    return pwd_context.verify(password[:72], hashed)


# ---------------- JWT FUNCTIONS ---------------- #

def create_access_token(data: dict):
    to_encode = data.copy()

    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})

    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


def verify_token(credentials: HTTPAuthorizationCredentials = Depends(security)):
    token = credentials.credentials

    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        return payload
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid or expired token")


# ---------------- API RETRY ---------------- #

def call_stress_api(payload):
    for i in range(3):
        try:
            response = requests.post(STRESS_API, json=payload, timeout=30)

            print("STATUS:", response.status_code)
            print("RAW:", response.text)

            if response.status_code == 200:
                return response

        except requests.exceptions.RequestException as e:
            print(f"Retry {i+1} failed:", e)
            time.sleep(2)

    return None


# ---------------- ROUTES ---------------- #

@app.get("/")
def home():
    return {"message": "Stress Monitoring Backend Running"}


# 🔷 SIGNUP (with OTP fallback)
@app.post("/signup")
def signup(user: User, background_tasks: BackgroundTasks):
    # 1. Check if user already exists
    if users_collection.find_one({"email": user.email}):
        raise HTTPException(status_code=400, detail="User already exists")

    # 2. Check if already pending, remove old OTP
    pending_users_collection.delete_many({"email": user.email})

    # 3. Generate 6-digit OTP
    otp_code = str(random.randint(100000, 999999))
    
    # 4. Try sending email synchronously first to check if SMTP works
    email_sent = send_otp_email(user.email, otp_code)
    
    if email_sent:
        # SMTP works → use OTP verification flow
        pending_users_collection.insert_one({
            "name": user.name,
            "email": user.email,
            "password": hash_password(user.password),
            "otp_code": otp_code,
            "created_at": datetime.utcnow()
        })
        return {"message": "Verification code sent to email"}
    else:
        # SMTP blocked (Render) → create account directly without OTP
        print(f"SMTP failed for {user.email}, creating account directly")
        
        result = users_collection.insert_one({
            "name": user.name,
            "email": user.email,
            "password": hash_password(user.password)
        })
        
        token = create_access_token({
            "sub": str(result.inserted_id)
        })
        
        return {
            "message": "Account created successfully",
            "token": token,
            "user_id": str(result.inserted_id),
            "name": user.name,
            "email": user.email,
            "phone": ""
        }


# 🔷 VERIFY EMAIL (Step 2: Create Account)
from models import VerifyEmailRequest

@app.post("/verify_email")
def verify_email(data: VerifyEmailRequest):
    # 1. Find the pending signup
    pending_user = pending_users_collection.find_one({
        "email": data.email,
        "otp_code": data.otp_code
    })

    if not pending_user:
        raise HTTPException(status_code=400, detail="Invalid or expired verification code")

    # 2. Move to main users collection
    result = users_collection.insert_one({
        "name": pending_user["name"],
        "email": pending_user["email"],
        "password": pending_user["password"]
    })

    # 3. Delete the pending record
    pending_users_collection.delete_many({"email": data.email})

    # 4. Create Token so user is logged in automatically
    token = create_access_token({
        "sub": str(result.inserted_id)
    })

    return {
        "message": "Email verified and account created successfully!",
        "token": token,
        "user_id": str(result.inserted_id),
        "name": pending_user.get("name", ""),
        "email": pending_user.get("email", ""),
        "phone": ""
    }


# 🔷 LOGIN (UPDATED WITH TOKEN)
@app.post("/login")
def login(user: Login):
    db_user = users_collection.find_one({"email": user.email})

    if not db_user:
        raise HTTPException(status_code=404, detail="User not found")

    if not verify_password(user.password, db_user["password"]):
        raise HTTPException(status_code=401, detail="Invalid password")

    # ✅ CREATE TOKEN
    token = create_access_token({
        "sub": str(db_user["_id"])
    })

    return {
        "message": "Login successful",
        "token": token,
        "user_id": str(db_user["_id"]),
        "name": db_user.get("name", ""),
        "email": db_user.get("email", ""),
        "phone": db_user.get("phone", "")
    }


# 🔷 PREDICT STRESS (PROTECTED)
@app.post("/predict_stress")
def predict_stress(
    data: StressInput,
    user=Depends(verify_token)  # 🔐 PROTECTED ROUTE
):
    try:
        response = call_stress_api(data.dict())

        if response is None:
            return {"error": "Stress API not responding"}

        try:
            result = response.json()
        except:
            return {
                "error": "Invalid JSON from Stress API",
                "raw": response.text
            }

        if "prediction" not in result:
            return {
                "error": "Invalid response from model",
                "raw": result
            }

        stress_map = {
            0: "Low",
            1: "Medium",
            2: "High"
        }

        stress_level = stress_map.get(result["prediction"], "Unknown")

        history_collection.insert_one({
            "user_id": data.user_id,
            "input": data.dict(),
            "prediction_value": result["prediction"],
            "stress_level": stress_level,
            "timestamp": datetime.utcnow()
        })

        return {
            "stress_level": stress_level,
            "prediction_value": result["prediction"]
        }

    except Exception as e:
        return {"error": str(e)}


# 🔷 HISTORY (OPTIONAL: PROTECT LATER)
@app.get("/history/{user_id}")
def get_history(
    user_id: str,
    user=Depends(verify_token)  # 🔐 PROTECTED ROUTE
):
    try:
        history = list(history_collection.find({"user_id": user_id}))
        
        # Retroactively populate timestamps for older records using their MongoDB ObjectId creation time
        for item in history:
            if "timestamp" not in item and "_id" in item:
                item["timestamp"] = item["_id"].generation_time.isoformat()
                
        return {"history": json.loads(dumps(history))}
    except Exception as e:
        print(f"History error for user {user_id}: {e}")
        return {"history": []}


# 🔷 CHATBOT
@app.post("/chat")
def chat(message: dict):
    try:
        response = requests.post(CHATBOT_API, json=message, timeout=30)
        return response.json()
    except:
        return {"error": "Chatbot API not responding"}


# 🔷 CHANGE PASSWORD (PROTECTED)
@app.post("/change_password")
def change_password(
    data: ChangePasswordRequest,
    user=Depends(verify_token)
):
    from bson import ObjectId

    user_id = user.get("sub")
    if not user_id:
        raise HTTPException(status_code=401, detail="Invalid token")

    db_user = users_collection.find_one({"_id": ObjectId(user_id)})
    if not db_user:
        raise HTTPException(status_code=404, detail="User not found")

    if not verify_password(data.old_password, db_user["password"]):
        raise HTTPException(status_code=401, detail="Old password is incorrect")

    new_hashed = hash_password(data.new_password)
    users_collection.update_one(
        {"_id": ObjectId(user_id)},
        {"$set": {"password": new_hashed}}
    )

    return {"message": "Password changed successfully"}