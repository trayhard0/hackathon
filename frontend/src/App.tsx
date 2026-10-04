import { useEffect, useMemo, useRef, useState } from "react";
import { fetchApplications, syncApplications, applyAllLabels, updateApplication, type BackendApp } from "./api";

type Job = {
    id: string;
    company: string;
    role: string;
    location: string;
    date: string;
    detail: string;
    notes: string;
    /** ISO date (YYYY-MM-DD) used for the recent / older-than-90-days filter. */
    dateAdded: string;
    // TODO(backend): real Gmail message URL, e.g. https://mail.google.com/mail/u/0/#all/<messageId>
    emailUrl?: string;
};

type ColumnKey = "progress" | "assessment" | "interview" | "rejected";

type Column = {
    key: ColumnKey;
    title: string;
    subtitle: string;
    jobs: Job[];
};

// Label for the per-job email link, tuned to each stage.
const emailLinkLabel: Record<ColumnKey, string> = {
    progress: "View confirmation email",
    assessment: "View assessment email",
    interview: "View interview email",
    rejected: "View rejection email",
};

function emailUrlFor(job: Job): string {
    return (
        job.emailUrl ||
        `https://mail.google.com/mail/u/0/#search/${encodeURIComponent(job.company)}`
    );
}

// Board structure only — jobs come from the backend (/api/applications).
const columnDefs: { key: ColumnKey; title: string; subtitle: string }[] = [
    { key: "progress", title: "Jobs in progress", subtitle: "Submitted, waiting to hear back" },
    { key: "assessment", title: "Online Assessment", subtitle: "Assessment requested by employer" },
    { key: "interview", title: "Interview", subtitle: "Assessment accepted · date booked" },
    { key: "rejected", title: "Rejections", subtitle: "Closed, with room for new growth" },
];

function toJob(app: BackendApp): Job {
    return {
        id: `A${app.id}`,
        company: app.company,
        role: app.role === "Unknown" ? "Role not detected" : app.role,
        location: "",
        date: `Active ${new Date(app.lastActivity).toLocaleDateString("en-US", { month: "short", day: "numeric" })}`,
        detail: app.latestSubject,
        notes: `${app.emailCount} email${app.emailCount === 1 ? "" : "s"} in timeline`,
        dateAdded: app.lastActivity.slice(0, 10),
    };
}

function toColumns(apps: BackendApp[]): Column[] {
    const buckets: Record<ColumnKey, Job[]> = {
        progress: [],
        assessment: [],
        interview: [],
        rejected: [],
    };
    for (const app of apps) {
        // The Unknown/Unknown bucket collects unparseable senders — hide it.
        if (app.company === "Unknown" && app.role === "Unknown") continue;
        const key: ColumnKey =
            app.status === "assessment"
                ? "assessment"
                : app.status === "interview"
                    ? "interview"
                    : app.status === "rejection"
                        ? "rejected"
                        : "progress";
        buckets[key].push(toJob(app));
    }
    return columnDefs.map((d) => ({ ...d, jobs: buckets[d.key] }));
}

const plantFor: Record<ColumnKey, string> = {
    progress: "seed",
    assessment: "sprout",
    interview: "branch",
    rejected: "compost",
};

const stageLabel: Record<ColumnKey, string> = {
    progress: "Applied",
    assessment: "Assessment",
    interview: "Interview",
    rejected: "Compost",
};

function Logo() {
    return (
        <svg className="logo-mark" viewBox="0 0 34 25" aria-hidden="true">
            <path d="M16 21C8 19 4 14 4 7c7-1 13 3 13 10" />
            <path d="M20 12C21 5 26 2 32 3c0 7-4 11-11 11" />
            <path d="M8 9c5 3 8 7 10 13" />
        </svg>
    );
}

function StageIcon({ type }: { type: ColumnKey }) {
    if (type === "progress") {
        return (
            <svg viewBox="0 0 20 20" aria-hidden="true">
                <circle cx="10" cy="10" r="7.2" />
                <circle cx="10" cy="10" r="1.2" className="filled" />
            </svg>
        );
    }
    if (type === "assessment") {
        return (
            <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M10 17V9m0 3c-4 0-6-2-6-5 4 0 6 2 6 5Zm0-3c0-4 2-6 6-6 0 4-2 6-6 6ZM5 17h10" />
            </svg>
        );
    }
    if (type === "interview") {
        return (
            <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M6 17V4m0 6 7-4" />
                <circle cx="6" cy="4" r="1.3" />
                <circle cx="14" cy="5.5" r="1.3" />
            </svg>
        );
    }
    return (
        <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="m7 4 2-2 2 2m-3-1 1 5-4 2m1-1-3 1 1 3m11 0 2-1-1-3m0 3-5 2-3-3m5 2 1 4m-7-4-1 4h9" />
        </svg>
    );
}

