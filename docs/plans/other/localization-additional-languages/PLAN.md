# localization-additional-languages — Implementation Plan

Covers: `docs/detailed-spec.md` §10 (Localization File Structure).

**Scope note**: this plan covers only adding translated resource files for the string keys that
already exist. It does not cover the UI strings themselves being introduced (each MVP/other
feature's own PLAN.md is responsible for adding its own string resource keys to the default
`values/strings.xml` as that feature is implemented) — this plan's job is translating whatever
keys already exist at the time it's implemented, and keeping the files in sync afterward.

**Correction at implementation time (supersedes the original resolution note below)**: by the time
this plan was implemented, every MVP/other feature had already added its strings directly to the
default `values/strings.xml` in **Japanese**, not English — the "default = English" design this
plan originally specified was never actually followed in practice, across every feature that
shipped before this one. Rather than a large, risky rewrite (moving every existing Japanese string
out of the default folder into `values-ja/` and back-translating the default folder to English),
the plan was corrected to match reality: `values/` (default, unqualified) stays Japanese, and a new
`values-en/` folder was added for English — the reverse of the original design, but the only change
that requires touching zero existing code or resource references.

**Original resolution note (superseded, kept for history)**: `docs/detailed-spec.md` §10 explicitly
stated `values/strings.xml` (the unqualified/default resource folder) should be **English**, not
Japanese — confirmed with `designer-lite` during the original planning pass, since an earlier
assumption floated during planning (that the default should be Japanese, given the project's
Japan-centric usage context) turned out not to be backed by any recorded decision at the time. That
plan was never enforced during actual implementation; see the correction above.

## Index

1. File Layout
2. Translation Workflow
3. Keeping Files in Sync
4. Cross-Feature Touchpoints

## 1. File Layout

```
res/values/strings.xml        # default — Japanese (see the correction note above)
res/values-en/strings.xml     # English
res/values-zh-rCN/strings.xml # Simplified Chinese — zh-rCN folder naming, not Traditional Chinese
```

Android's resource-resolution system automatically picks `values-en/` on English-locale devices
and `values-zh-rCN/` on Simplified-Chinese-locale devices, falling back to unqualified `values/`
(Japanese) for any other device locale — no in-app language-switching logic is needed; this is
standard `Configuration`-driven resource resolution.

## 2. Translation Workflow

At the point this feature is implemented, enumerate every string resource key already defined
across all MVP and other features' `values/strings.xml` entries (onboarding copy, settings labels,
FGS notification text, permission descriptions, etc.), and produce a matching
`values-en/strings.xml` and `values-zh-rCN/strings.xml` with the same keys, translated. No new
keys are introduced by this feature — it is a pure translation pass over the existing key set.

## 3. Keeping Files in Sync

Since this feature is implemented after MVP (and potentially after some other deferred features,
depending on implementation order), any string keys added by features implemented *after* this one
will initially only exist in `values/strings.xml` (Japanese) until a follow-up translation pass adds
them to `values-en/`/`values-zh-rCN/`. This is an accepted, ongoing maintenance task, not a one-time
gap to close — flagged here so it isn't mistaken for an oversight when a later-added feature's
strings aren't yet translated.

## 4. Cross-Feature Touchpoints

- Depends on every other feature's `values/strings.xml` string keys already existing before this
  feature's translation pass — self-contained in mechanism, but its actual translation content is
  necessarily downstream of whichever features have shipped strings by the time this is done.
