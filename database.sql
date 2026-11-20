PRAGMA user_version=3;
BEGIN TRANSACTION;
CREATE TABLE events (
 id INTEGER PRIMARY KEY, task_id INTEGER REFERENCES tasks(id),
 member TEXT NOT NULL, client TEXT NOT NULL, kind TEXT NOT NULL,
 text TEXT NOT NULL, data TEXT NOT NULL, created TEXT NOT NULL
);
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE outbox (
 event_id INTEGER PRIMARY KEY REFERENCES events(id), published INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE problems (
 id INTEGER PRIMARY KEY, task_id INTEGER NOT NULL REFERENCES tasks(id),
 member TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'open',
 resolution TEXT, sha TEXT, created TEXT NOT NULL, resolved TEXT
);
CREATE TABLE requests (
 request_id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL,
 status INTEGER NOT NULL, response TEXT NOT NULL, created TEXT NOT NULL
);
CREATE TABLE tasks (
 id INTEGER PRIMARY KEY, title TEXT NOT NULL, criteria TEXT NOT NULL,
 author TEXT NOT NULL, assignee TEXT, status TEXT NOT NULL DEFAULT 'ready',
 owner TEXT, client TEXT, lease_id TEXT, lease_expires REAL,
 checkpoint_sha TEXT, created TEXT NOT NULL, updated TEXT NOT NULL
, phase TEXT NOT NULL DEFAULT 'plan', plan TEXT, branch TEXT, submitter TEXT, builds INTEGER NOT NULL DEFAULT 0, review_notes TEXT);
CREATE TABLE workers (
 member TEXT NOT NULL, client TEXT NOT NULL, data TEXT NOT NULL, updated REAL NOT NULL,
 PRIMARY KEY (member, client)
);
COMMIT;
