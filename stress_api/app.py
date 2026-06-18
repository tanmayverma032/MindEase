from fastapi import FastAPI
import joblib
import numpy as np

app = FastAPI()

# Load saved objects
model = joblib.load("model.pkl")
scaler = joblib.load("scaler.pkl")
encoders = joblib.load("encoders.pkl")

@app.get("/")
def home():
    return {"message": "Stress Prediction API Running"}

@app.post("/predict")
def predict(data: dict):

    try:
        # 🔥 Define exact feature order (VERY IMPORTANT)
        feature_order = [
            "Eye_Blink_Rate_per_min",
            "Resting_Heart_Rate_BPM",
            "Heart_Rate_After_Exercise_BPM",
            "age",
            "gender",
            "worklife",
            "Sleep_Duration_Hours",
            "Chest_Pain",
            "Cholesterol_mg_dL",
            "ECG_Result",
            "Body_Temperature_C",
            "Steps_per_Day"
        ]

        # Map numeric values to strings for categorical features
        # (Android app sends numbers, model encoders expect strings)
        chest_pain_map = {0: "No", 1: "Mild", 2: "Yes", 3: "Yes"}
        ecg_result_map = {0: "Normal", 1: "Sinus_Tachycardia", 2: "Sinus_Tachycardia"}

        input_data = dict(data)
        if "Chest_Pain" in input_data and isinstance(input_data["Chest_Pain"], (int, float)):
            input_data["Chest_Pain"] = chest_pain_map.get(int(input_data["Chest_Pain"]), "No")
        if "ECG_Result" in input_data and isinstance(input_data["ECG_Result"], (int, float)):
            input_data["ECG_Result"] = ecg_result_map.get(int(input_data["ECG_Result"]), "Normal")

        values = []

        for key in feature_order:
            if key in encoders:
                values.append(encoders[key].transform([input_data[key]])[0])
            else:
                values.append(input_data[key])

        values = np.array(values).reshape(1, -1)
        values = scaler.transform(values)

        prediction = model.predict(values)

        return {"prediction": int(prediction[0])}

    except Exception as e:
        return {"error": str(e)}