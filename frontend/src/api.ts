// API layer for the GroveMail Spring Boot backend.
// All calls go through the Vite dev proxy (/api -> http://localhost:8080),
// so there are no CORS issues in dev.

export type BackendApp = {
  id: number;
  company: string;
  role: string;
  status: string;
  lastActivity: string; // ISO-8601 UTC, e.g. "2026-10-02T07:46:56Z"
  emailCount: number;
  latestSubject: string;
};

export type BackendEmail = {
  gmailMessageId: string;
  sender: string;
  subject: string;
  date: string; // ISO-8601 UTC
  snippet: string;
  predictedLabel: string;
  correctedLabel: string | null;
  confidence: number;
  source: string;
};

export type BackendAppDetail = BackendApp & {
  emails: BackendEmail[];
};

/** Dashboard table rows. Throws if the backend is unreachable. */
export async function fetchApplications(): Promise<BackendApp[]> {
  const r = await fetch("/api/applications");
  if (!r.ok) throw new Error(`backend: ${r.status}`);
  return r.json();
}

/** One application with its email timeline. Throws if unreachable. */
export async function fetchApplicationDetail(id: number): Promise<BackendAppDetail> {
  const r = await fetch(`/api/applications/${id}`);
  if (!r.ok) throw new Error(`backend: ${r.status}`);
  return r.json();
}

/** Funnel counts: { applied: N, assessment: N, interview: N, ... }. */
export async function fetchFunnel(): Promise<Record<string, number>> {
  const r = await fetch("/api/applications/stats/funnel");
  if (!r.ok) throw new Error(`backend: ${r.status}`);
  return r.json();
}

/** Pull the latest Gmail messages, classify, and group into applications. */
export async function syncApplications(max = 200): Promise<{ added: number }> {
  const r = await fetch(`/api/applications/sync?max=${max}`, { method: "POST" });
  if (!r.ok) throw new Error(`sync: ${r.status}`);
  return r.json();
}

/**
 * Correct one email's label. messageId is the Gmail hex message id
 * (from BackendEmail.gmailMessageId), NOT the numeric application id.
 * label: "applied" | "assessment" | "interview" | "rejection" | "recruiter" | "other"
 */
export async function correctEmail(
  messageId: string,
  label: string
): Promise<{ messageId: string; effectiveLabel: string; applicationId: number }> {
  const r = await fetch("/api/gmail/correct", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ messageId, label }),
  });
  if (!r.ok) throw new Error(`correct: ${r.status}`);
  return r.json();
}

/** Direct Gmail deep link for an email timeline entry. */
export function gmailUrl(messageId: string): string {
  return `https://mail.google.com/mail/u/0/#inbox/${messageId}`;
}
