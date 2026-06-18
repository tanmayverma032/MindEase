import pandas as pd
df = pd.read_csv(r"C:\Users\Tanmay\Downloads\stress_monitoring_dataset_2000_final_realistic.csv")
for i, col in enumerate(df.columns):
    sample = df[col].dropna().iloc[:2].tolist()
    print(f"{i}: {col}  |  {df[col].dtype}  |  {sample}")