function Plant({ type }: { type: string }) {
    if (type === "seed") {
        return (
            <svg viewBox="0 0 80 120" aria-hidden="true">
                <ellipse className="shadow" cx="40" cy="105" rx="26" ry="4" />
                <path className="soil" d="M33 91c0-10 7-20 12-25 8 8 12 16 10 23-3 10-18 11-22 2Z" />
            </svg>
        );
    }
    if (type === "sprout") {
        return (
            <svg viewBox="0 0 100 130" aria-hidden="true">
                <ellipse className="shadow" cx="50" cy="116" rx="31" ry="4" />
                <path className="wood" d="M50 115V58" />
                <path className="leaf" d="M49 70C32 69 24 60 23 47c14-1 27 7 27 21Z" />
                <path className="leaf" d="M51 58c3-16 13-24 30-24-1 16-12 26-30 27Z" />
                <path className="vein" d="M28 50c9 6 16 12 21 20m27-31C65 45 58 51 51 59" />
            </svg>
        );
    }
    if (type === "branch") {
        return (
            <svg viewBox="0 0 105 140" aria-hidden="true">
                <ellipse className="shadow" cx="52" cy="126" rx="31" ry="4" />
                <path className="wood branch-line" d="M52 126V48m0 31L31 59m21 43 24-28m-24-11 17-26" />
                <path className="leaf" d="M28 61c-11-5-15-13-11-22 11 3 17 10 14 21Z" />
                <path className="leaf" d="M67 40c-4-11 0-19 8-23 7 9 6 17-5 24Z" />
                <path className="leaf" d="M74 75c4-12 12-17 23-15-1 12-9 19-22 18Z" />
                <path className="leaf" d="M51 63c-9-7-11-15-5-23 10 5 14 13 8 23Z" />
                <path className="leaf" d="M52 82c-12-2-19-9-18-19 11 0 20 6 20 18Z" />
            </svg>
        );
    }
    if (type === "tree") {
        return (
            <svg viewBox="0 0 130 160" aria-hidden="true">
                <ellipse className="shadow" cx="65" cy="146" rx="42" ry="5" />
                <path className="trunk" d="m55 145 7-68h11l6 68Z" />
                <path className="tree-crown" d="M37 93C18 91 12 70 23 56c-7-18 5-36 23-37C55 2 81 3 90 21c19 0 31 18 25 35 12 14 5 36-13 40-10 11-29 12-38 3-9 8-21 6-27-6Z" />
            </svg>
        );
    }
    return (
        <svg viewBox="0 0 100 120" aria-hidden="true">
            <ellipse className="shadow" cx="50" cy="105" rx="32" ry="4" />
            <path className="compost" d="M29 99c3-17 13-28 28-28 15 1 23 12 25 28Z" />
            <path className="leaf" d="M58 73c3-15 13-23 28-22-1 15-10 24-28 25Z" />
            <path className="wood" d="m53 91 11-22" />
        </svg>
    );
}

function JobCard({ job, type }: { job: Job; type: ColumnKey }) {
    return (
        <article className={`job-card ${type === "rejected" ? "job-card--rejected" : ""}`}>
            <strong className="job-company">{job.company}</strong>
            <div className="job-role">{job.role}</div>
        </article>
    );
}

type FilterMode = "recent" | "latest" | "old";

const filterOptions: { value: FilterMode; label: string }[] = [
    { value: "recent", label: "Most recent" },
    { value: "latest", label: "Latest added" },
    { value: "old", label: "Older than 90 days" },
];

function jobNumber(id: string): number {
    const n = parseInt(id.replace(/\D/g, ""), 10);
    return isNaN(n) ? 0 : n;
}

