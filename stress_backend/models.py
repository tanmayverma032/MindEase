from pydantic import BaseModel

class User(BaseModel):
    name: str
    email: str
    password: str

class Login(BaseModel):
    email: str
    password: str

class StressInput(BaseModel):
    user_id: str
    Eye_Blink_Rate_per_min: float
    Resting_Heart_Rate_BPM: float
    Heart_Rate_After_Exercise_BPM: float
    age: int
    gender: str
    worklife: str
    Sleep_Duration_Hours: float
    Chest_Pain: int
    Cholesterol_mg_dL: float
    ECG_Result: int
    Body_Temperature_C: float
    Steps_per_Day: int

class ChangePasswordRequest(BaseModel):
    old_password: str
    new_password: str

class VerifyEmailRequest(BaseModel):
    email: str
    otp_code: str