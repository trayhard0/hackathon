import pandas as pd, joblib
from sklearn.model_selection import cross_val_score

df = pd.read_csv("labeled_emails.csv")
df["text"] = df["sender"].fillna("") + " " + df["subject"].fillna("") + " " + df["snippet"].fillna("")
df["binary"] = df["label"].apply(lambda l: "other" if l == "other" else "job")
pipe = joblib.load("model/pipeline.joblib")

scores = cross_val_score(pipe, df["text"], df["binary"], cv=5, scoring="f1_macro")
print("5-fold macro F1:", [round(s, 3) for s in scores], "mean:", round(scores.mean(), 3))

hard = df[df["predicted_label"] != df["label"]]
pred = pipe.predict(hard["text"])
print("hard-subset accuracy:", round((pred == hard["binary"]).mean(), 3), f"({len(hard)} rows)")
