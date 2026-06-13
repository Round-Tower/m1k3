# Requesting Access to M1K3

M1K3 is a **private, invitation-only** repository. Access is granted manually,
one person at a time, by the Owner (Kevin Murphy). This document explains how to
request access and what to expect.

---

## For people requesting access

1. **Email the Owner** at `kevin@round-tower.ie` with:
   - your full name;
   - your GitHub (or GitLab) handle;
   - who you are / how we're connected;
   - why you'd like access and what you intend to do with it; and
   - whether you intend to contribute code or just evaluate.
2. **Wait for review.** Not all requests are approved. The Owner vets each
   request individually.
3. **Accept the terms.** If approved, you'll be asked to confirm you've read and
   agree to the [LICENSE](LICENSE) and [Access Agreement](ACCESS_AGREEMENT.md)
   before the invite is sent.
4. **Receive your invite.** You'll be added as an individual collaborator with
   the access level appropriate to your purpose (read-only or contributor).

Access is personal and non-transferable. Do not share the repository, your
invite, or its contents with anyone else.

---

## For the Owner — vetting checklist

Before approving a request and sending an invite, confirm:

- [ ] **Identity** — you know who this person is, or have verified them via a
      mutual contact / their public profile.
- [ ] **Purpose** — their stated reason for access is legitimate and specific.
- [ ] **Agreement** — they have explicitly accepted the LICENSE and
      ACCESS_AGREEMENT.md (keep the email/confirmation).
- [ ] **Access level** — decide read-only vs. write. Default to **read-only**;
      grant write only to active contributors.
- [ ] **Record it** — log the grant (see below).

### Granting access (GitHub)

1. Repo must be **Private**: `Settings → General → Danger Zone → Change
   visibility → Private` (verify it already is).
2. Add the person: `Settings → Collaborators → Add people` → enter their handle.
3. Set their role: **Read** (default) or **Write** (contributors only). Avoid
   Admin/Maintain for outside collaborators.
4. (Optional, stronger) Move the repo into a GitHub **Organization** and manage
   access via a Team instead of per-person; this gives an audit log and lets you
   enforce SSO/2FA.

### Recommended repository protections

- Require **2FA** for all collaborators (Org setting, if using an org).
- Protect the default branch: require pull-request review before merge, disable
  force-pushes, and restrict who can push.
- Turn on **secret scanning** and **push protection** (Settings → Code security)
  so credentials can't be pushed by accident.
- Keep the conversation database and other private data **out of the repo**
  (already enforced via `.gitignore`).

### Access log

Keep a simple record of who has access and why. Suggested location:
`docs/access-log/` (gitignored if it contains personal data), one row per grant:

| Name | Handle | Granted | Level | Purpose | Agreement on file | Revoked |
|------|--------|---------|-------|---------|-------------------|---------|

### Revoking access

`Settings → Collaborators → Remove`, or remove them from the Team. Note the date
in the access log. Remind them (per the Access Agreement) to delete local copies.
Rotate any shared secrets if they ever had write access.

---

Questions: `kevin@round-tower.ie`