// Sort/filter the jobs in a section according to the chosen filter mode.
// - "recent": newest activity date first (default)
// - "latest": newest planted listing first (job ID order)
// - "old": only listings older than 90 days, oldest first
function applyFilter(jobs: Job[], mode: FilterMode): Job[] {
    const copy = [...jobs];
    if (mode === "recent") {
        copy.sort((a, b) => b.dateAdded.localeCompare(a.dateAdded));
        return copy;
    }
    if (mode === "latest") {
        copy.sort((a, b) => jobNumber(b.id) - jobNumber(a.id));
        return copy;
    }
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - 90);
    const cutoffStr = cutoff.toISOString().slice(0, 10);
    return copy
        .filter((j) => j.dateAdded < cutoffStr)
        .sort((a, b) => a.dateAdded.localeCompare(b.dateAdded));
}

function FilterDropdown({
                            value,
                            onChange,
                        }: {
    value: FilterMode;
    onChange: (v: FilterMode) => void;
}) {
    const [open, setOpen] = useState(false);
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handler = (e: MouseEvent) => {
            if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, []);

    const current = filterOptions.find((o) => o.value === value)!;

    return (
        <div className="filter-row">
            <span className="filter-label">Filter</span>
            <div className="filter-box" ref={ref}>
                <button
                    type="button"
                    className="filter-toggle"
                    onClick={() => setOpen((o) => !o)}
                    aria-haspopup="listbox"
                    aria-expanded={open}
                >
                    <span>{current.label}</span>
                    <span className={`filter-arrow${open ? " filter-arrow--open" : ""}`} aria-hidden="true">
            ▾
          </span>
                </button>
                {open && (
                    <ul className="filter-menu" role="listbox">
                        {filterOptions.map((opt) => (
                            <li key={opt.value}>
                                <button
                                    type="button"
                                    role="option"
                                    aria-selected={opt.value === value}
                                    className={`filter-option${opt.value === value ? " filter-option--active" : ""}`}
                                    onClick={() => {
                                        onChange(opt.value);
                                        setOpen(false);
                                    }}
                                >
                                    {opt.label}
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </div>
    );
}

function SiteHeader({ onHome }: { onHome: () => void }) {
    return (
        <header className="site-header">
            <button className="brand brand-button" onClick={onHome} aria-label="Back to journal home">
                <Logo />
                <span className="brand-name">grove</span>
                <span className="brand-divider">/</span>
                <span className="brand-note">application journal</span>
            </button>
            <nav className="main-nav" aria-label="Primary navigation">
                <button className="nav-link nav-active" onClick={onHome}>
                    My applications
                </button>
                <span>Notes</span>
                <span className="profile">JL</span>
            </nav>
        </header>
    );
}

type JobFormValues = {
    company: string;
    role: string;
    location: string;
    date: string;
    detail: string;
    notes: string;
};

// Shared add / edit dialog for a job listing.
function JobModal({
                      title,
                      eyebrow,
                      initial,
                      submitLabel,
                      onClose,
                      onSubmit,
                  }: {
    title: string;
    eyebrow: string;
    initial: JobFormValues;
    submitLabel: string;
    onClose: () => void;
    onSubmit: (values: JobFormValues) => void;
}) {
    const [values, setValues] = useState<JobFormValues>(initial);
    const set = (key: keyof JobFormValues) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
        setValues((v) => ({ ...v, [key]: e.target.value }));
    const valid = values.company.trim() !== "" && values.role.trim() !== "";

    return (
        <div className="modal-veil" onClick={onClose} role="presentation">
            <div
                className="modal"
                role="dialog"
                aria-modal="true"
                aria-label={title}
                onClick={(e) => e.stopPropagation()}
            >
                <div className="modal-head">
                    <div>
                        <p className="modal-eyebrow">{eyebrow}</p>
                        <h3 className="modal-title">{title}</h3>
                    </div>
                    <button className="modal-close" onClick={onClose} aria-label="Close" type="button">
                        ✕
                    </button>
                </div>
                <form
                    className="job-form"
                    onSubmit={(e) => {
                        e.preventDefault();
                        if (valid) onSubmit(values);
                    }}
                >
                    <div className="job-form-row">
                        <label>
                            Company
                            <input value={values.company} onChange={set("company")} placeholder="Acme Corp" autoFocus />
                        </label>
                        <label>
                            Role
                            <input value={values.role} onChange={set("role")} placeholder="Product Designer" />
                        </label>
                    </div>
                    <div className="job-form-row">
                        <label>
                            Location
                            <input value={values.location} onChange={set("location")} placeholder="Remote" />
                        </label>
                        <label>
                            Date
                            <input value={values.date} onChange={set("date")} placeholder="Applied Oct 3" />
                        </label>
                    </div>
                    <label>
                        Detail <span className="label-hint">(stage info, e.g. due date or round)</span>
                        <input value={values.detail} onChange={set("detail")} placeholder="HackerRank · Due Oct 6" />
                    </label>
                    <label>
                        Notes
                        <textarea value={values.notes} onChange={set("notes")} placeholder="Anything worth remembering…" rows={3} />
                    </label>
                    <div className="modal-actions">
                        <button type="button" className="modal-cancel" onClick={onClose}>
                            Cancel
                        </button>
                        <button type="submit" className="plant-new-button" disabled={!valid}>
                            {submitLabel}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

function DetailCard({
                        job,
                        columnKey,
                        onEdit,
                    }: {
    job: Job;
    columnKey: ColumnKey;
    onEdit: () => void;
}) {
    const emailLabel = emailLinkLabel[columnKey];
    return (
        <article className="detail-card">
            <div className={`detail-plant detail-plant--${plantFor[columnKey]}`}>
                <Plant type={plantFor[columnKey]} />
            </div>
            <div className="detail-body">
                <div className="detail-top">
                    <strong className="detail-company">{job.company}</strong>
                    <button className="edit-button" onClick={onEdit} type="button" aria-label={`Edit ${job.company}`}>
                        ✎ Edit
                    </button>
                </div>
                <div className="detail-role">
                    {job.role}{job.location ? ` · ${job.location}` : ""}
                </div>
                <div className="detail-meta">
                    <span>{job.date}</span>
                    {columnKey === "progress" ? (
                        <>
                            <i>·</i>
                            <a className="email-link" href={emailUrlFor(job)} target="_blank" rel="noreferrer">
                                {emailLabel} ↗
                            </a>
                        </>
                    ) : (
                        <>
                            <i>·</i>
                            <span className="green-text">{job.detail}</span>
                            <i>·</i>
                            <a className="email-link" href={emailUrlFor(job)} target="_blank" rel="noreferrer">
                                {emailLabel} ↗
                            </a>
                        </>
                    )}
                </div>
                <p className="detail-notes">{job.notes}</p>
            </div>
        </article>
    );
}

function SectionPage({
                         column,
                         onBack,
                         onAddJob,
                         onEditJob,
                     }: {
    column: Column;
    onBack: () => void;
    onAddJob: (key: ColumnKey, values: JobFormValues) => void;
    onEditJob: (key: ColumnKey, id: string, values: JobFormValues) => void;
}) {
    const [query, setQuery] = useState("");
    const [filterMode, setFilterMode] = useState<FilterMode>("recent");
    const [adding, setAdding] = useState(false);
    const [editing, setEditing] = useState<Job | null>(null);

    const q = query.trim().toLowerCase();
    const filtered = useMemo(() => {
        const base = q
            ? column.jobs.filter((job) =>
                [job.company, job.role, job.location, job.id, job.detail]
                    .join(" ")
                    .toLowerCase()
                    .includes(q)
            )
            : column.jobs;
        return applyFilter(base, filterMode);
    }, [column.jobs, q, filterMode]);

    return (
        <main className="page">
            <SiteHeader onHome={onBack} />
            <div className="content">
                <button className="back-link" onClick={onBack} type="button">
                    ← Back to journal
                </button>

                <section className="section-hero">
                    <div className="section-hero-main">
                        <div className="section-hero-icon">
                            <StageIcon type={column.key} />
                        </div>
                        <div className="overline">YOUR CAREER, CULTIVATED</div>
                        <h1 className="section-title">{column.title}</h1>
                        <p className="section-subtitle">
                            {column.subtitle} ·{" "}
                            <span className="green-text">
                {column.jobs.length} {column.jobs.length === 1 ? "job" : "jobs"}
              </span>
                        </p>
                    </div>
                    <div className="section-hero-actions">
                        <button className="plant-new-button" type="button" onClick={() => setAdding(true)}>
                            <span>＋</span> Plant New
                        </button>
                        <FilterDropdown value={filterMode} onChange={setFilterMode} />
                        <label className="search-wrap" aria-label={`Search ${column.title}`}>
                            <span className="search-icon" aria-hidden="true">⌕</span>
                            <input
                                className="search-input"
                                type="search"
                                value={query}
                                onChange={(e) => setQuery(e.target.value)}
                                placeholder={`Search ${column.title.toLowerCase()}…`}
                            />
                        </label>
                    </div>
                </section>

                <section className="detail-list" aria-label={`${column.title} jobs`}>
                    {filtered.length === 0 ? (
                        <p className="search-empty">
                            {q
                                ? `No matches for “${query.trim()}” in ${column.title.toLowerCase()}.`
                                : filterMode === "old"
                                    ? "Nothing older than 90 days in this section."
                                    : `Nothing in ${column.title.toLowerCase()} yet.`}
                        </p>
                    ) : (
                        filtered.map((job) => (
                            <DetailCard
                                key={job.id}
                                job={job}
                                columnKey={column.key}
                                onEdit={() => setEditing(job)}
                            />
                        ))
                    )}
                </section>

                <footer className="footer">
          <span>
            {column.jobs.length} {column.title.toLowerCase()} ·{" "}
              {column.key === "rejected"
                  ? "Every ending feeds the next beginning."
                  : "One step at a time."}
          </span>
                    <span>grove · application journal</span>
                </footer>
            </div>

            {adding && (
                <JobModal
                    eyebrow={column.title}
                    title="Plant a new job"
                    initial={{ company: "", role: "", location: "", date: "", detail: "", notes: "" }}
                    submitLabel="Plant it"
                    onClose={() => setAdding(false)}
                    onSubmit={(values) => {
                        onAddJob(column.key, values);
                        setAdding(false);
                    }}
                />
            )}
            {editing && (
                <JobModal
                    eyebrow={column.title}
                    title={`Edit ${editing.company}`}
                    initial={{
                        company: editing.company,
                        role: editing.role === "Role not detected" ? "" : editing.role,
                        location: editing.location,
                        date: editing.date,
                        detail: editing.detail,
                        notes: editing.notes,
                    }}
                    submitLabel="Save changes"
                    onClose={() => setEditing(null)}
                    onSubmit={(values) => {
                        onEditJob(column.key, editing.id, values);
                        setEditing(null);
                    }}
                />
            )}
        </main>
    );
}

type Route = { name: "home" } | { name: "section"; key: ColumnKey };

function nextId(columnsState: Column[]): string {
    const nums = columnsState.flatMap((c) =>
        c.jobs.map((j) => parseInt(j.id.replace(/\D/g, ""), 10)).filter((n) => !isNaN(n))
    );
    const max = nums.length ? Math.max(...nums) : 0;
    return `G${String(max + 1).padStart(2, "0")}`;
}

export default function App() {
    const [columnsState, setColumnsState] = useState<Column[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState(false);
    const [syncing, setSyncing] = useState(false);
    const [organizing, setOrganizing] = useState(false);
    const [organizeMsg, setOrganizeMsg] = useState<string | null>(null);
    const [route, setRoute] = useState<Route>({ name: "home" });

    const load = async () => {
        setLoading(true);
        setLoadError(false);
        try {
            setColumnsState(toColumns(await fetchApplications()));
        } catch {
            setLoadError(true);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
    }, []);

    const handleSync = async () => {
        setSyncing(true);
        try {
            await syncApplications(50);
            await load();
        } catch {
            setLoadError(true);
        } finally {
            setSyncing(false);
        }
    };

    const handleOrganize = async () => {
        setOrganizing(true);
        setOrganizeMsg(null);
        try {
            const counts = await applyAllLabels();
            const total = Object.values(counts).reduce((a, b) => a + b, 0);
            setOrganizeMsg(`Labeled ${total} emails in Gmail ✓`);
        } catch {
            setOrganizeMsg("Couldn't reach Gmail — is the backend up?");
        } finally {
            setOrganizing(false);
        }
    };


    useEffect(() => {
        window.scrollTo(0, 0);
    }, [route]);

    const goHome = () => setRoute({ name: "home" });
    const goSection = (key: ColumnKey) => setRoute({ name: "section", key });

    const addJob = (key: ColumnKey, values: JobFormValues) => {
        setColumnsState((prev) => {
            const id = nextId(prev);
            const dateAdded = new Date().toISOString().slice(0, 10);
            return prev.map((c) =>
                c.key === key
                    ? { ...c, jobs: [{ id, dateAdded, ...values }, ...c.jobs] }
                    : c
            );
        });
    };

    const editJob = async (key: ColumnKey, id: string, values: JobFormValues) => {
        const appId = parseInt(id.replace(/\D/g, ""), 10);

        // Optimistic local update so the UI feels instant.
        setColumnsState((prev) =>
            prev.map((c) =>
                c.key === key
                    ? { ...c, jobs: c.jobs.map((j) => (j.id === id ? { ...j, ...values } : j)) }
                    : c
            )
        );

        // Persist company/role to the backend so the edit survives a refresh.
        try {
            const role = values.role.trim() === "" ? "Unknown" : values.role.trim();
            await updateApplication(appId, { company: values.company.trim(), role });
        } catch {
            await load(); // revert to backend truth on failure
        }
    };

    // Forest stays in sync with the columns: every job gets a plant.
    const forest = [
        ...columnsState.flatMap((column) =>
            column.jobs.map((job) => ({
                id: job.id,
                company: job.company,
                stage: stageLabel[column.key],
                plant: plantFor[column.key],
            }))
        ),
    ];

    if (route.name === "section") {
        const column = columnsState.find((c) => c.key === route.key)!;
        return (
            <SectionPage column={column} onBack={goHome} onAddJob={addJob} onEditJob={editJob} />
        );
    }

    const growing = columnsState
        .filter((c) => c.key !== "rejected")
        .reduce((sum, c) => sum + c.jobs.length, 0);

    return (
        <main className="page">
            <SiteHeader onHome={goHome} />

            <div className="content">
                <section className="hero">
                    <div className="hero-copy">
                        <div className="overline">YOUR CAREER, CULTIVATED&nbsp; / &nbsp;{new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }).toUpperCase()}</div>
                        <div className="hero-title">Good things take root.</div>
                        <div className="hero-stats">
                            <span>{growing} applications growing</span>
                            <i>/</i>
                            <em>One step at a time.</em>
                        </div>
                    </div>
                    <div className="hero-actions">
                        <button className="plant-button" type="button" onClick={handleOrganize} disabled={organizing}>
                            <span>✦</span> {organizing ? "Organizing…" : "Organize Gmail"}
                        </button>
                        <button className="plant-button" type="button" onClick={handleSync} disabled={syncing}>
                            <span>＋</span> {syncing ? "Syncing…" : "Sync Gmail"}
                        </button>
                        {organizeMsg && <p className="organize-msg">{organizeMsg}</p>}
                    </div>
                </section>

                <section className="applications">
                    <div className="section-heading">
                        <strong>Your applications</strong>
                        <span>Latest activity⌄</span>
                    </div>
                    {loading ? (
                        <p className="search-empty">Loading your grove…</p>
                    ) : loadError ? (
                        <div className="search-empty">
                            <p>Couldn't reach the backend.</p>
                            <p>Start Spring Boot (:8080) and the classifier (:8000), then sync.</p>
                            <button type="button" className="plant-new-button" onClick={load}>
                                Retry
                            </button>
                        </div>
                    ) : (
                        <div className="board">
                            {columnsState.map((column) => (
                                <section className="board-column" key={column.key}>
                                    <div className="column-title-row">
                                        <StageIcon type={column.key} />
                                        <strong>{column.title}</strong>
                                        <span className="count">{column.jobs.length}</span>
                                    </div>
                                    <div className="column-subtitle">{column.subtitle}</div>
                                    <div className="job-list">
                                        {column.jobs.slice(0, 10).map((job) => (
                                            <JobCard key={job.id} job={job} type={column.key} />
                                        ))}
                                    </div>
                                    <button
                                        className="view-all-button"
                                        type="button"
                                        onClick={() => goSection(column.key)}
                                    >
                                        View all
                                    </button>
                                </section>
                            ))}
                        </div>
                    )}
                </section>

                <section className="forest-section">
                    <div className="forest-header">
                        <div className="forest-heading">
                            <span>Your little forest</span>
                            <small>Every application has a place to grow.</small>
                        </div>
                        <div className="forest-note">Job IDs match the applications above.</div>
                    </div>

                    <div className="forest-panel">
                        <div className="forest-grid">
                            {forest.map((item) => (
                                <div className={`plant-item plant-item--${item.plant}`} key={item.id}>
                                    <div className="plant-art">
                                        <Plant type={item.plant} />
                                    </div>
                                    <strong>{item.company}</strong>
                                    <span>{item.id} · {item.stage}</span>
                                </div>
                            ))}
                        </div>
                        <div className="legend">
                            <span><i />Seed · Applied</span>
                            <span><i />Sprout · Online assessment</span>
                            <span><i />Branches · Interview scheduled</span>
                            <span className="legend-compost"><i />Compost · Rejected</span>
                        </div>
                    </div>
                </section>

                <footer className="footer">
                    <span>One step at a time.</span>
                    <span>grove · application journal</span>
                </footer>
            </div>
        </main>
    );
}
