import joblib, numpy as np, pandas as pd, warnings
warnings.filterwarnings('ignore')

m = joblib.load('d:/stress_api/model.pkl')
s = joblib.load('d:/stress_api/scaler.pkl')
e = joblib.load('d:/stress_api/encoders.pkl')

fo = ['Eye_Blink_Rate_per_min','Resting_Heart_Rate_BPM','Heart_Rate_After_Exercise_BPM','age','gender','worklife','Sleep_Duration_Hours','Chest_Pain','Cholesterol_mg_dL','ECG_Result','Body_Temperature_C','Steps_per_Day']

df = pd.read_csv(r"C:\Users\Tanmay\Downloads\stress_monitoring_dataset_2000_final_realistic.csv")
rename = {}
for api_name in fo:
    for csv_col in df.columns:
        if csv_col.lower() == api_name.lower() and csv_col != api_name:
            rename[csv_col] = api_name
df = df.rename(columns=rename)

# Only Medium rows, drop NaN
med = df[df['Stress_Level'] == 'Medium'][fo + ['Stress_Level']].dropna()

print("=== GUARANTEED MEDIUM STRESS INPUTS ===\n")
count = 0
for _, row in med.iterrows():
    vals = []
    skip = False
    for k in fo:
        if k in e:
            try: vals.append(e[k].transform([row[k]])[0])
            except: skip = True; break
        else:
            vals.append(float(row[k]))
    if skip: continue

    arr = np.array(vals, dtype=float).reshape(1,-1)
    pred = m.predict(s.transform(arr))[0]
    proba = m.predict_proba(s.transform(arr))[0]

    if pred == 1:
        count += 1
        print(f"--- Input Set {count} (MEDIUM confirmed) ---")
        print(f"  Eye Blink Rate:       {int(row['Eye_Blink_Rate_per_min'])}")
        print(f"  Resting Heart Rate:   {int(row['Resting_Heart_Rate_BPM'])}")
        print(f"  Post-Exercise HR:     {int(row['Heart_Rate_After_Exercise_BPM'])}")
        print(f"  Age:                  {int(row['age'])}")
        print(f"  Gender:               {row['gender']}")
        print(f"  Worklife:             {row['worklife']}")
        print(f"  Sleep Hours:          {row['Sleep_Duration_Hours']}")
        print(f"  Chest Pain:           {row['Chest_Pain']} -> App sends: {'0' if row['Chest_Pain']=='No' else '1' if row['Chest_Pain']=='Mild' else '2'}")
        print(f"  Cholesterol:          {int(row['Cholesterol_mg_dL'])}")
        print(f"  ECG Result:           {row['ECG_Result']} -> App sends: {'0' if row['ECG_Result']=='Normal' else '1'}")
        print(f"  Body Temp:            {row['Body_Temperature_C']}")
        print(f"  Steps/Day:            {int(row['Steps_per_Day'])}")
        print(f"  Confidence:           Low={proba[0]:.0%} Med={proba[1]:.0%} High={proba[2]:.0%}")
        print()
        if count >= 5: break

print(f"Found {count} guaranteed Medium inputs")
