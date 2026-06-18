"""
Retrain stress model with column names matching stress_api/app.py feature_order exactly.
"""
import pandas as pd
import numpy as np
import joblib
import shutil
import os
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import LabelEncoder, StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, confusion_matrix

DATASET = r"C:\Users\Tanmay\Downloads\stress_monitoring_dataset_2000_final_realistic.csv"
OUT = r"d:\stress_api"
BACKUP = os.path.join(OUT, "backup_old_model")
TARGET = "Stress_Level"

# The API's exact feature_order (from stress_api/app.py line 21-34)
API_FEATURE_ORDER = [
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

# ─── Load ────────────────────────────────
print("Loading dataset...")
df = pd.read_csv(DATASET)
csv_features = [c for c in df.columns if c != TARGET]
print(f"  CSV columns: {csv_features}")
print(f"  API columns: {API_FEATURE_ORDER}")

# ─── Build column name mapping (CSV -> API) ────
# Match case-insensitively
csv_lower_map = {c.lower(): c for c in csv_features}
col_rename = {}
for api_name in API_FEATURE_ORDER:
    if api_name in csv_features:
        pass  # exact match
    elif api_name.lower() in csv_lower_map:
        csv_name = csv_lower_map[api_name.lower()]
        col_rename[csv_name] = api_name
        print(f"  Rename: '{csv_name}' -> '{api_name}'")
    else:
        print(f"  WARNING: No match for '{api_name}'!")

if col_rename:
    df = df.rename(columns=col_rename)
    print(f"\n  Renamed {len(col_rename)} columns")

# Verify all API features exist after rename
for f in API_FEATURE_ORDER:
    if f not in df.columns:
        print(f"  MISSING: '{f}' not in columns!")
        print(f"  Available: {df.columns.tolist()}")
        exit(1)

print(f"\nTarget distribution:\n{df[TARGET].value_counts()}\n")

# ─── Clean ───────────────────────────────
df_clean = df[API_FEATURE_ORDER + [TARGET]].dropna()
print(f"After dropna: {len(df_clean)} rows")

# ─── Encode ──────────────────────────────
print("\nEncoding...")
encoders = {}
X = df_clean[API_FEATURE_ORDER].copy()

for col in API_FEATURE_ORDER:
    try:
        X[col] = pd.to_numeric(X[col])
    except (ValueError, TypeError):
        le = LabelEncoder()
        X[col] = le.fit_transform(X[col].astype(str))
        encoders[col] = le
        print(f"  '{col}': {le.classes_.tolist()}")

y = df_clean[TARGET].str.strip().map({"Low": 0, "Medium": 1, "High": 2})
valid = ~y.isna()
X, y = X[valid], y[valid].astype(int)

print(f"\nClasses: Low={sum(y==0)}, Medium={sum(y==1)}, High={sum(y==2)}")

# ─── Scale & Train ───────────────────────
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)

X_tr, X_te, y_tr, y_te = train_test_split(X_scaled, y, test_size=0.2, random_state=42, stratify=y)

model = RandomForestClassifier(
    n_estimators=200, max_depth=15, min_samples_split=5, min_samples_leaf=2,
    class_weight='balanced', random_state=42, n_jobs=-1
)
model.fit(X_tr, y_tr)

# ─── Evaluate ────────────────────────────
y_pred = model.predict(X_te)
print("\n" + classification_report(y_te, y_pred, target_names=["Low", "Medium", "High"]))
print("Confusion Matrix:")
print(confusion_matrix(y_te, y_pred))
print(f"\nModel classes: {model.classes_}")
print(f"Encoder keys: {list(encoders.keys())}")

# ─── Verify alignment with API ───────────
print("\n--- Verification ---")
print(f"Feature order matches API: {API_FEATURE_ORDER}")
print(f"Encoders match API keys: {all(k in API_FEATURE_ORDER for k in encoders)}")

# ─── Save ────────────────────────────────
os.makedirs(BACKUP, exist_ok=True)
for f in ["model.pkl", "scaler.pkl", "encoders.pkl"]:
    src = os.path.join(OUT, f)
    if os.path.exists(src):
        shutil.copy2(src, os.path.join(BACKUP, f))

joblib.dump(model, os.path.join(OUT, "model.pkl"))
joblib.dump(scaler, os.path.join(OUT, "scaler.pkl"))
joblib.dump(encoders, os.path.join(OUT, "encoders.pkl"))
print(f"\n✅ Saved model, scaler, encoders to {OUT}")

# ─── Quick test with API-format input ────
print("\n--- Quick test (API format) ---")
stress_map = {0: "Low", 1: "Medium", 2: "High"}

tests = [
    ("Low stress", {"Eye_Blink_Rate_per_min": 25, "Resting_Heart_Rate_BPM": 68,
        "Heart_Rate_After_Exercise_BPM": 100, "age": 25, "gender": "Male",
        "worklife": "Student", "Sleep_Duration_Hours": 8.0, "Chest_Pain": "No",
        "Cholesterol_mg_dL": 170, "ECG_Result": "Normal", "Body_Temperature_C": 36.8,
        "Steps_per_Day": 10000}),
    ("Med stress", {"Eye_Blink_Rate_per_min": 20, "Resting_Heart_Rate_BPM": 82,
        "Heart_Rate_After_Exercise_BPM": 135, "age": 35, "gender": "Female",
        "worklife": "Student", "Sleep_Duration_Hours": 5.5, "Chest_Pain": "Mild",
        "Cholesterol_mg_dL": 210, "ECG_Result": "Sinus_Tachycardia", "Body_Temperature_C": 37.2,
        "Steps_per_Day": 5000}),
    ("High stress", {"Eye_Blink_Rate_per_min": 15, "Resting_Heart_Rate_BPM": 95,
        "Heart_Rate_After_Exercise_BPM": 170, "age": 50, "gender": "Male",
        "worklife": "Student", "Sleep_Duration_Hours": 3.0, "Chest_Pain": "Yes",
        "Cholesterol_mg_dL": 280, "ECG_Result": "Sinus_Tachycardia", "Body_Temperature_C": 38.0,
        "Steps_per_Day": 2000})
]

for name, data in tests:
    values = []
    for key in API_FEATURE_ORDER:
        if key in encoders:
            try:
                values.append(encoders[key].transform([data[key]])[0])
            except:
                values.append(0)
        else:
            values.append(data[key])
    arr = np.array(values, dtype=float).reshape(1, -1)
    pred = model.predict(scaler.transform(arr))[0]
    proba = model.predict_proba(scaler.transform(arr))[0]
    print(f"  {name}: {stress_map[pred]} (L={proba[0]:.2f}, M={proba[1]:.2f}, H={proba[2]:.2f})")

print("\nDONE! Redeploy stress_api to Render with new files.")
