import os
import joblib
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.metrics import classification_report, confusion_matrix

df = pd.read_csv("labeled_emails.csv")
df["text"] = df["sender"].fillna("") + " " + df["subject"].fillna("") + " " + df["snippet"].fillna("")
df = df[df["label"].notna() & (df["label"] != "")].copy()

df["binary"] = df["label"].apply(lambda l: "other" if l == "other" else "job")
print(df["binary"].value_counts())

X_train, X_test, y_train, y_test = train_test_split(
    df["text"], df["binary"], test_size=0.2, random_state=42, stratify=df["binary"])

pipe = Pipeline([
    ("tfidf", TfidfVectorizer(max_features=5000, ngram_range=(1, 2), stop_words="english")),
    ("clf", LogisticRegression(max_iter=1000, class_weight="balanced")),
])
pipe.fit(X_train, y_train)
pred = pipe.predict(X_test)
print(classification_report(y_test, pred))
print(confusion_matrix(y_test, pred, labels=["job", "other"]))

os.makedirs("model", exist_ok=True)
joblib.dump(pipe, "model/pipeline.joblib")
print("saved to model/pipeline.joblib")
